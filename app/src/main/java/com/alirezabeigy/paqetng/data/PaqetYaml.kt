package com.alirezabeigy.paqetng.data

/**
 * Builds paqet client YAML from [PaqetConfig]. Matches structure expected by paqet.
 * @param logLevel paqet log level: none, debug, info, warn, error, fatal
 */
/** Format TCP flag list for paqet YAML (e.g. ["PA", "S"]). */
private fun List<String>.toTcpFlagYaml(): String =
    if (isEmpty()) "[ \"$DEFAULT_TCP_FLAGS\" ]"
    else joinToString(", ") { "\"${it.trim()}\"" }.let { "[ $it ]" }

fun PaqetConfig.toPaqetYaml(logLevel: String = "debug"): String {
    val localList = localFlag?.takeIf { it.isNotEmpty() } ?: listOf(DEFAULT_TCP_FLAGS)
    val remoteList = remoteFlag?.takeIf { it.isNotEmpty() } ?: listOf(DEFAULT_TCP_FLAGS)
    val localFlagYaml = localList.toTcpFlagYaml()
    val remoteFlagYaml = remoteList.toTcpFlagYaml()
    val isManualMode = kcpMode == "manual"
    val manualParams = buildString {
        if (isManualMode) {
            val nodelay = kcpNodelay ?: KcpManualDefaults.NO_DELAY
            val interval = kcpInterval ?: KcpManualDefaults.INTERVAL
            val resend = kcpResend ?: KcpManualDefaults.RESEND
            val nocongestion = kcpNocongestion ?: KcpManualDefaults.NO_CONGESTION
            val wdelay = kcpWdelay ?: KcpManualDefaults.W_DELAY
            val acknodelay = kcpAcknodelay ?: KcpManualDefaults.ACK_NO_DELAY
            append("\n    nodelay: $nodelay")
            append("\n    interval: $interval")
            append("\n    resend: $resend")
            append("\n    nocongestion: $nocongestion")
            append("\n    wdelay: $wdelay")
            append("\n    acknodelay: $acknodelay")
        }
    }
    return """
role: "client"
log:
  level: "$logLevel"
socks5:
  - listen: "$socksListen"
    username: ""
    password: ""
network:
  interface: "$networkInterface"
  ipv4:
    addr: "$ipv4Addr"
    router_mac: "$routerMac"
  tcp:
    local_flag: $localFlagYaml
    remote_flag: $remoteFlagYaml
server:
  addr: "$serverAddr"
transport:
  protocol: "kcp"
  conn: $conn
  kcp:
    mode: "$kcpMode"
    mtu: $mtu
    rcvwnd: 512
    sndwnd: 512
    block: "$kcpBlock"
    key: "$kcpKey"$manualParams
""".trimIndent()
}
