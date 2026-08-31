package dev.sz.aid

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

data class ChatResult(
    val content: String,
    val usage: Usage?,
)

@Serializable
data class ChatMessage(
    val role: String,
    val content: String
)

@Serializable
data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Double? = null,
    @SerialName("max_tokens")
    val maxTokens: Int? = null,
    val stream: Boolean = false,
    @SerialName("extra_body")
    val extraBody: ExtraBody? = null,
    @SerialName("stream_options")
    val streamOptions: StreamOptions? = null,
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

    @Serializable
    data class StreamOptions(
        @SerialName("include_usage")
        val includeUsage: Boolean = false,
    )
}

@Serializable
data class Usage(
    @SerialName("prompt_tokens")
    val promptTokens: Int? = null,
    @SerialName("completion_tokens")
    val completionTokens: Int? = null,
    @SerialName("total_tokens")
    val totalTokens: Int? = null,
    @SerialName("prompt_tokens_details")
    val promptTokensDetails: PromptTokensDetails? = null,
    @SerialName("completion_tokens_details")
    val completionTokensDetails: CompletionTokensDetails? = null,
) {
    @Serializable
    data class PromptTokensDetails(
        @SerialName("cached_tokens")
        val cachedTokens: Int? = null
    )

    @Serializable
    data class CompletionTokensDetails(
        @SerialName("reasoning_tokens")
        val reasoningTokens: Int? = null
    )
}

@Serializable
data class ChatCompletionResponse(
    val id: String? = null,
    val choices: List<Choice> = emptyList(),
    val usage: Usage? = null,
) {
    @Serializable
    data class Choice(
        val index: Int,
        val message: ChatMessage? = null,
        @SerialName("finish_reason")
        val finishReason: String? = null
    )
}

@Serializable
data class ChatCompletionStreamResponse(
    val id: String? = null,
    val obj: String? = null,
    val choices: List<StreamChoice> = emptyList(),
    val usage: Usage? = null,
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
