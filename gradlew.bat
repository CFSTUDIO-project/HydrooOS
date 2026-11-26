@echo off
where gradle >nul 2>nul
if %ERRORLEVEL% EQU 0 (
  gradle %*
) else (
  echo Gradle wrapper has not been generated yet.
  exit /b 1
)
