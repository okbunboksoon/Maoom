@echo off
setlocal EnableExtensions EnableDelayedExpansion

rem ============================================================
rem 기본 경로
rem ============================================================
set "BASEDIR=%~dp0"
set "TOPICSDIR=%BASEDIR%topics"
set "TEMPDIR=%BASEDIR%temp"
set "MAPINPUTDIR=%BASEDIR%temp_map_input"
set "CHAPTERDIR=%BASEDIR%chapter"
set "XSLDIR=%BASEDIR%xsl"
set "JAVA_CP=%BASEDIR%lib\saxon-he-12.4.jar;%BASEDIR%lib\xmlresolver-5.2.2.jar"
set "SAXON_MAIN=net.sf.saxon.Transform"

echo Please wait a moment!
echo Processing...

rem 정리할 언어 폴더가 있어야 합니다.
rem 예: topics\German, topics\Serbian, topics\French
if not exist "%TOPICSDIR%\" (
    echo [ERROR] topics folder not found:
    echo         %TOPICSDIR%
    pause
    exit /b 1
)

rem ============================================================
rem 1. 언어 압축 해제 폴더를 언어별 폴더로 정리
rem
rem 입력 예
rem   topics\de-DE_2026MAR20\de-DE\*.dita
rem 출력 예
rem   topics\German\*.dita
rem ============================================================
call :NORMALIZE_EXTRACTED_FOLDERS
if errorlevel 1 exit /b 1

rem ============================================================
rem 2. 결과 임시 폴더 초기화
rem ============================================================
if exist "%TOPICSDIR%\temp_out" rd /s /q "%TOPICSDIR%\temp_out"
mkdir "%TOPICSDIR%\temp_out"

rem ============================================================
rem 3. 언어 폴더별 처리
rem ============================================================
for /d %%L in ("%TOPICSDIR%\*") do (
    if /I not "%%~nxL"=="temp_out" (
        call :PROCESS_LANGUAGE "%%~fL" "%%~nxL"
        if errorlevel 1 goto :PROCESS_FAILED
    )
)

rem ============================================================
rem 4. temp_out 결과를 원래 언어 폴더에 반영
rem ============================================================
for /d %%L in ("%TOPICSDIR%\temp_out\*") do (
    del "%TOPICSDIR%\%%~nxL\*.xml" >nul 2>nul
    move "%%~fL\*" "%TOPICSDIR%\%%~nxL\" >nul
)

rem ============================================================
rem 5. 공용 임시 폴더 정리
rem ============================================================
if exist "%CHAPTERDIR%" rd /s /q "%CHAPTERDIR%" >nul 2>nul
if exist "%TEMPDIR%" rd /s /q "%TEMPDIR%" >nul 2>nul
if exist "%MAPINPUTDIR%" rd /s /q "%MAPINPUTDIR%" >nul 2>nul
if exist "%TOPICSDIR%\temp_out" rd /s /q "%TOPICSDIR%\temp_out" >nul 2>nul

echo Done.
pause
exit /b 0

:PROCESS_FAILED
echo.
echo [ERROR] Processing stopped. Original language files were not replaced.
echo         Check the first error shown above.
pause
exit /b 1


rem ============================================================
rem 압축 해제 원본 폴더 정리
rem   최상위 폴더명에서 "_" 앞의 언어 코드를 추출한다.
rem   lang_map.txt를 사용해 German, French 등의 폴더명으로 바꾼다.
rem   안쪽 첫 번째 폴더의 파일을 최종 언어 폴더로 이동한다.
rem ============================================================
:NORMALIZE_EXTRACTED_FOLDERS
if not exist "%BASEDIR%lang_map.txt" (
    echo [ERROR] lang_map.txt not found:
    echo         %BASEDIR%lang_map.txt
    pause
    exit /b 1
)

