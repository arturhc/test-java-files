@echo off
setlocal

set "JAVA_BIN=C:\Program Files\Java\jdk-23\bin"
set "SRC=src\main\java"
set "OUT=out"
set "LIBS=libs\*"

echo === Compilando ===
if not exist "%OUT%" mkdir "%OUT%"

"%JAVA_BIN%\javac.exe" -encoding UTF-8 -cp "%LIBS%" -d "%OUT%" "%SRC%\app\QrGeneratorApp.java"
if errorlevel 1 goto :error

echo === Ejecutando app ===
"%JAVA_BIN%\java.exe" -cp "%OUT%;%LIBS%" app.QrGeneratorApp
if errorlevel 1 goto :error

goto :eof

:error
echo.
echo ERROR: Fallo la ejecucion. Revisa el output de arriba.
pause
