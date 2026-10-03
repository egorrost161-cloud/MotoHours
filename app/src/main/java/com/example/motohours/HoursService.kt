package com.example.motohours

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat

class HoursService : Service() {

    companion object {
        const val CHANNEL_ID = "motohours_service"
        const val NOTIF_ID = 101
        const val SAVE_INTERVAL_MS = 30000L  // 30 секунд
        const val DEFAULT_THRESHOLD_1 = 200L  // часы
        const val DEFAULT_THRESHOLD_2 = 250L  // часы
        const val PREFS_NAME = "motohours"

        /**
         * Сохраняет текущее накопленное значение.
         * Вызывается из ShutdownReceiver.
         */
        fun saveCurrentValue(context: Context) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val totalMs = prefs.getLong("total_ms", 0L)
            val lastStart = prefs.getLong("last_start_time", 0L)

            if (lastStart > 0) {
                val now = System.currentTimeMillis()
                val newTotal = totalMs + (now - lastStart)
                prefs.edit()
                    .putLong("total_ms", newTotal)
                    .putLong("last_start_time", 0L)
                    .commit()
                LogWriter.log("Сохранено: ${formatMs(newTotal)}")
            }
        }

        fun formatMs(ms: Long): String {
            val totalMinutes = ms / 60000
            val hours = totalMinutes / 60
            val minutes = totalMinutes % 60
            return "${hours} ч ${minutes} мин"
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var saveRunnable: Runnable? = null
    private var windowManager: WindowManager? = null
    private var popupView: View? = null

    override fun onCreate() {
        super.onCreate()
        LogWriter.init(this)
        createNotificationChannel()
        LogWriter.log("=== HoursService onCreate ===")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification()
        startForeground(NOTIF_ID, notification)

        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        var lastStart = prefs.getLong("last_start_time", 0L)

        // Если last_start_time = 0 — значит это первый запуск после выключения или установки
        // Устанавливаем метку старта
        if (lastStart == 0L) {
            lastStart = System.currentTimeMillis()
            prefs.edit().putLong("last_start_time", lastStart).commit()
            LogWriter.log("Старт отсчёта (last_start_time установлен)")
        } else {
            LogWriter.log("Продолжаем отсчёт с прошлой сессии")
        }

        // Запускаем цикл сохранения
        startSaveLoop()

        return START_STICKY
    }

    private fun startSaveLoop() {
        saveRunnable = object : Runnable {
            override fun run() {
                saveTick()
                handler.postDelayed(this, SAVE_INTERVAL_MS)
            }
        }
        handler.postDelayed(saveRunnable!!, SAVE_INTERVAL_MS)
        LogWriter.log("Цикл сохранения запущен (каждые ${SAVE_INTERVAL_MS / 1000} сек)")
    }

    private fun saveTick() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val totalMs = prefs.getLong("total_ms", 0L)
        val lastStart = prefs.getLong("last_start_time", 0L)

        if (lastStart > 0) {
            val now = System.currentTimeMillis()
            val newTotal = totalMs + (now - lastStart)
            prefs.edit()
                .putLong("total_ms", newTotal)
                .putLong("last_start_time", now)
                .commit()

            // Проверяем пороги
            checkThresholds(newTotal)
        }
    }

    private fun checkThresholds(totalMs: Long) {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val threshold1 = prefs.getLong("threshold_1", DEFAULT_THRESHOLD_1)
        val threshold2 = prefs.getLong("threshold_2", DEFAULT_THRESHOLD_2)
        val notified1 = prefs.getBoolean("notified_1", false)
        val notified2 = prefs.getBoolean("notified_2", false)

        val totalHours = totalMs / 3600000L

        if (!notified1 && totalHours >= threshold1) {
            LogWriter.log("Достигнут порог 1: ${threshold1}ч")
            showPopup(
                title = "МОТОЧАСЫ: ${threshold1} ч",
                message = "Пора планировать замену масла."
            )
            prefs.edit().putBoolean("notified_1", true).commit()
        }

        if (!notified2 && totalHours >= threshold2) {
            LogWriter.log("Достигнут порог 2: ${threshold2}ч")
            showPopup(
                title = "МОТОЧАСЫ: ${threshold2} ч",
                message = "МЕНЯЙ МАСЛО!"
            )
            prefs.edit().putBoolean("notified_2", true).commit()
        }
    }

    /**
     * Показывает всплывающее окно поверх всего.
     * Использует SYSTEM_ALERT_WINDOW.
     */
    private fun showPopup(title: String, message: String) {
        handler.post {
            try {
                if (windowManager == null) {
                    windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
                }

                // Убираем предыдущее окно
                popupView?.let {
                    try { windowManager?.removeView(it) } catch (e: Exception) {}
                }

                val layout = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(60, 60, 60, 60)
                    setBackgroundColor(0xFF222222.toInt())
                }

                layout.addView(TextView(this).apply {
                    text = title
                    textSize = 22f
                    setTextColor(0xFFFFFFFF.toInt())
                    setPadding(0, 0, 0, 30)
                })

                layout.addView(TextView(this).apply {
                    text = message
                    textSize = 18f
                    setTextColor(0xFFFFFFFF.toInt())
                    setPadding(0, 0, 0, 40)
                })

                layout.addView(Button(this).apply {
                    text = "OK"
                    textSize = 18f
                    setOnClickListener {
                        try { windowManager?.removeView(popupView) } catch (e: Exception) {}
                        popupView = null
                    }
                })

                val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
                }

                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    type,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.CENTER
                }

                windowManager?.addView(layout, params)
                popupView = layout

                LogWriter.log("Показано всплывающее окно: $title")
            } catch (e: Exception) {
                LogWriter.log("Ошибка показа окна: ${e.message}")
            }
        }
    }

    private fun buildNotification(): Notification {
        val openApp = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            this, 0, openApp,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Моточасы")
            .setContentText("Отсчёт идёт")
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setContentIntent(pi)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Моточасы",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Канал для сервиса подсчёта моточасов"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        saveRunnable?.let { handler.removeCallbacks(it) }
        popupView?.let {
            try { windowManager?.removeView(it) } catch (e: Exception) {}
        }
        popupView = null
        LogWriter.log("=== HoursService остановлен ===")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
