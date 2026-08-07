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
    private var projectDir = ""

    @CommandLine.Option(
        converter = [CodeScope.Converter::class],
        names = ["-s", "--scope"],
        required = false,
        defaultValue = "diff",
        description = ["Scope of code to analyze: diff (default), all"],
    )
    private var scope = CodeScope.DIFF

    @CommandLine.Option(
        names = ["-C", "--commit"],
        required = false,
        defaultValue = "HEAD",
        description = [
            "Commit to diff with: can be a full SHA, short SHA,",
            "HEAD (default), HEAD~1, a branch name, or a tag",
            "(anything Git can resolve to a commit)",
        ],
    )
    private var commit = "HEAD"

    @CommandLine.Option(
        names = ["-u", "--url"],
        required = false,
        defaultValue = "http://127.0.0.1:11434",
        description = ["LLM server url (default: http://127.0.0.1:11434)"],
    )
    private var url = "http://127.0.0.1:11434"

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
    private var model = ""

    @CommandLine.Option(
        names = ["-c", "--connect-timeout"],
        required = false,
        defaultValue = "10",
        description = ["LLM connect timeout in seconds (default: 10)"],
    )
    private var connectTimeoutSec = 10L

    @CommandLine.Option(
        names = ["-r", "--read-timeout"],
        required = false,
        defaultValue = "300",
        description = ["LLM read timeout in seconds (default: 300)"],
    )
    private var readTimeoutSec = 300L

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
    private var forceThinking = true

    @CommandLine.Option(
        names = ["-l", "--code-limit"],
        required = false,
        defaultValue = "256000",
        description = ["Max allowed code length in chars"],
    )
    private var codeLimit = 256000

    @CommandLine.Option(
        names = ["--debug-code-content"],
        required = false,
        defaultValue = "false",
        description = ["Print collected code to stderr"],
    )
    private var debugCodeContent = false

    override fun run() {
        val resolvedApiKey = apiKey ?: System.getenv("AID_API_KEY")
        val config = LlmClient.Config(url, model, connectTimeoutSec, readTimeoutSec, forceThinking, resolvedApiKey)
        val codeProvider = CodeProvider(Paths.get(projectDir), codeLimit)

        val code: String = when (scope) {
            CodeScope.DIFF -> codeProvider.collectDiff(commit)
            CodeScope.ALL -> codeProvider.collectAll()
        }
        if (debugCodeContent) logCode(code)

        val prompt = promptPath?.let {
            LlmClient.Prompt(Prompts.custom, Prompts.readUserPrompt(it), code)
        }
            ?: LlmClient.Prompt(Prompts.review, null, code)

        val result: String = LlmClient(config)
            .chat(prompt)

        println(result)
    }

    private fun logCode(code: String) {
        System.err.println("[CODE] ${code.length} chars:")
        System.err.println(code)
        System.err.println("------")
    }
}
