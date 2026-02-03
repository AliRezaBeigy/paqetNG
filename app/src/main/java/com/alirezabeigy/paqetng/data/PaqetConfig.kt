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
) {
    /** Port number from socksListen (e.g. 1284 from "127.0.0.1:1284"). */
    fun socksPort(): Int = socksListen.substringAfterLast(':', "1284").toIntOrNull() ?: 1284

    /**
     * Export as paqet://host:port?enc=...&local=...&remote=...&key=...#name
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
        val queryPart = if (params.isEmpty()) base else "$base?${params.joinToString("&")}"
        val nameVal = name.ifEmpty { serverAddr }
        return if (nameVal.isNotEmpty()) "$queryPart#${java.net.URLEncoder.encode(nameVal, Charsets.UTF_8.name())}" else queryPart
    }

    companion object {
        /**
         * Parse import text: paqet://host:port?enc=...&local=...&remote=...&key=...#name
         * Name is in the fragment (#); query params are URL-decoded. Or JSON. Returns null if invalid.
         */
        fun parseFromImport(text: String?, gson: com.google.gson.Gson): PaqetConfig? {
            val t = text?.trim() ?: return null
            if (t.isEmpty()) return null
            if (t.startsWith("paqet://")) {
                return try {
                    val withoutScheme = t.removePrefix("paqet://")
                    val hashIndex = withoutScheme.indexOf('#')
                    val beforeFragment = if (hashIndex < 0) withoutScheme else withoutScheme.substring(0, hashIndex)
                    val fragment = if (hashIndex < 0) null else withoutScheme.substring(hashIndex + 1)
                    val name = fragment?.let { java.net.URLDecoder.decode(it, Charsets.UTF_8.name()) }?.takeIf { it.isNotEmpty() }
                    val queryStart = beforeFragment.indexOf('?')
                    val authority = if (queryStart < 0) beforeFragment else beforeFragment.substring(0, queryStart)
                    val queryString = if (queryStart < 0) "" else beforeFragment.substring(queryStart + 1)
                    val params = queryString.split('&').associate { part ->
                        val eq = part.indexOf('=')
                        if (eq < 0) part to ""
                        else part.substring(0, eq) to java.net.URLDecoder.decode(part.substring(eq + 1), Charsets.UTF_8.name())
                    }
                    fun get(key: String) = params[key]?.takeIf { it.isNotEmpty() }
                    val resolvedName = name ?: authority
                    val encRaw = get("enc")
                    val encryption = if (encRaw != null && encRaw in KcpBlockOptions.all) encRaw else KcpBlockOptions.default
                    val localStr = get("local")
                    val localFlag = localStr?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.ifEmpty { null } ?: listOf(DEFAULT_TCP_FLAGS)
                    val remoteStr = get("remote")
                    val remoteFlag = remoteStr?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.ifEmpty { null } ?: listOf(DEFAULT_TCP_FLAGS)
                    val kcpKey = get("key") ?: ""
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
                        remoteFlag = remoteFlag
                    )
                } catch (_: Exception) {
                    null
                }
            }
            val jsonLine = t.lines().find { it.trim().startsWith("{") } ?: t
            return try {
                gson.fromJson(jsonLine, PaqetConfig::class.java)
            } catch (_: Exception) {
                null
            }
        }
    }
}
