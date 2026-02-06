package com.alirezabeigy.paqetng.paqet

import android.content.Context
import android.os.Build
import com.alirezabeigy.paqetng.data.TcpdumpBuffer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/**
 * Extracts the tcpdump binary from assets and runs it on the VPN TUN interface (tun0)
 * to capture TCP packet info (flags, seq, ack, etc.). Requires root.
 * Output is appended to [tcpdumpBuffer].
 */
class TcpdumpRunner(
    private val context: Context,
    private val tcpdumpBuffer: TcpdumpBuffer,
    private val tunInterface: String = "tun0"
) {

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private var process: Process? = null
    @Volatile
    private var stopRequested = false

    private var outputReaderThread: Thread? = null

    private val tcpdumpDir: File
        get() = File(context.filesDir, "tcpdump").also { if (!it.exists()) it.mkdirs() }

    private val binaryFile: File
        get() = File(tcpdumpDir, "tcpdump")

    private fun getTcpdumpAssetPath(): String {
        val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: return ASSET_BINARY_NAME
        val abiPath = "$abi/$ASSET_BINARY_NAME"
        return try {
            context.assets.open(abiPath).close()
            abiPath
        } catch (_: IOException) {
            ASSET_BINARY_NAME
        }
    }

    fun ensureBinary(): Boolean {
        if (binaryFile.exists()) {
            return binaryFile.canExecute()
        }
        return try {
            val assetPath = getTcpdumpAssetPath()
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
     * Starts tcpdump: -l (line buffered), -n (no DNS), -v (verbose, shows flags).
     * [interfaceName]: use this interface (e.g. "tun0" for VPN, "wlan0" for SOCKS-only). If null, uses [tunInterface] (tun0).
     * [hostFilter]: if set (e.g. server IP or hostname), only packets to/from that host are shown.
     * Runs as root. Append each line to [tcpdumpBuffer].
     */
    fun start(hostFilter: String? = null, interfaceName: String? = null): Boolean {
        if (!ensureBinary()) return false
        if (!isRootAvailableBlocking()) return false
        stop()
        outputReaderThread?.join(500)
        outputReaderThread = null
        stopRequested = false
        val iface = interfaceName?.trim()?.takeIf { it.isNotBlank() } ?: tunInterface
        val binaryPath = binaryFile.absolutePath
        val hostExpr = hostFilter?.trim()?.takeIf { it.isNotBlank() }?.let { " host $it" } ?: ""
        val cmd = "$binaryPath -i $iface -l -n -v$hostExpr 2>&1"
        return try {
            process = ProcessBuilder("su", "-c", cmd)
                .directory(tcpdumpDir)
                .redirectErrorStream(true)
                .start()
            _isRunning.value = true
            val proc = process
            if (proc != null) {
                outputReaderThread = Thread {
                    try {
                        BufferedReader(InputStreamReader(proc.inputStream)).useLines { lines ->
                            lines.forEach { line ->
                                if (!stopRequested) tcpdumpBuffer.append(line)
                            }
                        }
                        tcpdumpBuffer.append("[tcpdump exited]")
                    } catch (_: IOException) {
                        tcpdumpBuffer.append("[tcpdump stream closed]")
                    }
                }.apply { isDaemon = true; start() }
            }
            Thread {
                val proc = process
                if (proc != null) {
                    proc.waitFor()
                }
                _isRunning.value = false
                process = null
            }.start()
            true
        } catch (e: Exception) {
            tcpdumpBuffer.append("[tcpdump start failed: ${e.message}]")
            _isRunning.value = false
            false
        }
    }

    fun stop() {
        stopRequested = true
        val p = process
        process = null
        _isRunning.value = false
        if (p != null) {
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
            }.start()
        } else {
            outputReaderThread?.join(500)
            outputReaderThread = null
        }
    }

    private fun isRootAvailableBlocking(): Boolean {
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

    companion object {
        private const val ASSET_BINARY_NAME = "tcpdump"
    }
}
