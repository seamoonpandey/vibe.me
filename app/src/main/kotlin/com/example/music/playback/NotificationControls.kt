package com.example.music.playback

import android.os.Bundle
import androidx.media3.common.Player
import androidx.media3.session.CommandButton
import androidx.media3.session.SessionCommand
import com.example.music.R

/**
 * The extra buttons that appear in the media notification, on the lockscreen and in Android Auto.
 *
 * A note on how far "custom" goes: since Android 13 the system renders media notifications from
 * the MediaSession itself, and a custom RemoteViews layout is ignored. What an app genuinely
 * controls is the small icon, the accent colour, and these action buttons — including which of
 * them are promoted into the collapsed view. So that is what we control, and the icons swap to
 * reflect live state rather than sitting static.
 */
object NotificationControls {

    const val ACTION_SHUFFLE = "com.example.music.SHUFFLE"
    const val ACTION_REPEAT = "com.example.music.REPEAT"
    const val ACTION_FAVORITE = "com.example.music.FAVORITE"

    val allCommands = listOf(ACTION_SHUFFLE, ACTION_REPEAT, ACTION_FAVORITE)
        .map { SessionCommand(it, Bundle.EMPTY) }

    private fun button(action: String, iconRes: Int, name: String, on: Boolean) =
        CommandButton.Builder()
            .setDisplayName(name)
            .setIconResId(iconRes)
            .setSessionCommand(SessionCommand(action, Bundle.EMPTY))
            // Media3 uses this to decide what the collapsed notification shows.
            .setEnabled(true)
            .setExtras(Bundle().apply { putBoolean("active", on) })
            .build()

    /** Rebuilt whenever state changes, so the icons in the shade always match the player. */
    fun layout(shuffle: Boolean, repeatMode: Int, favorite: Boolean): List<CommandButton> = listOf(
        button(
            ACTION_SHUFFLE, R.drawable.ic_shuffle,
            if (shuffle) "Shuffle on" else "Shuffle off", shuffle,
        ),
        button(
            ACTION_REPEAT,
            if (repeatMode == Player.REPEAT_MODE_ONE) R.drawable.ic_repeat_one else R.drawable.ic_repeat,
            when (repeatMode) {
                Player.REPEAT_MODE_ONE -> "Repeat one"
                Player.REPEAT_MODE_ALL -> "Repeat all"
                else -> "Repeat off"
            },
            repeatMode != Player.REPEAT_MODE_OFF,
        ),
        button(
            ACTION_FAVORITE,
            if (favorite) R.drawable.ic_heart else R.drawable.ic_heart_outline,
            if (favorite) "Remove from favorites" else "Add to favorites",
            favorite,
        ),
    )
}
