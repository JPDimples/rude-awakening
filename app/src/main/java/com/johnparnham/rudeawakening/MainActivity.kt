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
import android.widget.Button
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {

    private lateinit var clockText: TextView
    private lateinit var nextAlarmText: TextView
    private lateinit var permissionText: TextView
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

        findViewById<Button>(R.id.setTimeButton).setOnClickListener {
            TimePickerDialog(
                this,
                { _, hour, minute ->
                    AlarmStore.saveTime(this, hour, minute)
                    updateNextAlarm()
                },
                AlarmStore.getHour(this), AlarmStore.getMinute(this), false
            ).show()
        }

        findViewById<Button>(R.id.setAlarmButton).setOnClickListener {
            if (!canScheduleExactAlarms()) {
                permissionText.text = "Exact alarm permission needed — opening settings."
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = Uri.parse("package:$packageName")
                }
                try { startActivity(intent) } catch (e: Exception) { }
                return@setOnClickListener
            }
            AlarmScheduler.schedule(this)
            AlarmStore.setEnabled(this, true)
            AlarmStore.resetShakes(this)
            updateNextAlarm()
            updatePermissions()
        }

        findViewById<Button>(R.id.cancelAlarmButton).setOnClickListener {
            AlarmScheduler.cancel(this)
            AlarmStore.setEnabled(this, false)
            updateNextAlarm()
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
        super.onDestroy()
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

    private fun updateNextAlarm() {
        val hour = AlarmStore.getHour(this)
        val minute = AlarmStore.getMinute(this)
        val time = String.format(Locale.getDefault(), "%02d:%02d", hour, minute)
        nextAlarmText.text = if (AlarmStore.isEnabled(this)) {
            "Alarm set for $time"
        } else {
            "Alarm time: $time (not set)"
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
