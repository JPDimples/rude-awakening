package com.johnparnham.rudeawakening

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {

    private lateinit var titleText: TextView
    private lateinit var clockText: TextView
    private lateinit var statusText: TextView
    private lateinit var alarmListContainer: LinearLayout
    private lateinit var permissionText: TextView
    private lateinit var statusLogText: TextView
    private lateinit var soundText: TextView
    private val clockHandler = Handler(Looper.getMainLooper())
    private val metalHandler = Handler(Looper.getMainLooper())
    private val clockRunnable = object : Runnable {
        override fun run() {
            clockText.text = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
            clockHandler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        titleText = findViewById(R.id.titleText)
        clockText = findViewById(R.id.clockText)
        statusText = findViewById(R.id.statusText)
        alarmListContainer = findViewById(R.id.alarmListContainer)
        permissionText = findViewById(R.id.permissionText)
        statusLogText = findViewById(R.id.statusLogText)
        soundText = findViewById(R.id.soundText)

        findViewById<Button>(R.id.addAlarmButton).setOnClickListener { addAlarm() }
        findViewById<Button>(R.id.settingsButton).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<Button>(R.id.testMetalButton).setOnClickListener { testMetal() }
        findViewById<Button>(R.id.testAlarmButton).setOnClickListener {
            startActivity(Intent(this, AlarmActivity::class.java))
        }

        requestNotificationPermission()
        clockHandler.post(clockRunnable)
        applyTheme()
        refreshAlarms()
        updatePermissions()
    }

    override fun onResume() {
        super.onResume()
        applyTheme()
        titleText.text = AlarmStore.getAppName(this)
        refreshAlarms()
        updatePermissions()
    }

    /** Background + text colours + the amber industrial clock. */
    private fun applyTheme() {
        BackgroundHelper.apply(this, findViewById(R.id.rootLayout))
        clockText.setTextColor(BackgroundHelper.accentColor(this))
    }

    override fun onDestroy() {
        clockHandler.removeCallbacks(clockRunnable)
        metalHandler.removeCallbacksAndMessages(null)
        MetalPlayer.stop()
        super.onDestroy()
    }

    /** Rebuild the alarm cards from storage. */
    private fun refreshAlarms() {
        titleText.text = AlarmStore.getAppName(this)
        alarmListContainer.removeAllViews()
        val alarms = AlarmStore.getAlarms(this)
            .sortedWith(compareBy({ it.hour }, { it.minute }))
        if (alarms.isEmpty()) {
            val empty = TextView(this).apply {
                text = "No alarms yet — tap + Add alarm."
                textSize = 16f
                setTextColor(BackgroundHelper.textColor(this@MainActivity))
                setPadding(8, 24, 8, 24)
            }
            alarmListContainer.addView(empty)
            // Don't leave a stale "Alarm set for ..." log when no alarms exist.
            statusLogText.text = ""
        }
        for (alarm in alarms) {
            alarmListContainer.addView(buildAlarmCard(alarm))
        }
        val enabled = alarms.count { it.enabled }
        statusText.text = if (enabled == 0) "All alarms are off"
            else "$enabled alarm${if (enabled == 1) "" else "s"} set"
        refreshSoundText()
    }

    private fun refreshSoundText() {
        val clip = AlarmStore.getMetalClip(this)
        val title = if (clip == MetalPlayer.SHUFFLE) "Shuffle all metal"
            else MetalPlayer.loadClips(this).firstOrNull { it.file == clip }?.title ?: clip
        soundText.text = "Sound: $title"
    }

    private fun buildAlarmCard(alarm: Alarm): View {
        val card = layoutInflater.inflate(R.layout.alarm_card, alarmListContainer, false)
        card.setBackgroundResource(BackgroundHelper.cardRes(this))
        val timeText = card.findViewById<TextView>(R.id.cardTimeText)
        val daysText = card.findViewById<TextView>(R.id.cardDaysText)
        timeText.text = alarm.timeString()
        daysText.text = AlarmStore.daysToString(alarm.days)
        timeText.setTextColor(BackgroundHelper.textColor(this))
        daysText.setTextColor(BackgroundHelper.textColor(this))
        val toggle = card.findViewById<Button>(R.id.cardToggle)
        styleToggle(toggle, alarm.enabled)
        toggle.setOnClickListener { onToggleAlarm(alarm, !alarm.enabled) }
        card.setOnClickListener { editAlarm(alarm) }
        card.findViewById<Button>(R.id.cardDeleteButton).setOnClickListener {
            AlarmScheduler.cancel(this, alarm)
            AlarmStore.deleteAlarm(this, alarm.id)
            refreshAlarms()
            Toast.makeText(this, "Alarm deleted", Toast.LENGTH_SHORT).show()
        }
        return card
    }

    /** Industrial ON/OFF button: amber when on, dark steel when off. */
    private fun styleToggle(toggle: Button, enabled: Boolean) {
        if (enabled) {
            toggle.text = "ON"
            toggle.setBackgroundResource(R.drawable.button_primary)
            toggle.setTextColor(0xFF111111.toInt())
        } else {
            toggle.text = "OFF"
            toggle.setBackgroundResource(R.drawable.button_bg)
            toggle.setTextColor(0xFF8A8A9A.toInt())
        }
    }

    private fun onToggleAlarm(alarm: Alarm, checked: Boolean) {
        if (checked) {
            if (!canScheduleExactAlarms()) {
                Toast.makeText(
                    this, "Exact alarm permission needed — opening settings",
                    Toast.LENGTH_LONG
                ).show()
                logStatus("Switch-on FAILED: exact alarm permission NOT granted. Grant it, then switch the alarm on.")
                openExactAlarmSettings()
                refreshAlarms()
                return
            }
            val updated = alarm.copy(enabled = true)
            if (AlarmScheduler.schedule(this, updated)) {
                AlarmStore.updateAlarm(this, updated)
                AlarmStore.resetShakes(this)
                Toast.makeText(
                    this, "Alarm set for ${alarm.timeString()}", Toast.LENGTH_SHORT
                ).show()
                logStatus("Alarm set for ${alarm.timeString()} (${AlarmStore.daysToString(alarm.days)}).")
            } else {
                logStatus("Alarm FAILED: the system refused it. Screenshot this screen and send it to Jason.")
                Toast.makeText(this, "Alarm was refused by the system", Toast.LENGTH_LONG).show()
            }
        } else {
            AlarmScheduler.cancel(this, alarm)
            AlarmStore.updateAlarm(this, alarm.copy(enabled = false))
            logStatus("Alarm ${alarm.timeString()} switched off.")
        }
        refreshAlarms()
        updatePermissions()
    }

    private fun addAlarm() {
        openTimePicker(7, 0) { hour, minute ->
            showDaysDialog(AlarmStore.ALL_DAYS, false, { days ->
                val alarm = Alarm(AlarmStore.newAlarmId(this), hour, minute, true, days)
                if (!canScheduleExactAlarms()) {
                    AlarmStore.addAlarm(this, alarm.copy(enabled = false))
                    refreshAlarms()
                    Toast.makeText(
                        this, "Exact alarm permission needed — alarm saved but off",
                        Toast.LENGTH_LONG
                    ).show()
                    logStatus("Alarm saved but OFF: exact alarm permission NOT granted. Grant it, then switch the alarm on.")
                    openExactAlarmSettings()
                    return@showDaysDialog
                }
                if (AlarmScheduler.schedule(this, alarm)) {
                    AlarmStore.addAlarm(this, alarm)
                    AlarmStore.resetShakes(this)
                    Toast.makeText(
                        this, "Alarm set for ${alarm.timeString()}", Toast.LENGTH_LONG
                    ).show()
                    logStatus("Alarm set for ${alarm.timeString()} (${AlarmStore.daysToString(days)}).")
                } else {
                    AlarmStore.addAlarm(this, alarm.copy(enabled = false))
                    logStatus("Alarm FAILED: the system refused it. Screenshot this screen and send it to Jason.")
                }
                refreshAlarms()
                updatePermissions()
            }, null)
        }
    }

    private fun editAlarm(alarm: Alarm) {
        openTimePicker(alarm.hour, alarm.minute) { hour, minute ->
            showDaysDialog(alarm.days, true, { days ->
                AlarmScheduler.cancel(this, alarm)
                val updated = alarm.copy(hour = hour, minute = minute, days = days)
                AlarmStore.updateAlarm(this, updated)
                if (updated.enabled) {
                    if (AlarmScheduler.schedule(this, updated)) {
                        Toast.makeText(
                            this, "Alarm updated to ${updated.timeString()}",
                            Toast.LENGTH_SHORT
                        ).show()
                        logStatus("Alarm updated to ${updated.timeString()} (${AlarmStore.daysToString(days)}).")
                    } else {
                        AlarmStore.updateAlarm(this, updated.copy(enabled = false))
                        logStatus("Alarm update FAILED: the system refused it.")
                    }
                }
                refreshAlarms()
            }, {
                AlarmScheduler.cancel(this, alarm)
                AlarmStore.deleteAlarm(this, alarm.id)
                refreshAlarms()
                Toast.makeText(this, "Alarm deleted", Toast.LENGTH_SHORT).show()
            })
        }
    }

    /** Days picker. onDelete is only offered when editing an existing alarm. */
    private fun showDaysDialog(
        currentDays: Int,
        allowDelete: Boolean,
        onPicked: (Int) -> Unit,
        onDelete: (() -> Unit)?
    ) {
        val dayNames = arrayOf(
            "Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"
        )
        val checked = BooleanArray(7) { i -> currentDays and (1 shl i) != 0 }
        val builder = AlertDialog.Builder(this)
            .setTitle("Repeat days")
            .setMultiChoiceItems(dayNames, checked) { _, which, isChecked ->
                checked[which] = isChecked
            }
            .setPositiveButton("Save") { _, _ ->
                var mask = 0
                for (i in 0..6) if (checked[i]) mask = mask or (1 shl i)
                onPicked(mask)
            }
            .setNegativeButton("Cancel", null)
        if (allowDelete && onDelete != null) {
            builder.setNeutralButton("Delete") { _, _ -> onDelete() }
        }
        builder.show()
    }

    private fun openTimePicker(
        defaultHour: Int,
        defaultMinute: Int,
        onPicked: (Int, Int) -> Unit
    ) {
        TimePickerDialog(
            this,
            { _, hour, minute -> onPicked(hour, minute) },
            defaultHour, defaultMinute, false
        ).show()
    }

    private fun openExactAlarmSettings() {
        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            data = Uri.parse("package:$packageName")
        }
        try { startActivity(intent) } catch (e: Exception) { }
        updatePermissions()
    }

    /** Plays the saved metal clip for 10 seconds (tap again to stop early). */
    private fun testMetal() {
        if (MetalPlayer.isPlaying()) {
            MetalPlayer.stop()
            refreshSoundText()
            return
        }
        MetalPlayer.play(this, AlarmStore.getMetalClip(this))
        soundText.text = "♪ Playing: ${MetalPlayer.currentTitle} (tap again to stop)"
        metalHandler.postDelayed({
            if (MetalPlayer.isPlaying()) {
                MetalPlayer.stop()
                refreshSoundText()
            }
        }, 10_000)
    }

    private fun canScheduleExactAlarms(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val am = getSystemService(ALARM_SERVICE) as AlarmManager
        return am.canScheduleExactAlarms()
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    private fun logStatus(msg: String) {
        statusLogText.text = msg
    }

    private fun updatePermissions() {
        val exact = if (canScheduleExactAlarms()) "exact alarm: OK" else "exact alarm: NOT GRANTED"
        val notif = if (Build.VERSION.SDK_INT < 33 ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        ) "notifications: OK" else "notifications: NOT GRANTED"
        permissionText.text = "$exact  •  $notif"
    }
}
