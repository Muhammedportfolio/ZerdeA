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
"%EDUTEST_JAVA%" --module-path "windows-lib" --add-modules javafx.controls,javafx.web -cp "EduTestSuite.jar;lib/*" SafeLaunch
if errorlevel 1 pause
