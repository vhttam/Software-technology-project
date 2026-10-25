@echo off
echo ==========================================================
echo   DANG BUILD LAI TOAN BO DOCKER IMAGES (BO QUA CACHE)...
echo ==========================================================
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0start.ps1" -NoCache %*
echo.
pause
