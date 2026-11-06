@echo off
rem Local Windows build: JDK 21 and Gradle 8.12 are found or downloaded automatically.
rem Log: build\local-build.log, result: build\local-build.status
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\build-local.ps1"
