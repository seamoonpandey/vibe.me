package me.vibe.data.remote

import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * NewPipeExtractor's HTTP SPI, over `HttpURLConnection`.
 *
 * ponytail: no OkHttp. Adding a second HTTP stack to the APK to serve one caller — which itself
 * only ever does small GETs and POSTs — would cost more than the forty lines it saves.
 */
object HttpDownloader : Downloader() {

    /** YouTube serves different, often worse, responses to something that admits to being a bot. */
    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:129.0) Gecko/20100101 Firefox/129.0"

    private const val TIMEOUT_MS = 30_000

    override fun execute(request: Request): Response {
        val url = URL(request.url())
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = request.httpMethod()
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", USER_AGENT)
            request.headers().forEach { (name, values) ->
                // The extractor uses an empty value list to mean "remove this header", which is
                // how it strips the User-Agent above for the clients that must not send one.
                if (values.isEmpty()) {
                    setRequestProperty(name, null)
                } else {
                    values.forEachIndexed { i, value ->
                        if (i == 0) setRequestProperty(name, value) else addRequestProperty(name, value)
                    }
                }
            }
        }

        try {
            request.dataToSend()?.let { body ->
                connection.doOutput = true
                connection.setFixedLengthStreamingMode(body.size)
                connection.outputStream.use { it.write(body) }
            }

            val code = connection.responseCode

            // 429 is YouTube asking for a captcha it will never show us. Surfacing it as its own
            // type is what lets the Explore tab say "blocked, try later" instead of "failed".
            if (code == 429) throw ReCaptchaException("reCaptcha challenge requested", request.url())

            // Errors carry a body too, and the extractor reads it to work out what went wrong.
            val body = (if (code >= 400) connection.errorStream else connection.inputStream)
                ?.use { it.readBytes().toString(Charsets.UTF_8) }

            return Response(
                code,
                connection.responseMessage,
                connection.headerFields,
                body,
                connection.url.toString(),
            )
        } catch (e: ReCaptchaException) {
            throw e
        } catch (e: Exception) {
            throw IOException("Request to ${request.url()} failed", e)
        } finally {
            connection.disconnect()
        }
    }
}
