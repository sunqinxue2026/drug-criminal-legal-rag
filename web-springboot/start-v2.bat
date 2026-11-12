@echo off
chcp 65001 >nul
echo ========================================
echo 法律咨询智能问答系统 - 启动脚本 v2.0
echo ========================================
echo.

cd /d "%~dp0"
call set-java17.bat

echo [检查] 正在检查 Java 版本...
java -version 2>&1 | findstr /i "17."
if %ERRORLEVEL% NEQ 0 (
    echo 未检测到 JDK 17，请先安装或修改 set-java17.bat
    pause
    exit /b 1
)

echo Java 环境正常
echo.
echo [1/4] 清理旧的编译文件...
call mvnw.cmd clean >nul 2>&1

echo [2/4] 编译项目...
call mvnw.cmd compile -DskipTests
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo 编译失败。
    echo 建议优先确认当前使用的是 JDK 17。
    pause
    exit /b 1
)

echo 编译成功
echo.
echo [3/4] 打包应用...
call mvnw.cmd package -DskipTests
if %ERRORLEVEL% NEQ 0 (
    echo 打包失败。
    pause
    exit /b 1
)

echo 打包成功
echo.
echo [4/4] 启动应用...
echo.
echo 访问地址：
echo   - API文档: http://localhost:8080/api/swagger-ui.html
echo   - 健康检查: http://localhost:8080/api/health
echo.

"%JAVA_HOME%\bin\java.exe" -jar target\legal-rag-backend-1.0.0.jar

pause
