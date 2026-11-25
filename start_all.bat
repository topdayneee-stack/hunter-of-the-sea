@echo off
cd /d "%~dp0"
title Hunter of the Sea - Khoi Dong He Thong

echo =====================================================================
echo          HUNTER OF THE SEA - THUY THAN DAI DUONG
echo    TRINH KHOI DONG DONG THOI BACKEND API VA GAME CLIENT
echo =====================================================================
echo.
echo [1] KHOI DONG TOAN DIEN (Bat Backend 8080 + Game Client + Dashboard)
echo [2] Chi chay rieng BACKEND SERVER (Port 8080)
echo [3] Chi chay rieng GAME CLIENT JAVA (HunterOfTheSea.jar)
echo [4] Mo Dashboard Kiem Thu API (test_dashboard.html)
echo [5] Chay WEB CLIENT (Hunter of the Sea Web tren localhost:3000)
echo [6] Thoat
echo.
set /p opt="Vui long chon thao tac (1-6, mac dinh [5]): "

if "%opt%"=="" set opt=5
if "%opt%"=="1" goto start_everything
if "%opt%"=="2" goto start_backend_only
if "%opt%"=="3" goto start_client_only
if "%opt%"=="4" goto open_dashboard
if "%opt%"=="5" goto start_web_client
if "%opt%"=="6" goto end

:start_everything
echo.
echo 1. Dang khoi dong Backend Server o cua so rieng...
start "Hunter Backend Server" run_backend.bat

echo 2. Dang cho 5 giay de Server len song tai cong 8080...
timeout /t 5 /nobreak >nul

echo 3. Dang mo API Test Dashboard tren trinh duyet...
if exist "Backend\test_dashboard.html" (
    start "" "%~dp0Backend\test_dashboard.html"
)

echo 4. Dang khoi dong Game Client (Hunter of the Sea)...
start "Hunter Client" run.bat
goto end

:start_backend_only
start "Hunter Backend Server" run_backend.bat
goto end

:start_client_only
start "Hunter Client" run.bat
goto end

:open_dashboard
if exist "Backend\test_dashboard.html" (
    start "" "%~dp0Backend\test_dashboard.html"
)
goto end

:start_web_client
call run_web.bat
goto end

:end
