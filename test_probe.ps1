param()
$p = Start-Process -FilePath "$PSScriptRoot\FastServer.exe" -ArgumentList "3000" -PassThru
Start-Sleep -Milliseconds 800
$out = curl.exe -sI "http://127.0.0.1:3000/index.html"
Stop-Process -Id $p.Id -Force
$out
