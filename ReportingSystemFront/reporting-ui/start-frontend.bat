@echo off
cd /d "%~dp0"
title reporting-ui
echo ===== Checking Node.js =====
where node > setup.log 2>&1
node -v >> setup.log 2>&1
call npm -v >> setup.log 2>&1
type setup.log
where node >nul 2>&1
if errorlevel 1 (
  echo.
  echo Node.js is not installed. Install it from https://nodejs.org and run this file again.
  pause
  exit /b 1
)
echo.
echo ===== npm install =====
call npm install >> setup.log 2>&1
if errorlevel 1 (
  echo npm install failed, see setup.log for details
  type setup.log
  pause
  exit /b 1
)
echo npm install finished.
echo.
echo ===== Starting (http://localhost:4200) =====
echo Close this window or press Ctrl+C to stop the frontend.
powershell -NoProfile -ExecutionPolicy Bypass -Command "npm start 2>&1 | Tee-Object -FilePath serve.log"
pause
