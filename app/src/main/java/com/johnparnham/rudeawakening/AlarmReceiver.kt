package com.johnparnham.rudeawakening

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

/** Schedules / cancels exact alarms with AlarmManager, one per alarm id. */
object AlarmScheduler {
    const val ACTION_FIRE = "com.johnparnham.rudeawakening.ALARM_FIRE"
    const val EXTRA_ALARM_ID = "alarm_id"

    private fun requestCode(alarmId: Long): Int = (alarmId and 0x7fffffff).toInt()

    fun alarmIntent(context: Context, alarmId: Long): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_FIRE
            putExtra(EXTRA_ALARM_ID, alarmId)
        }
        return PendingIntent.getBroadcast(
            context, requestCode(alarmId), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** Returns true if the exact alarm was accepted by the system. */
    fun schedule(context: Context, alarm: Alarm): Boolean =
        scheduleAt(context, alarm, AlarmStore.nextTriggerMillis(alarm))

    /** Returns false (instead of crashing) if the system refuses the alarm. */
    fun scheduleAt(context: Context, alarm: Alarm, triggerMillis: Long): Boolean {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return try {
            am.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP, triggerMillis, alarmIntent(context, alarm.id)
            )
            true
        } catch (e: SecurityException) {
            false
        }
    }

    fun scheduleAll(context: Context) {
        for (alarm in AlarmStore.getAlarms(context)) {
            if (alarm.enabled) schedule(context, alarm)
        }
    }

    fun cancel(context: Context, alarm: Alarm) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(alarmIntent(context, alarm.id))
    }

    fun cancelAll(context: Context) {
        for (alarm in AlarmStore.getAlarms(context)) cancel(context, alarm)
    }
}

/** Fired by AlarmManager at the alarm time. Starts the service + alarm screen. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val alarmId = intent?.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, -1L) ?: -1L
        val alarm = if (alarmId != -1L) AlarmStore.getAlarm(context, alarmId) else null
        if (alarm != null) {
            if (alarm.days != 0) {
                // Repeating alarm: arm the next occurrence now, so it survives
                // even if the phone is off when this one fires.
                AlarmScheduler.schedule(context, alarm)
            } else {
                // One-time alarm: it has fired, switch it off.
                AlarmStore.updateAlarm(context, alarm.copy(enabled = false))
            }
        }
        val serviceIntent = Intent(context, AlarmService::class.java).apply {
            putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
        }
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
            putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
        }
        try {
            context.startActivity(activityIntent)
        } catch (e: Exception) {
            // Locked / background start blocked — the service's full-screen
            // intent notification will surface the alarm instead.
        }
    }
}

/** After a reboot, re-arm every enabled alarm. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            AlarmScheduler.scheduleAll(context)
        }
    }
}
