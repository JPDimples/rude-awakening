package com.johnparnham.rudeawakening

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast

/** Settings: pick the alarm voice, shakes to stop, snooze length. */
class SettingsActivity : Activity() {

    private var tts: TextToSpeech? = null
    private lateinit var voiceListView: ListView
    private lateinit var selectedVoiceText: TextView
    private lateinit var shakesButton: Button
    private lateinit var snoozeButton: Button
    private var voices: List<Voice> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        voiceListView = findViewById(R.id.voiceListView)
        selectedVoiceText = findViewById(R.id.selectedVoiceText)
        shakesButton = findViewById(R.id.shakesButton)
        snoozeButton = findViewById(R.id.snoozeButton)

        refreshSettingButtons()
        selectedVoiceText.text =
            "Selected voice: ${AlarmStore.getVoiceName(this) ?: "none yet — tap a voice below to hear it"}"

        shakesButton.setOnClickListener { showShakesDialog() }
        snoozeButton.setOnClickListener { showSnoozeDialog() }
        findViewById<Button>(R.id.doneButton).setOnClickListener { finish() }

        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                voices = VoiceHelper.listEnglishVoices(tts!!)
                showVoiceList()
            } else {
                selectedVoiceText.text = "Text-to-speech failed to start — no voices to list."
            }
        }

        voiceListView.setOnItemClickListener { _, _, position, _ ->
            val voice = voices[position]
            AlarmStore.setVoiceName(this, voice.name)
            VoiceHelper.applyVoiceTo(tts!!, voice)
            selectedVoiceText.text = "Selected voice: ${voice.name}"
            showVoiceList()
            try {
                tts?.speak(
                    "Get your lazy ass up; the bed isn't going to fuck you.",
                    TextToSpeech.QUEUE_FLUSH, null, "preview"
                )
            } catch (e: Exception) {
                // Preview failed; the choice is still saved.
            }
            Toast.makeText(this, "Voice saved: ${voice.name}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        try { tts?.stop(); tts?.shutdown() } catch (e: Exception) { }
        super.onDestroy()
    }

    private fun showVoiceList() {
        val saved = AlarmStore.getVoiceName(this)
        val names = voices.map { if (it.name == saved) "✓ ${it.name}" else it.name }
        voiceListView.adapter =
            ArrayAdapter(this, android.R.layout.simple_list_item_1, names)
    }

    private fun refreshSettingButtons() {
        shakesButton.text = "Shakes to stop: ${AlarmStore.getShakesRequired(this)}"
        snoozeButton.text = "Snooze length: ${AlarmStore.getSnoozeMinutes(this)} min"
    }

    private fun showShakesDialog() {
        val options = arrayOf("10", "15", "20", "25", "30", "40")
        val values = intArrayOf(10, 15, 20, 25, 30, 40)
        val current = values.indexOf(AlarmStore.getShakesRequired(this)).coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle("Shakes to stop the alarm")
            .setSingleChoiceItems(options, current) { dialog, which ->
                AlarmStore.setShakesRequired(this, values[which])
                refreshSettingButtons()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showSnoozeDialog() {
        val options = arrayOf("5 min", "10 min", "15 min")
        val values = intArrayOf(5, 10, 15)
        val current = values.indexOf(AlarmStore.getSnoozeMinutes(this)).coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle("Snooze length")
            .setSingleChoiceItems(options, current) { dialog, which ->
                AlarmStore.setSnoozeMinutes(this, values[which])
                refreshSettingButtons()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
