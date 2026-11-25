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
import android.speech.tts.TextToSpeech
import android.view.WindowManager
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import java.util.Locale

class AlarmActivity : Activity() {

    private lateinit var insultText: TextView
    private lateinit var shakesText: TextView
    private lateinit var shakeProgress: ProgressBar

    private var tts: TextToSpeech? = null
    private var sensorManager: SensorManager? = null
    private var shakeDetector: ShakeDetector? = null
    private var deck: InsultDeck? = null
    private var shakes = 0
    private var shakesRequired = AlarmStore.DEFAULT_SHAKES
    private var finished = false

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

        insultText = findViewById(R.id.insultText)
        shakesText = findViewById(R.id.shakesText)
        shakeProgress = findViewById(R.id.shakeProgress)

        shakesRequired = AlarmStore.getShakesRequired(this)
        shakeProgress.max = shakesRequired
        updateShakeUi()

        deck = InsultDeck(InsultStore.load(this))

        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                tts?.setSpeechRate(1.1f)
                tts?.setPitch(0.9f)
                showNextInsult()
            }
        }

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        shakeDetector = ShakeDetector { onShake() }
        ShakeDetector.register(sensorManager!!, shakeDetector!!)

        findViewById<Button>(R.id.snoozeButton).setOnClickListener { snooze() }

        handler.postDelayed(insultRunnable, INSULT_INTERVAL_MS)
    }

    override fun onDestroy() {
        finished = true
        handler.removeCallbacks(insultRunnable)
        try { sensorManager?.let { ShakeDetector.unregister(it, shakeDetector!!) } } catch (e: Exception) { }
        try { tts?.stop(); tts?.shutdown() } catch (e: Exception) { }
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        // No escape. Shake the phone.
    }

    private fun showNextInsult() {
        val insult = deck?.next() ?: return
        insultText.text = insult
        speak(insult)
    }

    private fun speak(text: String) {
        try {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "insult")
        } catch (e: Exception) {
            // No TTS: the huge on-screen text still yells at him.
        }
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
        AlarmScheduler.scheduleAt(this, System.currentTimeMillis() + AlarmStore.SNOOZE_MINUTES * 60 * 1000L)
        AlarmService.stop(this)
        finish()
    }

    private fun finishAlarm() {
        if (finished) return
        finished = true
        handler.removeCallbacks(insultRunnable)
        try { tts?.stop() } catch (e: Exception) { }
        AlarmService.stop(this)
        AlarmStore.resetShakes(this)
        // Re-arm for tomorrow if the alarm is still enabled.
        if (AlarmStore.isEnabled(this)) {
            AlarmScheduler.schedule(this)
        }
        insultText.text = "FINALLY. You're up."
        handler.postDelayed({ finish() }, 1500)
    }

    companion object {
        const val INSULT_INTERVAL_MS = 15_000L
    }
}
