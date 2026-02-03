package com.alirezabeigy.paqetng.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.alirezabeigy.paqetng.MainActivity
import com.alirezabeigy.paqetng.R
import java.io.File

/**
 * Android VPN that creates a tun interface and forwards traffic to the local SOCKS5 port via tun2socks.
 * Flow: device apps -> tun -> tun2socks (hev-socks5-tunnel) -> 127.0.0.1:socksPort -> paqet.
 */
class PaqetNGVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var tun2Socks: Tun2SocksRunner? = null

    @get:RequiresApi(Build.VERSION_CODES.P)
    private val defaultNetworkRequest by lazy {
        NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
            .build()
    }

    private val connectivityManager by lazy {
        getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
    }

    @get:RequiresApi(Build.VERSION_CODES.P)
    private val defaultNetworkCallback by lazy {
        object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                setUnderlyingNetworks(arrayOf(network))
            }
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                setUnderlyingNetworks(arrayOf(network))
            }
            override fun onLost(network: Network) {
                setUnderlyingNetworks(null)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            Log.i(TAG, "Received ACTION_STOP - tearing down VPN")
            tearDown()
            stopSelf()
            return START_NOT_STICKY
        }
        val socksPort = intent?.getIntExtra(EXTRA_SOCKS_PORT, DEFAULT_PORT) ?: DEFAULT_PORT
        if (vpnInterface != null) {
            stopTun2Socks()
            vpnInterface?.close()
        }
        val prepare = prepare(this)
        if (prepare != null) {
            Log.e(TAG, "VPN preparation failed - user must grant VPN permission")
            stopSelf()
            return START_NOT_STICKY
        }
        if (!establishVpn(socksPort)) {
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, buildNotification())
        startTun2Socks(socksPort)
        return START_STICKY
    }

    override fun onDestroy() {
        tearDown()
        super.onDestroy()
    }

    /** Tears down VPN (tun, tun2socks, foreground) so the OS drops the VPN. */
    private fun tearDown() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            if (Build.VERSION.SDK_INT >= 31) {
                stopForeground(Service.STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                connectivityManager.unregisterNetworkCallback(defaultNetworkCallback)
            } catch (e: Exception) {
                // Callback may not have been registered
            }
        }
        stopTun2Socks()
        try {
            vpnInterface?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing VPN interface", e)
        }
        vpnInterface = null
    }

    private fun establishVpn(socksPort: Int): Boolean {
        return try {
            val builder = Builder()
                .setSession(getString(R.string.app_name))
                .setMtu(1500)
                .addAddress("10.0.0.2", 30)
                .addRoute("0.0.0.0", 0)
                .addDnsServer("8.8.8.8")
                .addDnsServer("8.8.4.4")
                .setBlocking(false)
            // Disallow our app so tun2socks' connection to 127.0.0.1:SOCKS does not go through VPN (avoids loop).
            try {
                builder.addDisallowedApplication(packageName)
            } catch (e: Exception) {
                Log.w(TAG, "Could not disallow self: ${e.message}")
            }
            vpnInterface = builder.establish()
            if (vpnInterface != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                try {
                    connectivityManager.requestNetwork(defaultNetworkRequest, defaultNetworkCallback)
                } catch (e: Exception) {
                    Log.w(TAG, "Could not request underlying network: ${e.message}")
                }
            }
            vpnInterface != null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to establish VPN", e)
            false
        }
    }

    private fun startTun2Socks(socksPort: Int) {
        val pfd = vpnInterface ?: return
        val fd = pfd.fd
        if (fd < 0) {
            Log.e(TAG, "Invalid VPN fd=$fd - TUN mode will not work")
            return
        }
        Log.i(TAG, "Starting tun2socks fd=$fd socksPort=$socksPort")
        tun2Socks = Tun2SocksRunner(this, socksPort)
        if (!tun2Socks!!.start(fd)) {
            Log.e(TAG, "tun2socks failed to start - TUN traffic will not be routed to SOCKS. Ensure libhev-socks5-tunnel.so is in jniLibs.")
        } else {
            Log.i(TAG, "tun2socks started; TUN mode active")
        }
    }

    private fun stopTun2Socks() {
        tun2Socks?.stop()
        tun2Socks = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "VPN",
                NotificationManager.IMPORTANCE_LOW
            ).apply { setShowBadge(false) }
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val pending = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("VPN connected")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(pending)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val TAG = "PaqetNGVpn"
        const val ACTION_STOP = "com.alirezabeigy.paqetng.vpn.STOP"
        const val EXTRA_SOCKS_PORT = "socks_port"
        private const val DEFAULT_PORT = 1284
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "vpn_channel"
    }
}
