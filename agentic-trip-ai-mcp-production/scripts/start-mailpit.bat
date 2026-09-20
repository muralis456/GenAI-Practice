@echo off
setlocal
set "MAILPIT_EXE=%~dp0..\mailpit-windows-amd64\mailpit.exe"
if not exist "%MAILPIT_EXE%" set "MAILPIT_EXE=C:\softwares\mailpit-windows-amd64\mailpit.exe"
if not exist "%MAILPIT_EXE%" (
  echo Mailpit executable not found.
  echo Set MAILPIT_EXE to the full path of mailpit.exe.
  exit /b 1
)
start "Mailpit" "%MAILPIT_EXE%"
echo Mailpit started. SMTP: localhost:1025  UI: http://localhost:8025
endlocal
