@echo off
cd /d "%~dp0"
title Hunter of the Sea - Thuy Than Dai Duong

echo =====================================================================
echo          HUNTER OF THE SEA - THUY THAN DAI DUONG
echo =====================================================================

where java >nul 2>nul
if %ERRORLEVEL% NEQ 0 goto no_java

echo Dang khoi dong tro choi...
echo.

if exist HunterOfTheSea.jar goto run_jar
goto run_bin

:run_jar
java -jar HunterOfTheSea.jar
goto check_error

:run_bin
echo Khong tim thay HunterOfTheSea.jar, dang chay tu thu muc bin...
java -cp bin huntersea.Main
goto check_error

:check_error
if %ERRORLEVEL% EQU 0 goto end
echo.
echo =====================================================================
echo [THONG BAO] Tro choi da dung lai voi ma loi: %ERRORLEVEL%
echo Vui long kiem tra phien ban Java hoac chay compile.bat de bien dich lai.
echo =====================================================================
echo.
pause
goto end

:no_java
echo.
echo [LOI] Khong tim thay Java tren may tinh cua ban!
echo Vui long cai dat Java 8 tro len tai: https://adoptium.net
echo Khi cai dat, hay tich chon Add to PATH.
echo.
pause
goto end

:end