for /d %%S in ("%TOPICSDIR%\*_*") do (
    if /I not "%%~nxS"=="temp_out" (
        set "SOURCE_NAME=%%~nxS"
        set "LANG_CODE="
        set "LANG_NAME="
        set "INNER_DIR="

        for /f "tokens=1 delims=_" %%A in ("%%~nxS") do set "LANG_CODE=%%A"

        for /f "usebackq tokens=1,* delims==" %%A in ("%BASEDIR%lang_map.txt") do (
            if /I "%%A"=="!LANG_CODE!" set "LANG_NAME=%%B"
        )

        if not defined LANG_NAME set "LANG_NAME=!LANG_CODE!"

        for /d %%D in ("%%~fS\*") do (
            if not defined INNER_DIR set "INNER_DIR=%%~fD"
        )

        if defined INNER_DIR (
            echo [PREPARE] !SOURCE_NAME!  -^>  !LANG_NAME!

            if not exist "%TOPICSDIR%\!LANG_NAME!" mkdir "%TOPICSDIR%\!LANG_NAME!"
            move "!INNER_DIR!\*" "%TOPICSDIR%\!LANG_NAME!\" >nul

            rd "!INNER_DIR!" >nul 2>nul
            rd "%%~fS" >nul 2>nul
        ) else (
            echo [WARNING] No inner folder: %%~nxS
        )
    )
)

rem Also support already-extracted language folders directly under topics.
rem Example:
rem   topics\ar-AE\*.dita
rem   topics\ar-AE\*.ditamap
rem becomes:
rem   topics\Arabic\*.dita
rem   topics\Arabic\*.ditamap
for /f "usebackq tokens=1,* delims==" %%A in ("%BASEDIR%lang_map.txt") do (
    if exist "%TOPICSDIR%\%%A\" (
        if /I not "%%A"=="%%B" (
            if not exist "%TOPICSDIR%\%%B" mkdir "%TOPICSDIR%\%%B"

            echo [PREPARE] %%A  -^>  %%B
            move "%TOPICSDIR%\%%A\*" "%TOPICSDIR%\%%B\" >nul 2>nul
            rd "%TOPICSDIR%\%%A" >nul 2>nul
        )
    )
)
exit /b 0


rem ============================================================
rem 언어 폴더 분기
rem   %~1 = 언어 폴더 전체 경로
rem   %~2 = 언어 폴더명
rem ============================================================
:PROCESS_LANGUAGE
set "LANG_DIR=%~1"
set "LANG_NAME=%~2"

if exist "%LANG_DIR%\*.ditamap" (
    echo [DITAMAP PROCESSING] %LANG_NAME%
    call :PROCESS_DITAMAPS "%LANG_DIR%" "%LANG_NAME%"
    if errorlevel 1 exit /b 1

    if /I "%LANG_NAME%"=="German" (
        call :REPLACE_SPECIAL_DITA "%LANG_DIR%" "%LANG_NAME%" "0201-chapter-merge_DE.xsl"
        if errorlevel 1 exit /b 1
    )

    if /I "%LANG_NAME%"=="Serbian" (
        call :REPLACE_SPECIAL_DITA "%LANG_DIR%" "%LANG_NAME%" "0202-chapter-merge_SL.xsl"
        if errorlevel 1 exit /b 1
    )

    if /I "%LANG_NAME%"=="Russian" (
        call :REPLACE_SPECIAL_DITA "%LANG_DIR%" "%LANG_NAME%" "0203-chapter-merge_ru.xsl"
        if errorlevel 1 exit /b 1
    )

    if /I "%LANG_NAME%"=="French_Canada" (
        call :REPLACE_SPECIAL_DITA "%LANG_DIR%" "%LANG_NAME%" "0204-chapter-merge_fr_ca.xsl"
        if errorlevel 1 exit /b 1
    )

    exit /b 0
)

if exist "%LANG_DIR%\*.xml" (
    echo [XML PROCESSING] %LANG_NAME%
    call :PROCESS_XML "%LANG_DIR%" "%LANG_NAME%"
    if errorlevel 1 exit /b 1
    exit /b 0
)

echo [SKIP] %LANG_NAME% - DITA Excerpt translation
exit /b 0


rem ============================================================
rem 기존 DITAMAP 전처리
rem ============================================================
:PROCESS_DITAMAPS
set "LANG_DIR=%~1"
set "LANG_NAME=%~2"

if not exist "%TEMPDIR%" mkdir "%TEMPDIR%"
if exist "%MAPINPUTDIR%" rd /s /q "%MAPINPUTDIR%"
mkdir "%MAPINPUTDIR%"

rem 입력과 출력 폴더를 분리해 파일 처리 중 충돌을 방지한다.
move "%LANG_DIR%\*.ditamap" "%MAPINPUTDIR%\" >nul

