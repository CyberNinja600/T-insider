@echo off
setlocal enabledelayedexpansion

if "%1"=="-show" goto :START_APP
if "%1"=="-hidden" goto :START_APP

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

:: Self-heal the Windows shortcut (.lnk) to ensure the custom program icon and paths are always valid on this machine
powershell -NoProfile -Command "$s=(New-Object -ComObject WScript.Shell).CreateShortcut('%~dp0T-Insider.lnk');$s.TargetPath='%~dp0tinsider.cmd';$s.WorkingDirectory='%~dp0';$s.IconLocation='%~dp0src\main\resources\t-insider.ico';$s.Save()" >nul 2>&1

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

:: Execute PowerShell inline to download, extract, and rename
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
    "[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12 -bor [Net.SecurityProtocolType]::Tls13;" ^
    "$arch = if ($env:PROCESSOR_ARCHITECTURE -eq 'ARM64') { 'aarch64' } else { 'x64' };" ^
    "$url = \"https://api.adoptium.net/v3/binary/latest/21/ga/windows/$arch/jdk/hotspot/normal/eclipse\";" ^
    "Write-Host '[INFO] Fetching JDK from Adoptium...';" ^
    "Invoke-WebRequest -Uri $url -OutFile '%PORTABLE_JDK_DIR%\jdk.zip' -MaximumRedirection 5;" ^
    "Write-Host '[INFO] Extracting files...';" ^
    "Expand-Archive -Path '%PORTABLE_JDK_DIR%\jdk.zip' -DestinationPath '%PORTABLE_JDK_DIR%' -Force;" ^
    "Remove-Item -Path '%PORTABLE_JDK_DIR%\jdk.zip' -Force;" ^
    "$extractedDir = Get-ChildItem -Path '%PORTABLE_JDK_DIR%' -Directory | Where-Object { $_.Name -ne 'jdk-21' } | Select-Object -First 1;" ^
    "if ($extractedDir) { Rename-Item -Path $extractedDir.FullName -NewName 'jdk-21' } else { throw 'Could not find extracted JDK directory' }"

if %ERRORLEVEL% neq 0 (
    echo [ERROR] Failed to download or configure portable JDK.
    echo Please make sure you have internet access and try again.
    pause
    exit /b 1
)

echo [INFO] Portable JDK 21 installed successfully!
set "JAVA_HOME=%LOCAL_JDK_HOME%"
set "PATH=%LOCAL_JDK_HOME%\bin;%PATH%"

if not "%1"=="-show" (
    echo [INFO] Hiding console and launching application...
    powershell -NoProfile -WindowStyle Hidden -Command "Start-Process cmd.exe -ArgumentList '/c \"\"%~f0\"\" -hidden' -WindowStyle Hidden"
    exit /b
)

:RUN_APP
echo [INFO] Executing application via Maven Wrapper...
if not exist "%~dp0logs" mkdir "%~dp0logs"
call "%~dp0mvnw.cmd" clean compile javafx:run 2>&1 | powershell -NoProfile -Command "Tee-Object -FilePath '%~dp0logs\start.log'"
if %ERRORLEVEL% neq 0 (
    echo.
    echo [ERROR] Application execution failed!
    pause
)

