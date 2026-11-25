package com.johnparnham.rudeawakening

import android.content.Context
import java.util.Calendar

/** SharedPreferences storage for the alarm. No account, no internet. */
object AlarmStore {
    private const val PREFS = "rude_awakening"
    private const val KEY_HOUR = "alarm_hour"
    private const val KEY_MINUTE = "alarm_minute"
    private const val KEY_ENABLED = "alarm_enabled"
    private const val KEY_SHAKES = "shakes_required"

    const val DEFAULT_SHAKES = 20
    const val MAX_SHAKES = 40
    const val SNOOZE_MINUTES = 5

    fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getHour(context: Context): Int = prefs(context).getInt(KEY_HOUR, 7)
    fun getMinute(context: Context): Int = prefs(context).getInt(KEY_MINUTE, 0)
    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, false)
    fun getShakesRequired(context: Context): Int =
        prefs(context).getInt(KEY_SHAKES, DEFAULT_SHAKES)

    fun saveTime(context: Context, hour: Int, minute: Int) {
        prefs(context).edit().putInt(KEY_HOUR, hour).putInt(KEY_MINUTE, minute).apply()
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun setShakesRequired(context: Context, shakes: Int) {
        prefs(context).edit().putInt(KEY_SHAKES, shakes.coerceIn(DEFAULT_SHAKES, MAX_SHAKES)).apply()
    }

    fun resetShakes(context: Context) {
        prefs(context).edit().putInt(KEY_SHAKES, DEFAULT_SHAKES).apply()
    }

    /** Next fire time in millis for the stored hour/minute (today, or tomorrow if past). */
    fun nextTriggerMillis(context: Context): Long {
        val now = Calendar.getInstance()
        val fire = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, getHour(context))
            set(Calendar.MINUTE, getMinute(context))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (fire.timeInMillis <= now.timeInMillis) {
            fire.add(Calendar.DAY_OF_YEAR, 1)
        }
        return fire.timeInMillis
    }
}
