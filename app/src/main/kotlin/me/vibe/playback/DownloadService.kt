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
import me.vibe.data.remote.DownloadState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Runs downloads one at a time, in the foreground, so they survive the app being left.
 *
 * Separate from [PlaybackService] deliberately. A download has to outlive playback stopping, and
 * Media3 owns that service's lifecycle — promoting and demoting it around audio, not around file
 * transfers.
 */
class DownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** The transfer in flight, so the Downloads page can stop it. */
    private val running = ConcurrentHashMap<String, Job>()

    /** Whether the process still owes the system a foreground notification. */
    private var foreground = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        channel()
        ensureForeground("Starting download", 0f)
        live = this
        scope.launch {
            // Serial: two downloads on one phone share one radio and finish no sooner.
            for (song in queue) {
                val id = song.streamKey ?: continue
                ensureForeground("${song.artist} — ${song.title}", 0f)

                val job = scope.launch {
                    // Both steps are inside the child, and both are contained: an exception thrown
                    // out of a launched coroutine reaches the thread's default handler and takes the
                    // app down, and an exception thrown here in the loop would strand the service
                    // holding a foreground notification it never gives back.
                    runCatching {
                        // Extraction needs the extractor and its PoToken provider up, and a download
                        // can be the first thing asked for after a cold start.
                        Deps.startRemote()
                        Deps.downloads.download(song)
                    }.onFailure { t ->
                        // A cancellation is the user's own doing and already reported by not being
                        // there; anything else has to leave a reason on the row.
                        if (t !is CancellationException) {
                            Deps.downloads.fail(song, t.message ?: "Download failed")
                        }
                    }
                }
                running[id] = job
                val progress = scope.launch {
                    Deps.downloads.jobs.collect { jobs ->
                        val state = jobs[id]?.state
                        if (state is DownloadState.Running) {
                            notify(notification("${song.artist} — ${song.title}", state.fraction))
                        }
                    }
                }
                job.join()
                progress.cancel()
                // A cancel already removed the job; this covers the ordinary completion path.
                running.remove(id)

                // The one piece of feedback that reaches a user who has left the app. Failure is
                // not announced here: it stays on the Downloads page carrying its reason, and a
                // notification that says nothing actionable is just something to dismiss.
                if (Deps.downloads.jobs.value[id]?.state is DownloadState.Done) notifySaved(song)
                if (!busy()) goIdle()
            }
        }
    }

    /** Idle once nothing is queued or in flight. Failures are terminal until retried, so they do
     *  not hold the service up. */
    private fun busy(): Boolean = Deps.downloads.jobs.value.values.any {
        it.state is DownloadState.Queued || it.state is DownloadState.Running
    }

    /**
     * Gives the notification back and stops.
     *
     * Stopped rather than parked, because a live-but-idle service would be restarted by the next
     * enqueue without onCreate — and the foreground contract would go unmet.
     */
    private fun goIdle() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        foreground = false
        stopSelf()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Everything arrives through the process-wide queue; the intent is only a wake-up. The
        // foreground call is repeated here because startForegroundService can be handed a service
        // that is already alive and will get no onCreate, and the system kills it if it is not
        // promoted within five seconds.
        if (!foreground) ensureForeground("Downloading", 0f)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        if (live === this) live = null
        // Backstop for every way out that is not [goIdle] — the system reclaiming the service, a
        // crash in the scope — because a "Downloading" notification with no service behind it is a
        // line that never moves and cannot be cancelled.
        if (foreground) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            foreground = false
        }
        scope.cancel()
        super.onDestroy()
    }

    /** Promote once, then only update — startForeground on an already-foreground service is noise. */
    private fun ensureForeground(text: String, fraction: Float) {
        val n = notification(text, fraction)
        if (foreground) notify(n) else { startForeground(NOTIFICATION_ID, n); foreground = true }
    }

    private fun notify(notification: Notification) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
    }

    /** Outlives the service so it cannot be swept away with the progress notification. */
    private fun notifySaved(song: Song) {
        getSystemService(NotificationManager::class.java).notify(
            SAVED_NOTIFICATION_ID,
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_note)
                .setContentTitle("Saved to your library")
                .setContentText("${song.artist} — ${song.title}")
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .build(),
        )
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
        private const val SAVED_NOTIFICATION_ID = 43

        /**
         * Unbounded, and process-wide rather than per-instance: the service may not be running when
         * a track is queued, and the send must not block the UI while it starts.
         */
        private val queue = Channel<Song>(Channel.UNLIMITED)

        /**
         * The live service, if any.
         *
         * Set in [onCreate] and cleared in [onDestroy] so a cancel arriving while the service is
         * stopped cannot keep a dead instance alive.
         */
        @Volatile private var live: DownloadService? = null

        fun enqueue(context: Context, song: Song) {
            val id = song.streamKey ?: return
            Deps.downloads.markQueued(song)
            queue.trySend(song)
            val intent = Intent(context, DownloadService::class.java)
            if (Build.VERSION.SDK_INT >= 26) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /**
         * Drops a queued or in-flight transfer.
         *
         * There is no resume — the spec rules it out — so this is honest about starting over:
         * the temp file is deleted and the job leaves the list. A transfer already running stops
         * within a chunk, because the fetch loop checks for cancellation between reads.
         */
        fun cancel(videoId: String) {
            Deps.downloads.remove(videoId)
            live?.running?.get(videoId)?.cancel()
        }
    }
}
