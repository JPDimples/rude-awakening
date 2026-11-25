package com.johnparnham.rudeawakening

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/**
 * One alarm. days is a bitmask, bit0 = Sunday … bit6 = Saturday.
 * days == 0 means "once" (no repeat).
 */
data class Alarm(
    val id: Long,
    val hour: Int,
    val minute: Int,
    val enabled: Boolean,
    val days: Int
) {
    fun timeString(): String {
        val ampm = if (hour < 12) "a.m." else "p.m."
        val h12 = if (hour % 12 == 0) 12 else hour % 12
        return "$h12:${minute.toString().padStart(2, '0')} $ampm"
    }
}

/** SharedPreferences storage. No account, no internet. */
object AlarmStore {
    private const val PREFS = "rude_awakening"
    private const val KEY_ALARMS = "alarms_json"
    // v1.x single-alarm keys (migration only)
    private const val KEY_HOUR = "alarm_hour"
    private const val KEY_MINUTE = "alarm_minute"
    private const val KEY_ENABLED = "alarm_enabled"
    private const val KEY_SHAKES = "shakes_required"
    private const val KEY_SNOOZE_MINUTES = "snooze_minutes"
    private const val KEY_VOICE_NAME = "voice_name"

    const val DEFAULT_SHAKES = 20
    const val MAX_SHAKES = 40
    const val DEFAULT_SNOOZE_MINUTES = 5
    const val ALL_DAYS = 0x7F

    fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ---- alarm list ----

    fun getAlarms(context: Context): List<Alarm> {
        val json = prefs(context).getString(KEY_ALARMS, null)
        if (json == null) {
            // First run after v1.x: migrate the old single alarm, if one existed.
            val hadOld = prefs(context).contains(KEY_HOUR)
            val list = if (hadOld) listOf(
                Alarm(
                    id = System.currentTimeMillis(),
                    hour = prefs(context).getInt(KEY_HOUR, 7),
                    minute = prefs(context).getInt(KEY_MINUTE, 0),
                    enabled = prefs(context).getBoolean(KEY_ENABLED, false),
                    days = ALL_DAYS
                )
            ) else emptyList()
            saveAlarms(context, list)
            return list
        }
        val out = mutableListOf<Alarm>()
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(
                    Alarm(
                        o.getLong("id"), o.getInt("hour"), o.getInt("minute"),
                        o.getBoolean("enabled"), o.getInt("days")
                    )
                )
            }
        } catch (e: Exception) {
            // Corrupt list: start clean rather than crash.
        }
        return out
    }

    fun saveAlarms(context: Context, alarms: List<Alarm>) {
        val arr = JSONArray()
        for (a in alarms) {
            arr.put(JSONObject().apply {
                put("id", a.id)
                put("hour", a.hour)
                put("minute", a.minute)
                put("enabled", a.enabled)
                put("days", a.days)
            })
        }
        prefs(context).edit().putString(KEY_ALARMS, arr.toString()).apply()
    }

    fun getAlarm(context: Context, id: Long): Alarm? =
        getAlarms(context).firstOrNull { it.id == id }

    fun updateAlarm(context: Context, alarm: Alarm) {
        saveAlarms(context, getAlarms(context).map { if (it.id == alarm.id) alarm else it })
    }

    fun addAlarm(context: Context, alarm: Alarm) {
        saveAlarms(context, getAlarms(context) + alarm)
    }

    fun deleteAlarm(context: Context, id: Long) {
        saveAlarms(context, getAlarms(context).filter { it.id != id })
    }

    fun newAlarmId(context: Context): Long {
        var id = System.currentTimeMillis()
        while (getAlarm(context, id) != null) id++
        return id
    }

    // ---- shakes / snooze / voice ----

    fun getShakesRequired(context: Context): Int =
        prefs(context).getInt(KEY_SHAKES, DEFAULT_SHAKES)

    fun setShakesRequired(context: Context, shakes: Int) {
        prefs(context).edit().putInt(KEY_SHAKES, shakes.coerceIn(10, MAX_SHAKES)).apply()
    }

    fun resetShakes(context: Context) {
        prefs(context).edit().putInt(KEY_SHAKES, DEFAULT_SHAKES).apply()
    }

    fun getSnoozeMinutes(context: Context): Int =
        prefs(context).getInt(KEY_SNOOZE_MINUTES, DEFAULT_SNOOZE_MINUTES)

    fun setSnoozeMinutes(context: Context, minutes: Int) {
        prefs(context).edit().putInt(KEY_SNOOZE_MINUTES, minutes.coerceIn(1, 30)).apply()
    }

    fun getVoiceName(context: Context): String? =
        prefs(context).getString(KEY_VOICE_NAME, null)

    fun setVoiceName(context: Context, name: String) {
        prefs(context).edit().putString(KEY_VOICE_NAME, name).apply()
    }

    // ---- days ----

    fun daysToString(days: Int): String {
        if (days == 0) return "Once"
        if (days == ALL_DAYS) return "Every day"
        if (days == 0x3E) return "Weekdays" // Mon–Fri, bits 1–5
        if (days == 0x41) return "Weekends" // Sun + Sat, bits 0 and 6
        val names = arrayOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
        return (0..6).filter { days and (1 shl it) != 0 }.joinToString(", ") { names[it] }
    }

    /** Next fire time for this alarm: first future slot on a selected day. */
    fun nextTriggerMillis(alarm: Alarm): Long {
        val now = Calendar.getInstance()
        for (offset in 0..7) {
            val fire = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, offset)
                set(Calendar.HOUR_OF_DAY, alarm.hour)
                set(Calendar.MINUTE, alarm.minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (fire.timeInMillis <= now.timeInMillis) continue
            if (alarm.days == 0) return fire.timeInMillis // once: first future slot
            val bit = fire.get(Calendar.DAY_OF_WEEK) - 1 // SUNDAY=1 -> bit 0
            if (alarm.days and (1 shl bit) != 0) return fire.timeInMillis
        }
        // Unreachable for a repeating alarm (it always matches within 7 days).
        val fallback = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, alarm.hour)
            set(Calendar.MINUTE, alarm.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return fallback.timeInMillis
    }
}
