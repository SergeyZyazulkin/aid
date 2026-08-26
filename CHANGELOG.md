# Changelog

All notable changes to this project will be documented in this file.

## [1.0.0] - 2026-08-07
### Added
- Implemented the `aid` CLI: a lightweight git code assistant that collects repository code (diff or full tracked tree) and sends it to an OpenAI-compatible LLM endpoint for review or custom prompts.

## [1.1.0] - 2026-08-07
### Added
- Added `--debug-code-content` flag to print collected source code to stderr for debugging.

## [1.2.0] - 2026-08-07
### Added
- Added `-C`/`--commit` option to specify an arbitrary commit reference (SHA, `HEAD~N`, branch, tag) for diff-based analysis.

## [1.2.1] - 2026-08-10
### Changed
- Added test suite (JUnit 5, Kotest, MockK, MockWebServer).

## [1.3.0] - 2026-08-13
### Added
- Added `-S`/`--sources` option (repeatable) and `sources` scope to analyze specific files or directories instead of the full diff or entire tree.

## [1.4.0] - 2026-08-15
### Added
- Added `--dry-run` flag to print the full LLM request JSON and exit without calling the API.

## [1.5.0] - 2026-08-17
### Added
- Added `--lang` option to configure LLM output language (`en` default, `ru`).

## [1.6.0] - 2026-08-18
### Added
- Added `--progress` flag to log timestamped execution steps to stderr.

## [1.7.0] - 2026-08-19
### Added
- Added `-f`/`--filter` option (repeatable) to include only files matching one of glob patterns against their git-relative path; applies to `all` and `sources` scopes.

## [1.7.1] - 2026-08-20
### Fixed
- Fail fast with a clear error message when no code is collected (empty diff, or `--filter` matches no files), instead of sending no code to the LLM.

## [1.8.0] - 2026-08-22
### Added
- Extended `-S`/`--sources` to accept full Git pathspec syntax (glob patterns, `:(exclude)`, `:(top)`, etc.) and made it applicable to the `diff` scope as well.

## [1.9.0] - 2026-08-23
### Added
- Added `--stream` flag to enable SSE-based streaming: partial LLM response tokens are written to stdout as they arrive.

## [1.9.1] - 2026-08-24
### Changed
- Renamed the final summary line from "Completed in Ns" to "Execution time: Ns"; now it is emitted even when execution fails.

## [1.10.0] - 2026-08-26
### Added
- Added `aid` / `aid.bat` wrapper scripts at the project root to simplify invocation (run `./aid` instead of `build/install/aid/bin/aid`).
