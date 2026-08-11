package com.example.music.data

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.dataStoreFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream

/**
 * Everything the app owns, as one small serializable blob. The library itself lives in MediaStore,
 * so this stays in the low kilobytes even for a large collection — cheap to hold in memory and
 * cheap to rewrite whole. No database, no migrations, no codegen.
 */
@kotlinx.serialization.Serializable
data class UserState(
    val playlists: List<Playlist> = emptyList(),
    val favorites: List<Long> = emptyList(),
    val playCounts: Map<Long, Int> = emptyMap(),
    val sortBy: Map<String, SortSpec> = emptyMap(),
    val queue: List<Long> = emptyList(),
    val queueIndex: Int = 0,
    val queuePositionMs: Long = 0,
    val shuffle: Boolean = false,
    val repeatMode: Int = 0,
    val crossfadeSeconds: Int = 0,
    val sleepTimerEndsAt: Long = 0,
    val eqEnabled: Boolean = false,
    val eqPreset: Int = 0,
    val bassBoost: Int = 0,
)

private object UserStateSerializer : Serializer<UserState> {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override val defaultValue = UserState()

    override suspend fun readFrom(input: InputStream): UserState = try {
        json.decodeFromString(UserState.serializer(), input.readBytes().decodeToString())
    } catch (e: SerializationException) {
        throw CorruptionException("user state unreadable", e)
    }

    override suspend fun writeTo(t: UserState, output: OutputStream) {
        output.write(json.encodeToString(UserState.serializer(), t).encodeToByteArray())
    }
}

class UserData(context: Context, scope: CoroutineScope) {

    private val store: DataStore<UserState> = DataStoreFactory.create(
        serializer = UserStateSerializer,
        scope = scope,
        // Corrupt file should cost the user their playlists, not the ability to open the app.
        corruptionHandler = androidx.datastore.core.handlers.ReplaceFileCorruptionHandler {
            UserState()
        },
        produceFile = { context.dataStoreFile("user.json") },
    )

    val state: Flow<UserState> = store.data

    suspend fun edit(block: (UserState) -> UserState) {
        store.updateData(block)
    }

    // --- playlists ---

    suspend fun createPlaylist(name: String) = edit {
        it.copy(playlists = it.playlists + Playlist(id = System.currentTimeMillis(), name = name))
    }

    suspend fun renamePlaylist(id: Long, name: String) = edit { s ->
        s.copy(playlists = s.playlists.map { if (it.id == id) it.copy(name = name) else it })
    }

    suspend fun deletePlaylist(id: Long) = edit { s ->
        s.copy(playlists = s.playlists.filterNot { it.id == id })
    }

    suspend fun addToPlaylist(id: Long, songIds: List<Long>) = edit { s ->
        s.copy(playlists = s.playlists.map {
            // Adding a song already present is a no-op rather than a duplicate row.
            if (it.id == id) it.copy(songIds = it.songIds + songIds.filterNot(it.songIds::contains))
            else it
        })
    }

    suspend fun removeFromPlaylist(id: Long, index: Int) = edit { s ->
        s.copy(playlists = s.playlists.map {
            if (it.id == id) it.copy(songIds = it.songIds.filterIndexed { i, _ -> i != index }) else it
        })
    }

    suspend fun movePlaylistItem(id: Long, from: Int, to: Int) = edit { s ->
        s.copy(playlists = s.playlists.map {
            if (it.id == id) it.copy(songIds = it.songIds.moved(from, to)) else it
        })
    }

    // --- misc ---

    suspend fun toggleFavorite(songId: Long) = edit {
        it.copy(
            favorites = if (songId in it.favorites) it.favorites - songId else it.favorites + songId,
        )
    }

    suspend fun bumpPlayCount(songId: Long) = edit {
        it.copy(playCounts = it.playCounts + (songId to (it.playCounts[songId] ?: 0) + 1))
    }

    suspend fun setSort(tab: String, spec: SortSpec) = edit {
        it.copy(sortBy = it.sortBy + (tab to spec))
    }

    /** Shuffle and repeat ride along with the queue: they are all "where playback was". */
    suspend fun saveQueue(
        ids: List<Long>,
        index: Int,
        positionMs: Long,
        shuffle: Boolean,
        repeatMode: Int,
    ) = edit {
        it.copy(
            queue = ids,
            queueIndex = index,
            queuePositionMs = positionMs,
            shuffle = shuffle,
            repeatMode = repeatMode,
        )
    }
}

/** Reorder helper shared by playlists and the play queue. Out-of-range moves are ignored. */
fun <T> List<T>.moved(from: Int, to: Int): List<T> {
    if (from !in indices || to !in indices || from == to) return this
    val out = toMutableList()
    out.add(to, out.removeAt(from))
    return out
}
