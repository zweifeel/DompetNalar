package com.example.data.ai

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class GeminiGenerateRequest(
    @Json(name = "contents") val contents: List<GeminiContent>,
    @Json(name = "generationConfig") val generationConfig: GeminiGenerationConfig? = null
)

@JsonClass(generateAdapter = true)
data class GeminiContent(
    @Json(name = "parts") val parts: List<GeminiPart>,
    @Json(name = "role") val role: String? = "user"
)

@JsonClass(generateAdapter = true)
data class GeminiPart(
    @Json(name = "text") val text: String? = null,
    @Json(name = "inlineData") val inlineData: GeminiInlineData? = null
)

@JsonClass(generateAdapter = true)
data class GeminiInlineData(
    @Json(name = "mimeType") val mimeType: String,
    @Json(name = "data") val data: String
)

@JsonClass(generateAdapter = true)
data class GeminiGenerationConfig(
    @Json(name = "temperature") val temperature: Float = 0.2f,
    @Json(name = "topP") val topP: Float? = 0.95f,
    @Json(name = "topK") val topK: Int? = 40
)

@JsonClass(generateAdapter = true)
data class GeminiGenerateResponse(
    @Json(name = "candidates") val candidates: List<GeminiCandidate>?
)

@JsonClass(generateAdapter = true)
data class GeminiCandidate(
    @Json(name = "content") val content: GeminiContentResponse?
)

@JsonClass(generateAdapter = true)
data class GeminiContentResponse(
    @Json(name = "parts") val parts: List<GeminiPartResponse>?
)

@JsonClass(generateAdapter = true)
data class GeminiPartResponse(
    @Json(name = "text") val text: String?
)

data class ParsedTransactionResult(
    val title: String,
    val amount: Double,
    val type: String, // "EXPENSE", "INCOME", "TRANSFER"
    val category: String,
    val categoryIcon: String,
    val suggestedWalletName: String,
    val suggestedTargetWalletName: String?,
    val note: String,
    val tags: String,
    val aiExplanation: String
)
