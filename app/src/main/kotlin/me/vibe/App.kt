package me.vibe

import android.app.Application
import me.vibe.data.MediaStoreLibrary
import me.vibe.data.TagWriter
import me.vibe.data.UserData
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
    lateinit var library: MediaStoreLibrary
        private set
    lateinit var userData: UserData
        private set
    lateinit var player: PlayerController
        private set
    lateinit var tagWriter: TagWriter
        private set

    /** One loader for the process: it owns the memory and disk caches, so per-use instances
     *  would throw the caches away every time. */
    lateinit var images: coil3.ImageLoader
        private set

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun init(app: Application) {
        userData = UserData(app, scope)
        library = MediaStoreLibrary(app, scope)
        tagWriter = TagWriter(app)
        images = coil3.ImageLoader.Builder(app).build()
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
