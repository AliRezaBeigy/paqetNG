package com.v2ray.ang.service

import android.util.Log

/**
 * JNI bridge for hev-socks5-tunnel (tun2socks).
 * Class and package name must match the native library registration so that the
 * prebuilt .so from v2rayNG (or a build with the same PKGNAME/CLSNAME) can be used.
 * Copy libhev-socks5-tunnel.so from v2rayNG build into app/src/main/jniLibs/&lt;abi&gt;/.
 */
class TProxyService {

    companion object {
        private const val TAG = "TProxyService"

        init {
            try {
                System.loadLibrary("hev-socks5-tunnel")
            } catch (e: UnsatisfiedLinkError) {
                Log.w(TAG, "hev-socks5-tunnel not available: ${e.message}")
            }
        }

    }

    @Suppress("FunctionName")
    private external fun TProxyStartService(configPath: String, fd: Int)

    @Suppress("FunctionName")
    private external fun TProxyStopService()

    /** Returns [tx_packets, tx_bytes, rx_packets, rx_bytes] from the native tunnel. */
    @Suppress("FunctionName")
    private external fun TProxyGetStats(): LongArray

    fun startTun2Socks(configPath: String, fd: Int) {
        TProxyStartService(configPath, fd)
    }

    fun stopTun2Socks() {
        TProxyStopService()
    }

    /** Returns [tx_packets, tx_bytes, rx_packets, rx_bytes] from the native tunnel. */
    fun getStats(): LongArray = TProxyGetStats()
}
