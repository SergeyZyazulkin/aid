# aid

A lightweight CLI code assistant that leverages local Ollama instances (or any OpenAI-compatible API) to review git repositories on demand.

## Features

- **Zero-Config Git Context**: Automatically extracts uncommitted diffs or all tracked files based on your `--scope`.
- **OpenAI-Compatible Endpoint**: Connects to `/v1/chat/completions`, making it compatible with Ollama, local LLM runners, and cloud APIs.
- **Customizable Prompts**: Swap the default structured PR reviewer for a custom interaction mode using your own prompts.

## Prerequisites

- **Java 25+**
- **Git** (target directory must contain `.git/config`)
- **Ollama** or compatible API server

## Installation & Usage

### Build
```sh
./gradlew instDist
```

### Usage
```sh
build/install/aid/bin/aid
build/install/aid/bin/aid.bat
```

### Run
```sh
build/install/aid/bin/aid -d="/path/to/repo" -m="model_name"
build/install/aid/bin/aid.bat -d="/path/to/repo" -m="model_name"
```

## Configuration & Prompts

The tool ships with two built-in system prompts in `src/main/resources/prompts/`:

- **`review.md`** (default): Structured PR reviewer focused on long-term codebase health, blocking issues, and concrete refactoring suggestions.
- **`custom.md`**: Senior engineer interaction mode for direct Q&A and iterative development help.

To use a custom prompt, pass the file path to `--prompt`.

## License

MIT
