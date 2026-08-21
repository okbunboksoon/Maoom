@echo off

set SAXON=%~dp0
set CLASSPATH=%SAXON%lib\saxon-ee-10.0.jar;%CLASSPATH%
set CLASSPATH=%SAXON%lib\xml-resolver-1.2.jar;%CLASSPATH%

REM 날짜+시간 이름 만들기(형식: YYYYMMDD_HHMM)
for /f %%I in ('powershell -NoProfile -Command "Get-Date -Format yyyyMMdd_HHmm"') do set datetime=%%I

REM 백업 경로 설정
set backupDir=xsl\_backupDB
 
REM 원본 파일들
set sourceFile1=xsl\asis-tobe_us.xml
set sourceFile2=xsl\asis-tobe_eu.xml
set sourceFile3=xsl\asis-tobe_exclude.xml
set sourceFile4=xsl\asis-tobe_eu_rg.xml

REM 백업 파일 경로
set backupFile1=%backupDir%\%datetime%_asis-tobe_us.xml
set backupFile2=%backupDir%\%datetime%_asis-tobe_eu.xml
set backupFile3=%backupDir%\%datetime%_asis-tobe_exclude.xml
set backupFile4=%backupDir%\%datetime%_asis-tobe_eu_rg.xml

REM 백업 폴더 없으면 생성
if not exist "%backupDir%" mkdir "%backupDir%"

REM 파일 백업
copy "%sourceFile1%" "%backupFile1%" >NUL
copy "%sourceFile2%" "%backupFile2%" >NUL
copy "%sourceFile3%" "%backupFile3%" >NUL
copy "%sourceFile4%" "%backupFile4%" >NUL

echo Please wait a moment!
echo Processing...

rem 1. REGION 입력
if "%~1"=="" (
    set /p REGION=Enter region ^(EU / EU_RG / US / exclude^):
) else (
    set REGION=%~1
)

rem 2. 파일명 분기
if /I "%REGION%"=="EU" (
    set MAPNAME=CV_EV_PE_en_GB_25MY.ditamap
) else if /I "%REGION%"=="EU_RG" (
    set MAPNAME=CV_EV_RG_en_GB_25MY.ditamap
) else if /I "%REGION%"=="US" (
    set MAPNAME=CVa_EV_PE_en_US_25MY.ditamap
) else if /I "%REGION%"=="exclude" (
    set MAPNAME=CVa_EV_PE_en_exclude_25MY.ditamap
) else (
    echo Invalid region. EU or EU_RG or US or exclude
    pause
    exit /b
)

rem 3. topics 폴더 없으면 생성
if not exist "topics" mkdir topics

rem 4. DITA 생성 (기존 로직)
java net.sf.saxon.Transform -catalog:xsl\catalog.xml 	-s:temp\excel.xml  							-o:topics\t00000.dita				-xsl:xsl\0100-excel-to-xml-update.xsl		region=%REGION%

rem 5. DITAMAP 생성
echo Creating %MAPNAME%...

(
echo ^<?xml version="1.0" encoding="UTF-8"?^>
echo ^<!DOCTYPE map PUBLIC "-//OASIS//DTD DITA Map//EN" "map.dtd"^>
echo ^<map^>
echo     ^<title^>%MAPNAME%^</title^>
echo     ^<topicref href="t00000.dita"/^>
echo ^</map^>
) > "topics\%MAPNAME%"

