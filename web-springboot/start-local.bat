@echo off
chcp 65001 >nul
echo ========================================
echo 本地开发启动脚本
echo ========================================
echo.

cd /d "%~dp0"
call set-java17.bat

set "SPRING_DATASOURCE_USERNAME=root"
set "SPRING_DATASOURCE_PASSWORD=sqx2022@"

echo [启动] 使用本地数据库配置启动 Spring Boot...
echo.
echo 访问地址:
echo   - API文档: http://localhost:8080/api/swagger-ui.html
echo   - 健康检查: http://localhost:8080/api/health
echo.
echo 按 Ctrl+C 停止应用
echo.

call mvnw.cmd spring-boot:run
