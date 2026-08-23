package dev.sz.aid

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSource
import java.io.IOException
import java.util.concurrent.TimeUnit

class LlmClient(val config: Config) {

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(config.connectTimeoutSec, TimeUnit.SECONDS)
            .readTimeout(config.readTimeoutSec, TimeUnit.SECONDS)
            .build()
    }

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
        encodeDefaults = true
        explicitNulls = false
    }

    private val prettyJson by lazy {
        Json(json) {
            prettyPrint = true
        }
    }

    fun chat(prompt: Prompt): String {
        val request: Request = buildChatRequest(prompt, stream = false)

        val responseBody = httpClient.newCall(request).execute().use { response ->
            val body = response.body.string() // body is guaranteed to be non-null in any case
            if (!response.isSuccessful) {
                throw IOException("LLM HTTP ${response.code} ${response.message}: $body\nHeaders: ${response.headers}")
            }
            body
        }

        val response: ChatCompletionResponse = json.decodeFromString(responseBody)

        return response.choices.firstOrNull()?.message?.content
            ?: throw IOException("No LLM response message: $responseBody")
    }

    fun chatStream(prompt: Prompt, onDelta: (String) -> Unit) {
        val request: Request = buildChatRequest(prompt, stream = true)

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val body = response.body.string() // body is guaranteed to be non-null in any case
                throw IOException("LLM HTTP ${response.code} ${response.message}: $body\nHeaders: ${response.headers}")
            }

            val contentType = response.header("Content-Type")?.lowercase() ?: ""
            if (!contentType.contains("text/event-stream")) {
                val body = response.body.string() // body is guaranteed to be non-null in any case
                throw IOException("Expected SSE stream but got Content-Type: $contentType\nBody: $body")
            }

            response.body.source().use { source ->
                parseSseStream(source).forEach { data ->
                    val chunk: ChatCompletionStreamResponse = try {
                        json.decodeFromString(data)
                    } catch (e: SerializationException) {
                        throw IllegalStateException("Invalid LLM data: $data", e)
                    }
                    val content = chunk.choices.firstOrNull()?.delta?.content
                    if (!content.isNullOrEmpty()) onDelta(content)
                }
            }
        }
    }

    // single-line data only; sufficient for OpenAI-compatible SSE
    private fun parseSseStream(source: BufferedSource): Sequence<String> = sequence {
        while (!source.exhausted()) {
            val line = source.readUtf8Line() ?: break
            if (!line.startsWith("data:")) continue
            val data = line.removePrefix("data:").trim()
            if (data == "[DONE]") break
            yield(data)
        }
    }

    private fun buildChatRequest(prompt: Prompt, stream: Boolean): Request {
        val requestModel: ChatCompletionRequest = prompt.toRequest(stream)
        val requestJson = json.encodeToString(requestModel)
        val baseUrl = config.url.removeSuffix("/")

        return Request.Builder()
            .url("$baseUrl/v1/chat/completions")
            .addHeader("Content-Type", "application/json")
            .apply {
                if (stream) addHeader("Accept", "text/event-stream")
                config.apiKey?.let { addHeader("Authorization", "Bearer $it") }
            }
            .post(requestJson.toRequestBody("application/json".toMediaType()))
            .build()
    }

    fun renderDryRun(prompt: Prompt, isStream: Boolean): String =
        prettyJson.encodeToString(prompt.toRequest(isStream))

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

    private fun Prompt.toRequest(stream: Boolean): ChatCompletionRequest {
        val chatMessages: List<ChatMessage> = listOf(
            ChatMessage("system", systemMessage),
            ChatMessage("user", combinedUserMessage)
        )

        val thinkingConfig: ChatCompletionRequest.ExtraBody? = if (config.forceThinking) {
            ChatCompletionRequest.ExtraBody(
                enableThinking = true,
                thinkingBudget = 512,
                preserveThinking = true,
            )
        } else {
            null
        }

        return ChatCompletionRequest(
            model = config.model,
            messages = chatMessages,
            stream = stream,
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

@Serializable
private data class ChatCompletionStreamResponse(
    val id: String? = null,
    val obj: String? = null,
    val choices: List<StreamChoice> = emptyList(),
) {
    @Serializable
    data class StreamChoice(
        val index: Int,
        val delta: StreamDelta? = null,
        @SerialName("finish_reason")
        val finishReason: String? = null
    )

    @Serializable
    data class StreamDelta(
        val role: String? = null,
        val content: String? = null
    )
}
