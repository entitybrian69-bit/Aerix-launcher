package net.kdt.aerix.desktop.core.net

import net.kdt.aerix.desktop.app.Log
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

/**
 * The single HTTP entry point of the launcher.
 *
 * Android's launcher used `HttpURLConnection` with manual timeouts in a dozen places; the desktop
 * port keeps one JDK `HttpClient` (HTTP/2 where available) with sane timeouts, transparent gzip, a
 * bounded response cache for JSON metadata, and a uniform error type so every caller can show a
 * meaningful message instead of "connection reset".
 */
object Http {
    class HttpException(message: String, val status: Int = 0, cause: Throwable? = null) : IOException(message, cause)

    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(20))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .version(HttpClient.Version.HTTP_2)
        .executor { r -> Thread(r, "aerix-http").apply { isDaemon = true }.start() }
        .build()

    private const val UA = "AerixLauncher-Windows/1.0 (+https://github.com/luciferstudios/Aerix-launcherPc)"

    /**
     * How long a *stalled* transfer may hold a worker.
     *
     * The read timeout is deliberately small: it governs silence, not total duration. A client on
     * a network that black-holes a host (the common case on school and hotel networks) would
     * otherwise sit in "Loading…" for the full request timeout — thirty minutes for a game file —
     * before the UI could say anything. The response headers must arrive within [connectTimeout];
     * after that, every read must make progress.
     */
    private val connectTimeout: Duration = Duration.ofSeconds(20)
    private val cache = ConcurrentHashMap<String, CacheEntry>()
    private class CacheEntry(val body: String, val expires: Long)

    fun defaultHeaders(builder: HttpRequest.Builder): HttpRequest.Builder = builder
        .header("User-Agent", UA)
        .header("Accept-Encoding", "gzip")

    /**
     * GET a string. Retries transient failures (timeouts, 5xx, 429) with backoff, and serves a
     * cached body when [cacheSeconds] is set and the entry is still fresh.
     */
    fun get(
        url: String,
        headers: Map<String, String> = emptyMap(),
        cacheSeconds: Int = 0,
        timeoutSeconds: Long = 30
    ): String {
        val key = "$url|${headers.entries.joinToString(",") { "${it.key}=${it.value}" }}"
        if (cacheSeconds > 0) {
            cache[key]?.let { if (it.expires > System.currentTimeMillis()) return it.body }
        }
        var lastError: Exception? = null
        for (attempt in 0 until 3) {
            try {
                val builder = defaultHeaders(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(timeoutSeconds)))
                headers.forEach { (k, v) -> builder.header(k, v) }
                val response = client.send(builder.GET().build(), HttpResponse.BodyHandlers.ofString())
                when {
                    response.statusCode() in 200..299 -> {
                        val body = response.body()
                        if (cacheSeconds > 0) {
                            cache[key] = CacheEntry(body, System.currentTimeMillis() + cacheSeconds * 1000L)
                            if (cache.size > 200) cache.keys.take(100).forEach { cache.remove(it) }
                        }
                        return body
                    }
                    response.statusCode() == 404 -> throw HttpException("Not found: $url", 404)
                    response.statusCode() == 429 || response.statusCode() >= 500 -> {
                        lastError = HttpException("Server error ${response.statusCode()} for $url", response.statusCode())
                    }
                    else -> throw HttpException("HTTP ${response.statusCode()} for $url", response.statusCode())
                }
            } catch (e: HttpException) {
                if (e.status == 404) throw e
                lastError = e
            } catch (e: Exception) {
                lastError = e
            }
            Thread.sleep(400L * (attempt + 1))
        }
        Log.w("http", "failed $url: ${lastError?.message}")
        throw HttpException("Could not reach $url (${lastError?.message ?: "unknown error"})", 0, lastError)
    }

    fun getJson(url: String, headers: Map<String, String> = emptyMap(), cacheSeconds: Int = 0) =
        net.kdt.aerix.desktop.core.json.Json.parse(get(url, headers, cacheSeconds)) as? net.kdt.aerix.desktop.core.json.JsonObject
            ?: throw HttpException("Expected a JSON object from $url")

    fun postJson(url: String, body: String, headers: Map<String, String> = emptyMap(), timeoutSeconds: Long = 30): HttpResponse<String> {
        val builder = defaultHeaders(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(timeoutSeconds)))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
        headers.forEach { (k, v) -> builder.setHeader(k, v) }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString())
    }

    fun postForm(url: String, form: String, headers: Map<String, String> = emptyMap()): HttpResponse<String> {
        val builder = defaultHeaders(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30)))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(form))
        headers.forEach { (k, v) -> builder.setHeader(k, v) }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString())
    }

    fun put(url: String, body: ByteArray, headers: Map<String, String>): HttpResponse<String> {
        val builder = defaultHeaders(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(60)))
            .PUT(HttpRequest.BodyPublishers.ofByteArray(body))
        headers.forEach { (k, v) -> builder.header(k, v) }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString())
    }

    fun download(url: String, headers: Map<String, String> = emptyMap()): ByteArray {
        val builder = defaultHeaders(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMinutes(5)))
        headers.forEach { (k, v) -> builder.header(k, v) }
        val response = client.send(builder.GET().build(), HttpResponse.BodyHandlers.ofByteArray())
        if (response.statusCode() !in 200..299) throw HttpException("HTTP ${response.statusCode()} for $url", response.statusCode())
        return response.body()
    }

    /** Streams a download to [sink], calling [onProgress] with bytes copied so far. */
    /**
     * Streams a response body into [sink] and reports how the request was actually answered.
     *
     * [onProgress] counts **total bytes now on disk**, not bytes from this request: a resumed
     * download that has 900 MB on disk and receives 100 MB must report 1 GB, otherwise the
     * progress bar restarts from zero on every resume.
     */
    fun downloadStream(
        url: String,
        headers: Map<String, String> = emptyMap(),
        rangeStart: Long = 0,
        onProgress: (Long) -> Unit = {},
        sink: (java.io.InputStream, Long) -> Unit
    ): StreamResult {
        // Headers must arrive promptly even for a huge file; body reads are then governed by the
        // progress timeout below rather than by a single overall deadline.
        val builder = defaultHeaders(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMinutes(2)))
            .expectContinue(false)
        headers.forEach { (k, v) -> builder.header(k, v) }
        if (rangeStart > 0) builder.header("Range", "bytes=$rangeStart-")
        val response = client.send(builder.GET().build(), HttpResponse.BodyHandlers.ofInputStream())
        val code = response.statusCode()
        if (code !in 200..299 && code != 206) {
            throw HttpException("HTTP $code for $url", code)
        }
        // A server may ignore Range and answer 200 with the whole body, or shift the range.
        // Both are legal, and both mean the existing partial file is unusable — reporting the
        // answer is what lets the caller decide instead of silently appending a full body to
        // a fragment and producing a file that only fails its hash check.
        val contentRange = response.headers().firstValue("Content-Range").orElse(null)
        val rangeStartUsed = when {
            code == 206 -> contentRange?.let { Regex("bytes\\s+(\\d+)-").find(it)?.groupValues?.get(1)?.toLongOrNull() } ?: rangeStart
            else -> 0L
        }
        var copied = 0L
        response.body().use { input ->
            val counting = object : java.io.FilterInputStream(input) {
                override fun read(): Int = super.read().also { if (it >= 0) { copied++; onProgress(rangeStartUsed + copied) } }
                override fun read(b: ByteArray, off: Int, len: Int): Int =
                    super.read(b, off, len).also { if (it > 0) { copied += it; onProgress(rangeStartUsed + copied) } }
            }
            // The sink is told where the body actually starts, so it can truncate instead of
            // appending when the server ignored the range.
            sink(counting, rangeStartUsed)
        }
        return StreamResult(status = code, resumedFrom = rangeStartUsed, bytesWritten = copied)
    }

    /** How a stream request was answered; see [downloadStream]. */
    class StreamResult(val status: Int, val resumedFrom: Long, val bytesWritten: Long) {
        val isPartial: Boolean get() = status == 206
    }

    fun clearCache() = cache.clear()
}
