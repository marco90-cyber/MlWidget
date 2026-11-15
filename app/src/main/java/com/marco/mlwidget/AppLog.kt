package com.marco.mlwidget

import android.content.Context
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Registro semplice degli errori, visibile nelle impostazioni. */
object AppLog {
    private val lines = ArrayDeque<String>()
    private var file: java.io.File? = null

    fun init(ctx: Context) {
        file = java.io.File(ctx.filesDir, "registro.txt")
        try {
            file?.takeIf { it.exists() }?.readLines()?.takeLast(200)?.forEach { lines.addLast(it) }
        } catch (e: Exception) { }
    }

    @Synchronized
    fun i(msg: String) = add("I", msg)

    @Synchronized
    fun e(msg: String, t: Throwable? = null) {
        Log.e("MlWidget", msg, t)
        add("E", msg + (t?.let { ": " + (it.message ?: it.javaClass.simpleName) } ?: ""))
    }

    @Synchronized
    private fun add(level: String, msg: String) {
        val line = SimpleDateFormat("dd/MM HH:mm:ss", Locale.ITALY).format(Date()) + " $level $msg"
        lines.addLast(line)
        while (lines.size > 200) lines.removeFirst()
        try { file?.writeText(lines.joinToString("\n")) } catch (e: Exception) { }
    }

    @Synchronized
    fun all(): List<String> = lines.toList().reversed()

    @Synchronized
    fun clear() {
        lines.clear()
        try { file?.delete() } catch (e: Exception) { }
    }
}
