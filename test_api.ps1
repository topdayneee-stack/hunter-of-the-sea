param()
$scriptDir = $PSScriptRoot
if (-not $scriptDir) { $scriptDir = (Get-Location).Path }

$server = Start-Process -FilePath "$scriptDir\FastServer.exe" -ArgumentList "3000" -PassThru -WindowStyle Hidden
Start-Sleep -Milliseconds 600

$cRes = curl.exe -s -X POST -H "Content-Type: application/json" -d "{""username"":""PlayerA"",""characterId"":2}" http://127.0.0.1:3000/api/rooms/create
Write-Host "CREATE: $cRes"

if ($cRes -match '"roomCode":"(\d{4})"') {
    $code = $matches[1]
    Write-Host "GOT CODE: $code"
    $jRes = curl.exe -s -X POST -H "Content-Type: application/json" -d "{""roomCode"":""$code"",""username"":""PlayerB"",""characterId"":1}" http://127.0.0.1:3000/api/rooms/join
    Write-Host "JOIN: $jRes"

    $getRes = curl.exe -s http://127.0.0.1:3000/api/rooms/$code
    Write-Host "GET: $getRes"
}

Stop-Process -Id $server.Id -Force
