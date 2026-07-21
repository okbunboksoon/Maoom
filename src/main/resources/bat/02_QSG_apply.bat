@echo off
setlocal EnableExtensions EnableDelayedExpansion
chcp 65001 >nul

set "BASEDIR=%~dp0"
set "SAXON=%BASEDIR%"
set "CLASSPATH=%SAXON%lib\saxon-ee-10.0.jar;%CLASSPATH%"
set "CLASSPATH=%SAXON%lib\xml-resolver-1.2.jar;%CLASSPATH%"

set "SOURCE_TOPICS=%BASEDIR%topics"
set "RESULT_DIR=%BASEDIR%result_Folder"
set "WORK_ROOT=%BASEDIR%_qsg_work"
set "LANG_MAP=%BASEDIR%lang_code_mapping.txt"
set "TEMP_DIR=%BASEDIR%temp"
set "PUSHD_DONE=0"
set "EXIT_CODE=1"

pushd "%BASEDIR%" || (
  echo [ERROR] pushd failed: "%BASEDIR%"
  goto :END
)
set "PUSHD_DONE=1"

if not exist "%SOURCE_TOPICS%" (
  echo [ERROR] topics folder not found: "%SOURCE_TOPICS%"
  goto :END
)

if not exist "%LANG_MAP%" (
  echo [ERROR] lang_code_mapping.txt not found: "%LANG_MAP%"
  goto :END
)

set "INPUT_LANG=%~1"
if "%INPUT_LANG%"=="" (
  set /p "INPUT_LANG=Language code: "
)

if "%INPUT_LANG%"=="" (
  echo [ERROR] Language code is empty.
  goto :END
)

set "LANG_CODE=%INPUT_LANG%"
set "LANG_NAME="
for /f "usebackq eol=# tokens=1,2 delims==" %%L in ("%LANG_MAP%") do (
  if /I "%%~M"=="%LANG_CODE%" set "LANG_NAME=%%~L"
)

if "%LANG_NAME%"=="" (
  echo [ERROR] Language code not found in lang_code_mapping.txt: "%LANG_CODE%"
  goto :END
)

set "LANG_FILE_CODE=%LANG_CODE:-=_%"
set "LANG_WORK=%WORK_ROOT%\%LANG_NAME%"
set "LANG_RESULT=%RESULT_DIR%\%LANG_NAME%"

echo Please wait a moment!
echo Processing...
echo [INFO] Language      = %LANG_NAME% (%LANG_CODE%)
echo [INFO] File code     = %LANG_FILE_CODE%
echo [INFO] Source topics = %SOURCE_TOPICS%
echo [INFO] Work folder   = %LANG_WORK%
echo [INFO] Result folder = %LANG_RESULT%
echo.

if not exist "%RESULT_DIR%" mkdir "%RESULT_DIR%"
if exist "%WORK_ROOT%" rd /s /q "%WORK_ROOT%"
mkdir "%WORK_ROOT%"

if exist "%LANG_WORK%" rd /s /q "%LANG_WORK%"
mkdir "%LANG_WORK%"

robocopy "%SOURCE_TOPICS%" "%LANG_WORK%" /E /NFL /NDL /NJH /NJS /NC /NS >nul
if errorlevel 8 (
  echo [ERROR] Failed to copy topics to work folder: "%LANG_WORK%"
  goto :END
)

call :PREPARE_DITAMAP
if errorlevel 1 goto :END

if exist "%TEMP_DIR%" rd /s /q "%TEMP_DIR%"
mkdir "%TEMP_DIR%"

echo ^<dummy/^> > "xsl\dummy.xml"

set "LANG_WORK_NO_TRAIL=%LANG_WORK%"
if "%LANG_WORK_NO_TRAIL:~-1%"=="\" set "LANG_WORK_NO_TRAIL=%LANG_WORK_NO_TRAIL:~0,-1%"
set "TOPICS_URI=file:///%LANG_WORK_NO_TRAIL:\=/%/"

