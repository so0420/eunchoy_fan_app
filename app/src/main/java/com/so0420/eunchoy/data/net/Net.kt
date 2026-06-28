package com.so0420.eunchoy.data.net

import com.so0420.eunchoy.data.Config
import com.so0420.eunchoy.data.naver.NaverSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.Retrofit
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Shared HTTP stack: one OkHttp client (browser UA + Naver cookies), Retrofit services, raw text GET. */
object Net {
    val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                val orig = chain.request()
                val b = orig.newBuilder()
                    .header("User-Agent", Config.UA)
                    .header("Accept", "application/json, text/plain, */*")
                val host = orig.url.host
                when {
                    host == "api.chzzk.naver.com" ->
                        b.header("Referer", "https://chzzk.naver.com/")
                    host == "apis.naver.com" ->
                        b.header("Referer", "https://cafe.naver.com/")
                }
                // Only attach the user's Naver session cookies to genuine naver.com subdomains
                // (an exact-suffix match like endsWith("naver.com") would also match evilnaver.com).
                if (host == "naver.com" || host.endsWith(".naver.com")) {
                    NaverSession.cookieHeader()?.let { b.header("Cookie", it) }
                }
                chain.proceed(b.build())
            }
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    private val converter by lazy {
        json.asConverterFactory("application/json".toMediaType())
    }

    private fun retrofit(baseUrl: String): Retrofit =
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(converter)
            .build()

    val chzzk: ChzzkApi by lazy { retrofit("https://api.chzzk.naver.com/").create(ChzzkApi::class.java) }
    val naver: NaverApi by lazy { retrofit("https://apis.naver.com/").create(NaverApi::class.java) }

    /** Raw GET returning the response body as text (for XML feeds). */
    suspend fun getText(url: String): String = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url).header("User-Agent", Config.UA).build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code} for $url")
            resp.body?.string().orEmpty()
        }
    }

    /** Raw GET returning bytes (notification images). Returns null on any failure. */
    suspend fun getBytes(url: String): ByteArray? = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder().url(url).header("User-Agent", Config.UA).build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) null else resp.body?.bytes()
            }
        }.getOrNull()
    }

    /** Streams [url] into [dest], reporting 0f..1f progress. Returns true on success. */
    suspend fun download(url: String, dest: java.io.File, onProgress: (Float) -> Unit = {}): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val req = Request.Builder().url(url).header("User-Agent", Config.UA).build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@withContext false
                    val body = resp.body ?: return@withContext false
                    val total = body.contentLength()
                    dest.parentFile?.mkdirs()
                    body.byteStream().use { input ->
                        dest.outputStream().use { output ->
                            val buf = ByteArray(16 * 1024)
                            var sum = 0L
                            var read = input.read(buf)
                            while (read != -1) {
                                output.write(buf, 0, read)
                                sum += read
                                if (total > 0) onProgress((sum.toFloat() / total).coerceIn(0f, 1f))
                                read = input.read(buf)
                            }
                        }
                    }
                    true
                }
            }.getOrDefault(false)
        }
}
