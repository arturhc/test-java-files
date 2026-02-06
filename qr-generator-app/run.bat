@echo off
setlocal

echo === Compilando ===
call mvn -DskipTests compile
if errorlevel 1 goto :error

echo === Ejecutando app ===
call mvn exec:java -Dexec.mainClass="app.QrGeneratorApp" -Dexec.cleanupDaemonThreads=false -Dexec.args="%*"
if errorlevel 1 goto :error

goto :eof

:error
echo.
echo ERROR: Maven fallo. Revisa el output de arriba.
pause
