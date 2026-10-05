package com.example.data.ai

import android.util.Log
import com.example.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.io.IOException
import java.util.concurrent.TimeUnit

interface GeminiApiService {
    /**
     * Endpoint teruji untuk Gemini 2.0 Flash pada v1beta API.
     * Path static tanpa variabel mencegah encoding titik dua (:) dari Retrofit.
     */
    @POST("v1beta/models/gemini-2.0-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GeminiGenerateRequest
    ): GeminiGenerateResponse

    /**
     * Endpoint dinamis dengan encoded = true jika ingin memanggil model tertentu.
     */
    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContentForModel(
        @Path(value = "model", encoded = true) model: String,
        @Query("key") apiKey: String,
        @Body request: GeminiGenerateRequest
    ): GeminiGenerateResponse
}

object GeminiClient {
    private const val TAG = "GeminiClient"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    // OkHttpClient dengan 30s timeout dan retry on connection failure
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .apply {
            if (BuildConfig.DEBUG) {
                addInterceptor(
                    HttpLoggingInterceptor().apply {
                        level = HttpLoggingInterceptor.Level.BODY
                    }
                )
            }
        }
        .build()

    val apiService: GeminiApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GeminiApiService::class.java)
    }

    /**
     * Membaca API Key secara aman dari BuildConfig (bebas hardcoded string)
     * dan membersihkan karakter petik ganda atau spasi berlebih.
     */
    fun getApiKey(): String {
        return try {
            val key = BuildConfig.GEMINI_API_KEY
                .trim()
                .replace("\"", "")
                .replace("'", "")
                .trim()

            if (key.isBlank() || key == "MY_GEMINI_API_KEY") {
                Log.e(
                    TAG,
                    "API Key tidak ditemukan di BuildConfig/local.properties atau masih bernilai default 'MY_GEMINI_API_KEY'. " +
                    "Pastikan GEMINI_API_KEY telah diatur di Secrets Panel Google AI Studio."
                )
                ""
            } else {
                key
            }
        } catch (e: Exception) {
            Log.e(TAG, "Gagal membaca BuildConfig.GEMINI_API_KEY: ${e.message}", e)
            ""
        }
    }

    /**
     * Eksekusi request dengan mekanisme Exponential Backoff (1s, 2s, 4s) maksimal 3x retry.
     * Menggunakan model resmi gemini-2.0-flash via Query Parameter 'key'.
     */
    suspend fun executeGenerateWithRetry(
        apiKey: String = getApiKey(),
        request: GeminiGenerateRequest,
        modelName: String = "gemini-2.0-flash",
        maxRetries: Int = 3
    ): GeminiGenerateResponse {
        val cleanKey = if (apiKey.isBlank()) getApiKey() else apiKey.trim().replace("\"", "").replace("'", "").trim()

        if (cleanKey.isBlank() || cleanKey == "MY_GEMINI_API_KEY") {
            throw IllegalStateException(
                "API Key tidak ditemukan di BuildConfig/local.properties. " +
                "Silakan masukkan GEMINI_API_KEY yang valid di Secrets Panel AI Studio."
            )
        }

        var currentDelayMs = 1000L

        for (attempt in 1..maxRetries) {
            try {
                Log.d(TAG, "Memanggil Gemini API ($modelName, Percobaan $attempt/$maxRetries)...")
                return if (modelName == "gemini-2.0-flash") {
                    apiService.generateContent(cleanKey, request)
                } else {
                    apiService.generateContentForModel(modelName, cleanKey, request)
                }
            } catch (e: HttpException) {
                val statusCode = e.code()
                val requestUrl = e.response()?.raw()?.request?.url?.toString() ?: "N/A"
                val errorBody = try { e.response()?.errorBody()?.string() } catch (_: Exception) { "N/A" }
                Log.e(TAG, "HttpException [HTTP $statusCode] URL: $requestUrl | Body: $errorBody", e)

                if (statusCode == 404) {
                    throw IllegalStateException(
                        "Endpoint/Model tidak ditemukan (HTTP 404). " +
                        "API Key di BuildConfig/local.properties belum valid, expired, atau di-revoke oleh Google karena terdeteksi di-hardcode. " +
                        "Periksa kembali GEMINI_API_KEY di Google AI Studio."
                    )
                }

                if (statusCode == 401 || statusCode == 403) {
                    throw IllegalStateException(
                        "Autentikasi ditolak (HTTP $statusCode). " +
                        "API Key Google AI Studio tidak valid atau izin ditolak. Pastikan key aktif."
                    )
                }

                val isTransientError = statusCode in listOf(503, 429, 500, 502, 504)
                if (attempt == maxRetries || !isTransientError) throw e

                Log.w(TAG, "Percobaan $attempt gagal [HTTP $statusCode]. Menunggu ${currentDelayMs}ms sebelum retry...")
                delay(currentDelayMs)
                currentDelayMs *= 2
            } catch (e: IOException) {
                Log.e(TAG, "Network IOException pada percobaan $attempt: ${e.message}", e)
                if (attempt == maxRetries) throw e
                delay(currentDelayMs)
                currentDelayMs *= 2
            } catch (e: Exception) {
                Log.e(TAG, "Error tidak terduga pada percobaan $attempt: ${e.message}", e)
                throw e
            }
        }

        return apiService.generateContent(cleanKey, request)
    }

    suspend fun executeGenerate(apiKey: String, request: GeminiGenerateRequest): GeminiGenerateResponse {
        return executeGenerateWithRetry(apiKey, request)
    }
}
