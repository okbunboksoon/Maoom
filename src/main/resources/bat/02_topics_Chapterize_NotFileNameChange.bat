@echo off
setlocal enabledelayedexpansion

set SAXON=%~dp0
set CLASSPATH=%SAXON%lib\saxon-he-12.4.jar;%CLASSPATH%
set CLASSPATH=%SAXON%lib\xmlresolver-5.2.2.jar;%CLASSPATH%

if not exist temp mkdir temp

rem ===============================================
rem 파일명 검사
rem ===============================================
set LOG=temp\filename_check.log
set ERROR_FOUND=0

echo ===== File Name Check ===== > %LOG%

for %%F in (topics\*.dita topics\*.ditamap) do (
    set "fname=%%~nxF"

rem    1. 공백 체크
    if not "!fname!"=="!fname: =!" (
        echo [SPACE] %%F >> %LOG%
        set ERROR_FOUND=1
    )

rem     2. 비ASCII 체크 (공백 제외)
    set "check=!fname: =!"

    for /f "tokens=* delims=ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789._-" %%A in ("!check!") do (
        if not "%%A"=="" (
            echo [NON_ASCII] !fname! >> %LOG%
            set ERROR_FOUND=1
        )
    )
)

rem 문제 있으면 중단
if !ERROR_FOUND!==1 (
    echo.
    echo File name error detected. Batch stopped.
    echo Check log: %LOG%
    pause
    exit /b
)

rem ===============================================
rem dummy.xml 재생성
rem ===============================================
cd /d "%~dp0"

if exist "xsl\dummy.xml" del /f /q "xsl\dummy.xml"

echo ^<?xml version="1.0" encoding="UTF-8"?^> > "xsl\dummy.xml"
echo ^<dummy/^> >> "xsl\dummy.xml"

rem ===============================================
rem 실제 배치 돌아가는 구간
rem ===============================================
echo Please wait a moment!
echo Processing... 

rem topics 폴더의 ditamap을 수집하고 mapname을 붙여 temp\0000-doctype-removed.xml 생성
java net.sf.saxon.Transform -catalog:xsl\catalog.xml 	-s:xsl\dummy.xml  										-o:xsl\dummy.xml  										-xsl:xsl\0000-doctype-remove.xsl
rem DITA namespace와 불필요한 processing-instruction 제거
java net.sf.saxon.Transform 							-s:temp\0000-doctype-removed.xml  					-o:temp\0001-namespace-removed.xml  				-xsl:xsl\0001-namespace-remove.xsl
rem map title을 기준으로 KIA metadata(platform/base/year/lang/model) 삽입
java net.sf.saxon.Transform 							-s:temp\0001-namespace-removed.xml 					-o:temp\0100-metadata_Insert.xml 	 					-xsl:xsl\0100-metadata_Insert.xsl
rem 1레벨 topicmeta/navtitle 기준으로 WebHelp tile SVG 이미지 보정
java net.sf.saxon.Transform 							-s:temp\0100-metadata_Insert.xml 						-o:temp\0110-svg_update.xml  							-xsl:xsl\0110-svg_update.xsl
rem topicmeta 제거 후 topicref href 정리 및 chapter filename 생성
java net.sf.saxon.Transform 							-s:temp\0110-svg_update.xml    						-o:temp\0002-toc-created.xml  							-xsl:xsl\0002-toc-create.xsl
rem chapter filename 목록만 가진 bookmap.xml 생성
java net.sf.saxon.Transform 							-s:temp\0002-toc-created.xml  							-o:xsl\bookmap.xml  									-xsl:xsl\0003-bookmap-create.xsl
rem topicref href가 가리키는 topics/*.dita 내용을 map 안으로 병합
java net.sf.saxon.Transform -catalog:xsl\catalog.xml	-s:temp\0002-toc-created.xml  							-o:temp\0004-topic-merged.xml  						-xsl:xsl\0004-topic-merge.xsl
rem table 안에 tgroup이 여러 개이면 하나의 tgroup으로 병합
java net.sf.saxon.Transform 							-s:temp\0004-topic-merged.xml						-o:temp\0130-merge_tgroup.xml 						-xsl:xsl\0130-merge_tgroup.xsl
rem image 파일명/확장자/scale 보정 및 불필요한 image 속성 제거
java net.sf.saxon.Transform 							-s:temp\0130-merge_tgroup.xml						-o:temp\0160-image_attr.xml 							-xsl:xsl\0160-image_attr.xsl
rem b/uicontrol/term 등 refinement 태그와 일부 표기 규칙 정리
java net.sf.saxon.Transform 							-s:temp\0160-image_attr.xml 	 						-o:temp\0170-refinement_tag.xml 						-xsl:xsl\0170-refinement_tag.xsl
rem 용어 사전 기준으로 translate="no" term 태그 자동 추가
java net.sf.saxon.Transform 							-s:temp\0170-refinement_tag.xml 						-o:temp\0180-translate_no_tagging.xml					 -xsl:xsl\0180-translate_no_tagging.xsl
rem outputclass의 review/legal 및 modified 작업용 속성 제거
java net.sf.saxon.Transform -catalog:xsl\catalog.xml	-s:temp\0180-translate_no_tagging.xml  				-o:temp\0400-remove_review.xml  						-xsl:xsl\0400-remove_review.xsl
rem 병합/보정 후 남은 namespace와 불필요한 PI 재정리
java net.sf.saxon.Transform 							-s:temp\0400-remove_review.xml   						-o:temp\0005-namespace-remove.xml  					-xsl:xsl\0005-namespace-remove.xsl
rem 파일명 변경 없이 topic id를 nid로 보관하고 원래 파일명을 oid로 설정
java net.sf.saxon.Transform 							-s:temp\0005-namespace-remove.xml  					-o:temp\0006-id-clean_NotFileNameChange.xml  		-xsl:xsl\0006-id-clean_NotFileNameChange.xsl
rem 파일명 변경 없이 xref href를 기존 파일명/id 기준으로 재작성
java net.sf.saxon.Transform 							-s:temp\0006-id-clean_NotFileNameChange.xml  		-o:temp\0007-xref-clean_NotFileNameChange.xml  		-xsl:xsl\0007-xref-clean_NotFileNameChange.xsl
rem 파일명 변경 없이 parent/child related-links 생성
java net.sf.saxon.Transform 							-s:temp\0007-xref-clean_NotFileNameChange.xml  		-o:temp\0008-related-links_NotFileNameChange.xml  	-xsl:xsl\0008-related-links_NotFileNameChange.xsl
rem bookmap 기준으로 topicref에 filename/chapnum을 다시 붙이고 출력 정리
java net.sf.saxon.Transform 							-s:temp\0008-related-links_NotFileNameChange.xml  	-o:temp\0009-dita-rebeautify.xml  						-xsl:xsl\0009-dita-rebeautify.xsl
rem 최종 map/topicref를 chapter/*.xml 파일로 분리 생성
java net.sf.saxon.Transform 							-s:temp\0009-dita-rebeautify.xml 						-o:xsl\dummy.xml										-xsl:xsl\0010-rechapterize.xsl

copy "%~dp0xsl\bookmap.xml" "%~dp0bookmap.xml" /Y > NUL

rd /q/s topics
rem rd /q/s temp
echo Done.
pause