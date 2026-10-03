package com.example.motohours

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ShutdownReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return

        LogWriter.init(context)
        LogWriter.log("ShutdownReceiver: событие $action")

        when (action) {
            Intent.ACTION_SHUTDOWN,
            "android.intent.action.QUICKBOOT_POWEROFF",
            Intent.ACTION_POWER_DISCONNECTED,
            Intent.ACTION_SCREEN_OFF -> {

                LogWriter.log("Сохраняем финальное значение перед выключением")
                HoursService.saveCurrentValue(context)
            }
        }
    }
}
