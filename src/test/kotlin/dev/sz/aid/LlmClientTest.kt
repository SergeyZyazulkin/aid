package dev.sz.aid

import io.kotest.matchers.ints.shouldBeLessThan
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.unmockkAll
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.IOException
import kotlin.test.AfterTest

class LlmClientTest {

    @AfterTest
    fun cleanup() = unmockkAll()

    @Test
    fun `doesn't allow invalid config`() {
        assertThrows<IllegalArgumentException> {
            LlmClient.Config(
                url = "not a valid http(s) url",
                model = "test",
                connectTimeoutSec = 1L,
                readTimeoutSec = 1L,
            )
        }.message.shouldContain("Not a valid HTTP(S) URL")
    }

    @Test
    fun `sends correct JSON payload and parses response`() {
        val config = LlmClient.Config(
            url = "http://localhost:1234",
            model = "dummy",
            connectTimeoutSec = 5,
            readTimeoutSec = 60
        )
        val responseMessage = "test response message"
        val mockResponseBody = """
            {
              "choices": [
                { 
                  "index": 0,
                  "message": {
                    "role": "assistant",
                    "content": "$responseMessage" 
                  } 
                }
              ]
            }
        """.trimIndent()

        val mockCall = mockk<Call>()
        mockkConstructor(OkHttpClient::class)
        every { anyConstructed<OkHttpClient>().newCall(any()) } returns mockCall
        every { mockCall.execute() } answers {
            Response.Builder()
                .request(Request.Builder().url(config.url).build())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(mockResponseBody.toResponseBody("application/json".toMediaType()))
                .build()
        }

        val client = LlmClient(config)
        client.addPrompt(
            LlmClient.Prompt(
                systemMessage = "You are helpful.",
                userMessage = null,
                code = "abc"
            )
        )
        val result = client.chat()
        result.content shouldBe responseMessage
        result.usage shouldBe null
    }

    @Test
    fun `throws if HTTP error response`() {
        val config = LlmClient.Config(
            url = "http://localhost:1234",
            model = "dummy",
            connectTimeoutSec = 5,
            readTimeoutSec = 60
        )

        val mockCall = mockk<Call>()
        mockkConstructor(OkHttpClient::class)
        every { anyConstructed<OkHttpClient>().newCall(any()) } returns mockCall
        every { mockCall.execute() } answers {
            Response.Builder()
                .request(Request.Builder().url(config.url).build())
                .protocol(Protocol.HTTP_1_1)
                .code(401)
                .message("Unauthorized")
                .body("Invalid key".toResponseBody("text/plain".toMediaType()))
                .build()
        }

        val client = LlmClient(config)
        client.addPrompt(
            LlmClient.Prompt(
                systemMessage = "Test",
                userMessage = null,
                code = ""
            )
        )
        assertThrows<IOException> {
            client.chat()
        }.message.shouldContain("LLM HTTP 401 Unauthorized")
    }

    @Test
    fun `chatStream parses SSE and invokes callbacks`() {
        val config = LlmClient.Config(
            url = "http://localhost:1234",
            model = "dummy",
            connectTimeoutSec = 5,
            readTimeoutSec = 60,
        )

        val sseBody = buildString {
            appendLine("""data: {"id":"1","object":"chat.completion.chunk","choices":[{"index":0,"delta":{"role":"assistant","content":"Hel"},"finish_reason":null}]}""")
            appendLine()
            appendLine("""data: {"id":"1","object":"chat.completion.chunk","choices":[{"index":0,"delta":{"content":"lo"},"finish_reason":null}]}""")
            appendLine()
            appendLine("data: [DONE]")
            appendLine()
        }

        val mockCall = mockk<Call>()
        mockkConstructor(OkHttpClient::class)
        every { anyConstructed<OkHttpClient>().newCall(any()) } returns mockCall
        every { mockCall.execute() } answers {
            Response.Builder()
                .request(Request.Builder().url(config.url).build())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .headers(
                    Headers.Builder()
                        .add("Content-Type", "text/event-stream")
                        .build()
                )
                .message("OK")
                .body(sseBody.toResponseBody("text/event-stream".toMediaType()))
                .build()
        }

        val deltas = mutableListOf<String>()
        val client = LlmClient(config)
        client.addPrompt(LlmClient.Prompt("sys", null, "code"))
        client.chatStream(onDelta = { deltas.add(it) })
        deltas shouldBe listOf("Hel", "lo")
    }

