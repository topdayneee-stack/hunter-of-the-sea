@echo off
cd /d "%~dp0"
title Hunter of the Sea - Khoi Chay Game

echo =====================================================================
echo                HUNTER OF THE SEA - WINDOWS DESKTOP
echo =====================================================================
echo.
echo Dang khoi dong ung dung game...

if exist "HunterOfTheSea.exe" (
    start "" "HunterOfTheSea.exe"
    exit /b
)

:: Neu chua bien dich HunterOfTheSea.exe, khoi chay qua PowerShell Fallback
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
    "$port = 28765; $root = (Get-Location).Path; " ^
    "$listener = New-Object System.Net.HttpListener; $listener.Prefixes.Add(\"http://127.0.0.1:$port/\"); $listener.Start(); " ^
    "Start-Process \"msedge.exe\" -ArgumentList \"--app=http://127.0.0.1:$port/index.html --window-size=1280,760\"; " ^
    "while ($listener.IsListening) { $ctx = $listener.GetContext(); " ^
    "[System.Threading.ThreadPool]::QueueUserWorkItem({ param($c) " ^
    "$req = $c.Request; $res = $c.Response; " ^
    "$raw = [System.Uri]::UnescapeDataString($req.Url.LocalPath).TrimStart('/'); if (-not $raw) { $raw = 'index.html' }; " ^
    "$p = Join-Path $using:root $raw; if (Test-Path $p) { " ^
    "$bytes = [System.IO.File]::ReadAllBytes($p); $res.ContentLength64 = $bytes.Length; $res.OutputStream.Write($bytes, 0, $bytes.Length) } " ^
    "else { $res.StatusCode = 404 }; $res.Close() }, $ctx) | Out-Null }"
