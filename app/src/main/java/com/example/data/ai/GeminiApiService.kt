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
    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContentForModel(
        @Path("model") model: String,
        @Query("key") apiKey: String,
        @Body request: GeminiGenerateRequest
    ): GeminiGenerateResponse

    @POST("v1beta/models/gemini-1.5-flash:generateContent")
    suspend fun generateContent(
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

    // OkHttpClient with 30s timeouts and automatic connection retry
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .addInterceptor(
            HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
        )
        .build()

    val apiService: GeminiApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GeminiApiService::class.java)
    }

    fun getApiKey(): String {
        return try {
            BuildConfig.GEMINI_API_KEY
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * Executes generation request focusing strictly on the stable gemini-1.5-flash model.
     * Retries up to 3 times using Exponential Backoff (1s, 2s, 4s) exclusively for transient errors:
     * HTTP 503 (Server Busy), 429 (Rate Limit), 500, 502, 504, or network IOException.
     */
    suspend fun executeGenerateWithRetry(
        apiKey: String,
        request: GeminiGenerateRequest,
        modelName: String = "gemini-1.5-flash",
        maxRetries: Int = 3
    ): GeminiGenerateResponse {
        var currentDelayMs = 1000L

        for (attempt in 1..maxRetries) {
            try {
                Log.d(TAG, "Memanggil Gemini API (Model: $modelName, Percobaan $attempt/$maxRetries)...")
                return apiService.generateContentForModel(modelName, apiKey, request)
            } catch (e: Exception) {
                val isTransientError = when (e) {
                    is HttpException -> e.code() in listOf(503, 429, 500, 502, 504)
                    is IOException -> true
                    else -> {
                        val msg = e.message ?: ""
                        msg.contains("503") || msg.contains("429") || msg.contains("500") || msg.contains("502") || msg.contains("504")
                    }
                }

                if (attempt == maxRetries || !isTransientError) {
                    Log.e(TAG, "Gagal memproses pada percobaan $attempt: ${e.message}", e)
                    throw e
                }

                Log.w(TAG, "Percobaan $attempt menemui transient error [${e.message}]. Menunggu ${currentDelayMs}ms sebelum retry...")
                delay(currentDelayMs)
                currentDelayMs *= 2 // Exponential Backoff: 1000ms -> 2000ms -> 4000ms
            }
        }

        return apiService.generateContentForModel(modelName, apiKey, request)
    }

    suspend fun executeGenerate(apiKey: String, request: GeminiGenerateRequest): GeminiGenerateResponse {
        return executeGenerateWithRetry(apiKey, request)
    }
}