for %%M in ("%MAPINPUTDIR%\*.ditamap") do (
    echo   - PROCESS: %%~nxM

    java -cp "%JAVA_CP%" %SAXON_MAIN% ^
        -catalog:"%XSLDIR%\catalog.xml" ^
        -s:"%%~fM" ^
        -o:"%TEMPDIR%\dummy.xml" ^
        -xsl:"%XSLDIR%\0000-ditamap-preprocess.xsl" ^
        langName=%LANG_NAME% ^
        srcFile=%%~nxM

    if errorlevel 1 (
        echo [ERROR] DITAMAP preprocessing failed: %%~nxM
        move /y "%MAPINPUTDIR%\*.ditamap" "%LANG_DIR%\" >nul 2>nul
        exit /b 1
    )

    del "%%~fM" >nul 2>nul
    del "%TEMPDIR%\dummy.xml" >nul 2>nul
)

move "%TEMPDIR%\*.ditamap" "%LANG_DIR%\" >nul
if exist "%MAPINPUTDIR%" rd /s /q "%MAPINPUTDIR%" >nul 2>nul
exit /b 0


rem ============================================================
rem 특정 언어의 기존 DITA 전체 재생성
rem   %~1 = 언어 폴더
rem   %~2 = 언어명
rem   %~3 = 언어별 chapter merge XSL
rem ============================================================
:REPLACE_SPECIAL_DITA
set "LANG_DIR=%~1"
set "LANG_NAME=%~2"
set "SPECIAL_MERGE_XSL=%~3"

echo [%LANG_NAME% DITA REPLACE]

rem 기존 언어 파일을 XSL 처리용 topics 루트로 복사한다.
xcopy "%LANG_DIR%\*.ditamap" "%TOPICSDIR%\" /Y >nul
xcopy "%LANG_DIR%\*.dita" "%TOPICSDIR%\" /Y >nul

call :PREPARE_EXISTING_DITA "%LANG_NAME%"
if errorlevel 1 (
    echo [ERROR] Existing DITA preparation failed: %LANG_NAME%
    del /q "%TOPICSDIR%\*.ditamap" >nul 2>nul
    del /q "%TOPICSDIR%\*.dita" >nul 2>nul
    exit /b 1
)

call :BUILD_DITA "%LANG_NAME%" "%SPECIAL_MERGE_XSL%"
if errorlevel 1 (
    echo [ERROR] DITA build failed: %LANG_NAME%
    del /q "%TOPICSDIR%\*.ditamap" >nul 2>nul
    del /q "%TOPICSDIR%\*.dita" >nul 2>nul
    exit /b 1
)

rem 생성 결과를 원래 언어 폴더에 반영한다.
del /q "%LANG_DIR%\*.ditamap" >nul 2>nul
del /q "%LANG_DIR%\*.dita" >nul 2>nul
xcopy "%TOPICSDIR%\*.ditamap" "%LANG_DIR%\" /Y >nul 2>nul
xcopy "%TOPICSDIR%\*.dita" "%LANG_DIR%\" /Y >nul 2>nul
del /q "%TOPICSDIR%\*.ditamap" >nul 2>nul
del /q "%TOPICSDIR%\*.dita" >nul 2>nul
exit /b 0


rem ============================================================
rem 기존 DITA 전처리 09 ~ 19
rem ============================================================
:PREPARE_EXISTING_DITA
set "LANG_NAME=%~1"

if not exist "%TEMPDIR%" mkdir "%TEMPDIR%"

java -cp "%JAVA_CP%" %SAXON_MAIN% ^
    -catalog:"%XSLDIR%\catalog.xml" ^
    -s:"%XSLDIR%\dummy.xml" ^
    -o:"%XSLDIR%\dummy.xml" ^
    -xsl:"%XSLDIR%\multilingual-0000-doctype-remove.xsl"
if errorlevel 1 exit /b 1

java -cp "%JAVA_CP%" %SAXON_MAIN% ^
    -s:"%TEMPDIR%\09-doctype-removed.xml" ^
    -o:"%TEMPDIR%\10-namespace-removed.xml" ^
    -xsl:"%XSLDIR%\0001-namespace-remove.xsl"
if errorlevel 1 exit /b 1

