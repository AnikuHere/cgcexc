@echo off
setlocal
where gradle >NUL 2>NUL
if %ERRORLEVEL%==0 (
  gradle %*
  exit /b %ERRORLEVEL%
)
set "GRADLE_VERSION=8.9"
set "BASE=%USERPROFILE%\.gradle\wrapper\dists\gradle-%GRADLE_VERSION%-bin"
set "ZIP=%BASE%\gradle-%GRADLE_VERSION%-bin.zip"
set "HOME=%BASE%\gradle-%GRADLE_VERSION%"
if not exist "%HOME%\bin\gradle.bat" (
  if not exist "%BASE%" mkdir "%BASE%"
  if not exist "%ZIP%" (
    powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-WebRequest -Uri 'https://services.gradle.org/distributions/gradle-%GRADLE_VERSION%-bin.zip' -OutFile '%ZIP%'"
  )
  powershell -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -Force '%ZIP%' '%BASE%'"
)
call "%HOME%\bin\gradle.bat" %*
