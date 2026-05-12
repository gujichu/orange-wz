@echo off
setlocal EnableExtensions
cd /d "%~dp0"

REM NOTE: This file must stay ASCII-only. UTF-8 + Chinese REM breaks under cmd.exe
REM (CP936): lines get misparsed and "start"/javaw never run correctly.
REM Optional: set ORZ_XMX=8g to cap heap; set ORZ_USE_JAVA=1 to use java.exe (see errors in console).

set "JAVA_EXE=%~dp0jre\bin\java.exe"
set "JAVAW_EXE=%~dp0jre\bin\javaw.exe"
set "JAR_FILE=%~dp0OrzRepacker.jar"

if not exist "%JAVAW_EXE%" (
  echo [OrzRepacker] Missing JRE: "%JAVAW_EXE%"
  pause
  exit /b 1
)
if not exist "%JAR_FILE%" (
  echo [OrzRepacker] Missing JAR: "%JAR_FILE%"
  pause
  exit /b 1
)

set "ARCH64=0"
"%JAVA_EXE%" -XshowSettings:properties -version 2>&1 | findstr /C:"sun.arch.data.model = 64" >nul
if not errorlevel 1 set "ARCH64=1"

if "%ARCH64%"=="1" (
  if defined ORZ_XMX (
    set "JVM_HEAP=-Xms512m -Xmx%ORZ_XMX%"
  ) else (
    set "JVM_HEAP=-Xms512m -Xmx16g"
  )
) else (
  set "JVM_HEAP=-Xms256m -Xmx1536m"
)

if /i "%ORZ_USE_JAVA%"=="1" (
  set "LAUNCHER=%JAVA_EXE%"
) else (
  set "LAUNCHER=%JAVAW_EXE%"
)

start "" "%LAUNCHER%" %JVM_HEAP% -jar "%JAR_FILE%"

endlocal
exit /b 0
