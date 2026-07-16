@echo off
title T-Insider Terminal App Runner
echo =======================================================
echo     Starting T-Insider Terminal App Build ^& Execution
echo =======================================================
echo.

cd /d "%~dp0"

:: 1. Load configuration from setup.properties dynamically
if exist "setup.properties" (
    echo [INFO] Loading configuration from setup.properties...
    for /f "usebackq tokens=1,2 delims==" %%a in ("setup.properties") do (
        set "%%a=%%b"
    )
) else (
    echo [WARN] setup.properties file not found! Using standard path lookup...
)

:: 2. Configure Java Environment
if defined JAVA_HOME (
    if exist "%JAVA_HOME%" (
        echo [INFO] Using configured JAVA_HOME: %JAVA_HOME%
        set "PATH=%JAVA_HOME%\bin;%PATH%"
    ) else (
        echo [WARN] Configured JAVA_HOME does not exist on disk! Checking system PATH...
    )
) else (
    echo [INFO] JAVA_HOME not configured. Checking system PATH...
)

echo [INFO] Using Java version:
java -version
echo.

:: 3. Configure and Run Maven
if defined MAVEN_CMD (
    if exist "%MAVEN_CMD%" (
        echo [INFO] Using configured MAVEN_CMD: %MAVEN_CMD%
        call "%MAVEN_CMD%" clean compile javafx:run
        goto END
    ) else (
        echo [WARN] Configured MAVEN_CMD does not exist on disk! Checking system PATH...
    )
) else (
    echo [INFO] MAVEN_CMD not configured. Checking system PATH...
)

:: Fallback to system PATH Maven if not configured locally
where mvn >nul 2>&1
if %ERRORLEVEL% equ 0 (
    echo [INFO] Running system PATH Maven...
    call mvn clean compile javafx:run
) else (
    echo [ERROR] Maven not found! Please configure MAVEN_CMD in setup.properties.
    echo.
    pause
    exit /b 1
)

:END
if %ERRORLEVEL% neq 0 (
    echo.
    echo [ERROR] Build or execution failed!
    echo.
    pause
)
