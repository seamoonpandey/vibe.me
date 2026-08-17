/*
 * Ported from NewPipe (org.schabi.newpipe.util.potoken.PoTokenWebView), GPL-3.0-or-later.
 * Copyright (C) Team NewPipe. See LICENSE.
 *
 * Changed from the original: RxJava `Single` replaced with coroutines throughout, since this app
 * has no Rx dependency and adding one for a single class would cost more than the port.
 */
package me.vibe.data.remote.potoken

import android.annotation.SuppressLint
import android.content.Context
import android.os.SystemClock
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import me.vibe.data.remote.HttpDownloader
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * A headless WebView that runs YouTube's BotGuard JavaScript to mint proof-of-origin tokens.
 *
 * This exists because YouTube gates most audio stream URLs behind such a token, and generating one
 * genuinely requires a browser. It is the least defensible part of the download feature against
 * this app's original premise, so it is kept on a short leash: created lazily on first use, never
 * given network access of its own, and destroyed when Explore is left.
 */
class PoTokenWebView private constructor(context: Context, private val scope: CoroutineScope) {

    @SuppressLint("SetJavaScriptEnabled")
    private val webView = WebView(context).apply {
        settings.javaScriptEnabled = true
        settings.userAgentString = USER_AGENT
        // The page is loaded from a local asset and every network call it needs is made by us, in
        // Kotlin, through the normal downloader. The WebView itself never reaches the network.
        settings.blockNetworkLoads = true
    }

    private var expiresAtElapsed = 0L
    private val pending = mutableMapOf<String, CancellableContinuation<String>>()
    private var initial: CancellableContinuation<PoTokenWebView>? = null

