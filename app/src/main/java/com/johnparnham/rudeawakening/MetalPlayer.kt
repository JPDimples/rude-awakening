package com.johnparnham.rudeawakening

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.media.MediaPlayer
import org.json.JSONObject

data class MetalClip(val file: String, val title: String, val creator: String, val license: String)

/**
 * Plays the heavy metal alarm clips from assets/metal/ (v1.4 replaces the
 * text-to-speech voices John hated). One clip loops until the alarm is
 * stopped, or SHUFFLE plays a different clip each time one ends.
 * Clips are royalty-free (CC0 / CC BY / CC BY-SA), trimmed to 30 seconds.
 */
object MetalPlayer {
    const val SHUFFLE = "shuffle"
    private var player: MediaPlayer? = null
    private var clips: List<MetalClip> = emptyList()
    var currentTitle: String = ""
        private set

    fun loadClips(context: Context): List<MetalClip> {
        if (clips.isNotEmpty()) return clips
        val out = mutableListOf<MetalClip>()
        try {
            val text = context.assets.open("metal/metal.json").bufferedReader().use { it.readText() }
            val arr = JSONObject(text).getJSONArray("clips")
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(
                    MetalClip(
                        o.getString("file"), o.getString("title"),
                        o.getString("creator"), o.getString("license")
                    )
                )
            }
        } catch (e: Exception) {
            // No manifest: no sound, but the alarm screen + insults still work.
        }
        clips = out
        return out
    }

    /** clip = a file name from metal.json, or SHUFFLE for all of them. */
    fun play(context: Context, clip: String) {
        stop()
        val all = loadClips(context)
        if (all.isEmpty()) return
        val start: MetalClip = if (clip == SHUFFLE) {
            all.shuffled().first()
        } else {
            all.firstOrNull { it.file == clip } ?: all.first()
        }
        playClip(context, start, all, clip == SHUFFLE)
    }

    private fun playClip(context: Context, clip: MetalClip, all: List<MetalClip>, shuffleMode: Boolean) {
        currentTitle = clip.title
        try {
            val fd: AssetFileDescriptor = context.assets.openFd("metal/${clip.file}")
            val mp = MediaPlayer()
            // Alarm stream (v1.6): rings on the alarm volume, not the media
            // volume, so it still wakes him with media muted.
            mp.setAudioAttributes(
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            mp.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
            fd.close()
            mp.setOnCompletionListener {
                if (shuffleMode) {
                    try { mp.release() } catch (e: Exception) { }
                    val next = all.filter { it.file != clip.file }.randomOrNull() ?: clip
                    try { playClip(context, next, all, true) } catch (e: Exception) { }
                } else {
                    // Single clip: loop it until the alarm is stopped.
                    try { mp.seekTo(0); mp.start() } catch (e: Exception) { }
                }
            }
            mp.prepare()
            mp.start()
            player = mp
        } catch (e: Exception) {
            player = null
        }
    }

    fun stop() {
        try { player?.stop() } catch (e: Exception) { }
        try { player?.release() } catch (e: Exception) { }
        player = null
        currentTitle = ""
    }

    fun isPlaying(): Boolean = try { player?.isPlaying == true } catch (e: Exception) { false }
}
