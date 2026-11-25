package com.johnparnham.rudeawakening

import android.app.Activity
import android.content.Context
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.WindowManager
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView

class AlarmActivity : Activity() {

    private lateinit var insultText: TextView
    private lateinit var shakesText: TextView
    private lateinit var shakeProgress: ProgressBar

    private var sensorManager: SensorManager? = null
    private var shakeDetector: ShakeDetector? = null
    private var deck: InsultDeck? = null
    private var shakes = 0
    private var shakesRequired = AlarmStore.DEFAULT_SHAKES
    private var finished = false
    private var alarmId: Long = -1L

    private val handler = Handler(Looper.getMainLooper())
    private val insultRunnable = object : Runnable {
        override fun run() {
            if (finished) return
            showNextInsult()
            handler.postDelayed(this, INSULT_INTERVAL_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_alarm)

        alarmId = intent.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, -1L)

        insultText = findViewById(R.id.insultText)
        shakesText = findViewById(R.id.shakesText)
        shakeProgress = findViewById(R.id.shakeProgress)

        shakesRequired = AlarmStore.getShakesRequired(this)
        shakeProgress.max = shakesRequired
        updateShakeUi()

        deck = InsultDeck(InsultStore.load(this))

        // v1.4: heavy metal instead of a voice. Insults still show on screen.
        BackgroundHelper.apply(this, findViewById(R.id.rootLayout))
        findViewById<TextView>(R.id.wakeUpText)
            .setTextColor(BackgroundHelper.accentColor(this))
        findViewById<ProgressBar>(R.id.shakeProgress).progressTintList =
            android.content.res.ColorStateList.valueOf(BackgroundHelper.accentColor(this))
        MetalPlayer.play(this, AlarmStore.getMetalClip(this))
        findViewById<TextView>(R.id.soundText).text = "♪ ${MetalPlayer.currentTitle}"
        showNextInsult()

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        shakeDetector = ShakeDetector { onShake() }
        ShakeDetector.register(sensorManager!!, shakeDetector!!)

        findViewById<Button>(R.id.snoozeButton).apply {
            text = "Snooze ${AlarmStore.getSnoozeMinutes(this@AlarmActivity)} min (+5 shakes)"
            setOnClickListener { snooze() }
        }

        handler.postDelayed(insultRunnable, INSULT_INTERVAL_MS)
    }

    override fun onDestroy() {
        finished = true
        handler.removeCallbacks(insultRunnable)
        try { sensorManager?.let { ShakeDetector.unregister(it, shakeDetector!!) } } catch (e: Exception) { }
        MetalPlayer.stop()
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        // No escape. Shake the phone.
    }

    private fun showNextInsult() {
        val insult = deck?.next() ?: return
        insultText.text = insult
    }

    private fun onShake() {
        if (finished) return
        shakes++
        vibrate()
        updateShakeUi()
        if (shakes >= shakesRequired) {
            finishAlarm()
        }
    }

    private fun updateShakeUi() {
        shakesText.text = "SHAKES: $shakes / $shakesRequired"
        shakeProgress.progress = shakes
    }

    private fun vibrate() {
        try {
            val v = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(50)
            }
        } catch (e: Exception) { }
    }

    private fun snooze() {
        if (finished) return
        finished = true
        handler.removeCallbacks(insultRunnable)
        val nextRequired = (shakesRequired + 5).coerceAtMost(AlarmStore.MAX_SHAKES)
        AlarmStore.setShakesRequired(this, nextRequired)
        // Test alarms (TEST ALARM NOW) have no stored alarm; snooze them as a
        // one-off with a throwaway id so they still ring again in 5 minutes.
        val alarm = currentAlarm() ?: Alarm(-1L, 0, 0, false, 0)
        AlarmScheduler.scheduleAt(
            this, alarm,
            System.currentTimeMillis() + AlarmStore.getSnoozeMinutes(this) * 60 * 1000L
        )
        MetalPlayer.stop()
        AlarmService.stop(this)
        finish()
    }

    private fun currentAlarm(): Alarm? =
        if (alarmId == -1L) null else AlarmStore.getAlarm(this, alarmId)

    private fun finishAlarm() {
        if (finished) return
        finished = true
        handler.removeCallbacks(insultRunnable)
        MetalPlayer.stop()
        AlarmService.stop(this)
        AlarmStore.resetShakes(this)
        // Repeating alarms were already re-armed by the receiver when they
        // fired; this is a belt-and-braces re-arm for the alarm that rang.
        val alarm = currentAlarm()
        if (alarm != null && alarm.enabled && alarm.days != 0) {
            AlarmScheduler.schedule(this, alarm)
        }
        insultText.text = "FINALLY. You're up."
        handler.postDelayed({ finish() }, 1500)
    }

    companion object {
        const val INSULT_INTERVAL_MS = 15_000L
    }
}
