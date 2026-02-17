@echo off
setlocal

set "SRC=src\main\java"
set "OUT=out\classes"
set "LIBS=libs\*"
set "DEFAULT_ARGS=--fps 6 --threshold 0.10 --decode-window 4"

echo === Compilando ===
if not exist "%OUT%" mkdir "%OUT%"

javac -encoding UTF-8 -cp "%LIBS%" -d "%OUT%" "%SRC%\app\QrVideoReaderApp.java"
if errorlevel 1 goto :error

echo === Ejecutando lector ===
java -cp "%OUT%;%LIBS%" app.QrVideoReaderApp %DEFAULT_ARGS% %*
if errorlevel 1 goto :error

goto :eof

:error
echo.
echo ERROR: Fallo la ejecucion. Revisa el output de arriba.
pause