java net.sf.saxon.Transform -catalog:xsl\catalog.xml 			-s:xsl\dummy.xml 			 			 			-o:xsl\dummy.xml 								-xsl:xsl\qsg-0000-doctype-remove.xsl 			TOPICS_URI="%TOPICS_URI%"
if errorlevel 1 goto :LANG_ERROR
java net.sf.saxon.Transform 									-s:temp\09-doctype-removed.xml 					-o:temp\10-namespace-removed.xml			-xsl:xsl\0001-namespace-remove.xsl
if errorlevel 1 goto :LANG_ERROR
java net.sf.saxon.Transform 									-s:temp\10-namespace-removed.xml 				-o:temp\11-toc-created.xml 					-xsl:xsl\0002-toc-create.xsl
if errorlevel 1 goto :LANG_ERROR
java net.sf.saxon.Transform 									-s:temp\11-toc-created.xml 						-o:xsl\bookmap.xml 							-xsl:xsl\0003-bookmap-create.xsl
if errorlevel 1 goto :LANG_ERROR
java net.sf.saxon.Transform 	-catalog:xsl\catalog.xml			-s:temp\11-toc-created.xml							-o:temp\13-topic-merged.xml 					-xsl:xsl\qsg-0004-topic-merge.xsl 				TOPICS_URI="%TOPICS_URI%"
if errorlevel 1 goto :LANG_ERROR
java net.sf.saxon.Transform 									-s:temp\13-topic-merged.xml 						-o:temp\30-kus-inline-normalized.xml 			-xsl:xsl\0300-kus-inline-normalize.xsl
if errorlevel 1 goto :LANG_ERROR
java net.sf.saxon.Transform									 -s:temp\30-kus-inline-normalized.xml 				-o:temp\31-kus-beautified.xml 					-xsl:xsl\0310-kus-beautify.xsl
if errorlevel 1 goto :LANG_ERROR
java net.sf.saxon.Transform									 -s:temp\31-kus-beautified.xml 						-o:temp\34-kus-db-applied.xml 				-xsl:xsl\0340-QSG-db-apply.xsl flag=on 			TARGET_LANG="%LANG_CODE%"
if errorlevel 1 goto :LANG_ERROR
java net.sf.saxon.Transform									 -s:temp\34-kus-db-applied.xml 					-o:temp\35-kus-beautified.xml 					-xsl:xsl\0310-kus-beautify.xsl
if errorlevel 1 goto :LANG_ERROR
java net.sf.saxon.Transform									 -s:temp\35-kus-beautified.xml 						-o:xsl\dummy.xml 								-xsl:xsl\qsg-0210-topicalize.xsl 					OUT_TOPICS_URI="%TOPICS_URI%"
if errorlevel 1 goto :LANG_ERROR
java net.sf.saxon.Transform	-catalog:xsl\catalog.xml 		-s:temp\35-kus-beautified.xml						-o:xsl\dummy.xml 								-xsl:xsl\qsg-0240-dita-beautify.xsl 				TOPICS_URI="%TOPICS_URI%" OUT_TOPICS_URI="%TOPICS_URI%"
if errorlevel 1 goto :LANG_ERROR

copy /y "xsl\bookmap.xml" "%LANG_WORK%\bookmap.xml" >nul

if exist "%LANG_RESULT%" rd /s /q "%LANG_RESULT%"
mkdir "%LANG_RESULT%"
robocopy "%LANG_WORK%" "%LANG_RESULT%" /E /NFL /NDL /NJH /NJS /NC /NS >nul
if errorlevel 8 goto :LANG_ERROR

echo [OK] Created: "%LANG_RESULT%"
set "EXIT_CODE=0"
goto :END

:PREPARE_DITAMAP
set "MAP_COUNT=0"
set "MAP_SRC="
for %%F in ("%LANG_WORK%\*.ditamap") do (
  set /a MAP_COUNT+=1
  set "MAP_SRC=%%~fF"
  set "MAP_NAME=%%~nxF"
)

if "%MAP_COUNT%"=="0" (
  echo [ERROR] No ditamap found in "%LANG_WORK%"
  exit /b 1
)

if not "%MAP_COUNT%"=="1" (
  echo [ERROR] Expected one ditamap, found %MAP_COUNT% in "%LANG_WORK%"
  exit /b 1
)

set "MAP_NEW_NAME=!MAP_NAME:en_GB=%LANG_FILE_CODE%!"
if "!MAP_NEW_NAME!"=="!MAP_NAME!" (
  echo [WARN] ditamap filename does not contain en_GB: "!MAP_NAME!"
)

if not "!MAP_NEW_NAME!"=="!MAP_NAME!" (
  ren "!MAP_SRC!" "!MAP_NEW_NAME!"
  if errorlevel 1 (
    echo [ERROR] Failed to rename ditamap to "!MAP_NEW_NAME!"
    exit /b 1
  )
)

powershell -NoProfile -ExecutionPolicy Bypass -Command "$p = Join-Path $env:LANG_WORK $env:MAP_NEW_NAME; $q = [char]34; $pattern = 'xml:lang=' + $q + '[^' + $q + ']*' + $q; $replacement = 'xml:lang=' + $q + $env:LANG_CODE + $q; $c = [System.IO.File]::ReadAllText($p, [System.Text.Encoding]::UTF8); $c = [regex]::Replace($c, $pattern, $replacement); [System.IO.File]::WriteAllText($p, $c, (New-Object System.Text.UTF8Encoding($false)))"
if errorlevel 1 (
  echo [ERROR] Failed to update xml:lang in ditamap.
  exit /b 1
)

echo [INFO] Prepared ditamap: !MAP_NEW_NAME!
exit /b 0

:LANG_ERROR
echo [ERROR] Failed: %LANG_NAME% (%LANG_CODE%)
set "EXIT_CODE=1"
goto :END

:END
if exist "%TEMP_DIR%" rd /s /q "%TEMP_DIR%"
if exist "%WORK_ROOT%" rd /s /q "%WORK_ROOT%"
echo ^<dummy/^> > "xsl\dummy.xml"
if "%PUSHD_DONE%"=="1" popd
exit /b %EXIT_CODE%