    @Test
    fun `chatStream throws on HTTP error`() {
        val config = LlmClient.Config(
            url = "http://localhost:1234",
            model = "dummy",
            connectTimeoutSec = 5,
            readTimeoutSec = 60,
        )

        val mockCall = mockk<Call>()
        mockkConstructor(OkHttpClient::class)
        every { anyConstructed<OkHttpClient>().newCall(any()) } returns mockCall
        every { mockCall.execute() } answers {
            Response.Builder()
                .request(Request.Builder().url(config.url).build())
                .protocol(Protocol.HTTP_1_1)
                .code(500)
                .message("Internal Server Error")
                .body("boom".toResponseBody("text/plain".toMediaType()))
                .build()
        }

        val client = LlmClient(config)
        client.addPrompt(LlmClient.Prompt("sys", null, "code"))
        assertThrows<IOException> {
            client.chatStream(onDelta = {})
        }.message.shouldContain("LLM HTTP 500")
    }

    @Test
    fun `chatStream throws on malformed SSE JSON`() {
        val config = LlmClient.Config(
            url = "http://localhost:1234",
            model = "dummy",
            connectTimeoutSec = 5,
            readTimeoutSec = 60,
        )

        val sseBody = buildString {
            appendLine("data: {not a valid json}")
            appendLine()
            appendLine("data: [DONE]")
            appendLine()
        }

        val mockCall = mockk<Call>()
        mockkConstructor(OkHttpClient::class)
        every { anyConstructed<OkHttpClient>().newCall(any()) } returns mockCall
        every { mockCall.execute() } answers {
            Response.Builder()
                .request(Request.Builder().url(config.url).build())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .headers(
                    Headers.Builder()
                        .add("Content-Type", "text/event-stream")
                        .build()
                )
                .body(sseBody.toResponseBody("text/event-stream".toMediaType()))
                .build()
        }

        val client = LlmClient(config)
        client.addPrompt(LlmClient.Prompt("sys", null, "code"))
        assertThrows<IllegalStateException> {
            client.chatStream(onDelta = {})
        }.message.shouldContain("Invalid LLM data: {not a valid json}")
    }

    @Test
    fun `chatStream throws on unexpected response content type`() {
        val config = LlmClient.Config(
            url = "http://localhost:1234",
            model = "dummy",
            connectTimeoutSec = 5,
            readTimeoutSec = 60,
        )

        val mockCall = mockk<Call>()
        mockkConstructor(OkHttpClient::class)
        every { anyConstructed<OkHttpClient>().newCall(any()) } returns mockCall
        every { mockCall.execute() } answers {
            Response.Builder()
                .request(Request.Builder().url(config.url).build())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .headers(
                    Headers.Builder()
                        .add("Content-Type", "text/plain")
                        .build()
                )
                .body("body".toResponseBody("text/plain".toMediaType()))
                .build()
        }

        val client = LlmClient(config)
        client.addPrompt(LlmClient.Prompt("sys", null, "code"))
        assertThrows<IOException> {
            client.chatStream(onDelta = {})
        }.message.shouldContain("Expected SSE stream but got Content-Type: text/plain\nBody: body")
    }

    @Test
    fun `chatStream handles empty choices and no content`() {
        val config = LlmClient.Config(
            url = "http://localhost:1234",
            model = "dummy",
            connectTimeoutSec = 5,
            readTimeoutSec = 60,
        )

        val sseBody = buildString {
            appendLine("""data: {"id":"1","choices":[]}""")
            appendLine()
            appendLine("""data: {"id":"1","choices":[{"index":0,"delta":{"content":null}}]}""")
            appendLine()
            appendLine("data: [DONE]")
            appendLine()
        }

        val mockCall = mockk<Call>()
        mockkConstructor(OkHttpClient::class)
        every { anyConstructed<OkHttpClient>().newCall(any()) } returns mockCall
        every { mockCall.execute() } answers {
            Response.Builder()
                .request(Request.Builder().url(config.url).build())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .headers(
                    Headers.Builder()
                        .add("Content-Type", "text/event-stream")
                        .build()
                )
                .body(sseBody.toResponseBody("text/event-stream".toMediaType()))
                .build()
        }

        val deltas = mutableListOf<String>()
        val client = LlmClient(config)
        client.addPrompt(LlmClient.Prompt("sys", null, "code"))
        client.chatStream(onDelta = { deltas.add(it) })
        deltas shouldBe emptyList()
    }

