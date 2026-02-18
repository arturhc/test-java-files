@echo off
setlocal

set "SRC=src\main\java"
set "OUT=out\classes"
set "LIBS=libs\*"
set "DEFAULT_ARGS=--video video\qrs.mp4 --frames frames --fps 6 --threshold 0.10 --analysis-size 64"
set "MAIN_CLASS=app.QrFrameChangeDetectorApp"
set "MAIN_CLASS_FILE=QrFrameChangeDetectorApp.class"
set "SOURCES_LIST=%OUT%\sources.list"
set "JAVA_CMD="
set "JAVAC_CMD="
set "RESOLVED_JAVA_HOME="

call :resolve_java
if errorlevel 1 goto :error

echo === Compilando ===
if not exist "%OUT%" mkdir "%OUT%"
dir /b /s "%SRC%\app\*.java" > "%SOURCES_LIST%"
if errorlevel 1 (
  echo ERROR: No se encontraron fuentes Java en "%SRC%\app".
  goto :error
)

"%JAVAC_CMD%" -encoding UTF-8 -cp "%LIBS%" -d "%OUT%" @"%SOURCES_LIST%"
if errorlevel 1 goto :error

if not exist "%OUT%\app\%MAIN_CLASS_FILE%" (
  echo ERROR: javac no genero clases en "%OUT%\app".
  echo Verifica que JAVA_HOME apunte a un JDK valido.
  goto :error
)

echo === Ejecutando detector de cambios ===
"%JAVA_CMD%" -cp "%OUT%;%LIBS%" %MAIN_CLASS% %DEFAULT_ARGS% %*
if errorlevel 1 goto :error

goto :eof

:resolve_java
if defined JAVA_HOME (
  if exist "%JAVA_HOME%\bin\java.exe" if exist "%JAVA_HOME%\bin\javac.exe" (
    set "RESOLVED_JAVA_HOME=%JAVA_HOME%"
  )
)

if not defined RESOLVED_JAVA_HOME (
  for /f "delims=" %%D in ('dir /b /ad /o-n "C:\Program Files\Java\jdk-*" 2^>nul') do (
    if not defined RESOLVED_JAVA_HOME set "RESOLVED_JAVA_HOME=C:\Program Files\Java\%%D"
  )
)

if not defined RESOLVED_JAVA_HOME (
  echo ERROR: No se encontro un JDK valido.
  echo Configura JAVA_HOME apuntando al JDK. Ejemplo:
  echo   set JAVA_HOME=C:\Program Files\Java\jdk-23
  exit /b 1
)

set "JAVA_CMD=%RESOLVED_JAVA_HOME%\bin\java.exe"
set "JAVAC_CMD=%RESOLVED_JAVA_HOME%\bin\javac.exe"
echo Usando JDK: "%RESOLVED_JAVA_HOME%"
exit /b 0

:error
echo.
echo ERROR: Fallo la ejecucion. Revisa el output de arriba.
pause
exit /b 1
