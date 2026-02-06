package com.alirezabeigy.paqetng.paqet

import android.content.Context
import android.os.Build
import com.alirezabeigy.paqetng.data.AppLogBuffer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/**
 * Extracts the paqet binary from assets, checks root, and runs/stops paqet with a given config path.
 * Captures paqet stdout/stderr into [logBuffer] when provided.
 */
class PaqetRunner(
    private val context: Context,
    private val logBuffer: AppLogBuffer? = null
) {

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _rootAvailable = MutableStateFlow<Boolean?>(null)
    val rootAvailable: StateFlow<Boolean?> = _rootAvailable.asStateFlow()

    private var process: Process? = null

    /** When true, process exit is due to [stop()]; do not treat as crash. */
    @Volatile
    private var stopRequested = false

    /** Last config used for start; kept so we can restart on crash. Cleared on [stop()]. */
    private var lastConfigYaml: String? = null
    private var lastSessionSummary: String? = null

    /** Called when paqet exits unexpectedly (crash). Arguments are last config for restart. */
    var onCrashed: ((configYaml: String, sessionSummary: String?) -> Unit)? = null

    private val paqetDir: File
        get() = File(context.filesDir, "paqet").also { if (!it.exists()) it.mkdirs() }

    private val binaryFile: File
        get() = File(paqetDir, "paqet")

    /**
     * Asset path for the paqet binary: ABI-specific (e.g. arm64-v8a/paqet) if built by buildPaqet, else "paqet".
     */
    private fun getPaqetAssetPath(): String {
        val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: return ASSET_BINARY_NAME
        val abiPath = "$abi/$ASSET_BINARY_NAME"
        return try {
            context.assets.open(abiPath).close()
            abiPath
        } catch (_: IOException) {
            ASSET_BINARY_NAME
        }
    }

    /**
     * Copies the paqet binary from assets to app filesDir and makes it executable.
     * Prefers ABI-specific asset (arm64-v8a/paqet, armeabi-v7a/paqet) when built from submodule; else uses "paqet".
     * Safe to call multiple times; overwrites if already present.
     */
    fun ensureBinary(): Boolean {
        if (binaryFile.exists()) {
            return binaryFile.canExecute()
        }
        return try {
            val assetPath = getPaqetAssetPath()
            context.assets.open(assetPath).use { input ->
                binaryFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            binaryFile.setExecutable(true, false)
            true
        } catch (e: IOException) {
            false
        }
    }

    /**
     * Checks whether root (su) is available. Runs on a background thread; result via [rootAvailable].
     */
    fun checkRoot() {
        Thread {
            val available = isRootAvailableBlocking()
            _rootAvailable.value = available
        }.start()
    }

    /**
     * Blocking check: runs `su -c "id"` and returns true if exit code is 0.
     */
    fun isRootAvailableBlocking(): Boolean {
        return try {
            val p = ProcessBuilder("su", "-c", "id")
                .redirectErrorStream(true)
                .start()
            val exit = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                p.waitFor(5, TimeUnit.SECONDS)
            } else {
                // For API < 26, use a thread with timeout
                var exited = false
                val thread = Thread {
                    exited = p.waitFor() == 0
                }
                thread.start()
                thread.join(5000)
                exited
            }
            p.destroy()
            exit
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Kills any running paqet binary process (e.g. orphaned from a previous run).
     * Uses the binary path so only the extracted paqet executable is killed, not the app process.
     * Safe to call when no paqet is running.
     */
    private fun killAnyExistingPaqetBlocking() {
        if (!binaryFile.exists()) return
        try {
            val path = binaryFile.absolutePath
            val escapedPath = path.replace("'", "'\\''")
            val cmd = "pkill -9 -f '$escapedPath' || true"
            val p = ProcessBuilder("su", "-c", cmd)
                .redirectErrorStream(true)
                .start()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                p.waitFor(1, TimeUnit.SECONDS)
            } else {
                // For API < 26, use a thread with timeout
                val thread = Thread {
                    p.waitFor()
                }
                thread.start()
                thread.join(1000)
            }
            p.destroy()
            Thread.sleep(150)
        } catch (e: Exception) {
            // ignore
        }
    }

    private var outputReaderThread: Thread? = null

    /**
     * Writes [configYaml] to a file and starts paqet as root with that config.
     * @param configYaml full YAML content for paqet client
     * @param sessionSummary optional one-line summary to log (e.g. server, interface, router MAC)
     * @return true if the process was started (root + binary available)
     */
    fun startWithYaml(configYaml: String, sessionSummary: String? = null): Boolean {
        if (!ensureBinary()) return false
        if (!isRootAvailableBlocking()) return false
        stop()
        outputReaderThread?.join(500)
        outputReaderThread = null
        killAnyExistingPaqetBlocking()
        lastConfigYaml = configYaml
        lastSessionSummary = sessionSummary
        val configFile = File(paqetDir, "config_run.yaml")
        return try {
            configFile.writeText(configYaml)
            logBuffer?.append(AppLogBuffer.TAG_SESSION, "=== paqet start ===")
            sessionSummary?.let { logBuffer?.append(AppLogBuffer.TAG_SESSION, it) }
            logBuffer?.append(AppLogBuffer.TAG_SESSION, "config: $configFile")
            val binaryPath = binaryFile.absolutePath
            val configPath = configFile.absolutePath
            val command = "$binaryPath run -c $configPath"
            logBuffer?.append(AppLogBuffer.TAG_PAQET, "command: $command")
            process = ProcessBuilder("su", "-c", command)
                .directory(paqetDir)
                .redirectErrorStream(true)
                .start()
            stopRequested = false
            _isRunning.value = true
            val proc = process
            if (proc != null && logBuffer != null) {
                outputReaderThread = Thread {
                    try {
                        BufferedReader(InputStreamReader(proc.inputStream)).useLines { lines ->
                            lines.forEach { line ->
                                logBuffer.append(AppLogBuffer.TAG_PAQET, line)
                            }
                        }
                        logBuffer.append(AppLogBuffer.TAG_PAQET, "process exited")
                    } catch (e: IOException) {
                        // Stream closed when process is destroyed (e.g. stop() on another thread)
                        logBuffer.append(AppLogBuffer.TAG_PAQET, "process exited")
                    }
                }.apply { isDaemon = true; start() }
            }
            Thread {
                val proc = process
                val exitCode = if (proc != null) {
                    proc.waitFor()
                } else {
                    -1
                }
                _isRunning.value = false
                process = null
                if (!stopRequested) {
                    val yaml = lastConfigYaml
                    val summary = lastSessionSummary
                    if (yaml != null) {
                        logBuffer?.append(AppLogBuffer.TAG_SESSION, "paqet exited unexpectedly (exitCode=$exitCode); will attempt restart if enabled")
                        onCrashed?.invoke(yaml, summary)
                    }
                }
            }.start()
            true
        } catch (e: Exception) {
            logBuffer?.append(AppLogBuffer.TAG_SESSION, "start failed: ${e.message}")
            _isRunning.value = false
            false
        }
    }

    /**
     * Stops and kills the running paqet process if any.
     * Sends destroy immediately; a background thread does wait, destroyForcibly, and killAnyExistingPaqet.
     * Clears stored config so we do not auto-restart after user disconnect.
     */
    fun stop() {
        stopRequested = true
        lastConfigYaml = null
        lastSessionSummary = null
        val p = process
        process = null
        _isRunning.value = false
        if (p != null) {
            logBuffer?.append(AppLogBuffer.TAG_SESSION, "=== paqet stop ===")
            p.destroy()
            Thread {
                val terminated = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    p.waitFor(2, TimeUnit.SECONDS)
                } else {
                    // For API < 26, use a thread with timeout
                    val thread = Thread {
                        p.waitFor()
                    }
                    thread.start()
                    thread.join(2000)
                    !thread.isAlive
                }
                if (!terminated) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        p.destroyForcibly()
                    } else {
                        // For API < 26, destroy() is already called, try again
                        p.destroy()
                    }
                }
                outputReaderThread?.join(1000)
                outputReaderThread = null
                killAnyExistingPaqetBlocking()
            }.start()
        } else {
            Thread { killAnyExistingPaqetBlocking() }.start()
        }
    }

    companion object {
        private const val ASSET_BINARY_NAME = "paqet"
    }
}
