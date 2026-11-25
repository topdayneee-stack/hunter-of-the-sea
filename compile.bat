@echo off
cd /d "%~dp0"
title Hunter of the Sea - Bien Dich

echo =====================================================================
echo    DANG TIEN HANH BIEN DICH GAME CLIENT: HUNTER OF THE SEA...
echo    Target Bytecode: Java 8 tuong thich toi da
echo =====================================================================

if not exist bin mkdir bin

javac --release 8 -Xlint:-options -encoding UTF-8 -d bin src\huntersea\*.java src\huntersea\model\*.java src\huntersea\ui\*.java
if %ERRORLEVEL% NEQ 0 goto compile_err

echo.
echo [THANH CONG] Da bien dich toan bo ma nguon Java thanh cong!
echo Dang dong goi HunterOfTheSea.jar...
jar cfe HunterOfTheSea.jar huntersea.Main -C bin .
if %ERRORLEVEL% NEQ 0 goto jar_err

echo [HOAN TAT] Da tao xong HunterOfTheSea.jar tuong thich Java 8+.
echo Ban co the chay ngay bang file run.bat!
goto end

:compile_err
echo.
echo [LOI] Co loi xay ra trong qua trinh bien dich javac!
goto end

:jar_err
echo.
echo [LOI] Co loi xay ra khi dong goi file jar!
goto end

:end
pause
