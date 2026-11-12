@echo off
chcp 65001 >nul
echo ========================================
echo 快速启动脚本（开发模式）
echo ========================================
echo.
echo 此脚本会直接启动应用，不进行打包
echo 适合开发调试使用
echo.

cd /d "%~dp0"
call set-java17.bat

echo [启动] 正在启动应用...
echo.
echo 访问地址：
echo   - API文档: http://localhost:8080/api/swagger-ui.html
echo   - 健康检查: http://localhost:8080/api/health
echo.
echo 按 Ctrl+C 停止应用
echo.

call mvnw.cmd spring-boot:run
