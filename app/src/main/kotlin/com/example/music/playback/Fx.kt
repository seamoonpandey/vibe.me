package com.example.music.playback

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import androidx.media3.exoplayer.ExoPlayer
import com.example.music.data.UserState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Audio effects, fade transitions and the sleep timer — all of it player-side.
 *
 * These live in the service rather than the UI because they need the real ExoPlayer and its audio
 * session id, neither of which crosses a MediaController. Settings arrive by observing the same
 * DataStore the settings screen writes to, which avoids inventing a custom-command protocol just
 * to move three integers.
 */
class Fx(private val player: ExoPlayer, private val scope: CoroutineScope) {

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var settings = UserState()

    val presetNames: List<String>
        get() = equalizer?.let { eq ->
            (0 until eq.numberOfPresets).map { eq.getPresetName(it.toShort()) }
        }.orEmpty()

    fun start() {
        scope.launch { fadeLoop() }
        scope.launch { sleepLoop() }
    }

    fun apply(state: UserState) {
        settings = state
        applyEffects(state)
        if (player.playbackParameters.speed != state.playbackSpeed) {
            player.setPlaybackSpeed(state.playbackSpeed.coerceIn(0.5f, 2.5f))
        }
        player.skipSilenceEnabled = state.skipSilence
    }

    private fun applyEffects(state: UserState) {
        val sessionId = player.audioSessionId
        if (sessionId == 0) return

        if (!state.eqEnabled) {
            release()
            return
        }
        try {
            val eq = equalizer ?: Equalizer(0, sessionId).also { equalizer = it }
            eq.enabled = true
            if (state.eqPreset in 0 until eq.numberOfPresets) {
                eq.usePreset(state.eqPreset.toShort())
            }
            val bass = bassBoost ?: BassBoost(0, sessionId).also { bassBoost = it }
            bass.enabled = state.bassBoost > 0
            if (bass.strengthSupported) {
                bass.setStrength(state.bassBoost.coerceIn(0, 1000).toShort())
            }
        } catch (e: RuntimeException) {
            // Effect hardware is optional and some devices simply refuse. Silence beats crashing.
            release()
        }
    }

    /**
     * Volume fade across track boundaries.
     *
     * ponytail: this is a fade, not a true overlapping crossfade — one player cannot render two
     * tracks at once. A real crossfade needs a second ExoPlayer whose output overlaps the first,
     * and the MediaSession has to be taught that the item it reports is not the item it is
     * rendering. Upgrade path is a second player in PlaybackService with a switchover on the
     * ramp's midpoint; do it if the fade turns out to be audibly insufficient.
     */
    private suspend fun fadeLoop() {
        while (scope.isActive) {
            delay(200)
            val seconds = settings.crossfadeSeconds
            if (seconds <= 0) {
                if (player.volume != 1f) player.volume = 1f
                continue
            }
            if (!player.isPlaying) continue

            val duration = player.duration
            val position = player.currentPosition
            if (duration <= 0) continue

            val window = seconds * 1000L
            val remaining = duration - position
            player.volume = when {
                remaining in 0..window && player.hasNextMediaItem() -> remaining.toFloat() / window
                position < window -> (position.toFloat() / window).coerceAtMost(1f)
                else -> 1f
            }.coerceIn(0f, 1f)
        }
    }

    private suspend fun sleepLoop() {
        while (scope.isActive) {
            delay(1000)
            if (!player.isPlaying) continue
            val endsAt = settings.sleepTimerEndsAt
            val due = endsAt > 0 && System.currentTimeMillis() >= endsAt
            // "Stop after this track" waits for the deadline to pass AND the track to run out, so
            // a timer set mid-song never cuts it off part way.
            if (due) {
                if (!settings.sleepAtTrackEnd) {
                    player.pause()
                } else if (player.duration > 0 && player.duration - player.currentPosition < 1200) {
                    player.pause()
                }
            }
        }
    }

    fun release() {
        equalizer?.release()
        bassBoost?.release()
        equalizer = null
        bassBoost = null
    }
}
