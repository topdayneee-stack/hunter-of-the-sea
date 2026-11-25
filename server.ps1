param (
    [int]$Port = 3000
)

$root = $PSScriptRoot
if (-not $root) { $root = (Get-Location).Path }

$listener = New-Object System.Net.HttpListener
$prefix = "http://localhost:$Port/"
$listener.Prefixes.Add($prefix)

try {
    $listener.Start()
} catch {
    Write-Host "[CANH BAO] Khong the mo $prefix. Dang thu cong 5000..." -ForegroundColor Yellow
    $Port = 5000
    $prefix = "http://localhost:$Port/"
    $listener = New-Object System.Net.HttpListener
    $listener.Prefixes.Add($prefix)
    $listener.Start()
}

Write-Host "=========================================================" -ForegroundColor Cyan
Write-Host "  HUNTER OF THE SEA - WEB SERVER DANG CHAY" -ForegroundColor Green
Write-Host "  Dia chi: $prefix" -ForegroundColor Yellow
Write-Host "  Thu muc: $root" -ForegroundColor Gray
Write-Host "  Nhan Ctrl + C de dung server." -ForegroundColor Gray
Write-Host "=========================================================" -ForegroundColor Cyan

while ($listener.IsListening) {
    try {
        $context = $listener.GetContext()
        $state = @{ Ctx = $context; Root = $root }
        [System.Threading.ThreadPool]::QueueUserWorkItem({
            param($s)
            $ctx = $s.Ctx
            $baseDir = $s.Root
            $req = $ctx.Request
            $res = $ctx.Response
            
            # CORS Headers cho moi yeu cau
            $res.Headers.Add("Access-Control-Allow-Origin", "*")
            $res.Headers.Add("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
            $res.Headers.Add("Access-Control-Allow-Headers", "Content-Type")

            if ($req.HttpMethod -eq "OPTIONS") {
                $res.StatusCode = 200
                $res.OutputStream.Close()
                return
            }

            $rawPath = [System.Uri]::UnescapeDataString($req.Url.LocalPath).TrimStart('/')
            if ([string]::IsNullOrWhiteSpace($rawPath)) {
                $rawPath = "index.html"
            }

            $filePath = Join-Path $baseDir $rawPath

            if (Test-Path $filePath -PathType Leaf) {
                $ext = [System.IO.Path]::GetExtension($filePath).ToLower()
                $mime = "application/octet-stream"
                switch ($ext) {
                    ".html" { $mime = "text/html; charset=utf-8" }
                    ".htm"  { $mime = "text/html; charset=utf-8" }
                    ".css"  { $mime = "text/css; charset=utf-8" }
                    ".js"   { $mime = "application/javascript; charset=utf-8" }
                    ".json" { $mime = "application/json; charset=utf-8" }
                    ".png"  { $mime = "image/png" }
                    ".jpg"  { $mime = "image/jpeg" }
                    ".jpeg" { $mime = "image/jpeg" }
                    ".gif"  { $mime = "image/gif" }
                    ".svg"  { $mime = "image/svg+xml" }
                    ".ico"  { $mime = "image/x-icon" }
                    ".mp3"  { $mime = "audio/mpeg" }
                    ".wav"  { $mime = "audio/wav" }
                }
                $res.ContentType = $mime
                try {
                    $bytes = [System.IO.File]::ReadAllBytes($filePath)
                    $res.ContentLength64 = $bytes.Length
                    $res.OutputStream.Write($bytes, 0, $bytes.Length)
                } catch {}
            } else {
                $res.StatusCode = 404
                $msg = [System.Text.Encoding]::UTF8.GetBytes("404 Not Found: $rawPath")
                $res.OutputStream.Write($msg, 0, $msg.Length)
            }
            try { $res.OutputStream.Close() } catch {}
        }, $state) | Out-Null
    } catch {
        # Neu co loi tam thoi thi tiep tuc lang nghe, khong break ngay lap tuc
        Start-Sleep -Milliseconds 50
    }
}
