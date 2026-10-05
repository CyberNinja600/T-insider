@echo off
setlocal enabledelayedexpansion

if "%1"=="-preloader" goto :START_APP
if "%1"=="-show" goto :START_APP
if "%1"=="-hidden" goto :START_APP

start "" powershell.exe -NoProfile -WindowStyle Hidden -ExecutionPolicy Bypass -File "%~dp0splash\T-Insider-Preloader.ps1"
exit /b 0

set "PORTABLE_JDK_DIR=%~dp0.jdks"
set "LOCAL_JDK_HOME=%PORTABLE_JDK_DIR%\jdk-21"

set "HAS_JAVA=0"

:: Check if local portable JDK is valid
if exist "%LOCAL_JDK_HOME%\bin\java.exe" (
    "%LOCAL_JDK_HOME%\bin\java.exe" -version >nul 2>&1
    if !ERRORLEVEL! equ 0 set "HAS_JAVA=1"
)

:: Check if system java is valid (JDK 21+)
if !HAS_JAVA! equ 0 (
    where java >nul 2>&1
    if !ERRORLEVEL! equ 0 (
        java -version 2>&1 | findstr /R "version..2[1-9] version..[3-9][0-9]" >nul
        if !ERRORLEVEL! equ 0 set "HAS_JAVA=1"
    )
)

if !HAS_JAVA! equ 1 (
    :: Relaunch itself hidden using PowerShell because we already have Java
    powershell -NoProfile -WindowStyle Hidden -Command "Start-Process cmd.exe -ArgumentList '/c \"\"%~f0\"\" -hidden' -WindowStyle Hidden"
    exit /b
)

:START_APP
title T-Insider App Runner

:: Self-heal the Windows shortcuts (.lnk) to ensure the custom program icon and paths are always valid in both the project root and on the Desktop
powershell -NoProfile -Command "$w=New-Object -ComObject WScript.Shell; foreach($p in @('%~dp0T-Insider.lnk', [System.IO.Path]::Combine($w.SpecialFolders.Item('Desktop'), 'T-Insider.lnk'))) { $s=$w.CreateShortcut($p); $s.TargetPath='%~dp0tinsider.cmd'; $s.WorkingDirectory='%~dp0'; $s.IconLocation='%~dp0src\main\resources\t-insider.ico'; $s.Save() }" >nul 2>&1

echo =======================================================
echo     Starting T-Insider Build ^& Execution
echo =======================================================
echo.

cd /d "%~dp0"

set "PORTABLE_JDK_DIR=%~dp0.jdks"
set "LOCAL_JDK_HOME=%PORTABLE_JDK_DIR%\jdk-21"

:: Check if local portable JDK is already set up and valid
if not exist "%LOCAL_JDK_HOME%\bin\java.exe" goto :CHECK_SYSTEM_JAVA

"%LOCAL_JDK_HOME%\bin\java.exe" -version 2>&1 | findstr /R "version..2[1-9] version..[3-9][0-9]" >nul
if !ERRORLEVEL! equ 0 (
    echo [INFO] Using portable JDK 21 from %LOCAL_JDK_HOME%
    set "JAVA_HOME=%LOCAL_JDK_HOME%"
    set "PATH=%LOCAL_JDK_HOME%\bin;%PATH%"
    goto :RUN_APP
) else (
    echo [WARN] Found existing but invalid JDK directory. Cleaning up...
    rmdir /s /q "%LOCAL_JDK_HOME%"
)

:CHECK_SYSTEM_JAVA
:: Check if system java is valid (JDK 21+)
where java >nul 2>&1
if !ERRORLEVEL! neq 0 goto :DOWNLOAD_JDK

java -version 2>&1 | findstr /R "version..2[1-9] version..[3-9][0-9]" >nul
if !ERRORLEVEL! equ 0 (
    echo [INFO] Compatible system Java 21+ detected.
    goto :RUN_APP
)

:DOWNLOAD_JDK
:: Download and extract portable JDK 21
echo [INFO] No compatible Java 21+ detected. Automating setup...
echo [INFO] Downloading a self-contained portable JDK 21 (Eclipse Temurin) to run the application...
echo [INFO] This ensures the app runs perfectly without requiring any installation or configuration!
echo.

if not exist "%PORTABLE_JDK_DIR%" mkdir "%PORTABLE_JDK_DIR%"

if not exist "%~dp0logs" mkdir "%~dp0logs"
if defined TINSIDER_LOADER_PROGRESS_FILE (
    powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0splash\Download-Jdk.ps1" -DestinationRoot "%PORTABLE_JDK_DIR%" > "%~dp0logs\start.log" 2>&1
) else (
    powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0splash\Download-Jdk.ps1" -DestinationRoot "%PORTABLE_JDK_DIR%"
)

if %ERRORLEVEL% neq 0 (
    echo [ERROR] Failed to download or configure portable JDK.
    echo Please make sure you have internet access and try again.
    if "%1"=="-preloader" exit /b 1
    pause
    exit /b 1
)

echo [INFO] Portable JDK 21 installed successfully!
set "JAVA_HOME=%LOCAL_JDK_HOME%"
set "PATH=%LOCAL_JDK_HOME%\bin;%PATH%"

if not "%1"=="-show" if not "%1"=="-preloader" (
    echo [INFO] Hiding console and launching application...
    powershell -NoProfile -WindowStyle Hidden -Command "Start-Process cmd.exe -ArgumentList '/c \"\"%~f0\"\" -hidden' -WindowStyle Hidden"
    exit /b
)

:RUN_APP
if defined TINSIDER_LOADER_PROGRESS_FILE powershell -NoProfile -Command "[System.IO.File]::WriteAllText($env:TINSIDER_LOADER_PROGRESS_FILE, '85|Starting application')"
echo [INFO] Executing application via Maven Wrapper...
if not exist "%~dp0logs" mkdir "%~dp0logs"
if "%1"=="-hidden" goto :RUN_APP_HIDDEN
if "%1"=="-preloader" goto :RUN_APP_HIDDEN
call "%~dp0mvnw.cmd" clean compile javafx:run 2>&1 | powershell -NoProfile -Command "$writer = [System.IO.StreamWriter]::new('%~dp0logs\start.log', $false); $writer.AutoFlush = $true; $input | ForEach-Object { $_; $writer.WriteLine($_) }; $writer.Close()"
goto :RUN_APP_COMPLETE

:RUN_APP_HIDDEN
call "%~dp0mvnw.cmd" clean compile javafx:run > "%~dp0logs\start.log" 2>&1

:RUN_APP_COMPLETE
if %ERRORLEVEL% neq 0 (
    echo.
    echo [ERROR] Application execution failed!
    if "%1"=="-preloader" exit /b 1
    pause
)

