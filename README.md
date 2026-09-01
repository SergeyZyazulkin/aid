# aid

A lightweight CLI code assistant that leverages local Ollama instances (or any OpenAI-compatible API) to review git repositories on demand.

## Features

- **Flexible Git Context**: Analyze uncommitted changes (`diff`), the entire tracked codebase (`all`), or specific source paths/files (`sources`).
- **Scope filters**: Limit scope with Git pathspecs (`--sources`) and glob patterns (`--filter`).
- **OpenAI-Compatible API**: Connects to `/v1/chat/completions`, making it compatible with Ollama, local LLM runners, and cloud APIs.
- **Customizable Prompts**: Use the default PR reviewer or swap it for a custom interaction mode using your own prompt.
- **Streaming mode**: Read partial LLM results in real time with `--stream`.
- **Dry Run & Debugging**: Preview the exact LLM request JSON without sending it (`--dry-run`), or print collected code to stderr (`--debug-code-content`).
- **Configurable output language**: Supports English and Russian.
- **Token usage reporting**: Print LLM token usage to stderr with `--usage`.
- **Configurable diff context**: Control surrounding lines in diff output with `--context-lines` (`-U`).

## Prerequisites

- **Java 25+**
- **Git** (target directory must contain `.git/config`)
- **Ollama** or any OpenAI-compatible API server

## Installation & Usage

### Build
```sh
./gradlew instDist
```

### Show usage
```sh
./aid
```

### Set key (Windows)
```sh
@set AID_API_KEY=<key>
```

### Set key (Linux)
```sh
export AID_API_KEY=<key>
```

### Analyze entire tracked codebase
```sh
./aid -d=<repo_path> -m=<model_name> -s=all -u=<api_url> > <output_path>
```

### Review uncommitted changes vs HEAD
```sh
./aid -d=<repo_path> -m=<model_name> -s=diff -u=<api_url> > <output_path>
```

### Review N last commits
```sh
./aid -d=<repo_path> -m=<model_name> -s=diff -C=HEAD~<N> -u=<api_url> > <output_path>
```

### Custom prompt about entire tracked codebase
```sh
./aid -d=<repo_path> -m=<model_name> -s=all -u=<api_url> -p=<prompt_path> > <output_path>
```

### Stream partial results with progress tracking
```sh
./aid -d=<repo_path> -m=<model_name> -s=all -u=<api_url> --stream --progress > <output_path>
```

### Arguments from files
Store frequently-used option sets in a plain-text file and reference them with the `@` prefix.
Use `@@` to escape the `@` prefix if needed.
BOM in the beginning of a file is ignored.
Each non-blank, non-`#` line is split on whitespace into individual arguments:

```text
# review.args
-d /home/user/project
-m llama3
-s all
--stream
--progress
```

Then invoke:

```sh
./aid @review.args -u http://my-llm:11434 > output.md
```

Multiple `@file` tokens may be mixed with inline options in any order.
`@` tokens inside an arg file are **not** recursively expanded; they are passed through as literal arguments.

## Configuration & Prompts

The tool ships with two built-in system prompts in `src/main/resources/prompts/`:

- **`review.md`** (default): Structured PR reviewer focused on long-term codebase health, blocking issues, and concrete refactoring suggestions.
- **`custom.md`**: Senior engineer interaction mode for direct Q&A and iterative development help.

Also, there is one built-in directive in `src/main/resources/prompts/directives/`:
- **`ru.md`**: Switching output language to Russian.

To use a custom prompt, pass the file path to `--prompt`.

## License

MIT
