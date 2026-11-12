@echo off
set "PREFERRED_JAVA_HOME=C:\Program Files\Microsoft\jdk-17.0.18.8-hotspot"

if exist "%PREFERRED_JAVA_HOME%\bin\java.exe" (
  set "JAVA_HOME=%PREFERRED_JAVA_HOME%"
  set "Path=%JAVA_HOME%\bin;%Path%"
  echo [JAVA] Using JDK 17: %JAVA_HOME%
) else (
  echo [JAVA] Preferred JDK 17 not found, using current JAVA_HOME=%JAVA_HOME%
)
