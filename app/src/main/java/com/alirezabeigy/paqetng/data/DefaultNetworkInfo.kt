package com.alirezabeigy.paqetng.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.Inet4Address

/**
 * Detected default network info: interface name, device IPv4 (with :0 for paqet), and gateway MAC.
 */
data class DefaultNetworkInfo(
    val interfaceName: String,
    val ipv4Addr: String,
    val routerMac: String
)

/**
 * Fetches the current default network's interface name, IPv4 address, and gateway MAC.
 * Uses ConnectivityManager + LinkProperties; gateway MAC is read from /proc/net/arp.
 */
class DefaultNetworkInfoProvider(private val context: Context) {

    private companion object {
        private const val TAG = "DefaultNetworkInfo"
    }

    suspend fun getDefaultNetworkInfo(): DefaultNetworkInfo? = withContext(Dispatchers.IO) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return@withContext null
        val network = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            cm.activeNetwork ?: return@withContext null
        } else {
            return@withContext null
        }
        // Prefer LinkProperties (works well for WiFi); on cellular it often returns null.
        cm.getLinkProperties(network)?.let { lp ->
            val interfaceName = lp.interfaceName?.takeIf { it.isNotBlank() } ?: "wlan0"
            val ipv4 = lp.linkAddresses
                .mapNotNull { la -> la.address }
                .filterIsInstance<Inet4Address>()
                .firstOrNull()
                ?.hostAddress
                ?: return@withContext getDefaultNetworkInfoViaRoot()
            val ipv4Addr = "$ipv4:0"
            val gateway = lp.routes
                ?.firstOrNull { it.gateway != null && it.isDefaultRoute && it.gateway is Inet4Address }
                ?.gateway
                ?: lp.routes?.firstOrNull { it.gateway != null && it.gateway is Inet4Address }?.gateway
            val gatewayIp = gateway?.hostAddress ?: ""
            Log.d(TAG, "gatewayIp=$gatewayIp")
            val routerMacFromFile = readMacFromArp(gatewayIp)
            val routerMacFromRoot = if (routerMacFromFile == null) readMacFromArpViaRoot(gatewayIp) else null
            val routerMac = routerMacFromFile ?: routerMacFromRoot ?: ""
            Log.d(TAG, "routerMac: fromFile=$routerMacFromFile fromRoot=$routerMacFromRoot final=$routerMac")
            DefaultNetworkInfo(interfaceName, ipv4Addr, routerMac)
        } ?: getDefaultNetworkInfoViaRoot()
    }

    /**
     * Fallback for when LinkProperties is null (e.g. on cellular/mobile network).
     * Uses root to run `ip route get 8.8.8.8` and `ip -4 addr show dev <iface>` to get
     * the default route interface and local IPv4; then tries to get gateway MAC via ip neigh.
     */
    private fun getDefaultNetworkInfoViaRoot(): DefaultNetworkInfo? {
        // ip route get 8.8.8.8 → e.g. "8.8.8.8 from 10.0.0.1 dev rmnet_data0 src 10.0.0.2 uid 0"
        val routeOut = runSuCommand("ip route get 8.8.8.8")
        if (routeOut.isNullOrBlank()) {
            Log.w(TAG, "getDefaultNetworkInfoViaRoot: ip route get 8.8.8.8 returned empty")
            return null
        }
        Log.d(TAG, "getDefaultNetworkInfoViaRoot: route output=$routeOut")
        val devMatch = Regex("""\bdev\s+(\S+)""").find(routeOut)
        val srcMatch = Regex("""\bsrc\s+(\S+)""").find(routeOut)
        val viaMatch = Regex("""\bvia\s+(\S+)""").find(routeOut)
        val iface = devMatch?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
            ?: run {
                val defaultRoute = runSuCommand("ip route show default")
                defaultRoute?.let { line ->
                    Regex("""default\s+via\s+\S+\s+dev\s+(\S+)""").find(line)?.groupValues?.get(1)
                }
            }
        if (iface.isNullOrBlank()) {
            Log.w(TAG, "getDefaultNetworkInfoViaRoot: could not parse interface from route")
            return null
        }
        var srcIp = srcMatch?.groupValues?.get(1)
        if (srcIp.isNullOrBlank()) {
            val addrOut = runSuCommand("ip -4 addr show dev $iface")
            addrOut?.let { output ->
                Regex("""inet\s+(\d+\.\d+\.\d+\.\d+)""").find(output)?.let { m ->
                    srcIp = m.groupValues[1]
                }
            }
        }
        if (srcIp.isNullOrBlank()) {
            Log.w(TAG, "getDefaultNetworkInfoViaRoot: could not get IPv4 for $iface")
            return null
        }
        val ipv4Addr = "$srcIp:0"
        val gatewayIp = viaMatch?.groupValues?.get(1) ?: ""
        val routerMac = if (gatewayIp.isNotBlank()) {
            readMacFromArpViaRoot(gatewayIp) ?: ""
        } else {
            ""
        }.ifEmpty { "00:00:00:00:00:00" }
        Log.d(TAG, "getDefaultNetworkInfoViaRoot: iface=$iface ipv4=$ipv4Addr routerMac=$routerMac (cellular fallback)")
        return DefaultNetworkInfo(iface, ipv4Addr, routerMac)
    }

    private fun readMacFromArp(ip: String): String? {
        if (ip.isEmpty()) return null
        val arpFile = File("/proc/net/arp")
        if (!arpFile.canRead()) {
            Log.d(TAG, "readMacFromArp: /proc/net/arp not readable")
            return null
        }
        val text = arpFile.readText()
        Log.d(TAG, "readMacFromArp: arp content (first 500 chars)=${text.take(500)}")
        return parseArpOutput(text, ip)
    }

    /**
     * Run `su -c "cat /proc/net/arp"` then fallback to `ip neigh show` to get gateway MAC (root required).
     */
    private fun readMacFromArpViaRoot(ip: String): String? {
        if (ip.isEmpty()) return null
        val arpOut = runSuCommand("cat /proc/net/arp")
        Log.d(TAG, "readMacFromArpViaRoot: su cat /proc/net/arp exit, length=${arpOut?.length}, preview=${arpOut?.take(400)}")
        arpOut?.let { output ->
            parseArpOutput(output, ip)?.let { mac ->
                Log.d(TAG, "readMacFromArpViaRoot: parsed MAC from arp: $mac")
                return mac
            }
        }
        val neighOut = runSuCommand("ip neigh show")
        Log.d(TAG, "readMacFromArpViaRoot: su ip neigh show length=${neighOut?.length}, preview=${neighOut?.take(400)}")
        neighOut?.lines()?.forEach { line ->
            val parts = line.split(Regex("\\s+"))
            if (parts.isNotEmpty() && parts[0] == ip) {
                val lladdrIdx = parts.indexOf("lladdr")
                if (lladdrIdx >= 0 && lladdrIdx + 1 < parts.size) {
                    val mac = parts[lladdrIdx + 1]
                    if (mac.contains(":")) {
                        Log.d(TAG, "readMacFromArpViaRoot: parsed MAC from ip neigh: $mac")
                        return mac
                    }
                }
            }
        }
        Log.d(TAG, "readMacFromArpViaRoot: no MAC found for $ip")
        return null
    }

    private fun runSuCommand(cmd: String): String? {
        return try {
            val process = ProcessBuilder("su", "-c", cmd)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            if (exitCode != 0) {
                Log.w(TAG, "runSuCommand: cmd=$cmd exitCode=$exitCode output=${output.take(200)}")
                return null
            }
            output
        } catch (e: Exception) {
            Log.w(TAG, "runSuCommand: cmd=$cmd exception=${e.message}", e)
            null
        }
    }

    /**
     * Parse /proc/net/arp: columns are IP(0), HW type(1), Flags(2), HW address(3), Mask(4), Device(5).
     */
    private fun parseArpOutput(arpText: String, targetIp: String): String? {
        val lines = arpText.lines()
        if (lines.isEmpty()) return null
        for (line in lines.drop(1)) {
            val parts = line.trim().split(Regex("\\s+"))
            if (parts.size >= 4 && parts[0] == targetIp) {
                val mac = parts[3]
                if (mac.contains(":") && mac != "00:00:00:00:00:00") return mac
            }
        }
        return null
    }
}
