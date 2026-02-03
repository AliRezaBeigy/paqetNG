package com.alirezabeigy.paqetng.vpn

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Runs tun2socks (hev-socks5-tunnel) to forward TUN traffic to the local SOCKS5 port.
 * Requires libhev-socks5-tunnel.so in jniLibs (e.g. from v2rayNG compile-hevtun.sh).
 */
class Tun2SocksRunner(
    private val context: Context,
    private val socksPort: Int
) {
    private var hevService: com.v2ray.ang.service.TProxyService? = null

    fun start(tunFd: Int): Boolean {
        return try {
            val configContent = buildHevConfig()
            val configFile = File(context.filesDir, "hev-socks5-tunnel.yaml").apply {
                writeText(configContent)
            }
            Log.d(TAG, "Hev tunnel config:\n$configContent")
            val service = com.v2ray.ang.service.TProxyService()
            service.startTun2Socks(configFile.absolutePath, tunFd)
            hevService = service
            true
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "tun2socks native lib not loaded: ${e.message}. Copy libhev-socks5-tunnel.so to app/src/main/jniLibs/<abi>/ or run buildHevtun.", e)
            false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start tun2socks", e)
            false
        }
    }

    fun stop() {
        try {
            hevService?.stopTun2Socks()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping tun2socks", e)
        }
        hevService = null
    }

    private fun buildHevConfig(): String {
        val mtu = 1500
        val ipv4Client = "10.0.0.2"
        val tcpTimeout = 300_000  // ms
        val udpTimeout = 60_000
        return buildString {
            appendLine("tunnel:")
            appendLine("  mtu: $mtu")
            appendLine("  ipv4: $ipv4Client")
            appendLine("socks5:")
            appendLine("  port: $socksPort")
            appendLine("  address: 127.0.0.1")
            appendLine("  udp: 'udp'")
            appendLine("misc:")
            appendLine("  tcp-read-write-timeout: $tcpTimeout")
            appendLine("  udp-read-write-timeout: $udpTimeout")
            appendLine("  log-level: warn")
        }
    }

    companion object {
        private const val TAG = "Tun2Socks"
    }
}
