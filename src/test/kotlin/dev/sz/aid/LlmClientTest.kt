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
}