java net.sf.saxon.Transform -catalog:xsl\catalog.xml 	-s:xsl\dummy.xml  							-o:xsl\dummy.xml  							-xsl:xsl\0000-doctype-remove.xsl
java net.sf.saxon.Transform 							-s:temp\0000-doctype-removed.xml  			-o:temp\10-namespace-removed.xml  		-xsl:xsl\0001-namespace-remove.xsl
java net.sf.saxon.Transform 							-s:temp\10-namespace-removed.xml  		-o:temp\11-toc-created.xml  				-xsl:xsl\0002-toc-create.xsl
java net.sf.saxon.Transform 							-s:temp\11-toc-created.xml  				-o:xsl\bookmap.xml  						-xsl:xsl\0003-bookmap-create.xsl
java net.sf.saxon.Transform -catalog:xsl\catalog.xml	-s:temp\11-toc-created.xml  				-o:temp\13-topic-merged.xml  				-xsl:xsl\0004-topic-merge.xsl
java net.sf.saxon.Transform 							-s:temp\13-topic-merged.xml				-o:temp\26-kus-1st-grouped.xml			-xsl:xsl\0260-kus-1st-group.xsl
java net.sf.saxon.Transform 							-s:temp\26-kus-1st-grouped.xml			-o:temp\27-kus-2nd-grouped.xml			-xsl:xsl\0270-kus-2nd-group.xsl
java net.sf.saxon.Transform 							-s:temp\27-kus-2nd-grouped.xml			-o:temp\28-kus-3rd-grouped.xml			-xsl:xsl\0280-kus-3rd-group.xsl
java net.sf.saxon.Transform 							-s:temp\28-kus-3rd-grouped.xml			-o:temp\29-kus-text-normalized.xml		-xsl:xsl\0290-kus-text-normalize.xsl
java net.sf.saxon.Transform 							-s:temp\29-kus-text-normalized.xml			-o:temp\30-kus-inline-normalized.xml		-xsl:xsl\0300-kus-inline-normalize.xsl
java net.sf.saxon.Transform 							-s:temp\30-kus-inline-normalized.xml		-o:temp\31-kus-beautified.xml				-xsl:xsl\0310-kus-beautify.xsl

java net.sf.saxon.Transform 							-s:temp\30-kus-inline-normalized.xml		-o:temp\32-kus-pair-extracted.xml			-xsl:xsl\0320-kus-pair-extract_ber.xsl
java net.sf.saxon.Transform 							-s:temp\32-kus-pair-extracted.xml			-o:temp\asis-tobe2.xml						-xsl:xsl\0330-kus-db-make_ber.xsl

rem java net.sf.saxon.Transform 						-s:temp\asis-tobe2.xml						-o:temp\multi-case.xml						-xsl:xsl\40-make-multi-case.xsl
java net.sf.saxon.Transform 							-s:temp\asis-tobe2.xml																	-xsl:xsl\0420-kus-db-clean_ber.xsl

rem 260331 eu us 분기 설정 = 0430-kus-db-update_ber.xsl
rem java net.sf.saxon.Transform 							-s:xsl\asis-tobe.xml						-o:xsl\asis-tobe.xml						-xsl:xsl\43-kus-db-update.xsl

java net.sf.saxon.Transform								-s:xsl\asis-tobe_us.xml					-o:xsl\asis-tobe_us.xml					-xsl:xsl\0430-kus-db-update_ber.xsl	 region=us
java net.sf.saxon.Transform								-s:xsl\asis-tobe_eu.xml					-o:xsl\asis-tobe_eu.xml					-xsl:xsl\0430-kus-db-update_ber.xsl	 region=eu
java net.sf.saxon.Transform								-s:xsl\asis-tobe_eu_rg.xml				-o:xsl\asis-tobe_eu_rg.xml				-xsl:xsl\0430-kus-db-update_ber.xsl	 region=eu_rg
java net.sf.saxon.Transform								-s:xsl\asis-tobe_exclude.xml				-o:xsl\asis-tobe_exclude.xml			-xsl:xsl\0430-kus-db-update_ber.xsl	 region=exclude

REM temp 안에서 asis-tobe_eu/us/exclude.xml만 빼고 나머지 .xml 삭제
for %%F in (temp\*.xml) do (
    if /I not "%%~nxF"=="asis-tobe_eu.xml" if /I not "%%~nxF"=="asis-tobe_eu_rg.xml" if /I not "%%~nxF"=="asis-tobe_us.xml"  if /I not "%%~nxF"=="asis-tobe_exclude.xml" del "%%F"
)

rem del temp\asis-tobe2.xml >NUL
rem copy temp\asis-tobe.xml xsl >NUL
rem rd /q/s temp

echo Done.
if not "%~1"=="" exit /b 0
pause
