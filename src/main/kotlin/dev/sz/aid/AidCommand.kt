package dev.sz.aid

import picocli.CommandLine
import java.nio.file.Paths

@CommandLine.Command(
    name = "aid",
    description = ["A lightweight CLI code assistant to review git repositories on demand"]
)
class AidCommand : Runnable {

    @CommandLine.Option(
        names = ["-d", "--dir"],
        required = true,
        description = ["Target project directory to analyze"],
    )
    private var projectDir: String = ""

    @CommandLine.Option(
        converter = [CodeScope.Converter::class],
        names = ["-s", "--scope"],
        required = false,
        defaultValue = "diff",
        description = ["Scope of code to analyze: diff (default), all"],
    )
    private var scope: CodeScope = CodeScope.DIFF

    @CommandLine.Option(
        names = ["-u", "--url"],
        required = false,
        defaultValue = "http://127.0.0.1:11434",
        description = ["LLM server url (default: http://127.0.0.1:11434)"],
    )
    private var url: String = "http://127.0.0.1:11434"

    @CommandLine.Option(
        names = ["-k", "--api-key"],
        required = false,
        description = ["LLM server API key (falls back to AID_API_KEY env var)"],
    )
    private var apiKey: String? = null

    @CommandLine.Option(
        names = ["-m", "--model"],
        required = true,
        description = ["LLM model to use"],
    )
    private var model: String = ""

    @CommandLine.Option(
        names = ["-c", "--connect-timeout"],
        required = false,
        defaultValue = "10",
        description = ["LLM connect timeout in seconds (default: 10)"],
    )
    private var connectTimeoutSec: Long = 10

    @CommandLine.Option(
        names = ["-r", "--read-timeout"],
        required = false,
        defaultValue = "300",
        description = ["LLM read timeout in seconds (default: 300)"],
    )
    private var readTimeoutSec: Long = 300

    @CommandLine.Option(
        names = ["-p", "--prompt"],
        required = false,
        description = ["Path to a custom code-related prompt file"],
    )
    private var promptPath: String? = null

    @CommandLine.Option(
        names = ["-t", "--force-thinking"],
        required = false,
        defaultValue = "true",
        description = ["Force the model's thinking mode (Ollama-specific); may not work with generic OpenAI APIs"],
    )
    private var forceThinking: Boolean = true

    @CommandLine.Option(
        names = ["-l", "--code-limit"],
        required = false,
        defaultValue = "256000",
        description = ["Max allowed code length in chars"],
    )
    private var codeLimit: Int = 256000

    override fun run() {
        val resolvedApiKey = apiKey ?: System.getenv("AID_API_KEY")
        val config = LlmClient.Config(url, model, connectTimeoutSec, readTimeoutSec, forceThinking, resolvedApiKey)
        val code: String = CodeProvider(Paths.get(projectDir), codeLimit).collect(scope)

        val prompt = promptPath?.let {
            LlmClient.Prompt(Prompts.custom, Prompts.readUserPrompt(it), code)
        }
            ?: LlmClient.Prompt(Prompts.review, null, code)

        val result: String = LlmClient(config)
            .chat(prompt)

        println(result)
    }
}