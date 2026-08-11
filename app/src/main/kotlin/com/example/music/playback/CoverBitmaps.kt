package com.example.music.playback

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import com.example.music.data.initialsOf
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.SettableFuture

private const val SIZE = 512

/**
 * The same generated cover the app draws, as a real Bitmap.
 *
 * Kept deliberately in step with `coverColors` and the tiles in the list: the notification showing
 * a different picture from the one on screen would be worse than showing none.
 */
fun generatedCover(seed: String): Bitmap {
    var h = 0
    for (ch in seed.lowercase()) h = h * 31 + ch.code
    val hue = ((h % 360) + 360) % 360

    val top = Color.HSVToColor(floatArrayOf(hue.toFloat(), 0.44f, 0.88f))
    val bottom = Color.HSVToColor(floatArrayOf(((hue + 28) % 360).toFloat(), 0.52f, 0.72f))

    val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
            0f, 0f, SIZE.toFloat(), SIZE.toFloat(), top, bottom, Shader.TileMode.CLAMP,
        )
    }
    canvas.drawRect(0f, 0f, SIZE.toFloat(), SIZE.toFloat(), fill)

    val text = initialsOf(seed)
    val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(190, 58, 42, 48)
        textSize = SIZE * 0.3f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }
    val baseline = SIZE / 2f - (ink.descent() + ink.ascent()) / 2f
    canvas.drawText(text, SIZE / 2f, baseline, ink)
    return bitmap
}

/**
 * Supplies notification artwork, falling back to a generated cover.
 *
 * Most files in a downloaded library carry no embedded art, so the media notification and the
 * lockscreen would otherwise show an empty square — which is exactly where artwork matters most.
 * This wraps Media3's own loader and only steps in when it comes back with nothing.
 */
@UnstableApi
class CoverBitmapLoader(private val delegate: BitmapLoader) : BitmapLoader {

    override fun supportsMimeType(mimeType: String) = delegate.supportsMimeType(mimeType)

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> = delegate.decodeBitmap(data)

    override fun loadBitmap(uri: android.net.Uri): ListenableFuture<Bitmap> = delegate.loadBitmap(uri)

    override fun loadBitmapFromMetadata(metadata: MediaMetadata): ListenableFuture<Bitmap>? {
        val seed = metadata.title?.toString().orEmpty()
        val attempt = delegate.loadBitmapFromMetadata(metadata)
            ?: return Futures.immediateFuture(generatedCover(seed))

        // A failed load is the normal case here, not an error worth surfacing.
        val out = SettableFuture.create<Bitmap>()
        Futures.addCallback(
            attempt,
            object : FutureCallback<Bitmap> {
                override fun onSuccess(result: Bitmap?) {
                    out.set(result ?: generatedCover(seed))
                }

                override fun onFailure(t: Throwable) {
                    out.set(generatedCover(seed))
                }
            },
            MoreExecutors.directExecutor(),
        )
        return out
    }
}
