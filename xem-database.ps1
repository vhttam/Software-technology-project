Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "  DANH SÁCH BẢNG VÀ DỮ LIỆU TRONG MICROSOFT SQL SERVER" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

$saPass = "LocalDevPassword123!"

function Run-Query($title, $sql) {
    Write-Host "`n>>> $title" -ForegroundColor Yellow
    docker exec cnpm-sqlserver /opt/mssql-tools18/bin/sqlcmd -S localhost -U sa -P $saPass -C -Q $sql
}

Run-Query "1. TẤT CẢ CÁC BẢNG TRONG CÁC DATABASE:" "
SELECT TABLE_CATALOG as [Database], TABLE_NAME as [Table] 
FROM authdb.INFORMATION_SCHEMA.TABLES WHERE TABLE_TYPE='BASE TABLE'
UNION ALL 
SELECT TABLE_CATALOG, TABLE_NAME FROM contentdb.INFORMATION_SCHEMA.TABLES WHERE TABLE_TYPE='BASE TABLE'
UNION ALL 
SELECT TABLE_CATALOG, TABLE_NAME FROM narrationdb.INFORMATION_SCHEMA.TABLES WHERE TABLE_TYPE='BASE TABLE'
ORDER BY [Database], [Table];
"

Run-Query "2. DỮ LIỆU BẢNG TÀI KHOẢN (authdb.dbo.users):" "
SELECT user_id, username, name, role, status FROM authdb.dbo.users;
"

Run-Query "3. DỮ LIỆU BẢNG ĐIỂM THAM QUAN (contentdb.dbo.points_of_interest):" "
SELECT poi_id, name, latitude, longitude, trigger_radius FROM contentdb.dbo.points_of_interest;
"

Run-Query "4. DỮ LIỆU BẢNG NỘI DUNG (contentdb.dbo.content_versions):" "
SELECT content_id, version, title, source_lang FROM contentdb.dbo.content_versions;
"

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "  KẾT NỐI QUA PHẦN MỀM GIAO DIỆN (DBeaver / SSMS / VS Code):" -ForegroundColor Green
Write-Host "  - Host:      127.0.0.1,1433" -ForegroundColor Green
Write-Host "  - Username:  sa" -ForegroundColor Green
Write-Host "  - Password:  $saPass" -ForegroundColor Green
Write-Host "  - Trust cert: True" -ForegroundColor Green
Write-Host "==========================================================" -ForegroundColor Cyan
