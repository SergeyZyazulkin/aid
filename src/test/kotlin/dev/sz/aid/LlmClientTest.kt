package dev.sz.aid

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
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
        val prompt = LlmClient.Prompt(
            systemMessage = "You are helpful.",
            userMessage = null,
            code = "abc"
        )
        client.chat(prompt) shouldBe responseMessage
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
        val prompt = LlmClient.Prompt(
            systemMessage = "Test",
            userMessage = null,
            code = ""
        )
        assertThrows<IOException> {
            client.chat(prompt)
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
            appendLine("data: {\"id\":\"1\",\"object\":\"chat.completion.chunk\",\"choices\":[{\"index\":0,\"delta\":{\"role\":\"assistant\",\"content\":\"Hel\"},\"finish_reason\":null}]}")
            appendLine()
            appendLine("data: {\"id\":\"1\",\"object\":\"chat.completion.chunk\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\"lo\"},\"finish_reason\":null}]}")
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
                .headers(Headers.Builder()
                    .add("Content-Type", "text/event-stream")
                    .build())
                .message("OK")
                .body(sseBody.toResponseBody("text/event-stream".toMediaType()))
                .build()
        }

        val deltas = mutableListOf<String>()
        val client = LlmClient(config)
        client.chatStream(
            prompt = LlmClient.Prompt("sys", null, "code"),
            onDelta = { deltas.add(it) },
        )
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
        assertThrows<IOException> {
            client.chatStream(
                prompt = LlmClient.Prompt("sys", null, "code"),
                onDelta = {},
            )
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
                .headers(Headers.Builder()
                    .add("Content-Type", "text/event-stream")
                    .build())
                .body(sseBody.toResponseBody("text/event-stream".toMediaType()))
                .build()
        }

        val client = LlmClient(config)
        assertThrows<IllegalStateException> {
            client.chatStream(
                prompt = LlmClient.Prompt("sys", null, "code"),
                onDelta = {},
            )
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
                .headers(Headers.Builder()
                    .add("Content-Type", "text/plain")
                    .build())
                .body("body".toResponseBody("text/plain".toMediaType()))
                .build()
        }

        val client = LlmClient(config)
        assertThrows<IOException> {
            client.chatStream(
                prompt = LlmClient.Prompt("sys", null, "code"),
                onDelta = {},
            )
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
            appendLine("data: {\"id\":\"1\",\"choices\":[]}")
            appendLine()
            appendLine("data: {\"id\":\"1\",\"choices\":[{\"index\":0,\"delta\":{\"content\":null}}]}")
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
                .headers(Headers.Builder()
                    .add("Content-Type", "text/event-stream")
                    .build())
                .body(sseBody.toResponseBody("text/event-stream".toMediaType()))
                .build()
        }

        val deltas = mutableListOf<String>()
        val client = LlmClient(config)
        client.chatStream(
            prompt = LlmClient.Prompt("sys", null, "code"),
            onDelta = { deltas.add(it) },
        )
        deltas shouldBe emptyList()
    }
}
