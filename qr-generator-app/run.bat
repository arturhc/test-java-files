@echo off
setlocal

mvn -q -DskipTests compile
if errorlevel 1 exit /b 1

mvn -q exec:java -Dexec.mainClass="app.QrGeneratorApp" -Dexec.args="%*"