    @Test
    fun `chat returns usage data`() {
        val config = LlmClient.Config(
            url = "http://localhost:1234",
            model = "dummy",
            connectTimeoutSec = 5,
            readTimeoutSec = 60,
        )
        val mockResponseBody = """
        {
          "choices": [{"index": 0, "message": {"role": "assistant", "content": "hello"}}],
          "usage": {
            "prompt_tokens": 42, 
            "completion_tokens": 7, 
            "total_tokens": 49,
            "prompt_tokens_details": {
              "cached_tokens": 0
            },
            "completion_tokens_details": {
              "reasoning_tokens": 22
            }
          }
        }
        """.trimIndent()

        val mockCall = mockk<Call>()
        mockkConstructor(OkHttpClient::class)
        every { anyConstructed<OkHttpClient>().newCall(any()) } returns mockCall
        every { mockCall.execute() } answers {
            Response.Builder()
                .request(Request.Builder().url(config.url).build())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(mockResponseBody.toResponseBody("application/json".toMediaType()))
                .build()
        }

        val client = LlmClient(config)
        client.addPrompt(LlmClient.Prompt("sys", null, "code"))
        val result = client.chat()
        result.content shouldBe "hello"
        result.usage?.promptTokens shouldBe 42
        result.usage?.completionTokens shouldBe 7
        result.usage?.totalTokens shouldBe 49
        result.usage?.promptTokensDetails?.cachedTokens shouldBe 0
        result.usage?.completionTokensDetails?.reasoningTokens shouldBe 22
    }

    @Test
    fun `chatStream returns usage data`() {
        val config = LlmClient.Config(
            url = "http://localhost:1234",
            model = "dummy",
            connectTimeoutSec = 5,
            readTimeoutSec = 60,
            requestStreamUsage = true,
        )

        val sseBody = buildString {
            appendLine("""data: {"id":"1","choices":[]}""")
            appendLine()
            appendLine("""data: {"id":"1","choices":[{"index":0,"delta":{"content":null}}],"usage":{"prompt_tokens":122,"completion_tokens":17,"total_tokens":139}}""")
            appendLine()
            appendLine("data: [DONE]")
            appendLine()
        }

        val mockCall = mockk<Call>()
        mockkConstructor(OkHttpClient::class)
        every { anyConstructed<OkHttpClient>().newCall(any()) } returns mockCall
        every { mockCall.execute() } answers {
            Response.Builder()
                .request(Request.Builder().url(config.url).build())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .headers(
                    Headers.Builder()
                        .add("Content-Type", "text/event-stream")
                        .build()
                )
                .body(sseBody.toResponseBody("text/event-stream".toMediaType()))
                .build()
        }

        val client = LlmClient(config)
        client.addPrompt(LlmClient.Prompt("sys", null, "code"))
        val usage: Usage? = client.chatStream(onDelta = {})
        usage.shouldNotBeNull()
        usage.promptTokens shouldBe 122
        usage.completionTokens shouldBe 17
        usage.totalTokens shouldBe 139
        usage.promptTokensDetails.shouldBeNull()
        usage.completionTokensDetails.shouldBeNull()
    }

    @Test
    fun `chat throws on missing content`() {
        val config = LlmClient.Config(
            url = "http://localhost:1234",
            model = "dummy",
            connectTimeoutSec = 5,
            readTimeoutSec = 60,
        )
        val mockResponseBody =
            """{"choices":[{"index":0,"message":{"role":"assistant","content":null,"reasoning_content":null}}]}"""

        val mockCall = mockk<Call>()
        mockkConstructor(OkHttpClient::class)
        every { anyConstructed<OkHttpClient>().newCall(any()) } returns mockCall
        every { mockCall.execute() } answers {
            Response.Builder()
                .request(Request.Builder().url(config.url).build())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(mockResponseBody.toResponseBody("application/json".toMediaType()))
                .build()
        }

        val client = LlmClient(config)
        client.addPrompt(LlmClient.Prompt("sys", null, "code"))
        assertThrows<IllegalArgumentException> {
            client.chat()
        }.message.shouldContain("requires at least one of content or reasoningContent")
    }

    @Test
    fun `Prompt combinedUserMessage with no userMessage and no context returns code`() {
        val prompt = LlmClient.Prompt(null, null, "my code")
        prompt.combinedUserMessage shouldBe "my code"
    }

    @Test
    fun `Prompt combinedUserMessage with userMessage and no context`() {
        val prompt = LlmClient.Prompt("sys", "Do something", "my code")
        prompt.combinedUserMessage shouldBe "Do something\n\n## CODE ##\nmy code"
    }

    @Test
    fun `Prompt combinedUserMessage with context but no userMessage`() {
        val prompt = LlmClient.Prompt(null, null, "my code", listOf("issue.md" to "Bug description"))
        prompt.combinedUserMessage shouldBe "my code\n\n## CONTEXT: issue.md ##\nBug description"
    }

    @Test
    fun `Prompt combinedUserMessage with userMessage and multiple context files`() {
        val prompt = LlmClient.Prompt(
            "sys", "Explain", "code here",
            listOf("a.md" to "Content A", "b.txt" to "Content B")
        )
        prompt.combinedUserMessage shouldBe
                "Explain\n\n## CODE ##\ncode here\n\n## CONTEXT: a.md ##\nContent A\n\n## CONTEXT: b.txt ##\nContent B"
    }

