@echo off
setlocal
set "SCRIPT=%~dp0START_SUPABASE.ps1"
if not exist "%SCRIPT%" (
  echo START_SUPABASE.ps1 was not found beside this launcher.
  pause
  exit /b 1
)
powershell.exe -NoProfile -ExecutionPolicy Bypass -NoExit -File "%SCRIPT%"
