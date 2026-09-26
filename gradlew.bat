@echo off
setlocal
set "APP_HOME=%~dp0"
set "GRADLE_VERSION=8.9"
set "DIST_DIR=%APP_HOME%.gradle-dist\gradle-%GRADLE_VERSION%"
set "GRADLE_BIN=%DIST_DIR%\bin\gradle.bat"
set "ZIP_FILE=%APP_HOME%.gradle-dist\gradle-%GRADLE_VERSION%-bin.zip"

if exist "%GRADLE_BIN%" goto RUN

if not exist "%APP_HOME%.gradle-dist" mkdir "%APP_HOME%.gradle-dist"
if not exist "%ZIP_FILE%" (
  powershell -NoProfile -Command "Invoke-WebRequest -UseBasicParsing 'https://services.gradle.org/distributions/gradle-%GRADLE_VERSION%-bin.zip' -OutFile '%ZIP_FILE%'"
)

if not exist "%DIST_DIR%" (
  powershell -NoProfile -Command "Expand-Archive -Force '%ZIP_FILE%' '%APP_HOME%.gradle-dist'"
)

:RUN
call "%GRADLE_BIN%" %*
exit /b %ERRORLEVEL%
