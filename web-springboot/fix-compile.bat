@echo off
chcp 65001 >nul
echo ========================================
echo 编译问题自动修复脚本
echo ========================================
echo.

cd /d "%~dp0"
call set-java17.bat

echo [1/4] 当前 Java 版本...
java -version
echo.

echo [2/4] 清理旧的编译文件...
call mvnw.cmd clean
if %ERRORLEVEL% NEQ 0 (
  echo 清理失败。
  pause
  exit /b 1
)
echo.

echo [3/4] 重新编译项目...
call mvnw.cmd compile -DskipTests
if %ERRORLEVEL% NEQ 0 (
  echo 编译仍然失败，请优先确认 JDK 17 路径是否正确。
  pause
  exit /b 1
)
echo.

echo [4/4] 验证打包...
call mvnw.cmd package -DskipTests
if %ERRORLEVEL% NEQ 0 (
  echo 打包失败，请查看上方日志。
  pause
  exit /b 1
)

echo.
echo 修复完成，当前环境已经固定使用 JDK 17。
pause
