@echo off
chcp 65001 >nul
echo ====================================
echo 法律咨询智能问答系统 - 启动脚本
echo ====================================
echo.

cd /d "%~dp0"
call set-java17.bat

echo [1/3] 清理并编译项目...
call mvnw.cmd clean package -DskipTests

if %ERRORLEVEL% NEQ 0 (
    echo 编译失败，请检查错误信息。
    pause
    exit /b 1
)

echo.
echo [2/3] 编译成功
echo.

echo [3/3] 启动应用...
echo.
echo 应用将在 http://localhost:8080/api 启动
echo Swagger文档: http://localhost:8080/api/swagger-ui.html
echo.

"%JAVA_HOME%\bin\java.exe" -jar target\legal-rag-backend-1.0.0.jar

pause
