@echo off
cd /d "%~dp0"
title Hunter of the Sea - Backend Server (Port 8080)

echo =====================================================================
echo          HUNTER OF THE SEA - BACKEND RESTFUL API SERVER
echo =====================================================================

:: 1. Kiem tra va giai phong cong 8080 neu bi ket tien trinh cu
echo Dang kiem tra cong 8080...
for /f "tokens=5" %%a in ('netstat -ano ^| findstr :8080') do (
    echo Phat hien tien trinh cu %%a dang chiem cong 8080, dang giai phong...
    taskkill /f /pid %%a >nul 2>&1
)

:: 2. Kiem tra xem may da cai Java chua
where java >nul 2>nul
if %ERRORLEVEL% NEQ 0 goto no_java

:: 3. Kiem tra xem co lenh mvn hay khong
cd Backend
where mvn >nul 2>&1
if %ERRORLEVEL% EQU 0 goto run_with_mvn
goto run_with_standalone

:run_with_mvn
echo Phat hien Apache Maven tren may tinh!
echo Dang khoi dong Backend Spring Boot...
:: Kiem tra cong 1433 (SQL Server)
powershell -Command "Test-NetConnection -Port 1433 -ComputerName localhost -WarningAction SilentlyContinue | Select-Object -ExpandProperty TcpTestSucceeded" 2>nul | findstr "True" >nul
if %ERRORLEVEL% EQU 0 goto mvn_mssql
goto mvn_h2

:mvn_mssql
echo Phat hien SQL Server dang chay o cong 1433. Khoi dong profile SQL Server...
mvn spring-boot:run
goto end

:mvn_h2
echo Khong tim thay SQL Server o cong 1433. Tu dong khoi dong profile H2 In-Memory...
mvn spring-boot:run -Dspring-boot.run.profiles=h2
goto end

:run_with_standalone
echo Dang su dung Embedded Standalone Server (Java Built-in, 0-Dependency)...
if not exist "target\classes\com\game\hunter\StandaloneServer.class" goto compile_standalone
goto start_standalone

:compile_standalone
echo Dang bien dich StandaloneServer.java...
if not exist "target\classes" mkdir "target\classes"
javac --release 8 -Xlint:-options -encoding UTF-8 -d target\classes src\main\java\com\game\hunter\StandaloneServer.java
if %ERRORLEVEL% NEQ 0 goto compile_err

:start_standalone
echo Khoi chay Server tai cong 8080...
java -cp target\classes com.game.hunter.StandaloneServer
if %ERRORLEVEL% NEQ 0 goto server_err
goto end

:compile_err
echo.
echo [LOI] Khong the bien dich StandaloneServer.java!
pause
goto end

:server_err
echo.
echo [THONG BAO] Server da dung voi ma loi %ERRORLEVEL%.
pause
goto end

:no_java
echo.
echo [LOI] Khong tim thay Java! Vui long cai dat Java 8 tro len tai https://adoptium.net
pause
goto end

:end
cd ..
