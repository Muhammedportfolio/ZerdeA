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
echo Open http://COMPUTER_IP:8080 on phones in the same Wi-Fi.
echo Find COMPUTER_IP with ipconfig. Allow Java on your private network.
"%EDUTEST_JAVA%" -Dedutest.host=0.0.0.0 --module-path "windows-lib" --add-modules javafx.controls,javafx.web -cp "EduTestSuite.jar;lib/*" SafeLaunch
if errorlevel 1 pause
