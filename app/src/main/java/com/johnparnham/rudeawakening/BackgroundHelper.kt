package com.johnparnham.rudeawakening

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView

/**
 * Custom backgrounds (v1.4): preset gradients or a photo John picks from
 * his gallery. Also recolors on-screen text so it stays readable — dark
 * presets and photos get light text, light presets get dark text.
 */
object BackgroundHelper {
    const val PASTEL = "pastel"
    const val SUNRISE = "sunrise"
    const val OCEAN = "ocean"
    const val MIDNIGHT = "midnight"
    const val BLOOD = "blood"
    const val FOREST = "forest"
    const val EMBER = "ember"
    const val STEEL = "steel"
    const val TOXIC = "toxic"
    const val CUSTOM = "custom"

    val PRESETS = listOf(PASTEL, SUNRISE, OCEAN, MIDNIGHT, BLOOD, FOREST, EMBER, STEEL, TOXIC)
    val PRESET_NAMES = mapOf(
        PASTEL to "Pastel",
        SUNRISE to "Sunrise",
        OCEAN to "Ocean",
        MIDNIGHT to "Midnight",
        BLOOD to "Blood",
        FOREST to "Forest",
        EMBER to "Ember",
        STEEL to "Steel",
        TOXIC to "Toxic"
    )
    private val DARK_PRESETS = setOf(MIDNIGHT, BLOOD, FOREST, EMBER, STEEL, TOXIC)

    fun getBackground(context: android.content.Context): String =
        AlarmStore.prefs(context).getString(AlarmStore.KEY_BACKGROUND, MIDNIGHT) ?: MIDNIGHT

    fun isDark(context: android.content.Context): Boolean {
        val bg = getBackground(context)
        if (bg == CUSTOM) {
            return AlarmStore.prefs(context)
                .getBoolean(AlarmStore.KEY_BACKGROUND_DARK_TEXT, true)
        }
        return bg in DARK_PRESETS
    }

    fun backgroundRes(context: android.content.Context): Int = when (getBackground(context)) {
        SUNRISE -> R.drawable.bg_sunrise
        OCEAN -> R.drawable.bg_ocean
        MIDNIGHT -> R.drawable.bg_midnight
        BLOOD -> R.drawable.bg_blood
        FOREST -> R.drawable.bg_forest
        EMBER -> R.drawable.bg_ember
        STEEL -> R.drawable.bg_steel
        TOXIC -> R.drawable.bg_toxic
        else -> R.drawable.bg_gradient
    }

    fun cardRes(context: android.content.Context): Int =
        if (isDark(context)) R.drawable.card_bg_dark else R.drawable.card_bg

    fun textColor(context: android.content.Context): Int =
        if (isDark(context)) 0xFFE8E8F0.toInt() else 0xFF55556A.toInt()

    /** Industrial amber accent: bright on dark backgrounds, darker on light ones. */
    fun accentColor(context: android.content.Context): Int =
        if (isDark(context)) 0xFFFFB000.toInt() else 0xFF7A5200.toInt()

    /** Apply the saved background + text colours to an activity's content view. */
    fun apply(activity: Activity, root: View) {
        val bg = getBackground(activity)
        if (bg == CUSTOM) {
            val uriString = AlarmStore.prefs(activity)
                .getString(AlarmStore.KEY_BACKGROUND_URI, null)
            var done = false
            if (uriString != null) {
                try {
                    val bmp = loadPhoto(activity, Uri.parse(uriString))
                    if (bmp != null) {
                        root.background = BitmapDrawable(activity.resources, bmp)
                        done = true
                    }
                } catch (e: Exception) {
                    // Bad / missing photo: fall through to the pastel default.
                }
            }
            if (!done) root.setBackgroundResource(R.drawable.bg_gradient)
        } else {
            root.setBackgroundResource(backgroundRes(activity))
        }
        applyTextColors(root, textColor(activity))
    }

    /** Downsample a gallery photo so a 12MP image doesn't blow up memory. */
    private fun loadPhoto(activity: Activity, uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        activity.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        var sample = 1
        val maxDim = maxOf(bounds.outWidth, bounds.outHeight)
        while (maxDim / sample > 1080) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return activity.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        }
    }

    private fun applyTextColors(view: View, color: Int) {
        if (view is Button) return // buttons keep their own lavender style
        if (view is TextView) view.setTextColor(color)
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) applyTextColors(view.getChildAt(i), color)
        }
    }
}
