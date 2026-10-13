@echo off
setlocal
cd /d "%~dp0"
call mvnw.cmd -q -DskipTests compile
if errorlevel 1 exit /b 1
set "SAXON_JAR=src\main\resources\lib\saxon-he-12.4.jar"
set "RESOLVER_JAR=src\main\resources\lib\xmlresolver-5.2.2.jar"
if not exist "%SAXON_JAR%" (
  echo Saxon JAR not found: %SAXON_JAR%
  pause
  exit /b 1
)
if not exist "%RESOLVER_JAR%" (
  echo XML Resolver JAR not found: %RESOLVER_JAR%
  pause
  exit /b 1
)
java -cp "target\classes;%SAXON_JAR%;%RESOLVER_JAR%" com.sts.dita.app.ValidatorApp
if errorlevel 1 exit /b 1
