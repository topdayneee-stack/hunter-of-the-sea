param()
$scriptDir = $PSScriptRoot
if (-not $scriptDir) { $scriptDir = (Get-Location).Path }

# Dung cac process cu neu co
Get-Process FastServer, cloudflared -ErrorAction SilentlyContinue | Stop-Process -Force

$serverProc = Start-Process -FilePath "$scriptDir\FastServer.exe" -ArgumentList "3000" -PassThru -WindowStyle Hidden
$logFile = "$scriptDir\tunnel.log"
if (Test-Path $logFile) { Remove-Item $logFile -Force }

$tunnelProc = Start-Process -FilePath "$scriptDir\cloudflared.exe" -ArgumentList "tunnel --url http://127.0.0.1:3000 --logfile `"$logFile`"" -PassThru -WindowStyle Hidden

$tunnelUrl = ""
for ($i = 0; $i -lt 30; $i++) {
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
    Write-Output "TUNNEL_URL: $tunnelUrl"
} else {
    Write-Output "TUNNEL_FAILED"
}
