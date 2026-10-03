package com.example.motohours

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LogWriter {

    private const val TAG = "MotoHours"
    private const val LOG_FILE = "motohours_log.txt"
    private const val MAX_SIZE_BYTES = 500 * 1024L

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun log(message: String) {
        Log.i(TAG, message)

        try {
            val ctx = appContext ?: return
            val file = File(ctx.filesDir, LOG_FILE)

            if (file.exists() && file.length() > MAX_SIZE_BYTES) {
                val lines = file.readLines()
                val last = lines.takeLast(100)
                file.writeText(last.joinToString("\n") + "\n")
            }

            val timestamp = dateFormat.format(Date())
            file.appendText("$timestamp  $message\n")
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка записи лога: ${e.message}")
        }
    }

    fun readLog(): String {
        return try {
            val ctx = appContext ?: return "LogWriter не инициализирован"
            val file = File(ctx.filesDir, LOG_FILE)
            if (!file.exists()) return "Лог пуст — событий ещё не было"
            file.readText()
        } catch (e: Exception) {
            "Ошибка чтения лога: ${e.message}"
        }
    }

    fun clearLog() {
        try {
            val ctx = appContext ?: return
            val file = File(ctx.filesDir, LOG_FILE)
            if (file.exists()) file.delete()
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка очистки лога: ${e.message}")
        }
    }
}