java -cp "%JAVA_CP%" %SAXON_MAIN% ^
    -s:"%TEMPDIR%\10-namespace-removed.xml" ^
    -o:"%TEMPDIR%\11-toc-created.xml" ^
    -xsl:"%XSLDIR%\0002-toc-create.xsl"
if errorlevel 1 exit /b 1

java -cp "%JAVA_CP%" %SAXON_MAIN% ^
    -s:"%TEMPDIR%\11-toc-created.xml" ^
    -o:"%XSLDIR%\bookmap.xml" ^
    -xsl:"%XSLDIR%\0003-bookmap-create.xsl"
if errorlevel 1 exit /b 1

java -cp "%JAVA_CP%" %SAXON_MAIN% ^
    -catalog:"%XSLDIR%\catalog.xml" ^
    -s:"%TEMPDIR%\11-toc-created.xml" ^
    -o:"%TEMPDIR%\13-topic-merged.xml" ^
    -xsl:"%XSLDIR%\0004-topic-merge.xsl"
if errorlevel 1 exit /b 1

java -cp "%JAVA_CP%" %SAXON_MAIN% ^
    -s:"%TEMPDIR%\13-topic-merged.xml" ^
    -o:"%TEMPDIR%\14-namespace-removed.xml" ^
    -xsl:"%XSLDIR%\0005-namespace-remove.xsl"
if errorlevel 1 exit /b 1

java -cp "%JAVA_CP%" %SAXON_MAIN% ^
    -s:"%TEMPDIR%\14-namespace-removed.xml" ^
    -o:"%TEMPDIR%\15-id-cleaned.xml" ^
    -xsl:"%XSLDIR%\0006-id-clean_NotFileNameChange.xsl"
if errorlevel 1 exit /b 1

java -cp "%JAVA_CP%" %SAXON_MAIN% ^
    -s:"%TEMPDIR%\15-id-cleaned.xml" ^
    -o:"%TEMPDIR%\16-xref-cleaned.xml" ^
    -xsl:"%XSLDIR%\0007-xref-clean_NotFileNameChange.xsl"
if errorlevel 1 exit /b 1

java -cp "%JAVA_CP%" %SAXON_MAIN% ^
    -s:"%TEMPDIR%\16-xref-cleaned.xml" ^
    -o:"%TEMPDIR%\17-related-links.xml" ^
    -xsl:"%XSLDIR%\0008-related-links_NotFileNameChange.xsl"
if errorlevel 1 exit /b 1

java -cp "%JAVA_CP%" %SAXON_MAIN% ^
    -s:"%TEMPDIR%\17-related-links.xml" ^
    -o:"%TEMPDIR%\18-dita-rebeautified.xml" ^
    -xsl:"%XSLDIR%\0009-dita-rebeautify.xsl"
if errorlevel 1 exit /b 1

java -cp "%JAVA_CP%" %SAXON_MAIN% ^
    -s:"%TEMPDIR%\18-dita-rebeautified.xml" ^
    -o:"%XSLDIR%\dummy.xml" ^
    -xsl:"%XSLDIR%\0010-rechapterize.xsl"
if errorlevel 1 exit /b 1
exit /b 0


rem ============================================================
rem XML 입력 처리
rem ============================================================
:PROCESS_XML
set "LANG_DIR=%~1"
set "LANG_NAME=%~2"
set "SPECIAL_MERGE_XSL="

if exist "%CHAPTERDIR%" rd /s /q "%CHAPTERDIR%"
mkdir "%CHAPTERDIR%"
if not exist "%TEMPDIR%" mkdir "%TEMPDIR%"

xcopy "%LANG_DIR%\*.xml" "%CHAPTERDIR%\" /Y >nul

if /I "%LANG_NAME%"=="German" set "SPECIAL_MERGE_XSL=0201-chapter-merge_DE.xsl"
if /I "%LANG_NAME%"=="Serbian" set "SPECIAL_MERGE_XSL=0202-chapter-merge_SL.xsl"
if /I "%LANG_NAME%"=="Russian" set "SPECIAL_MERGE_XSL=0203-chapter-merge_ru.xsl"
if /I "%LANG_NAME%"=="French_Canada" set "SPECIAL_MERGE_XSL=0204-chapter-merge_fr_ca.xsl"

call :BUILD_DITA "%LANG_NAME%" "%SPECIAL_MERGE_XSL%"
if errorlevel 1 (
    echo [ERROR] XML to DITA build failed: %LANG_NAME%
    exit /b 1
)

