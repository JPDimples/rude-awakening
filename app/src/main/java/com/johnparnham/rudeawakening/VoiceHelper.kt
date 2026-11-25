package com.johnparnham.rudeawakening

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import java.util.Locale

/**
 * Voice picking for the insults.
 *
 * v1.1 tried to guess a male voice from the voice names and got it wrong on
 * John's S25+ — Samsung's voices don't say "male" in their names, so the
 * guess fell back to the default (female) voice. v1.3 stops guessing: the
 * Settings screen lists every installed English voice and John taps the
 * one he wants to hear. The guess below is now only a fallback when no
 * voice has been picked yet.
 */
object VoiceHelper {

    private val MALE_NAMES = listOf(
        "daniel", "david", "james", "guy", "michael", "robert",
        "john", "alex", "brian", "ryan", "liam", "george", "fred"
    )
    private val FEMALE_NAMES = listOf(
        "samantha", "karen", "susan", "zira", "aria", "jenny",
        "emma", "olivia", "sophia", "ava", "female"
    )

    fun listEnglishVoices(tts: TextToSpeech): List<Voice> {
        val voices: List<Voice> = try {
            tts.voices?.toList() ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
        return voices.filter { it.locale.language == "en" }
            .sortedBy { it.name.lowercase(Locale.US) }
    }

    fun selectMaleVoice(tts: TextToSpeech): Voice? {
        val en = listEnglishVoices(tts)
        if (en.isEmpty()) return null
        fun score(v: Voice): Int {
            val name = v.name.lowercase(Locale.US)
            val feats = try { v.features.joinToString(" ").lowercase(Locale.US) } catch (e: Exception) { "" }
            var s = 0
            if ("male" in name || "male" in feats) s += 100
            if ("female" in name || "female" in feats) s -= 100
            for (w in MALE_NAMES) if (w in name) s += 20
            for (w in FEMALE_NAMES) if (w in name) s -= 50
            if (v.locale.toString().startsWith("en_US")) s += 5
            return s
        }
        val best = en.maxByOrNull { score(it) } ?: return null
        // Only accept it if it is not clearly a female voice.
        return if (score(best) >= 0) best else null
    }

    /**
     * Apply John's saved voice if he picked one in Settings, else the male
     * guess. Returns the name of the voice actually used (or "default").
     */
    fun applyVoice(tts: TextToSpeech, context: Context): String {
        tts.language = Locale.US
        val stored = AlarmStore.getVoiceName(context)
        val voices = try { tts.voices?.toList() ?: emptyList() } catch (e: Exception) { emptyList() }
        val chosen = stored?.let { name -> voices.firstOrNull { it.name == name } }
            ?: selectMaleVoice(tts)
        if (chosen != null) {
            try { tts.voice = chosen } catch (e: Exception) { }
        }
        tts.setSpeechRate(1.0f)
        tts.setPitch(0.8f)
        return chosen?.name ?: "default voice"
    }

    /** Apply one exact voice (used by the Settings preview). */
    fun applyVoiceTo(tts: TextToSpeech, voice: Voice): String {
        try { tts.voice = voice } catch (e: Exception) { }
        tts.setSpeechRate(1.0f)
        tts.setPitch(0.8f)
        return voice.name
    }
}
