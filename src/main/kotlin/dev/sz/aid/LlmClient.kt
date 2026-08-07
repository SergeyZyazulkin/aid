package dev.sz.aid

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class LlmClient(val config: Config) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(config.connectTimeoutSec, TimeUnit.SECONDS)
        .readTimeout(config.readTimeoutSec, TimeUnit.SECONDS)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
        encodeDefaults = false
    }

    fun chat(prompt: Prompt): String {
        val requestModel: ChatCompletionRequest = prompt.toRequest()
        val requestJson = json.encodeToString(requestModel)
        val baseUrl = config.url.removeSuffix("/")

        val request = Request.Builder()
            .url("$baseUrl/v1/chat/completions")
            .addHeader("Content-Type", "application/json")
            .apply { config.apiKey?.let { apiKey -> addHeader("Authorization", "Bearer $apiKey") } }
            .post(requestJson.toRequestBody("application/json".toMediaType()))
            .build()

        val responseBody = httpClient.newCall(request).execute().use { response ->
            val responseBody = response.body.string() // body is guaranteed to be non-null in any case
            if (!response.isSuccessful) {
                throw IOException(
                    "LLM HTTP ${response.code} ${response.message}: $responseBody\nHeaders: ${response.headers}")
            }
            responseBody
        }

        val response: ChatCompletionResponse = json.decodeFromString(responseBody)

        return response.choices.firstOrNull()?.message?.content
            ?: throw IOException("No LLM response message: $responseBody")
    }

    data class Config(
        val url: String,
        val model: String,
        val connectTimeoutSec: Long,
        val readTimeoutSec: Long,
        val forceThinking: Boolean = false,
        val apiKey: String? = null,
    ) {
        init {
            try {
                url.toHttpUrl()
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException("Not a valid HTTP(S) URL", e)
            }

            require(model.isNotBlank()) { "Model cannot be blank" }
            require(connectTimeoutSec > 0 && readTimeoutSec > 0) { "Timeouts must be positive integers" }
        }
    }

    data class Prompt(
        val systemMessage: String,
        val userMessage: String?,
        val code: String
    ) {
        val combinedUserMessage: String
            get() = if (userMessage != null) {
                "$userMessage\n\n## CODE ##\n$code"
            } else {
                code
            }
    }

    private fun Prompt.toRequest(): ChatCompletionRequest {
        val chatMessages: List<ChatMessage> = listOf(
            ChatMessage("system", systemMessage),
            ChatMessage("user", combinedUserMessage)
        )

        val thinkingConfig: ChatCompletionRequest.ExtraBody? = if (config.forceThinking) {
            ChatCompletionRequest.ExtraBody(
                enableThinking = true,
                thinkingBudget = 512,
                preserveThinking = true
            )
        } else {
            null
        }

        return ChatCompletionRequest(
            model = config.model,
            messages = chatMessages,
            stream = false,
            extraBody = thinkingConfig,
        )
    }
}

@Serializable
private data class ChatMessage(
    val role: String,
    val content: String
)

@Serializable
private data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Double? = null,
    @SerialName("max_tokens")
    val maxTokens: Int? = null,
    val stream: Boolean = false,
    @SerialName("extra_body")
    val extraBody: ExtraBody? = null
) {
    @Serializable
    data class ExtraBody(
        @SerialName("enable_thinking")
        val enableThinking: Boolean = true,
        @SerialName("thinking_budget")
        val thinkingBudget: Int = 512,
        @SerialName("preserve_thinking")
        val preserveThinking: Boolean = true
    )
}

@Serializable
private data class ChatCompletionResponse(
    val id: String? = null,
    val choices: List<Choice> = emptyList(),
    val usage: Usage? = null
) {
    @Serializable
    data class Choice(
        val index: Int,
        val message: ChatMessage? = null,
        @SerialName("finish_reason")
        val finishReason: String? = null
    )

    @Serializable
    data class Usage(
        @SerialName("prompt_tokens")
        val promptTokens: Int? = null,
        @SerialName("completion_tokens")
        val completionTokens: Int? = null,
        @SerialName("total_tokens")
        val totalTokens: Int? = null
    )
}
