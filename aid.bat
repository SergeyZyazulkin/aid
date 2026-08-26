@echo off
rem convenience wrapper; delegates to the Gradle-installed binary.
rem Usage: aid.bat [options]   (after running ./gradlew instDist)

set "SCRIPT_DIR=%~dp0"
set "BIN=%SCRIPT_DIR%build\install\aid\bin\aid.bat"

if not exist "%BIN%" (
    echo aid: binary not found at %BIN% >&2
    echo Run "./gradlew instDist" first. >&2
    exit /b 1
)

"%BIN%" %*
exit /b %ERRORLEVEL%
