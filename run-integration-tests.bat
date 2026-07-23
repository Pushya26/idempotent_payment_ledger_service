@echo off
REM Idempotent Payment Ledger Service - Integration Test Runner (Windows)
REM This script helps run integration tests with docker-compose

setlocal enabledelayedexpansion

echo.
echo ========================================
echo Integration Test Runner
echo ========================================
echo.

REM Check if docker-compose is available
docker-compose version >nul 2>&1
if errorlevel 1 (
    echo Warning: Docker not found. Integration tests require docker-compose.
    exit /b 1
)

REM Start docker-compose services
echo Starting Docker Compose services...
docker-compose up -d

REM Wait for services to be ready (PostgreSQL on 5432, Redis on 6379)
echo Waiting for services to be ready...
set /a count=0
:wait_loop
if %count% geq 30 (
    echo Error: Services did not start in time
    exit /b 1
)

REM Simple port check using PowerShell
powershell -Command "$sock = New-Object System.Net.Sockets.TcpClient; $sock.Connect('localhost', 5432, 1000)" 2>nul
if errorlevel 1 (
    set /a count=!count!+1
    echo Waiting for PostgreSQL... (!count!/30)
    timeout /t 1 /nobreak
    goto wait_loop
)

powershell -Command "$sock = New-Object System.Net.Sockets.TcpClient; $sock.Connect('localhost', 6379, 1000)" 2>nul
if errorlevel 1 (
    set /a count=!count!+1
    echo Waiting for Redis... (!count!/30)
    timeout /t 1 /nobreak
    goto wait_loop
)

echo Services are ready!
echo.

REM Run integration tests
echo Running integration tests...
call mvnw.cmd verify -Pintegration
set TEST_RESULT=%errorlevel%

echo.
if %TEST_RESULT% equ 0 (
    echo ✓ Integration tests passed!
) else (
    echo ✗ Integration tests failed.
)

echo.
set /p STOP_SERVICES="Stop Docker Compose services? (y/n) "
if /i "%STOP_SERVICES%"=="y" (
    echo Stopping Docker Compose services...
    docker-compose down
    echo Services stopped.
)

exit /b %TEST_RESULT%
