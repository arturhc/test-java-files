@echo off
setlocal
cd /d "%~dp0"

if not exist out (
  mkdir out
)

javac -encoding UTF-8 -d out TyperApp.java
if errorlevel 1 (
  echo Error: falló la compilacion.
  exit /b 1
)

java -cp out TyperApp
