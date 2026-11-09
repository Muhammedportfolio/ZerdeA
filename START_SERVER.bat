@echo off
setlocal
cd /d "%~dp0"
set "EDUTEST_JAVA=java"
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "EDUTEST_JAVA=%JAVA_HOME%\bin\java.exe"
if exist "%~dp0runtime\bin\java.exe" set "EDUTEST_JAVA=%~dp0runtime\bin\java.exe"
"%EDUTEST_JAVA%" -version
if errorlevel 1 (
 echo Install Java 17 x64, then run this file again. See START_HERE.txt.
 pause
 exit /b 1
)
echo Server-only mode. Close this window to stop the server.
"%EDUTEST_JAVA%" -Dedutest.host=0.0.0.0 -cp "EduTestSuite.jar;lib/*" Server
if errorlevel 1 pause
