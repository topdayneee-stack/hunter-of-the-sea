param()
$scriptDir = $PSScriptRoot
if (-not $scriptDir) { $scriptDir = (Get-Location).Path }

Write-Host "=========================================================================" -ForegroundColor Cyan
Write-Host "       HUNTER OF THE SEA - DANG KHOI CHAY SERVER VA DUONG HAM PVP" -ForegroundColor Green
Write-Host "=========================================================================" -ForegroundColor Cyan
Write-Host ""

# 1. Don dep cac process cu
Get-Process FastServer, cloudflared -ErrorAction SilentlyContinue | Stop-Process -Force

# 2. Khoi dong Web & API Server
$serverProc = Start-Process -FilePath "$scriptDir\FastServer.exe" -ArgumentList "3000" -PassThru -WindowStyle Hidden
Start-Sleep -Milliseconds 700

# 3. Khoi dong Cloudflare Tunnel
$logFile = "$scriptDir\tunnel.log"
if (Test-Path $logFile) { Remove-Item $logFile -Force }

$tunnelProc = Start-Process -FilePath "$scriptDir\cloudflared.exe" -ArgumentList "tunnel --url http://127.0.0.1:3000 --logfile `"$logFile`"" -PassThru -WindowStyle Hidden

Write-Host ">> Dang ket noi mang luoi Cloudflare de lay duong link..." -ForegroundColor Yellow

$tunnelUrl = ""
for ($i = 0; $i -lt 35; $i++) {
    Start-Sleep -Seconds 1
    if (Test-Path $logFile) {
        $content = Get-Content $logFile -Raw -ErrorAction SilentlyContinue
        if ($content -match '(https://[a-zA-Z0-9-]+\.trycloudflare\.com)') {
            $tunnelUrl = $matches[1]
            break
        }
    }
}

if ($tunnelUrl) {
    Set-Content -Path "$scriptDir\public_url.txt" -Value $tunnelUrl
    try { Set-Clipboard -Value $tunnelUrl } catch {}

    Write-Host ""
    Write-Host "=========================================================================" -ForegroundColor Green
    Write-Host "   DUONG LINK PVP CONG CONG (HTTPS) CHO BAN VA BAN BE:" -ForegroundColor Yellow
    Write-Host "   $tunnelUrl" -ForegroundColor Cyan
    Write-Host "=========================================================================" -ForegroundColor Green
    Write-Host ""
    Write-Host " [DA TU DONG COPY VAO CLIPBOARD (Ctrl + V)]" -ForegroundColor Green
    Write-Host " QUY TAC KET NOI:" -ForegroundColor Yellow
    Write-Host "   - CA BAN VA BAN BE PHAI CUNG MO DUONG LINK TREN DE VAO GAME." -ForegroundColor White
    Write-Host "   - Khong tao phong tu file:/// roi dung link tunnel de vao vi 2 noi doc lap voi nhau." -ForegroundColor Gray
    Write-Host ""
    Write-Host " LUU Y: GIU NGUYEN CUA SO TERMINAL NAY TRONG SUOT QUA TRINH CHOI." -ForegroundColor Yellow
    Write-Host " (Khi tat cua so nay, server va duong ham se tu dong dung de tiet kiem pin/RAM)." -ForegroundColor Gray
    Write-Host ""
    Write-Host "Nhan phim [Enter] hoac [Ctrl + C] de tat server khi choi xong..." -ForegroundColor DarkGray

    try {
        [Console]::ReadLine()
    } catch {
        while ($true) { Start-Sleep -Seconds 10 }
    }
} else {
    Write-Host "[LOI] Khong the lay duong link Cloudflare. Hay kiem tra ket noi Internet!" -ForegroundColor Red
    Start-Sleep -Seconds 5
}

Stop-Process -Id $serverProc.Id -Force -ErrorAction SilentlyContinue
Stop-Process -Id $tunnelProc.Id -Force -ErrorAction SilentlyContinue
Write-Host ">> Da tat server va duong ham an toan." -ForegroundColor Yellow
