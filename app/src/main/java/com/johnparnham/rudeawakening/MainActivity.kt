package com.johnparnham.rudeawakening

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {

    private lateinit var clockText: TextView
    private lateinit var nextAlarmText: TextView
    private lateinit var permissionText: TextView
    private lateinit var statusText: TextView
    private lateinit var statusLogText: TextView
    private lateinit var voiceText: TextView
    private var testTts: TextToSpeech? = null
    private val clockHandler = Handler(Looper.getMainLooper())
    private val clockRunnable = object : Runnable {
        override fun run() {
            clockText.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            clockHandler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        clockText = findViewById(R.id.clockText)
        nextAlarmText = findViewById(R.id.nextAlarmText)
        permissionText = findViewById(R.id.permissionText)
        statusText = findViewById(R.id.statusText)
        statusLogText = findViewById(R.id.statusLogText)
        voiceText = findViewById(R.id.voiceText)

        // SET ALARM — one tap: pick the time, then it schedules immediately.
        // v1 split this into two buttons and gave no feedback, so it looked
        // like nothing happened when the exact-alarm permission was missing.
        findViewById<Button>(R.id.setAlarmButton).setOnClickListener {
            openTimePicker { hour, minute ->
                AlarmStore.saveTime(this, hour, minute)
                trySchedule()
            }
        }

        findViewById<Button>(R.id.setTimeButton).setOnClickListener {
            openTimePicker { hour, minute ->
                AlarmStore.saveTime(this, hour, minute)
                updateNextAlarm()
                Toast.makeText(this, "Alarm time set to ${formatTime(hour, minute)}", Toast.LENGTH_SHORT).show()
                logStatus("Time changed to ${formatTime(hour, minute)} — tap Set alarm to arm it.")
            }
        }

        findViewById<Button>(R.id.testVoiceButton).setOnClickListener {
            testMaleVoice()
        }

        findViewById<Button>(R.id.cancelAlarmButton).setOnClickListener {
            AlarmScheduler.cancel(this)
            AlarmStore.setEnabled(this, false)
            updateNextAlarm()
            Toast.makeText(this, "Alarm cancelled", Toast.LENGTH_SHORT).show()
            logStatus("Alarm cancelled.")
        }

        findViewById<Button>(R.id.testAlarmButton).setOnClickListener {
            startActivity(Intent(this, AlarmActivity::class.java))
        }

        requestNotificationPermission()
        clockHandler.post(clockRunnable)
        updateNextAlarm()
        updatePermissions()
    }

    override fun onResume() {
        super.onResume()
        updateNextAlarm()
        updatePermissions()
    }

    override fun onDestroy() {
        clockHandler.removeCallbacks(clockRunnable)
        try { testTts?.stop(); testTts?.shutdown() } catch (e: Exception) { }
        super.onDestroy()
    }

    private fun openTimePicker(onPicked: (Int, Int) -> Unit) {
        TimePickerDialog(
            this,
            { _, hour, minute -> onPicked(hour, minute) },
            AlarmStore.getHour(this), AlarmStore.getMinute(this), false
        ).show()
    }

    /** Schedule the alarm and say, on screen, exactly what happened. */
    private fun trySchedule() {
        val hour = AlarmStore.getHour(this)
        val minute = AlarmStore.getMinute(this)
        if (!canScheduleExactAlarms()) {
            logStatus("Set alarm tapped at ${formatTime(hour, minute)} — FAILED: exact alarm permission NOT granted. Opening settings; grant it, come back, tap Set alarm again.")
            Toast.makeText(this, "Exact alarm permission needed — opening settings", Toast.LENGTH_LONG).show()
            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = Uri.parse("package:$packageName")
            }
            try { startActivity(intent) } catch (e: Exception) { }
            updatePermissions()
            return
        }
        val ok = AlarmScheduler.schedule(this)
        if (ok) {
            AlarmStore.setEnabled(this, true)
            AlarmStore.resetShakes(this)
            updateNextAlarm()
            updatePermissions()
            Toast.makeText(this, "Alarm set for ${formatTime(hour, minute)}", Toast.LENGTH_LONG).show()
            logStatus("Alarm set for ${formatTime(hour, minute)} — the system accepted it.")
        } else {
            logStatus("Set alarm FAILED: the system refused the alarm. Screenshot this screen and send it to Jason.")
            Toast.makeText(this, "Alarm was refused by the system — see the message on screen", Toast.LENGTH_LONG).show()
        }
    }

    /** Speaks one insult with the male voice, so John can hear it right now. */
    private fun testMaleVoice() {
        try { testTts?.stop(); testTts?.shutdown() } catch (e: Exception) { }
        Toast.makeText(this, "Testing male voice…", Toast.LENGTH_SHORT).show()
        testTts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val name = VoiceHelper.applyMaleVoice(testTts!!)
                voiceText.text = "Voice: $name"
                logStatus("Test voice: $name")
                try {
                    testTts?.speak(
                        "Get your lazy ass up; the bed isn't going to fuck you.",
                        TextToSpeech.QUEUE_FLUSH, null, "test"
                    )
                } catch (e: Exception) {
                    Toast.makeText(this, "Voice test failed to speak", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Text-to-speech failed to start", Toast.LENGTH_SHORT).show()
                logStatus("Test voice FAILED: text-to-speech did not start.")
            }
        }
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

    private fun formatTime(hour: Int, minute: Int): String {
        val ampm = if (hour < 12) "a.m." else "p.m."
        val h12 = if (hour % 12 == 0) 12 else hour % 12
        return "$h12:${String.format(Locale.getDefault(), "%02d", minute)} $ampm"
    }

    private fun logStatus(msg: String) {
        statusLogText.text = msg
    }

    private fun updateNextAlarm() {
        val hour = AlarmStore.getHour(this)
        val minute = AlarmStore.getMinute(this)
        val time = formatTime(hour, minute)
        if (AlarmStore.isEnabled(this)) {
            statusText.text = "Alarm set for $time"
            nextAlarmText.text = "Alarm set for $time — 20 shakes to stop"
        } else {
            statusText.text = "All alarms are off"
            nextAlarmText.text = "Alarm time: $time (not set)"
        }
    }

    private fun updatePermissions() {
        val exact = if (canScheduleExactAlarms()) "exact alarm: OK" else "exact alarm: NOT GRANTED"
        val notif = if (Build.VERSION.SDK_INT < 33 ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        ) "notifications: OK" else "notifications: NOT GRANTED"
        permissionText.text = "$exact  •  $notif"
    }
}
