package com.example.motohours

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    companion object {
        const val PREFS_NAME = "motohours"
        const val UI_UPDATE_MS = 1000L
    }

    private lateinit var hoursText: TextView
    private lateinit var statusText: TextView
    private lateinit var thresholdText: TextView
    private val handler = Handler(Looper.getMainLooper())
    private var uiRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LogWriter.init(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 60, 40, 40)
        }

        // ===== Заголовок =====
        root.addView(TextView(this).apply {
            text = "МОТОЧАСЫ"
            textSize = 22f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 20)
        })

        // ===== Главная цифра =====
        hoursText = TextView(this).apply {
            textSize = 44f
            gravity = Gravity.CENTER
            setPadding(0, 40, 0, 40)
        }
        root.addView(hoursText)

        // ===== Разделитель =====
        root.addView(TextView(this).apply {
            text = "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#666666"))
            setPadding(0, 20, 0, 20)
        })

        // ===== Статус =====
        statusText = TextView(this).apply {
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 8)
        }
        root.addView(statusText)

        thresholdText = TextView(this).apply {
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 30)
        }
        root.addView(thresholdText)

        // ===== Разделитель =====
        root.addView(TextView(this).apply {
            text = "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#666666"))
            setPadding(0, 20, 0, 20)
        })

        // ===== Кнопка сброса =====
        root.addView(Button(this).apply {
            text = "СБРОСИТЬ (замена масла)"
            setOnClickListener {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Сброс счётчика")
                    .setMessage("Точно сбросить? Данные о моточасах будут обнулены.")
                    .setPositiveButton("Да, сбросить") { _, _ ->
                        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                        prefs.edit()
                            .putLong("total_ms", 0L)
                            .putLong("last_start_time", System.currentTimeMillis())
                            .putBoolean("notified_1", false)
                            .putBoolean("notified_2", false)
                            .commit()
                        LogWriter.log("СЧЁТЧИК СБРОШЕН")
                        Toast.makeText(
                            this@MainActivity,
                            "Счётчик обнулён",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    .setNegativeButton("Отмена", null)
                    .show()
            }
        })

        // ===== Спойлер: Настройки =====
        val settingsContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 8, 0, 8)
        }

        settingsContent.addView(TextView(this).apply {
            text = "ПОРОГИ УВЕДОМЛЕНИЙ"
            textSize = 14f
            setPadding(0, 10, 0, 10)
        })

        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val currentTh1 = prefs.getLong("threshold_1", HoursService.DEFAULT_THRESHOLD_1)
        val currentTh2 = prefs.getLong("threshold_2", HoursService.DEFAULT_THRESHOLD_2)

        settingsContent.addView(TextView(this).apply {
            text = "Порог 1 (предупреждение):"
            textSize = 13f
        })
        val th1Input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(currentTh1.toString())
            textSize = 16f
        }
        settingsContent.addView(th1Input)

        settingsContent.addView(TextView(this).apply {
            text = "Порог 2 (замена):"
            textSize = 13f
            setPadding(0, 12, 0, 0)
        })
        val th2Input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(currentTh2.toString())
            textSize = 16f
        }
        settingsContent.addView(th2Input)

        settingsContent.addView(Button(this).apply {
            text = "СОХРАНИТЬ ПОРОГИ"
            setOnClickListener {
                val v1 = th1Input.text.toString().toLongOrNull()
                val v2 = th2Input.text.toString().toLongOrNull()
                if (v1 == null || v2 == null || v1 <= 0 || v2 <= 0 || v1 >= v2) {
                    Toast.makeText(
                        this@MainActivity,
                        "Пороги должны быть положительными, порог 1 меньше порога 2",
                        Toast.LENGTH_LONG
                    ).show()
                    return@setOnClickListener
                }
                prefs.edit()
                    .putLong("threshold_1", v1)
                    .putLong("threshold_2", v2)
                    .putBoolean("notified_1", false)
                    .putBoolean("notified_2", false)
                    .commit()
                Toast.makeText(
                    this@MainActivity,
                    "Пороги сохранены",
                    Toast.LENGTH_SHORT
                ).show()
            }
        })

        // ===== Тест уведомлений =====
        settingsContent.addView(TextView(this).apply {
            text = "\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n"
            textSize = 12f
            setTextColor(Color.parseColor("#666666"))
        })
        settingsContent.addView(TextView(this).apply {
            text = "ТЕСТ УВЕДОМЛЕНИЙ"
            textSize = 14f
            setPadding(0, 10, 0, 10)
        })
        settingsContent.addView(Button(this).apply {
            text = "Проверить всплывающие окна"
            setOnClickListener {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (!Settings.canDrawOverlays(this@MainActivity)) {
                        Toast.makeText(
                            this@MainActivity,
                            "Сначала выдай разрешение «Наложение поверх окон»",
                            Toast.LENGTH_LONG
                        ).show()
                        startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:$packageName")
                            )
                        )
                        return@setOnClickListener
                    }
                }
                showTestPopups()
            }
        })

        // ===== Логи =====
        settingsContent.addView(TextView(this).apply {
            text = "\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n"
            textSize = 12f
            setTextColor(Color.parseColor("#666666"))
        })
        settingsContent.addView(TextView(this).apply {
            text = "ЛОГИ"
            textSize = 14f
            setPadding(0, 10, 0, 10)
        })
        settingsContent.addView(Button(this).apply {
            text = "Показать последние 30 строк"
            setOnClickListener {
                val text = LogWriter.readLog()
                if (text.isEmpty() || text == "Лог пуст — событий ещё не было") {
                    Toast.makeText(this@MainActivity, "Лог пуст", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val lines = text.lines().takeLast(30)
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Последние 30 строк")
                    .setMessage(lines.joinToString("\n"))
                    .setPositiveButton("OK", null)
                    .show()
            }
        })
        settingsContent.addView(Button(this).apply {
            text = "Очистить лог"
            setOnClickListener {
                LogWriter.clearLog()
                Toast.makeText(this@MainActivity, "Лог очищен", Toast.LENGTH_SHORT).show()
            }
        })

        root.addView(createSpoyler("▼ НАСТРОЙКИ", settingsContent))

        val outerScroll = ScrollView(this)
        outerScroll.addView(root)
        setContentView(outerScroll)

        // Проверка разрешения при старте
        checkOverlayPermission()

        // Первое обновление
        updateUi()

        // Запускаем периодическое обновление UI (каждую секунду)
        uiRunnable = object : Runnable {
            override fun run() {
                updateUi()
                handler.postDelayed(this, UI_UPDATE_MS)
            }
        }
        handler.postDelayed(uiRunnable!!, UI_UPDATE_MS)
    }

    override fun onResume() {
        super.onResume()
        updateUi()
    }

    override fun onDestroy() {
        super.onDestroy()
        uiRunnable?.let { handler.removeCallbacks(it) }
        uiRunnable = null
    }

    private fun updateUi() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val totalMs = prefs.getLong("total_ms", 0L)
        val lastStart = prefs.getLong("last_start_time", 0L)
        val th2 = prefs.getLong("threshold_2", HoursService.DEFAULT_THRESHOLD_2)

        // Текущее значение
        val currentMs = if (lastStart > 0) {
            totalMs + (System.currentTimeMillis() - lastStart)
        } else {
            totalMs
        }

        hoursText.text = HoursService.formatMs(currentMs)

        // Статус сервиса
        statusText.text = if (lastStart > 0) {
            "Статус сервиса: РАБОТАЕТ"
        } else {
            "Статус сервиса: ОСТАНОВЛЕН"
        }

        // До замены
        val th2Ms = th2 * 3600000L
        val remaining = th2Ms - currentMs
        if (remaining > 0) {
            thresholdText.text = "До замены масла: ${HoursService.formatMs(remaining)}"
        } else {
            thresholdText.text = "ПОРА МЕНЯТЬ МАСЛО!"
        }
    }

    private fun showTestPopups() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val th1 = prefs.getLong("threshold_1", HoursService.DEFAULT_THRESHOLD_1)
        val th2 = prefs.getLong("threshold_2", HoursService.DEFAULT_THRESHOLD_2)

        AlertDialog.Builder(this)
            .setTitle("МОТОЧАСЫ: ${th1} ч")
            .setMessage("Пора планировать замену масла.")
            .setPositiveButton("OK") { _, _ ->
                AlertDialog.Builder(this)
                    .setTitle("МОТОЧАСЫ: ${th2} ч")
                    .setMessage("МЕНЯЙ МАСЛО!")
                    .setPositiveButton("OK", null)
                    .show()
            }
            .show()
    }

    private fun checkOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                AlertDialog.Builder(this)
                    .setTitle("Нужно разрешение")
                    .setMessage(
                        "Для всплывающих уведомлений нужно разрешить " +
                                "«Наложение поверх других окон».\n\nВыдать сейчас?"
                    )
                    .setPositiveButton("Выдать") { _, _ ->
                        startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:$packageName")
                            )
                        )
                    }
                    .setNegativeButton("Позже", null)
                    .show()
            }
        }
    }

    private fun createSpoyler(title: String, content: View): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 10, 0, 10)

            val toggle = Button(this@MainActivity).apply {
                text = title
                textSize = 15f
                setOnClickListener {
                    if (content.visibility == View.GONE) {
                        content.visibility = View.VISIBLE
                        text = title.replace("▼", "▲")
                    } else {
                        content.visibility = View.GONE
                        text = title.replace("▲", "▼")
                    }
                }
            }
            addView(toggle)
            addView(content)
        }
    }
}