    @Test
    fun `addPrompt with system message and code produces system and user messages`() {
        val client = LlmClient(
            LlmClient.Config(
                url = "http://localhost:1234",
                model = "test-model",
                connectTimeoutSec = 5,
                readTimeoutSec = 60,
            )
        )
        client.addPrompt(
            LlmClient.Prompt(
                systemMessage = "You are a reviewer.",
                userMessage = null,
                code = "fun main() {}",
            )
        )

        client.dryRun(isStream = false)
            .shouldContain("\"role\": \"system\"")
            .shouldContain("You are a reviewer.")
            .shouldContain("\"role\": \"user\"")
            .shouldContain("fun main() {}")
    }

    @Test
    fun `addPrompt with null system message produces only user message`() {
        val client = LlmClient(
            LlmClient.Config(
                url = "http://localhost:1234",
                model = "test-model",
                connectTimeoutSec = 5,
                readTimeoutSec = 60,
            )
        )
        client.addPrompt(
            LlmClient.Prompt(
                systemMessage = null,
                userMessage = "Explain this",
                code = "val x = 1",
            )
        )

        client.dryRun(isStream = false)
            .shouldNotContain("\"role\": \"system\"")
            .shouldContain("\"role\": \"user\"")
            .shouldContain("Explain this")
            .shouldContain("## CODE ##")
            .shouldContain("val x = 1")
    }

    @Test
    fun `addPrompt with null code and null userMessage produces empty user message`() {
        val client = LlmClient(
            LlmClient.Config(
                url = "http://localhost:1234",
                model = "test-model",
                connectTimeoutSec = 5,
                readTimeoutSec = 60,
            )
        )
        client.addPrompt(
            LlmClient.Prompt(
                systemMessage = "sys",
                userMessage = null,
                code = null,
            )
        )

        client.dryRun(isStream = false)
            .shouldContain("\"role\": \"system\"")
            .shouldContain("\"role\": \"user\"")
            .shouldContain("\"content\": \"\"")
    }

    @Test
    fun `addPrompt with context sections includes them in user message`() {
        val client = LlmClient(
            LlmClient.Config(
                url = "http://localhost:1234",
                model = "test-model",
                connectTimeoutSec = 5,
                readTimeoutSec = 60,
            )
        )
        client.addPrompt(
            LlmClient.Prompt(
                systemMessage = null,
                userMessage = null,
                code = "code here",
                contextSections = listOf("issue.md" to "Bug description"),
            )
        )

        client.dryRun(isStream = false)
            .shouldContain("code here")
            .shouldContain("## CONTEXT: issue.md ##")
            .shouldContain("Bug description")
    }

    @Test
    fun `multiple addPrompt calls accumulate messages in order`() {
        val client = LlmClient(
            LlmClient.Config(
                url = "http://localhost:1234",
                model = "test-model",
                connectTimeoutSec = 5,
                readTimeoutSec = 60,
            )
        )
        client.addPrompt(LlmClient.Prompt("system-1", null, "first code"))
        client.addPrompt(LlmClient.Prompt(null, "follow-up question", null))

        val body = client.dryRun(isStream = false)

        // Both system and user messages from first prompt
        body.shouldContain("system-1")
            .shouldContain("first code")
            // Second prompt: no system, user with follow-up
            .shouldContain("follow-up question")

        // Verify ordering: system-1 appears before "first code", which appears before "follow-up question"
        val sysIdx = body.indexOf("system-1")
        val firstCodeIdx = body.indexOf("first code")
        val followUpIdx = body.indexOf("follow-up question")
        sysIdx shouldBeLessThan firstCodeIdx
        firstCodeIdx shouldBeLessThan followUpIdx
    }

    @Test
    fun `addPrompt with null code and userMessage only produces user message with prompt text`() {
        val client = LlmClient(
            LlmClient.Config(
                url = "http://localhost:1234",
                model = "test-model",
                connectTimeoutSec = 5,
                readTimeoutSec = 60,
            )
        )
        client.addPrompt(
            LlmClient.Prompt(
                systemMessage = null,
                userMessage = "Just a question",
                code = null,
            )
        )

        client.dryRun(isStream = false)
            .shouldContain("Just a question")
            .shouldNotContain("## CODE ##")
    }

    @Test
    fun `addPrompt with code but no userMessage sends code without header`() {
        val client = LlmClient(
            LlmClient.Config(
                url = "http://localhost:1234",
                model = "test-model",
                connectTimeoutSec = 5,
                readTimeoutSec = 60,
            )
        )
        client.addPrompt(
            LlmClient.Prompt(
                systemMessage = null,
                userMessage = null,
                code = "int x = 5;",
            )
        )

        client.dryRun(isStream = false)
            .shouldNotContain("## CODE ##")
            .shouldContain("int x = 5;")
    }
}
