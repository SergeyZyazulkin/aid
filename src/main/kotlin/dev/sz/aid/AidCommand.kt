package dev.sz.aid

import picocli.CommandLine
import java.nio.file.Path
import java.nio.file.Paths

@CommandLine.Command(
    name = "aid",
    description = ["A lightweight CLI code assistant to review git repositories on demand"]
)
class AidCommand(private val environment: Environment = SystemEnvironment) : Runnable {

    @CommandLine.Option(
        names = ["-d", "--dir"],
        required = true,
        description = ["Target project directory to analyze"],
    )
    var projectDir: Path = Paths.get(".")
        private set

    @CommandLine.Option(
        converter = [CodeScope.Converter::class],
        names = ["-s", "--scope"],
        required = false,
        defaultValue = "diff",
        description = [
            "Scope of code to analyze: diff (default), all,",
            "sources (specified with --sources option)",
        ],
    )
    var scope = CodeScope.DIFF
        private set

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
    var commit = "HEAD"
        private set

    @CommandLine.Option(
        names = ["-S", "--sources"],
        required = false,
        description = [
            "Git pathspecs limiting the analysis scope",
            "(repeatable). Applies to 'diff' and 'sources'",
            "scopes. Supports plain paths, glob patterns and Git",
            "magic prefixes (e.g. ':(exclude)build/', '*.kt',",
            "':(top)src/').",
        ],
    )
    var sources = emptyList<String>()
        private set

    @CommandLine.Option(
        names = ["-f", "--filter"],
        required = false,
        description = [
            "Glob pattern to filter included files by relative",
            "path (repeatable); matched against the git-relative",
            "path, e.g. --filter '**.java' --filter 'src/*.kt';",
            "applies to 'all' and 'sources' scopes only",
        ],
    )
    var fileFilters = emptyList<String>()
        private set

    @CommandLine.Option(
        names = ["-u", "--url"],
        required = false,
        defaultValue = "http://127.0.0.1:11434",
        description = ["LLM server url (default: http://127.0.0.1:11434)"],
    )
    var url = "http://127.0.0.1:11434"
        private set

    @CommandLine.Option(
        names = ["-k", "--api-key"],
        required = false,
        description = [
            "LLM server API key (falls back to",
            "AID_API_KEY env var)",
        ],
    )
    var apiKey: String? = null
        private set

    @CommandLine.Option(
        names = ["-m", "--model"],
        required = true,
        description = ["LLM model to use"],
    )
    var model = ""
        private set

    @CommandLine.Option(
        names = ["-c", "--connect-timeout"],
        required = false,
        defaultValue = "10",
        description = ["LLM connect timeout in seconds (default: 10)"],
    )
    var connectTimeoutSec = 10L
        private set

    @CommandLine.Option(
        names = ["-r", "--read-timeout"],
        required = false,
        defaultValue = "300",
        description = ["LLM read timeout in seconds (default: 300)"],
    )
    var readTimeoutSec = 300L
        private set

    @CommandLine.Option(
        names = ["-p", "--prompt"],
        required = false,
        description = ["Path to a custom code-related prompt file"],
    )
    var promptPath: String? = null
        private set

    @CommandLine.Option(
        names = ["-t", "--force-thinking"],
        required = false,
        defaultValue = "false",
        description = [
            "Force the model's thinking mode (Ollama-specific);",
            "may not work with generic OpenAI APIs",
        ],
    )
    var forceThinking = false
        private set

    @CommandLine.Option(
        names = ["-l", "--code-limit"],
        required = false,
        defaultValue = "256000",
        description = ["Max allowed code length in chars"],
    )
    var codeLimit = 256000
        private set

    @CommandLine.Option(
        names = ["--debug-code-content"],
        required = false,
        defaultValue = "false",
        description = ["Print collected code to stderr"],
    )
    var debugCodeContent = false
        private set

    @CommandLine.Option(
        names = ["--dry-run"],
        required = false,
        defaultValue = "false",
        description = [
            "Print the LLM request JSON and exit",
            "without calling the LLM",
        ]
    )
    var dryRun: Boolean = false
        private set

