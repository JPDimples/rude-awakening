package com.johnparnham.rudeawakening

import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import java.util.Locale

/**
 * Picks a MALE English voice for the insults.
 * v1 used the phone's default voice, which on John's S25+ was female —
 * he wants a male voice. We score every installed English voice by name
 * and features, preferring ones that say "male", and deepen the pitch a
 * little as a fallback. Returns the voice name picked (or "default").
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

    fun selectMaleVoice(tts: TextToSpeech): Voice? {
        val voices: List<Voice> = try {
            tts.voices?.toList() ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
        val en = voices.filter { it.locale.language == "en" }
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

    /** Apply the male voice (if one is found) + a slightly deeper pitch. */
    fun applyMaleVoice(tts: TextToSpeech): String {
        tts.language = Locale.US
        val voice = selectMaleVoice(tts)
        if (voice != null) {
            try { tts.voice = voice } catch (e: Exception) { }
        }
        tts.setSpeechRate(1.05f)
        tts.setPitch(0.85f)
        return voice?.name ?: "default voice"
    }
}