    init {
        webView.addJavascriptInterface(this, JS_INTERFACE)
        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(m: ConsoleMessage): Boolean {
                // Everything that can fail in the page is inside a try/catch, so an uncaught error
                // means the WebView could not parse the code at all — an implementation too old for
                // the JavaScript BotGuard needs. That is permanent, so it gets its own exception.
                if (m.message().contains("Uncaught")) {
                    failEverything(
                        BadWebViewException("\"${m.message()}\", ${m.sourceId()}:${m.lineNumber()}"),
                    )
                }
                return super.onConsoleMessage(m)
            }
        }
    }

    val isExpired: Boolean get() = SystemClock.elapsedRealtime() > expiresAtElapsed

    // --- initialization ---

    private suspend fun load(context: Context): PoTokenWebView {
        val html = withContext(Dispatchers.IO) {
            context.assets.open("po_token.html").bufferedReader().use { it.readText() }
        }
        return suspendCancellableCoroutine { cont ->
            initial = cont
            webView.loadDataWithBaseURL(
                "https://www.youtube.com",
                // Kick the chain off as soon as the page is parsed.
                html.replaceFirst("</script>", "\n$JS_INTERFACE.downloadAndRunBotguard()</script>"),
                "text/html",
                "utf-8",
                null,
            )
        }
    }

    /** Called from the page once it has loaded. */
    @JavascriptInterface
    fun downloadAndRunBotguard() {
        botguardRequest("$JNN/Create", "[ \"$REQUEST_KEY\" ]") { body ->
            val challenge = parseChallengeData(body)
            evaluate(
                """try {
                    data = $challenge
                    runBotGuard(data).then(function (result) {
                        this.webPoSignalOutput = result.webPoSignalOutput
                        $JS_INTERFACE.onRunBotguardResult(result.botguardResponse)
                    }, function (error) {
                        $JS_INTERFACE.onJsInitializationError(error + "\n" + error.stack)
                    })
                } catch (error) {
                    $JS_INTERFACE.onJsInitializationError(error + "\n" + error.stack)
                }""",
            )
        }
    }

    @JavascriptInterface
    fun onJsInitializationError(error: String) = failEverything(PoTokenException(error))

    /** Called from the page with BotGuard's output; trades it for an integrity token. */
    @JavascriptInterface
    fun onRunBotguardResult(botguardResponse: String) {
        botguardRequest("$JNN/GenerateIT", "[ \"$REQUEST_KEY\", \"$botguardResponse\" ]") { body ->
            val (integrityToken, lifetimeSeconds) = parseIntegrityTokenData(body)
            // Ten minutes of margin, so a token never expires mid-request.
            expiresAtElapsed = SystemClock.elapsedRealtime() + (lifetimeSeconds - 600) * 1000
            evaluate("this.integrityToken = $integrityToken") {
                initial?.let { it.resume(this); initial = null }
            }
        }
    }

    // --- generating tokens ---

    suspend fun generate(identifier: String): String = suspendCancellableCoroutine { cont ->
        synchronized(pending) { pending[identifier] = cont }
        scope.launch(Dispatchers.Main) {
            evaluate(
                """try {
                    identifier = "$identifier"
                    u8Identifier = ${stringToU8(identifier)}
                    poTokenU8 = obtainPoToken(webPoSignalOutput, integrityToken, u8Identifier)
                    poTokenU8String = ""
                    for (i = 0; i < poTokenU8.length; i++) {
                        if (i != 0) poTokenU8String += ","
                        poTokenU8String += poTokenU8[i]
                    }
                    $JS_INTERFACE.onObtainPoTokenResult(identifier, poTokenU8String)
                } catch (error) {
                    $JS_INTERFACE.onObtainPoTokenError(identifier, error + "\n" + error.stack)
                }""",
            )
        }
    }

    @JavascriptInterface
    fun onObtainPoTokenError(identifier: String, error: String) {
        pop(identifier)?.resumeWithException(PoTokenException(error))
    }

    @JavascriptInterface
    fun onObtainPoTokenResult(identifier: String, poTokenU8: String) {
        val cont = pop(identifier) ?: return
        runCatching { u8ToBase64(poTokenU8) }
            .onSuccess(cont::resume)
            .onFailure(cont::resumeWithException)
    }

    // --- plumbing ---

    /**
     * A POST the page cannot make itself, because its network access is switched off. Failures here
     * are always fatal to this instance: they all happen during initialization.
     */
    private fun botguardRequest(url: String, data: String, onBody: (String) -> Unit) {
        scope.launch {
            val body = try {
                withContext(Dispatchers.IO) {
                    val response = HttpDownloader.post(
                        url,
                        mapOf(
                            "User-Agent" to listOf(USER_AGENT),
                            "Accept" to listOf("application/json"),
                            "Content-Type" to listOf("application/json+protobuf"),
                            "x-goog-api-key" to listOf(GOOGLE_API_KEY),
                            "x-user-agent" to listOf("grpc-web-javascript/0.1"),
                        ),
                        data.toByteArray(),
                    )
                    if (response.responseCode() != 200) {
                        throw PoTokenException("BotGuard request failed: ${response.responseCode()}")
                    }
                    response.responseBody()
                }
            } catch (t: Throwable) {
                failEverything(t)
                return@launch
            }
            withContext(Dispatchers.Main) { runCatching { onBody(body) }.onFailure(::failEverything) }
        }
    }

    private fun evaluate(js: String, onDone: (() -> Unit)? = null) {
        webView.evaluateJavascript(js) { onDone?.invoke() }
    }

    private fun pop(identifier: String): CancellableContinuation<String>? =
        synchronized(pending) { pending.remove(identifier) }

    /** Any failure kills the instance: a half-initialized BotGuard cannot mint anything. */
    private fun failEverything(error: Throwable) {
        val waiting = synchronized(pending) { pending.values.toList().also { pending.clear() } }
        waiting.forEach { runCatching { it.resumeWithException(error) } }
        val first = initial
        initial = null
        scope.launch(Dispatchers.Main) {
            close()
            first?.let { runCatching { it.resumeWithException(error) } }
        }
    }

    fun close() {
        webView.clearHistory()
        webView.clearCache(true)
        webView.loadUrl("about:blank")
        webView.onPause()
        webView.removeAllViews()
        webView.destroy()
    }

    companion object {
        private const val JNN = "https://www.youtube.com/api/jnn/v1"

        // Public keys, read off BotGuard's own requests. Not secrets.
        private const val GOOGLE_API_KEY = "AIzaSyDyT5W0Jh49F30Pqqtyfdf7pDLFKLJoAnw"
        private const val REQUEST_KEY = "O43z0dpjhgX20SCx4KAo"
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.3"
        private const val JS_INTERFACE = "PoTokenWebView"

        /** Builds one and waits until BotGuard has run and an integrity token is in hand. */
        suspend fun create(context: Context, scope: CoroutineScope): PoTokenWebView =
            withContext(Dispatchers.Main) {
                PoTokenWebView(context, scope).load(context)
            }
    }
}
