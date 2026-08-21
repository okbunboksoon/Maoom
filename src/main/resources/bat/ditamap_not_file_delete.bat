@echo off
chcp 65001 >nul
setlocal EnableDelayedExpansion

set "TOPICS=%~dp0topics"
set "MAP="
set "MAP_COUNT=0"

for %%M in ("%TOPICS%\*.ditamap") do (
    if exist "%%~fM" (
        set "MAP=%%~nxM"
        set /a MAP_COUNT+=1
    )
)

if "%MAP_COUNT%"=="0" (
    echo.
    echo topics 폴더에 DITAMAP 파일이 없습니다.
    exit /b 1
)

if not "%MAP_COUNT%"=="1" (
    echo.
    echo topics 폴더에 DITAMAP 파일이 여러 개 있습니다.
    echo 하나만 남긴 뒤 다시 실행해주세요.
    exit /b 1
)

echo.
echo ==========================================
echo DITAMAP : %MAP%
echo ==========================================
echo.

for %%F in ("%TOPICS%\*.dita") do (
    findstr /I /C:"href=\"%%~nxF\"" "%TOPICS%\%MAP%" >nul
    if errorlevel 1 (
        del "%%F"
    )
)

echo Done.
exit /b 0
