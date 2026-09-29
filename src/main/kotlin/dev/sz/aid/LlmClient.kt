package dev.sz.aid

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

    private val messages: MutableList<ChatCompletionRequest.ChatMessage> = mutableListOf()

    fun addPrompt(prompt: Prompt) {
        prompt.systemMessage?.let { messages.add(ChatCompletionRequest.ChatMessage("system", it)) }
        messages.add(ChatCompletionRequest.ChatMessage("user", prompt.combinedUserMessage))
    }

    fun chat(): ChatResult {
        val request: Request = buildChatRequest(stream = false)

        val responseBody = httpClient.newCall(request).execute().use { response ->
            val body = response.body.string() // body is guaranteed to be non-null in any case
            if (!response.isSuccessful) {
                throw IOException("LLM HTTP ${response.code} ${response.message}: $body\nHeaders: ${response.headers}")
            }
            body
        }

        val response: ChatCompletionResponse = json.decodeFromString(responseBody)

        val message: ChatCompletionResponse.Choice.ChatMessage = response.choices.firstOrNull()?.message
            ?: throw IOException("No LLM response message: $responseBody")

        addAssistantResponse(message.content ?: "")

        return ChatResult(
            content = message.content,
            reasoningContent = message.reasoningContent?.takeIf { it.isNotEmpty() },
            usage = response.usage,
        )
    }

    fun chatStream(
        onDelta: (String) -> Unit,
        onReasoningDelta: ((String) -> Unit)? = null,
    ): Usage? {
        val request: Request = buildChatRequest(stream = true)
        var usage: Usage? = null
        val assistantMessage: StringBuilder? = if (config.addAssistantResponseToHistory) StringBuilder() else null

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

                    val delta: ChatCompletionStreamResponse.StreamDelta? = chunk.choices.firstOrNull()?.delta
                    val content: String? = delta?.content
                    if (!content.isNullOrEmpty()) {
                        onDelta(content)
                        assistantMessage?.append(content)
                    }

                    val reasoning: String? = delta?.reasoningContent
                    if (!reasoning.isNullOrEmpty()) onReasoningDelta?.invoke(reasoning)

                    chunk.usage?.let { usage = it }
                }
            }
        }

        addAssistantResponse(assistantMessage?.toString())
        return usage
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

    private fun buildChatRequest(stream: Boolean): Request {
        val requestModel: ChatCompletionRequest = buildChatRequestModel(stream)
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

    fun dryRun(isStream: Boolean): String = prettyJson.encodeToString(buildChatRequestModel(isStream))

    private fun buildChatRequestModel(stream: Boolean): ChatCompletionRequest {
        val thinkingConfig: ChatCompletionRequest.ExtraBody? = if (config.forceThinking) {
            ChatCompletionRequest.ExtraBody(
                enableThinking = true,
                thinkingBudget = 512,
                preserveThinking = true,
            )
        } else null

        val streamOptions: ChatCompletionRequest.StreamOptions? = if (stream && config.requestStreamUsage) {
            ChatCompletionRequest.StreamOptions(includeUsage = true)
        } else null

        return ChatCompletionRequest(
            model = config.model,
            messages = messages,
            stream = stream,
            extraBody = thinkingConfig,
            streamOptions = streamOptions,
        )
    }

    private fun addAssistantResponse(response: String?) {
        if (config.addAssistantResponseToHistory) {
            response?.let { messages.add(ChatCompletionRequest.ChatMessage("assistant", it)) }
        }
    }

    data class Config(
        val url: String,
        val model: String,
        val connectTimeoutSec: Long,
        val readTimeoutSec: Long,
        val forceThinking: Boolean = false,
        val apiKey: String? = null,
        val requestStreamUsage: Boolean = false,
        val addAssistantResponseToHistory: Boolean = false,
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
        val systemMessage: String?,
        val userMessage: String?,
        val code: String?,
        val contextSections: List<Pair<String, String>> = emptyList(),
    ) {
        val combinedUserMessage: String
            get() = buildString {
                userMessage?.let { append(it) }
                code?.let {
                    if (isNotEmpty()) append("\n\n## CODE ##\n")
                    append(it)
                }
                if (contextSections.isNotEmpty()) {
                    if (isNotEmpty()) append("\n\n")
                    val context = contextSections.joinToString("\n\n") { (name, content) ->
                        "## CONTEXT: $name ##\n$content"
                    }
                    append(context)
                }
            }
    }
}
