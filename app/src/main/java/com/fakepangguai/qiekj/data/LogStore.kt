package com.fakepangguai.qiekj.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 内存环形日志：手动开启后记录接口调用与关键操作，可停止并导出。 */
class LogStore(private val maxLines: Int = 4000) {
    private val buffer = ArrayDeque<String>()
    private val formatter = SimpleDateFormat("HH:mm:ss.SSS", Locale.CHINA)

    @Volatile
    var recording: Boolean = false
        private set

    fun start() {
        synchronized(buffer) {
            recording = true
            pushRaw("=== 开始记录 " + timestamp() + " ===")
        }
    }

    fun stop() {
        synchronized(buffer) {
            pushRaw("=== 停止记录 " + timestamp() + " ===")
            recording = false
        }
    }

    fun append(line: String) {
        if (!recording) return
        synchronized(buffer) { pushRaw("[" + timestamp() + "] " + line) }
    }

    fun lineCount(): Int = synchronized(buffer) { buffer.size }

    fun content(): String = synchronized(buffer) {
        buildString {
            for (line in buffer) {
                append(line)
                append('\n')
            }
        }
    }

    fun clear() {
        synchronized(buffer) { buffer.clear() }
    }

    private fun pushRaw(line: String) {
        buffer.addLast(line)
        while (buffer.size > maxLines) buffer.removeFirst()
    }

    private fun timestamp(): String = formatter.format(Date())
}