if not exist "%TOPICSDIR%\temp_out\%LANG_NAME%" (
    mkdir "%TOPICSDIR%\temp_out\%LANG_NAME%"
)

if exist "%CHAPTERDIR%" rd /s /q "%CHAPTERDIR%"
if exist "%TEMPDIR%" rd /s /q "%TEMPDIR%"

move "%TOPICSDIR%\*.dita" "%TOPICSDIR%\temp_out\%LANG_NAME%\" >nul 2>nul
move "%TOPICSDIR%\*.ditamap" "%TOPICSDIR%\temp_out\%LANG_NAME%\" >nul 2>nul
exit /b 0


rem ============================================================
rem 공통 DITA 생성: 20 ~ 24
rem   %~1 = 언어명
rem   %~2 = 선택한 언어별 chapter merge XSL
rem ============================================================
:BUILD_DITA
set "LANG_NAME=%~1"
set "SPECIAL_MERGE_XSL=%~2"

if not exist "%TEMPDIR%" mkdir "%TEMPDIR%"

rem 20. chapter merge
java -cp "%JAVA_CP%" %SAXON_MAIN% ^
    -s:"%XSLDIR%\bookmap.xml" ^
    -o:"%TEMPDIR%\20-chapter-merged.xml" ^
    -xsl:"%XSLDIR%\0200-chapter-merge.xsl"
if errorlevel 1 exit /b 1

rem 언어별 merge가 있으면 공통 결과를 교체한다.
if defined SPECIAL_MERGE_XSL (
    echo [%LANG_NAME% MODE]

    if exist "%BASEDIR%temp_work" rd /s /q "%BASEDIR%temp_work"
    mkdir "%BASEDIR%temp_work"

    java -cp "%JAVA_CP%" %SAXON_MAIN% ^
        -s:"%TEMPDIR%\20-chapter-merged.xml" ^
        -o:"%BASEDIR%temp_work\20-chapter-merged.xml" ^
        -xsl:"%XSLDIR%\%SPECIAL_MERGE_XSL%"
    if errorlevel 1 exit /b 1

    move /y "%BASEDIR%temp_work\20-chapter-merged.xml" "%TEMPDIR%\20-chapter-merged.xml" >nul
    if errorlevel 1 exit /b 1
    rd /s /q "%BASEDIR%temp_work"
)

rem 21. topic 분리
java -cp "%JAVA_CP%" %SAXON_MAIN% ^
    -s:"%TEMPDIR%\20-chapter-merged.xml" ^
    -o:"%XSLDIR%\dummy.xml" ^
    -xsl:"%XSLDIR%\0210-topicalize.xsl"
if errorlevel 1 exit /b 1

rem 22. map 생성
java -cp "%JAVA_CP%" %SAXON_MAIN% ^
    -s:"%TEMPDIR%\20-chapter-merged.xml" ^
    -o:"%TEMPDIR%\tempmap.xml" ^
    -xsl:"%XSLDIR%\0220-make-map.xsl"
if errorlevel 1 exit /b 1

rem metadata 삽입
java -cp "%JAVA_CP%" %SAXON_MAIN% ^
    -s:"%TEMPDIR%\tempmap.xml" ^
    -o:"%TEMPDIR%\11-metadata_Insert.xml" ^
    -xsl:"%XSLDIR%\0225-chapter_metadata_Insert_all.xsl" ^
    langName=%LANG_NAME%
if errorlevel 1 exit /b 1

rem 23. map beautify
java -cp "%JAVA_CP%" %SAXON_MAIN% ^
    -s:"%TEMPDIR%\11-metadata_Insert.xml" ^
    -o:"%XSLDIR%\dummy.xml" ^
    -xsl:"%XSLDIR%\0230-map-beautify_meta_all.xsl"
if errorlevel 1 exit /b 1

rem 24. DITA beautify
java -cp "%JAVA_CP%" %SAXON_MAIN% ^
    -catalog:"%XSLDIR%\catalog.xml" ^
    -s:"%TEMPDIR%\11-metadata_Insert.xml" ^
    -o:"%XSLDIR%\dummy.xml" ^
    -xsl:"%XSLDIR%\0240-dita-beautify.xsl"
if errorlevel 1 exit /b 1
exit /b 0




