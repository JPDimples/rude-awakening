package com.johnparnham.rudeawakening

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast

/**
 * Customize (v1.4): heavy metal alarm sound, app name, background,
 * shakes to stop, snooze length. The old voice picker is gone — the
 * voices are gone, the alarm now plays metal clips.
 */
class SettingsActivity : Activity() {

    private lateinit var soundListView: ListView
    private lateinit var selectedSoundText: TextView
    private lateinit var nameButton: Button
    private lateinit var backgroundButton: Button
    private lateinit var shakesButton: Button
    private lateinit var snoozeButton: Button
    private var clips: List<MetalClip> = emptyList()

    companion object {
        private const val PICK_PHOTO = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        soundListView = findViewById(R.id.soundListView)
        selectedSoundText = findViewById(R.id.selectedSoundText)
        nameButton = findViewById(R.id.nameButton)
        backgroundButton = findViewById(R.id.backgroundButton)
        shakesButton = findViewById(R.id.shakesButton)
        snoozeButton = findViewById(R.id.snoozeButton)

        clips = MetalPlayer.loadClips(this)
        showSoundList()
        refreshButtons()

        soundListView.setOnItemClickListener { _, _, position, _ ->
            if (position == 0) {
                AlarmStore.setMetalClip(this, MetalPlayer.SHUFFLE)
                MetalPlayer.play(this, MetalPlayer.SHUFFLE)
                selectedSoundText.text = "Sound: Shuffle all metal"
                Toast.makeText(this, "Sound saved: Shuffle all metal", Toast.LENGTH_SHORT).show()
            } else {
                val clip = clips[position - 1]
                AlarmStore.setMetalClip(this, clip.file)
                MetalPlayer.play(this, clip.file)
                selectedSoundText.text = "Sound: ${clip.title} — ${clip.creator}"
                Toast.makeText(this, "Sound saved: ${clip.title}", Toast.LENGTH_SHORT).show()
            }
            showSoundList()
        }

        nameButton.setOnClickListener { showNameDialog() }
        backgroundButton.setOnClickListener { showBackgroundDialog() }
        shakesButton.setOnClickListener { showShakesDialog() }
        snoozeButton.setOnClickListener { showSnoozeDialog() }
        findViewById<Button>(R.id.doneButton).setOnClickListener {
            MetalPlayer.stop()
            finish()
        }

        BackgroundHelper.apply(this, findViewById(R.id.rootLayout))
    }

    override fun onResume() {
        super.onResume()
        BackgroundHelper.apply(this, findViewById(R.id.rootLayout))
        refreshButtons()
    }

    override fun onDestroy() {
        MetalPlayer.stop()
        super.onDestroy()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_PHOTO && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            try {
                contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                // Permission didn't stick; the photo may not survive a reboot,
                // but it will work for now.
            }
            AlarmStore.prefs(this).edit()
                .putString(AlarmStore.KEY_BACKGROUND, BackgroundHelper.CUSTOM)
                .putString(AlarmStore.KEY_BACKGROUND_URI, uri.toString())
                .apply()
            showPhotoTextDialog()
        }
    }

    private fun showSoundList() {
        val saved = AlarmStore.getMetalClip(this)
        val names = mutableListOf<String>()
        names.add(if (saved == MetalPlayer.SHUFFLE) "✓ Shuffle all metal" else "Shuffle all metal")
        for (clip in clips) {
            val mark = if (clip.file == saved) "✓ " else ""
            names.add("$mark${clip.title} — ${clip.creator}")
        }
        // Recolor list rows so they stay readable on dark backgrounds too.
        soundListView.setBackgroundResource(BackgroundHelper.cardRes(this))
        soundListView.adapter =
            object : ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, names) {
                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val v = super.getView(position, convertView, parent)
                    (v as? TextView)?.setTextColor(
                        BackgroundHelper.textColor(this@SettingsActivity)
                    )
                    return v
                }
            }
        val savedTitle = if (saved == MetalPlayer.SHUFFLE) "Shuffle all metal"
            else clips.firstOrNull { it.file == saved }?.title ?: saved
        selectedSoundText.text = "Sound: $savedTitle"
    }

    private fun refreshButtons() {
        nameButton.text = "App name: ${AlarmStore.getAppName(this)}"
        backgroundButton.text =
            "Background: ${BackgroundHelper.PRESET_NAMES[BackgroundHelper.getBackground(this)] ?: "My photo"}"
        shakesButton.text = "Shakes to stop: ${AlarmStore.getShakesRequired(this)}"
        snoozeButton.text = "Snooze length: ${AlarmStore.getSnoozeMinutes(this)} min"
    }

    private fun showNameDialog() {
        val input = EditText(this).apply {
            setText(AlarmStore.getAppName(this@SettingsActivity))
            selectAll()
        }
        AlertDialog.Builder(this)
            .setTitle("App name")
            .setMessage("This is the name shown at the top of the app. (The name under the icon on your home screen stays “Rude Awakening” — Android sets that when the app is installed.)")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                AlarmStore.setAppName(this, input.text.toString().trim())
                refreshButtons()
                Toast.makeText(this, "Name saved", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showBackgroundDialog() {
        val presetNames = BackgroundHelper.PRESETS.map {
            BackgroundHelper.PRESET_NAMES[it] ?: it
        }
        val options = (presetNames + "Pick a photo…").toTypedArray()
        val current = BackgroundHelper.PRESETS.indexOf(BackgroundHelper.getBackground(this))
            .coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle("Background")
            .setSingleChoiceItems(options, current) { dialog, which ->
                if (which == options.size - 1) {
                    dialog.dismiss()
                    openPhotoPicker()
                } else {
                    AlarmStore.prefs(this).edit()
                        .putString(AlarmStore.KEY_BACKGROUND, BackgroundHelper.PRESETS[which])
                        .apply()
                    BackgroundHelper.apply(this, findViewById(R.id.rootLayout))
                    refreshButtons()
                    dialog.dismiss()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun openPhotoPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
        }
        try {
            startActivityForResult(intent, PICK_PHOTO)
        } catch (e: Exception) {
            Toast.makeText(this, "No photo picker found on this phone", Toast.LENGTH_SHORT).show()
        }
    }

    /** After picking a photo, ask whether the text on top should be light or dark. */
    private fun showPhotoTextDialog() {
        val dark = BackgroundHelper.isDark(this)
        AlertDialog.Builder(this)
            .setTitle("Text on your photo")
            .setSingleChoiceItems(
                arrayOf("Light text", "Dark text"), if (dark) 0 else 1
            ) { dialog, which ->
                AlarmStore.prefs(this).edit()
                    .putBoolean(AlarmStore.KEY_BACKGROUND_DARK_TEXT, which == 0)
                    .apply()
                BackgroundHelper.apply(this, findViewById(R.id.rootLayout))
                refreshButtons()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { _, _ ->
                // Photo is already saved; apply it with the default light text.
                BackgroundHelper.apply(this, findViewById(R.id.rootLayout))
                refreshButtons()
            }
            .show()
    }

    private fun showShakesDialog() {
        val options = arrayOf("10", "15", "20", "25", "30", "40")
        val values = intArrayOf(10, 15, 20, 25, 30, 40)
        val current = values.indexOf(AlarmStore.getShakesRequired(this)).coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle("Shakes to stop the alarm")
            .setSingleChoiceItems(options, current) { dialog, which ->
                AlarmStore.setShakesRequired(this, values[which])
                refreshButtons()
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
                refreshButtons()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
