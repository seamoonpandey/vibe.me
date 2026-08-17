package me.vibe

import android.app.Application
import coil3.request.crossfade
import me.vibe.data.MediaStoreLibrary
import me.vibe.data.TagWriter
import me.vibe.data.UserData
import me.vibe.data.remote.Downloads
import me.vibe.data.remote.ThumbnailFetcher
import me.vibe.data.remote.YouTube
import me.vibe.data.remote.potoken.PoTokens
import me.vibe.playback.PlayerController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The entire dependency-injection story. Four singletons with a plain lifetime and no cycles do
 * not need a graph, a framework, or code generation.
 */
object Deps {
    lateinit var app: Application
        private set
    lateinit var library: MediaStoreLibrary
        private set
    lateinit var userData: UserData
        private set
    lateinit var player: PlayerController
        private set
    lateinit var tagWriter: TagWriter
        private set

    lateinit var downloads: Downloads
        private set

    /** One loader for the process: it owns the memory and disk caches, so per-use instances
     *  would throw the caches away every time. */
    lateinit var images: coil3.ImageLoader
        private set

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /**
     * Built on first use, not at startup.
     *
     * Instantiating it loads a WebView and Google's BotGuard JavaScript, which nothing but Explore
     * needs. A user who never opens that tab never pays for it, and the app keeps behaving exactly
     * as it did before this feature existed.
     */
    val poTokens: PoTokens by lazy { PoTokens(app, scope) }

    /** Also on first use, for the same reason: it is what pulls the extractor into memory. */
    fun startRemote() = YouTube.init(poTokens)

    fun init(app: Application) {
        this.app = app
        userData = UserData(app, scope)
        downloads = Downloads(app)
        library = MediaStoreLibrary(app, scope)
        tagWriter = TagWriter(app)
        // Artwork fades in rather than popping. Coil skips the fade for memory-cache hits, so
        // scrolling back over rows you have already seen stays instant.
        images = coil3.ImageLoader.Builder(app)
            .crossfade(180)
            // Coil 3 has no http fetcher built in; without this, every search thumbnail silently
            // falls through to the generated initials tile.
            .components { add(ThumbnailFetcher.Factory()) }
            .build()
        player = PlayerController(app, scope) { song ->
            scope.launch { userData.bumpPlayCount(song.id) }
        }
    }
}

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Deps.init(this)
    }
}
