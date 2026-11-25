package com.johnparnham.rudeawakening

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

/** Schedules / cancels the exact alarm with AlarmManager. */
object AlarmScheduler {
    const val ACTION_FIRE = "com.johnparnham.rudeawakening.ALARM_FIRE"
    private const val REQUEST_CODE = 1001

    fun alarmIntent(context: Context): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply { action = ACTION_FIRE }
        return PendingIntent.getBroadcast(
            context, REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun schedule(context: Context) {
        scheduleAt(context, AlarmStore.nextTriggerMillis(context))
    }

    fun scheduleAt(context: Context, triggerMillis: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, alarmIntent(context))
    }

    fun cancel(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(alarmIntent(context))
    }
}

/** Fired by AlarmManager at the alarm time. Starts the service + alarm screen. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val serviceIntent = Intent(context, AlarmService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } catch (e: Exception) {
            // Service start failed; the activity attempt below is the fallback.
        }
        val activityIntent = Intent(context, AlarmActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        try {
            context.startActivity(activityIntent)
        } catch (e: Exception) {
            // Locked / background start blocked — the service's full-screen
            // intent notification will surface the alarm instead.
        }
    }
}

/** After a reboot, re-arm the alarm if it was enabled. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED && AlarmStore.isEnabled(context)) {
            AlarmScheduler.schedule(context)
        }
    }
}