    @CommandLine.Option(
        names = ["--lang"],
        required = false,
        defaultValue = "en",
        converter = [OutputLanguage.Converter::class],
        description = ["Output language: en (default), ru"],
    )
    var lang = OutputLanguage.EN
        private set

    @CommandLine.Option(
        names = ["--progress"],
        required = false,
        defaultValue = "false",
        description = ["Log progress to stderr"],
    )
    var progress = false
        private set

    @CommandLine.Option(
        names = ["--stream"],
        required = false,
        defaultValue = "false",
        description = [
            "Enable streaming mode: write partial results to",
            "stdout as they are received from the LLM",
        ],
    )
    var stream = false
        private set

    override fun run() {
        val progressLogger = ProgressLogger(enabled = progress)
        val resolvedApiKey = apiKey ?: environment["AID_API_KEY"]
        val config = LlmClient.Config(url, model, connectTimeoutSec, readTimeoutSec, forceThinking, resolvedApiKey)

        progressLogger.log("Collecting code...")
        val code: String = CodeProvider(projectDir, codeLimit).collectCode()
        require(code.isNotBlank()) { "No code collected (result is blank)" }
        if (debugCodeContent) logCode(code)

        progressLogger.log("Building prompt...")
        val prompt: LlmClient.Prompt = buildPrompt(code)

        val client = LlmClient(config)
        when {
            dryRun -> client.renderDryRun(prompt, progressLogger)
            stream -> client.renderStreamChat(prompt, progressLogger)
            else -> client.renderChat(prompt, progressLogger)
        }

        progressLogger.logCompletion()
    }

    private fun CodeProvider.collectCode(): String = when (scope) {
        CodeScope.DIFF -> collectDiff(commit, sources)
        CodeScope.ALL -> collectAll(fileFilters)
        CodeScope.SOURCES -> {
            require(sources.isNotEmpty()) { "At least one --sources option must be specified" }
            collectFiles(sources, fileFilters)
        }
    }

    private fun buildPrompt(code: String): LlmClient.Prompt {
        val baseSystemMessage: String = if (promptPath != null) Prompts.custom else Prompts.review
        val langDirective = lang.directiveResource?.let { Prompts.readSystemDirective(it) }
        val systemMessage: String = langDirective?.let { "$baseSystemMessage\n\n$it" } ?: baseSystemMessage
        val userMessage: String? = promptPath?.let { Prompts.readUserPrompt(it) }
        return LlmClient.Prompt(systemMessage, userMessage, code)
    }

    private fun logCode(code: String) {
        System.err.println("[CODE] ${code.length} chars:")
        System.err.println(code)
        System.err.println("------")
    }

    private fun LlmClient.renderDryRun(prompt: LlmClient.Prompt, progressLogger: ProgressLogger) {
        val result = renderDryRun(prompt, stream)
        progressLogger.log("Printing result...")
        println(result)
    }

    private fun LlmClient.renderStreamChat(prompt: LlmClient.Prompt, progressLogger: ProgressLogger) {
        progressLogger.log("Sending streaming request to LLM...")
        var printingStarted = false
        var waitLogger: AutoCloseable = progressLogger.startWaitLogging(intervalSec = 10)
        try {
            chatStream(
                prompt,
                onDelta = { delta ->
                    if (!printingStarted) {
                        waitLogger.close()
                        printingStarted = true
                        progressLogger.log("Printing result...")
                        waitLogger = progressLogger.startWaitLogging(intervalSec = 10)
                    }
                    print(delta)
                    System.out.flush() // explicit flushing
                },
            )
        } finally {
            waitLogger.close()
        }
        println() // trailing newline after streamed output
    }

    private fun LlmClient.renderChat(prompt: LlmClient.Prompt, progressLogger: ProgressLogger) {
        progressLogger.log("Sending request to LLM...")
        val result = progressLogger.startWaitLogging(intervalSec = 10).use {
            chat(prompt)
        }
        progressLogger.log("Printing result...")
        println(result)
    }
}
