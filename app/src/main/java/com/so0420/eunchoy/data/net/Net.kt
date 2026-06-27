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
                if (host.endsWith("naver.com")) {
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
}
