package com.alirezabeigy.paqetng.data

/**
 * Client config for paqet. Stored in the app and serialized to paqet YAML when running.
 */
object KcpBlockOptions {
    val all = listOf(
        "aes",
        "aes-128",
        "aes-128-gcm",
        "aes-192",
        "salsa20",
        "blowfish",
        "twofish",
        "cast5",
        "3des",
        "tea",
        "xtea",
        "xor",
        "sm4",
        "none",
    )
    const val default = "aes"
}

/** Default TCP flags used to carry data (Push+Ack). paqet cycles through these; valid chars: F,S,R,P,A,U,E,C,N. */
const val DEFAULT_TCP_FLAGS = "PA"

object KcpModeOptions {
    val all = listOf("normal", "fast", "fast2", "fast3", "manual")
    const val default = "fast"
}

object KcpManualDefaults {
    const val nodelay = 1
    const val interval = 10
    const val resend = 2
    const val nocongestion = 1
    const val wdelay = false
    const val acknodelay = true
}

data class PaqetConfig(
    val id: String,
    val name: String,
    val serverAddr: String,
    val networkInterface: String,
    val ipv4Addr: String,
    val routerMac: String,
    val kcpKey: String,
    val kcpBlock: String = KcpBlockOptions.default,
    val socksListen: String = "127.0.0.1:1284",
    /** Local TCP flag combinations (e.g. ["PA"] = Push+Ack). Used by paqet to vary outgoing packets. Null = use default (imported configs). */
    val localFlag: List<String>? = listOf(DEFAULT_TCP_FLAGS),
    /** Remote TCP flag combinations. Used by paqet for incoming packet patterns. Null = use default. */
    val remoteFlag: List<String>? = listOf(DEFAULT_TCP_FLAGS),
    /** Number of KCP connections (1-256, default: 1). */
    val conn: Int = 1,
    /** KCP mode: normal, fast, fast2, fast3, manual (default: fast). */
    val kcpMode: String = KcpModeOptions.default,
    /** Maximum transmission unit in bytes (50-1500, default: 1350). */
    val mtu: Int = 1350,
    /** Manual mode: nodelay (0=disable, 1=enable). Only used when mode="manual". */
    val kcpNodelay: Int? = null,
    /** Manual mode: interval in milliseconds (10-5000). Only used when mode="manual". */
    val kcpInterval: Int? = null,
    /** Manual mode: resend trigger (0-2). Only used when mode="manual". */
    val kcpResend: Int? = null,
    /** Manual mode: nocongestion (0=enabled, 1=disabled). Only used when mode="manual". */
    val kcpNocongestion: Int? = null,
    /** Manual mode: wdelay write batching (false=flush immediately, true=batch). Only used when mode="manual". */
    val kcpWdelay: Boolean? = null,
    /** Manual mode: acknodelay (true=send ACKs immediately, false=batch). Only used when mode="manual". */
    val kcpAcknodelay: Boolean? = null,
) {
    /** Port number from socksListen (e.g. 1284 from "127.0.0.1:1284"). */
    fun socksPort(): Int = socksListen.substringAfterLast(':', "1284").toIntOrNull() ?: 1284

    /**
     * Normalizes config to ensure all fields have proper defaults.
     * This is needed when loading old configs from JSON that don't have the new fields.
     * Handles cases where Gson may set non-nullable fields to null at runtime.
     */
    fun withDefaults(): PaqetConfig {
        val safeKcpMode = try {
            val mode = kcpMode
            if (mode.isEmpty() || mode !in KcpModeOptions.all) KcpModeOptions.default else mode
        } catch (e: Exception) {
            KcpModeOptions.default
        }
        val safeKcpBlock = try {
            val block = kcpBlock
            if (block.isEmpty() || block !in KcpBlockOptions.all) KcpBlockOptions.default else block
        } catch (e: Exception) {
            KcpBlockOptions.default
        }
        val safeSocksListen = try {
            val listen = socksListen
            if (listen.isEmpty()) "127.0.0.1:1284" else listen
        } catch (e: Exception) {
            "127.0.0.1:1284"
        }
        val isManualMode = safeKcpMode == "manual"
        return copy(
            conn = if (conn <= 0 || conn > 256) 1 else conn,
            kcpMode = safeKcpMode,
            mtu = if (mtu <= 0 || mtu < 50 || mtu > 1500) 1350 else mtu,
            kcpBlock = safeKcpBlock,
            socksListen = safeSocksListen,
            // Manual mode parameters: validate if mode is manual and use defaults if null, otherwise set to null
            kcpNodelay = if (isManualMode) {
                try {
                    kcpNodelay?.coerceIn(0, 1) ?: KcpManualDefaults.nodelay
                } catch (e: Exception) {
                    KcpManualDefaults.nodelay
                }
            } else null,
            kcpInterval = if (isManualMode) {
                try {
                    kcpInterval?.coerceIn(10, 5000) ?: KcpManualDefaults.interval
                } catch (e: Exception) {
                    KcpManualDefaults.interval
                }
            } else null,
            kcpResend = if (isManualMode) {
                try {
                    kcpResend?.coerceIn(0, 2) ?: KcpManualDefaults.resend
                } catch (e: Exception) {
                    KcpManualDefaults.resend
                }
            } else null,
            kcpNocongestion = if (isManualMode) {
                try {
                    kcpNocongestion?.coerceIn(0, 1) ?: KcpManualDefaults.nocongestion
                } catch (e: Exception) {
                    KcpManualDefaults.nocongestion
                }
            } else null,
            kcpWdelay = if (isManualMode) {
                try {
                    kcpWdelay ?: KcpManualDefaults.wdelay
                } catch (e: Exception) {
                    KcpManualDefaults.wdelay
                }
            } else null,
            kcpAcknodelay = if (isManualMode) {
                try {
                    kcpAcknodelay ?: KcpManualDefaults.acknodelay
                } catch (e: Exception) {
                    KcpManualDefaults.acknodelay
                }
            } else null
        )
    }

    /**
     * Export as paqet://host:port?enc=...&local=...&remote=...&key=...&conn=...&mode=...&mtu=...#name
     * Query params are URL-encoded; name is in the fragment (#) at the end.
     */
    fun toPaqetUri(): String {
        val authority = serverAddr.trim()
        val base = "paqet://$authority"
        val params = mutableListOf<String>()
        if (kcpBlock != KcpBlockOptions.default) params.add("enc=${java.net.URLEncoder.encode(kcpBlock, Charsets.UTF_8.name())}")
        val localList = localFlag?.takeIf { it.isNotEmpty() } ?: listOf(DEFAULT_TCP_FLAGS)
        val remoteList = remoteFlag?.takeIf { it.isNotEmpty() } ?: listOf(DEFAULT_TCP_FLAGS)
        if (localList.joinToString(",") != DEFAULT_TCP_FLAGS) params.add("local=${java.net.URLEncoder.encode(localList.joinToString(","), Charsets.UTF_8.name())}")
        if (remoteList.joinToString(",") != DEFAULT_TCP_FLAGS) params.add("remote=${java.net.URLEncoder.encode(remoteList.joinToString(","), Charsets.UTF_8.name())}")
        params.add("key=${java.net.URLEncoder.encode(kcpKey, Charsets.UTF_8.name())}")
        if (conn != 1) params.add("conn=$conn")
        if (kcpMode != KcpModeOptions.default) params.add("mode=${java.net.URLEncoder.encode(kcpMode, Charsets.UTF_8.name())}")
        if (mtu != 1350) params.add("mtu=$mtu")
        // Manual mode parameters (only include if mode is manual and values are set)
        if (kcpMode == "manual") {
            kcpNodelay?.let { params.add("nodelay=$it") }
            kcpInterval?.let { params.add("interval=$it") }
            kcpResend?.let { params.add("resend=$it") }
            kcpNocongestion?.let { params.add("nocongestion=$it") }
            kcpWdelay?.let { params.add("wdelay=$it") }
            kcpAcknodelay?.let { params.add("acknodelay=$it") }
        }
        val queryPart = if (params.isEmpty()) base else "$base?${params.joinToString("&")}"
        val nameVal = name.ifEmpty { serverAddr }
        return if (nameVal.isNotEmpty()) "$queryPart#${java.net.URLEncoder.encode(nameVal, Charsets.UTF_8.name())}" else queryPart
    }

    companion object {
        /**
         * Parse import text: paqet://host:port?enc=...&local=...&remote=...&key=...&conn=...&mode=...&mtu=...#name
         * Name is in the fragment (#); query params are URL-decoded. Or JSON. Returns null if invalid.
         */
        fun parseFromImport(text: String?, gson: com.google.gson.Gson): PaqetConfig? {
            val t = text?.trim() ?: return null
            if (t.isEmpty()) return null
            if (t.startsWith("paqet://")) {
                return try {
                    val withoutScheme = t.removePrefix("paqet://")
                    val hashIndex = withoutScheme.indexOf('#')
                    val beforeFragment = if (hashIndex < 0) withoutScheme else withoutScheme.take(hashIndex)
                    val fragment = if (hashIndex < 0) null else withoutScheme.substring(hashIndex + 1)
                    val name = fragment?.let { java.net.URLDecoder.decode(it, Charsets.UTF_8.name()) }?.takeIf { it.isNotEmpty() }
                    val queryStart = beforeFragment.indexOf('?')
                    val authority = if (queryStart < 0) beforeFragment else beforeFragment.take(queryStart)
                    val queryString = if (queryStart < 0) "" else beforeFragment.substring(queryStart + 1)
                    val params = queryString.split('&').associate { part ->
                        val eq = part.indexOf('=')
                        if (eq < 0) part to ""
                        else part.take(eq) to java.net.URLDecoder.decode(part.substring(eq + 1), Charsets.UTF_8.name())
                    }
                    fun get(key: String) = params[key]?.takeIf { it.isNotEmpty() }
                    val resolvedName = name ?: authority
                    val encRaw = get("enc")
                    val encryption = if (encRaw != null && encRaw in KcpBlockOptions.all) encRaw else KcpBlockOptions.default
                    val localStr = get("local")
                    val localFlagList = localStr?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }
                    val localFlag = if (localFlagList.isNullOrEmpty()) listOf(DEFAULT_TCP_FLAGS) else localFlagList
                    val remoteStr = get("remote")
                    val remoteFlagList = remoteStr?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }
                    val remoteFlag = if (remoteFlagList.isNullOrEmpty()) listOf(DEFAULT_TCP_FLAGS) else remoteFlagList
                    val kcpKey = get("key") ?: ""
                    val connRaw = get("conn")
                    val conn = connRaw?.toIntOrNull()?.coerceIn(1, 256) ?: 1
                    val modeRaw = get("mode")
                    val kcpMode = if (modeRaw != null && modeRaw in KcpModeOptions.all) modeRaw else KcpModeOptions.default
                    val mtuRaw = get("mtu")
                    val mtu = mtuRaw?.toIntOrNull()?.coerceIn(50, 1500) ?: 1350
                    // Manual mode parameters (only parse if mode is manual)
                    val nodelay = if (kcpMode == "manual") get("nodelay")?.toIntOrNull()?.coerceIn(0, 1) else null
                    val interval = if (kcpMode == "manual") get("interval")?.toIntOrNull()?.coerceIn(10, 5000) else null
                    val resend = if (kcpMode == "manual") get("resend")?.toIntOrNull()?.coerceIn(0, 2) else null
                    val nocongestion = if (kcpMode == "manual") get("nocongestion")?.toIntOrNull()?.coerceIn(0, 1) else null
                    val wdelay = if (kcpMode == "manual") get("wdelay")?.toBooleanStrictOrNull() else null
                    val acknodelay = if (kcpMode == "manual") get("acknodelay")?.toBooleanStrictOrNull() else null
                    PaqetConfig(
                        id = "",
                        name = resolvedName,
                        serverAddr = authority,
                        networkInterface = "wlan0",
                        ipv4Addr = "",
                        routerMac = "",
                        kcpKey = kcpKey,
                        kcpBlock = encryption,
                        socksListen = "127.0.0.1:1284",
                        localFlag = localFlag,
                        remoteFlag = remoteFlag,
                        conn = conn,
                        kcpMode = kcpMode,
                        mtu = mtu,
                        kcpNodelay = nodelay,
                        kcpInterval = interval,
                        kcpResend = resend,
                        kcpNocongestion = nocongestion,
                        kcpWdelay = wdelay,
                        kcpAcknodelay = acknodelay
                    )
                } catch (_: Exception) {
                    null
                }
            }
            val jsonLine = t.lines().find { it.trim().startsWith("{") } ?: t
            return try {
                gson.fromJson(jsonLine, PaqetConfig::class.java)?.withDefaults()
            } catch (_: Exception) {
                null
            }
        }
    }
}
