package me.vibe.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import me.vibe.Deps
import me.vibe.R
import me.vibe.data.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * Runs downloads one at a time, in the foreground, so they survive the app being left.
 *
 * Separate from [PlaybackService] deliberately. A download has to outlive playback stopping, and
 * Media3 owns that service's lifecycle — promoting and demoting it around audio, not around file
 * transfers.
 */
class DownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        channel()
        startForeground(NOTIFICATION_ID, notification("Starting download", 0f))
        scope.launch {
            // Serial: two downloads on one phone share one radio and finish no sooner.
            for (song in queue) {
                val id = song.streamKey ?: continue
                notify(notification("${song.artist} — ${song.title}", 0f))
                val progress = scope.launch {
                    Deps.downloads.states.collect { states ->
                        val s = states[id]
                        if (s is me.vibe.data.remote.DownloadState.Running) {
                            notify(notification("${song.artist} — ${song.title}", s.fraction))
                        }
                    }
                }
                runCatching { Deps.downloads.download(song) }
                progress.cancel()
            }
            // The channel never closes, so this is only reached if the scope is cancelled.
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Everything arrives through the process-wide queue; the intent is only a wake-up.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun notify(notification: Notification) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
    }

    private fun notification(text: String, fraction: Float): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_note)
            .setContentTitle("Downloading")
            .setContentText(text)
            .setOngoing(true)
            .setProgress(100, (fraction * 100).toInt(), fraction <= 0f)
            .setSilent(true)
            .build()

    private fun channel() {
        if (Build.VERSION.SDK_INT < 26) return
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Downloads", NotificationManager.IMPORTANCE_LOW),
        )
    }

    companion object {
        private const val CHANNEL_ID = "downloads"
        private const val NOTIFICATION_ID = 42

        /**
         * Unbounded, and process-wide rather than per-instance: the service may not be running when
         * a track is queued, and the send must not block the UI while it starts.
         */
        private val queue = Channel<Song>(Channel.UNLIMITED)

        fun enqueue(context: Context, song: Song) {
            val id = song.streamKey ?: return
            Deps.downloads.markQueued(id)
            queue.trySend(song)
            val intent = Intent(context, DownloadService::class.java)
            if (Build.VERSION.SDK_INT >= 26) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
