package com.example.network

import com.example.BuildConfig
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

// --- Request Models ---

@JsonClass(generateAdapter = true)
data class GenerateContentRequest(
    val contents: List<Content>,
    val generationConfig: GenerationConfig? = null,
    val systemInstruction: Content? = null
)

@JsonClass(generateAdapter = true)
data class Content(
    val parts: List<Part>
)

@JsonClass(generateAdapter = true)
data class Part(
    val text: String? = null,
    val inlineData: InlineData? = null
)

@JsonClass(generateAdapter = true)
data class InlineData(
    val mimeType: String,
    val data: String // Base64 encoded string
)

@JsonClass(generateAdapter = true)
data class GenerationConfig(
    val responseMimeType: String? = null,
    val temperature: Float? = null,
    val thinkingConfig: ThinkingConfig? = null
)

@JsonClass(generateAdapter = true)
data class ThinkingConfig(
    val thinkingLevel: String? = null
)

// --- Response Models ---

@JsonClass(generateAdapter = true)
data class GenerateContentResponse(
    val candidates: List<Candidate>? = null
)

@JsonClass(generateAdapter = true)
data class Candidate(
    val content: Content? = null,
    val finishReason: String? = null
)

// --- Retrofit Interface ---

interface GeminiApiService {
    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContent(
        @Path("model") model: String,
        @Query("key") apiKey: String,
        @Body request: GenerateContentRequest
    ): GenerateContentResponse
}

// --- Retrofit Client ---

object GeminiClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .addInterceptor(loggingInterceptor)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    val apiService: GeminiApiService = retrofit.create(GeminiApiService::class.java)

    // Helper to run analysis using gemini-3.1-pro-preview with thinking level HIGH
    suspend fun analyzeImage(base64Image: String, prompt: String): String? {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return "{\"error\": \"Gemini API key is not configured. Please add GEMINI_API_KEY to secrets panel.\"}"
        }

        val request = GenerateContentRequest(
            contents = listOf(
                Content(
                    parts = listOf(
                        Part(text = prompt),
                        Part(inlineData = InlineData(mimeType = "image/jpeg", data = base64Image))
                    )
                )
            ),
            generationConfig = GenerationConfig(
                responseMimeType = "application/json",
                temperature = 1.0f, // Use modern standard or leave defaults
                thinkingConfig = ThinkingConfig(thinkingLevel = "high") // set thinkingLevel to ThinkingLevel.HIGH (high string)
            )
        )

        return try {
            val response = apiService.generateContent(
                model = "gemini-3.1-pro-preview",
                apiKey = apiKey,
                request = request
            )
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
        } catch (e: Exception) {
            e.printStackTrace()
            "{\"error\": \"Failed to analyze image: ${e.localizedMessage}\"}"
        }
    }
}
