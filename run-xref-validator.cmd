@echo off
setlocal
cd /d "%~dp0"
call mvnw.cmd -q -DskipTests compile
if errorlevel 1 exit /b 1
java -cp "target\classes" com.sts.dita.app.XrefValidatorApp
if errorlevel 1 exit /b 1
