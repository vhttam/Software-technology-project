@echo off
chcp 65001 >nul
echo ==========================================================
echo   DANG BUILD VA CAP NHAT GIAO DIEN WEB VAO DOCKER...
echo ==========================================================
cd "%~dp0web"
call pnpm build
if %ERRORLEVEL% neq 0 (
    echo [LOI] Build that bai! Vui long kiem tra lai code TSX/CSS.
    pause
    exit /b %ERRORLEVEL%
)
cd "%~dp0"
echo [INFO] Dang chep ban build moi vao container cnpm-web...
docker cp web\dist\. cnpm-web:/usr/share/nginx/html/
docker exec cnpm-web nginx -s reload
echo.
echo ==========================================================
echo   CAP NHAT THANH CONG! F5 LAI TRINH DUYET DE XEM KET QUA.
echo ==========================================================
pause
