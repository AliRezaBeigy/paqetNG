package com.alirezabeigy.paqetng.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

/**
 * In-memory log buffer with timestamp and tag (logcat-style).
 * Used for paqet output and session details when user connects.
 */
class AppLogBuffer(private val maxLines: Int = 2000) {

    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> = _lines.asStateFlow()

    private val list = CopyOnWriteArrayList<String>()
    private val dateFormat = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)

    private fun timestamp(): String = dateFormat.format(Date())

    @Synchronized
    fun append(tag: String, message: String) {
        val line = "${timestamp()}  ${tag.padEnd(8)}  $message"
        list.add(line)
        while (list.size > maxLines) list.removeAt(0)
        _lines.value = list.toList()
    }

    /** Full log text for export (newest last). */
    fun getFullText(): String = list.joinToString("\n")

    @Synchronized
    fun clear() {
        list.clear()
        _lines.value = emptyList()
    }

    companion object {
        const val TAG_PAQET = "paqet"
        const val TAG_SESSION = "session"
        const val TAG_VPN = "vpn"
    }
}
