package com.example.music

import android.app.Application
import com.example.music.data.MediaStoreLibrary
import com.example.music.data.TagWriter
import com.example.music.data.UserData
import com.example.music.playback.PlayerController
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

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun init(app: Application) {
        userData = UserData(app, scope)
        library = MediaStoreLibrary(app, scope)
        tagWriter = TagWriter(app)
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
