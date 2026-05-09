@echo off
setlocal

cd /d "%~dp0"
set "ROOT=%CD%"
set "PROJECT_DIR=%ROOT%\BYOW"
set "OUT_DIR=%PROJECT_DIR%\out\game"
set "SOURCES_FILE=%OUT_DIR%\sources.txt"

set "LIBRARY_JAR="
if exist "%ROOT%\..\library-sp26\algs4.jar" set "LIBRARY_JAR=%ROOT%\..\library-sp26\algs4.jar"
if not defined LIBRARY_JAR if exist "%ROOT%\library-sp26\algs4.jar" set "LIBRARY_JAR=%ROOT%\library-sp26\algs4.jar"
if not defined LIBRARY_JAR if defined BYOW_LIBRARY if exist "%BYOW_LIBRARY%\algs4.jar" set "LIBRARY_JAR=%BYOW_LIBRARY%\algs4.jar"

if not defined LIBRARY_JAR (
    echo Could not find library-sp26\algs4.jar.
    echo Keep library-sp26 next to this launcher, or set BYOW_LIBRARY to that folder.
    pause
    exit /b 1
)

call :find_jdk
if not defined JAVA_EXE (
    echo Could not find Java.
    echo Install JDK 17 or newer, or set JAVA_HOME to your JDK folder.
    pause
    exit /b 1
)
if not defined JAVAC_EXE (
    echo Could not find javac.
    echo Install JDK 17 or newer, or set JAVA_HOME to your JDK folder.
    pause
    exit /b 1
)

if not exist "%OUT_DIR%" mkdir "%OUT_DIR%"
dir /s /b "%PROJECT_DIR%\src\*.java" > "%SOURCES_FILE%"

"%JAVAC_EXE%" -cp "%LIBRARY_JAR%" -d "%OUT_DIR%" @"%SOURCES_FILE%"
if errorlevel 1 (
    echo Build failed.
    pause
    exit /b 1
)

if /i "%~1"=="--compile-only" exit /b 0

cd /d "%PROJECT_DIR%"
"%JAVA_EXE%" -cp "%OUT_DIR%;%LIBRARY_JAR%" core.Main
exit /b %ERRORLEVEL%

:find_jdk
if defined JAVA_HOME (
    if exist "%JAVA_HOME%\bin\java.exe" set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
    if exist "%JAVA_HOME%\bin\javac.exe" set "JAVAC_EXE=%JAVA_HOME%\bin\javac.exe"
    if defined JAVA_EXE if defined JAVAC_EXE exit /b 0
)

for %%J in ("%USERPROFILE%\.jdks\ms-17.0.18" "%USERPROFILE%\.jdks\openjdk-25.0.2" "C:\Program Files\Eclipse Adoptium" "C:\Program Files\Java") do (
    if exist "%%~J\bin\java.exe" if exist "%%~J\bin\javac.exe" (
        set "JAVA_EXE=%%~J\bin\java.exe"
        set "JAVAC_EXE=%%~J\bin\javac.exe"
        exit /b 0
    )
)

for /f "delims=" %%J in ('where java 2^>nul') do if not defined JAVA_EXE set "JAVA_EXE=%%J"
for /f "delims=" %%J in ('where javac 2^>nul') do if not defined JAVAC_EXE set "JAVAC_EXE=%%J"
exit /b 0
