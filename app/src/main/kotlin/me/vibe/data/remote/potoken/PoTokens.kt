/*
 * Ported from NewPipe (org.schabi.newpipe.util.potoken.PoTokenProviderImpl), GPL-3.0-or-later.
 * Copyright (C) Team NewPipe. See LICENSE.
 */
package me.vibe.data.remote.potoken

import android.content.Context
import android.webkit.CookieManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.services.youtube.InnertubeClientRequestInfo
import org.schabi.newpipe.extractor.services.youtube.PoTokenProvider
import org.schabi.newpipe.extractor.services.youtube.PoTokenResult
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper

/**
 * Supplies the extractor with proof-of-origin tokens, or gives up honestly.
 *
 * Only the web client is implemented, matching NewPipe: the android and iOS client tokens want a
 * different attestation path and returning null for them makes the extractor fall back rather than
 * fail.
 */
class PoTokens(
    private val context: Context,
    private val scope: CoroutineScope,
) : PoTokenProvider {

    private val lock = Any()
    private var generator: PoTokenWebView? = null
    private var visitorData: String? = null
    private var streamingPot: String? = null

    /** Set once a device proves it cannot run BotGuard at all. Never retried after that. */
    @Volatile private var unsupported = false

    private val hasWebView: Boolean by lazy {
        // Cheapest reliable probe: a device with no WebView throws from this rather than reporting
        // the package as missing.
        runCatching { CookieManager.getInstance() }.isSuccess
    }

    override fun getWebClientPoToken(videoId: String): PoTokenResult? {
        if (!hasWebView || unsupported) return null
        return try {
            webToken(videoId, forceRecreate = false)
        } catch (e: BadWebViewException) {
            unsupported = true
            null
        } catch (e: PoTokenException) {
            // A miss here costs stream quality, not correctness — the extractor tries without.
            null
        }
    }

    override fun getWebEmbedClientPoToken(videoId: String): PoTokenResult? = null

    override fun getAndroidClientPoToken(videoId: String): PoTokenResult? = null

    override fun getIosClientPoToken(videoId: String): PoTokenResult? = null

    /**
     * Blocking on purpose. The extractor's SPI is synchronous and is only ever called from
     * `Dispatchers.IO` inside [me.vibe.data.remote.YouTube] — never from the main thread, which is
     * where the WebView work this waits on has to happen.
     */
    private fun webToken(videoId: String, forceRecreate: Boolean): PoTokenResult = runBlocking {
        val (gen, visitor, streaming, recreated) = synchronized(lock) {
            val recreate = generator == null || forceRecreate || generator!!.isExpired
            if (recreate) {
                visitorData = YoutubeParsingHelper.getVisitorDataFromInnertube(
                    InnertubeClientRequestInfo.ofWebClient().apply {
                        clientInfo.clientVersion = YoutubeParsingHelper.getClientVersion()
                    },
                    NewPipe.getPreferredLocalization(),
                    NewPipe.getPreferredContentCountry(),
                    YoutubeParsingHelper.getYouTubeHeaders(),
                    YoutubeParsingHelper.YOUTUBEI_V1_URL,
                    null,
                    false,
                )
                generator?.let { old -> scope.launch(Dispatchers.Main) { old.close() } }
                generator = runBlocking { PoTokenWebView.create(context, scope) }
                // The streaming token must be minted exactly once, before any per-video token.
                streamingPot = runBlocking { generator!!.generate(visitorData!!) }
            }
            Quad(generator!!, visitorData!!, streamingPot!!, recreate)
        }

        val playerPot = try {
            gen.generate(videoId)
        } catch (t: Throwable) {
            // A backgrounded app can lose the WebView's content out from under it. One rebuild is
            // worth trying; a second failure straight after a rebuild is a real failure.
            if (recreated) throw t else return@runBlocking webToken(videoId, forceRecreate = true)
        }

        PoTokenResult(visitor, playerPot, streaming)
    }

    /** Released when Explore is left, so a user who never searches never keeps a WebView alive. */
    fun release() {
        val old = synchronized(lock) {
            generator.also { generator = null; visitorData = null; streamingPot = null }
        }
        old?.let { scope.launch(Dispatchers.Main) { it.close() } }
    }

    private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
}
