# =========================================================================
# HUNTER OF THE SEA - TU DONG DONG GOI BAN TAI VE WINDOWS (DESKTOP)
# =========================================================================

$ErrorActionPreference = "Stop"
$scriptDir = $PSScriptRoot
if (-not $scriptDir) { $scriptDir = (Get-Location).Path }

Write-Host "=========================================================================" -ForegroundColor Cyan
Write-Host "   HUNTER OF THE SEA - BAT DAU DONG GOI GOI WINDOWS DESKTOP (v2.6.0)" -ForegroundColor Green
Write-Host "=========================================================================" -ForegroundColor Cyan
Write-Host ""

# 1. Bien dich HunterOfTheSea.exe bang C# Compiler (csc.exe co san tren moi may Windows)
Write-Host ">> [1/4] Bien dich bo chay Windows Native (HunterOfTheSea.exe)..." -ForegroundColor Yellow
$cscPath = "C:\Windows\Microsoft.NET\Framework64\v4.0.30319\csc.exe"
if (-not (Test-Path $cscPath)) {
    $cscPath = "C:\Windows\Microsoft.NET\Framework\v4.0.30319\csc.exe"
}

if (Test-Path $cscPath) {
    & $cscPath /nologo /target:winexe /out:"$scriptDir\HunterOfTheSea.exe" /r:System.Windows.Forms.dll /r:System.Drawing.dll "$scriptDir\HunterOfTheSea.cs"
    Write-Host "   + Bien dich thanh cong HunterOfTheSea.exe!" -ForegroundColor Green
} else {
    Write-Host "   ! Khong tim thay csc.exe, su dung file .exe da co hoac file .bat launcher." -ForegroundColor Yellow
}

# 2. Chuan bi thu muc phan phoi (dist)
Write-Host ">> [2/4] Chuan bi thu muc dong goi..." -ForegroundColor Yellow
$distDir = Join-Path $scriptDir "dist"
$targetDir = Join-Path $distDir "HunterOfTheSea-Windows-v2.6.0"

if (Test-Path $targetDir) {
    Remove-Item -Recurse -Force $targetDir
}
New-Item -ItemType Directory -Force -Path $targetDir | Out-Null

# 3. Sao chep cac file can thiet vao goi phan phoi
Write-Host ">> [3/4] Sao chep tai nguyen game va cau hinh..." -ForegroundColor Yellow

# Danh sach file le can chep
$coreFiles = @(
    "HunterOfTheSea.exe",
    "ChoiGame-Desktop.bat",
    "index.html",
    "styles.css"
)

foreach ($f in $coreFiles) {
    $src = Join-Path $scriptDir $f
    if (Test-Path $src) {
        Copy-Item -Path $src -Destination $targetDir -Force
    }
}

# Sao chep thu muc js/
$jsDest = Join-Path $targetDir "js"
New-Item -ItemType Directory -Force -Path $jsDest | Out-Null
Copy-Item -Path (Join-Path $scriptDir "js\*") -Destination $jsDest -Recurse -Force

# Sao chep hinh anh png va jpg o thu muc goc
Get-ChildItem -Path $scriptDir -Filter "*.png" | ForEach-Object {
    Copy-Item $_.FullName -Destination $targetDir -Force
}
Get-ChildItem -Path $scriptDir -Filter "*.jpg" | ForEach-Object {
    Copy-Item $_.FullName -Destination $targetDir -Force
}

# Sao chep thu muc cá neu co
$caDir = Join-Path $scriptDir "cá"
if (Test-Path $caDir) {
    Copy-Item -Path $caDir -Destination $targetDir -Recurse -Force
}

# Tao file huong dan nguoi choi di kem goi
$guideContent = @"
=========================================================================
  HUNTER OF THE SEA - THO SAN BIEN CA (PHIEN BAN WINDOWS DESKTOP v2.6.0)
=========================================================================

1. CACH CHOI:
   - Chi can nhap dup chuot vao file [HunterOfTheSea.exe] (hoac [ChoiGame-Desktop.bat]).
   - Cua so game se mo len ngay lap tuc ma KHONG CAN CAI DAT bat ky phan mem nao!
   - Khong lo bi loi file:/// hay bi chan ket noi.

2. CHOI DOI KHANG (PVP TU XA):
   - Nguoi tao phong: Chon PvP -> Tao Phong -> Copy ma phong (4 so).
   - Nguoi vao phong: Chon PvP -> Nhap ma phong 4 so -> Tham Gia.
   - Ca hai se duoc ket noi vao cung tran dau!

3. THAY DOI DIA CHI SERVER ONLINE:
   - Neu nguoi quan tri da deploy server len Cloud (Render/Railway):
   - Mo file [js/config.js] bang Notepad.
   - Sua dong ONLINE_SERVER_URL thanh link server that cua ban.
   - Luu lai la xong!

Chuc ban co nhung giay phut giai tri vui ve!
"@

Set-Content -Path (Join-Path $targetDir "HUONG_DAN_CHOI_GAME.txt") -Value $guideContent -Encoding UTF8

# 4. Nen thanh file ZIP phan phoi
Write-Host ">> [4/4] Nen file ZIP phan phoi..." -ForegroundColor Yellow
$zipPath = Join-Path $distDir "HunterOfTheSea-Windows-v2.6.0.zip"
if (Test-Path $zipPath) { Remove-Item -Force $zipPath }

Add-Type -AssemblyName System.IO.Compression.FileSystem
[System.IO.Compression.ZipFile]::CreateFromDirectory($targetDir, $zipPath)

$zipSizeMb = [math]::Round((Get-Item $zipPath).Length / 1MB, 2)

Write-Host ""
Write-Host "=========================================================================" -ForegroundColor Green
Write-Host "   DONG GOI THANH CONG! FILE PHAN PHOI SAN SANG:" -ForegroundColor Green
Write-Host "   Thu muc: $targetDir" -ForegroundColor Cyan
Write-Host "   File ZIP: $zipPath ($zipSizeMb MB)" -ForegroundColor Yellow
Write-Host "=========================================================================" -ForegroundColor Green
