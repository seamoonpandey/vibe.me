package me.vibe.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import me.vibe.Deps
import me.vibe.R
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Owns the one ExoPlayer instance. Everything that makes playback feel like a real app —
 * surviving the UI being swept away, the media notification, lockscreen and Bluetooth transport
 * controls, Android Auto — comes from being a MediaSessionService rather than a bound service.
 */
/** Set on the intent the media notification fires, so the activity knows to show the player. */
const val EXTRA_OPEN_PLAYER = "me.vibe.OPEN_PLAYER"

@UnstableApi
class PlaybackService : MediaSessionService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var session: MediaSession? = null
    private var fx: Fx? = null
    private var favorites: Set<Long> = emptySet()
    private var layoutState: Triple<Boolean, Int, Boolean>? = null

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            // Pause instead of playing to the room when headphones are yanked out.
            .setHandleAudioBecomingNoisy(true)
            // Hold a plain CPU wakelock only while actually playing, and let Media3 drop it the
            // moment playback stops. Without it a dozing device starves the decode thread and the
            // audio breaks up; a network flag is not wanted here, since every file is local.
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()

        session = MediaSession.Builder(this, player)
            .setCallback(SessionCallback())
            // Without this the notification, the lockscreen card and the Bluetooth "now playing"
            // entry are all dead to the touch — they control playback but go nowhere. Tapping the
            // body of any of them should land on the player, on the track it is showing.
            .setSessionActivity(openPlayerIntent())
            // Artwork for the shade and lockscreen, generated when the file has none embedded.
            .setBitmapLoader(
                CoverBitmapLoader(
                    androidx.media3.session.CacheBitmapLoader(
                        androidx.media3.datasource.DataSourceBitmapLoader(this),
                    ),
                ),
            )
            .setCustomLayout(NotificationControls.layout(false, Player.REPEAT_MODE_OFF, false))
            .build()

        // Our icon and accent, rather than the stock Android bell.
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this).build().apply {
                setSmallIcon(R.drawable.ic_note)
            },
        )

        player.addListener(object : Player.Listener {
            override fun onEvents(p: Player, events: Player.Events) = refreshLayout()
        })

        fx = Fx(player, scope).apply { start() }
        // Settings reach the player by watching the same store the settings screen writes to.
        // It must be the shared instance: DataStore refuses two active stores over one file, and
        // this service shares a process with the UI.
        scope.launch {
            Deps.userData.state.collectLatest {
                fx?.apply(it)
                favorites = it.favorites.toSet()
                refreshLayout()
            }
        }
    }

    private fun openPlayerIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, me.vibe.MainActivity::class.java)
            .setAction(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .putExtra(EXTRA_OPEN_PLAYER, true)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Keep the shade's buttons showing the truth about shuffle, repeat and favorite. */
    private fun refreshLayout() {
        val s = session ?: return
        val currentId = s.player.currentMediaItem?.mediaId?.toLongOrNull()
        val next = Triple(
            s.player.shuffleModeEnabled,
            s.player.repeatMode,
            currentId != null && currentId in favorites,
        )
        // onEvents fires for a great deal that these three buttons do not depend on. Every
        // setCustomLayout is a binder call out to the system UI and a notification redraw, so it is
        // worth knowing whether anything actually changed before making one.
        if (next == layoutState) return
        layoutState = next
        s.setCustomLayout(
            NotificationControls.layout(
                shuffle = next.first,
                repeatMode = next.second,
                favorite = next.third,
            ),
        )
    }

    private inner class SessionCallback : MediaSession.Callback {

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
            NotificationControls.allCommands.forEach { commands.add(it) }
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(commands.build())
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: android.os.Bundle,
        ): ListenableFuture<SessionResult> {
            val player = session.player
            when (customCommand.customAction) {
                NotificationControls.ACTION_SHUFFLE ->
                    player.shuffleModeEnabled = !player.shuffleModeEnabled

                NotificationControls.ACTION_REPEAT ->
                    player.repeatMode = when (player.repeatMode) {
                        Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                        Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                        else -> Player.REPEAT_MODE_OFF
                    }

                NotificationControls.ACTION_FAVORITE -> {
                    val id = player.currentMediaItem?.mediaId?.toLongOrNull()
                    if (id != null) scope.launch { Deps.userData.toggleFavorite(id) }
                }
            }
            refreshLayout()
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Swiping the app away while paused should not leave a dead notification behind.
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        fx?.release()
        scope.cancel()
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }
}
