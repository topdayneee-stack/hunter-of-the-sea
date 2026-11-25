@echo off
cd /d "%~dp0"
title Hunter of the Sea - Local Web Server

echo =====================================================================
echo          HUNTER OF THE SEA - WEB CLIENT LOCALHOST SERVER
echo =====================================================================
echo.
echo Dang khoi dong Web Server tai cong 3000...
echo Trinh duyet se tu dong mo http://localhost:3000
echo.
echo (Nhan Ctrl+C hoac dong cua so nay de dung server)
echo =====================================================================
echo.

start "" "http://localhost:3000"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0server.ps1" -Port 3000
pause
