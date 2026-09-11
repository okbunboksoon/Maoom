@echo off
setlocal enabledelayedexpansion

rem 배치 파일 위치를 기준으로 Saxon 라이브러리 경로를 잡는다.
set SAXON=%~dp0
set CLASSPATH=%SAXON%lib\saxon-ee-10.0.jar;%CLASSPATH%
set CLASSPATH=%SAXON%lib\xml-resolver-1.2.jar;%CLASSPATH%

if not exist temp mkdir temp

rem 기본 옵션값이다. 명령줄 인자로 Y 또는 모드를 넘기면 아래에서 필요한 값만 바꾼다.
set "FILE_NAME_CHANGE=Y"
set "FILE_NAME_MODE=TITLE_PREFIX"
set "TITLE_FILE_NAME_PREFIX=Y"
set "INPUT_TYPE="
set "OUTPUT_TYPE="
set "REMOVE_SIMPLE=N"
set "REMOVE_SIMPLE_OPERATION=N"
set "REMOVE_DELIVERY_TARGET=N"
set "DELETE_DRAFT=N"
set "TEXT_DB_APPLY=N"
set "NOTE_DB_APPLY=N"
rem BER DB 반영 옵션의 기본값을 미적용으로 설정한다.
set "BER_DB_APPLY=N"
set "FORBIDDEN_QC_REPORT=N"

rem 명령줄 옵션을 검사한다. findstr 결과가 맞으면 관련 플래그를 함께 켠다.
echo %* | findstr /I /C:"FILE_NAME_CHANGE=N" >NUL && (
    set "FILE_NAME_CHANGE=N"
    set "FILE_NAME_MODE=DEFAULT"
    set "TITLE_FILE_NAME_PREFIX=N"
)
echo %* | findstr /I /C:"FILE_NAME_CHANGE=Y" >NUL && (
    set "FILE_NAME_CHANGE=Y"
    set "FILE_NAME_MODE=T00000"
    set "TITLE_FILE_NAME_PREFIX=N"
)
echo %* | findstr /I /C:"FILE_NAME_CHANGE=TITLE_PREFIX" >NUL && (
    set "FILE_NAME_CHANGE=Y"
    set "FILE_NAME_MODE=TITLE_PREFIX"
    set "TITLE_FILE_NAME_PREFIX=Y"
)
echo %* | findstr /I /C:"TITLE_FILE_NAME_PREFIX=Y" >NUL && (
    set "FILE_NAME_CHANGE=Y"
    set "FILE_NAME_MODE=TITLE_PREFIX"
    set "TITLE_FILE_NAME_PREFIX=Y"
)
echo %* | findstr /I /C:"INPUT_TYPE=xml" >NUL && set "INPUT_TYPE=xml"
echo %* | findstr /I /C:"INPUT_TYPE=dita" >NUL && set "INPUT_TYPE=dita"
echo %* | findstr /I /C:"OUTPUT_TYPE=xml" >NUL && set "OUTPUT_TYPE=xml"
echo %* | findstr /I /C:"OUTPUT_TYPE=dita" >NUL && set "OUTPUT_TYPE=dita"
echo %* | findstr /I /C:"REMOVE_SIMPLE=Y" >NUL && (
    set "REMOVE_SIMPLE=Y"
    set "REMOVE_SIMPLE_OPERATION=Y"
    set "REMOVE_DELIVERY_TARGET=Y"
)
echo %* | findstr /I /C:"REMOVE_SIMPLE_OPERATION=Y" >NUL && (
    set "REMOVE_SIMPLE=Y"
    set "REMOVE_SIMPLE_OPERATION=Y"
)
echo %* | findstr /I /C:"REMOVE_DELIVERY_TARGET=Y" >NUL && (
    set "REMOVE_SIMPLE=Y"
    set "REMOVE_DELIVERY_TARGET=Y"
)
echo %* | findstr /I /C:"DELETE_DRAFT=Y" >NUL && set "DELETE_DRAFT=Y"
echo %* | findstr /I /C:"TEXT_DB_APPLY=Y" >NUL && set "TEXT_DB_APPLY=Y"
echo %* | findstr /I /C:"NOTE_DB_APPLY=Y" >NUL && set "NOTE_DB_APPLY=Y"
rem BER_DB_APPLY=Y 인자가 있으면 BER DB 반영 옵션을 적용으로 설정한다.
echo %* | findstr /I /C:"BER_DB_APPLY=Y" >NUL && set "BER_DB_APPLY=Y"
echo %* | findstr /I /C:"FORBIDDEN_QC_REPORT=Y" >NUL && set "FORBIDDEN_QC_REPORT=Y"

rem 실행 옵션은 temp\option_check.log에 남겨 문제 재현 시 확인할 수 있게 한다.
set OPTION_LOG=temp\option_check.log
echo ===== Options ===== > %OPTION_LOG%
echo RAW_ARGS=%* >> %OPTION_LOG%
echo FILE_NAME_CHANGE=!FILE_NAME_CHANGE! >> %OPTION_LOG%
echo FILE_NAME_MODE=!FILE_NAME_MODE! >> %OPTION_LOG%
echo TITLE_FILE_NAME_PREFIX=!TITLE_FILE_NAME_PREFIX! >> %OPTION_LOG%
echo INPUT_TYPE=!INPUT_TYPE! >> %OPTION_LOG%
echo OUTPUT_TYPE=!OUTPUT_TYPE! >> %OPTION_LOG%
echo REMOVE_SIMPLE=!REMOVE_SIMPLE! >> %OPTION_LOG%
echo REMOVE_SIMPLE_OPERATION=!REMOVE_SIMPLE_OPERATION! >> %OPTION_LOG%
echo REMOVE_DELIVERY_TARGET=!REMOVE_DELIVERY_TARGET! >> %OPTION_LOG%
echo DELETE_DRAFT=!DELETE_DRAFT! >> %OPTION_LOG%
echo TEXT_DB_APPLY=!TEXT_DB_APPLY! >> %OPTION_LOG%
echo NOTE_DB_APPLY=!NOTE_DB_APPLY! >> %OPTION_LOG%
echo BER_DB_APPLY=!BER_DB_APPLY! >> %OPTION_LOG%
echo FORBIDDEN_QC_REPORT=!FORBIDDEN_QC_REPORT! >> %OPTION_LOG%

set LOG=temp\filename_check.log
set ERROR_FOUND=0

echo ===== File Name Check ===== > %LOG%

rem topics 폴더의 DITA 파일명에 공백이나 허용되지 않는 문자가 있으면 변환을 중단한다.
for %%F in (topics\*.dita topics\*.ditamap) do (
    set "fname=%%~nxF"

    if not "!fname!"=="!fname: =!" (
        echo [SPACE] %%F >> %LOG%
        set ERROR_FOUND=1
    )

    set "check=!fname: =!"

    for /f "tokens=* delims=ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789._-" %%A in ("!check!") do (
        if not "%%A"=="" (
            echo [NON_ASCII] !fname! >> %LOG%
            set ERROR_FOUND=1
        )
    )
)

if !ERROR_FOUND!==1 (
    echo.
    echo File name error detected. Batch stopped.
    echo Check log: %LOG%
    pause
    exit /b
)

cd /d "%~dp0"

if exist "xsl\dummy.xml" del /f /q "xsl\dummy.xml"

echo ^<?xml version="1.0" encoding="UTF-8"?^> > "xsl\dummy.xml"
echo ^<dummy/^> >> "xsl\dummy.xml"


if /I "!TEXT_DB_APPLY!"=="Y" goto :PREPARE_DB
if /I "!NOTE_DB_APPLY!"=="Y" goto :PREPARE_DB
goto :DB_READY

:PREPARE_DB
rem DB 적용 옵션이 켜진 경우 ditamap 파일명으로 국문/영문 DB를 자동 선택한다.
set "DITAMAP_NAME="
for %%F in ("topics\*.ditamap") do (
    if exist "%%~fF" if not defined DITAMAP_NAME set "DITAMAP_NAME=%%~nxF"
)

if not defined DITAMAP_NAME (
    echo No ditamap file was found in the topics folder.
    pause
    exit /b 1
)

echo(!DITAMAP_NAME!| findstr /i "KO" >NUL
if errorlevel 1 (
    set "SENTENCE_DB=asis-tobe_eg.xml"
    set "NOTE_DB=note_db_eg.xml"
    set "DB_LANGUAGE=EG"
) else (
    set "SENTENCE_DB=asis-tobe_ko.xml"
    set "NOTE_DB=note_db_ko.xml"
    set "DB_LANGUAGE=KO"
)

if not exist "xsl\!SENTENCE_DB!" (
    echo Sentence DB file not found: xsl\!SENTENCE_DB!
    pause
    exit /b 1
)
if not exist "xsl\!NOTE_DB!" (
    echo Note DB file not found: xsl\!NOTE_DB!
    pause
    exit /b 1
)

copy /y "xsl\!SENTENCE_DB!" "xsl\asis-tobe.xml" >NUL
if errorlevel 1 (
    echo Failed to prepare sentence DB: xsl\!SENTENCE_DB!
    pause
    exit /b 1
)
copy /y "xsl\!NOTE_DB!" "xsl\note_db.xml" >NUL
if errorlevel 1 (
    echo Failed to prepare note DB: xsl\!NOTE_DB!
    pause
    exit /b 1
)
echo DITAMAP_NAME=!DITAMAP_NAME! >> %OPTION_LOG%
echo DB_LANGUAGE=!DB_LANGUAGE! >> %OPTION_LOG%
echo SENTENCE_DB=!SENTENCE_DB! >> %OPTION_LOG%
echo NOTE_DB=!NOTE_DB! >> %OPTION_LOG%

:DB_READY

echo Please wait a moment!
echo Processing... 

rem 아래 변환 단계는 각 XSL 결과를 temp 폴더에 순서대로 저장하고 실패 시 즉시 종료한다.
rem 0000-doctype-remove.xsl: 입력 ditamap과 DITA의 DOCTYPE을 제거해 첫 번째 임시 XML을 생성한다.
java net.sf.saxon.Transform -catalog:xsl\catalog.xml 						-s:xsl\dummy.xml  										-o:xsl\dummy.xml  												-xsl:xsl\0000-doctype-remove.xsl
if errorlevel 1 exit /b !errorlevel!
rem 0001-namespace-remove.xsl: 병합 전 XML에 포함된 불필요한 namespace를 제거한다.
java net.sf.saxon.Transform 												-s:temp\0000-doctype-removed.xml  					-o:temp\0001-namespace-removed.xml  						-xsl:xsl\0001-namespace-remove.xsl
if errorlevel 1 exit /b !errorlevel!
rem 0110-svg_update.xsl: SVG 참조와 관련된 정보를 정제 기준에 맞게 갱신한다.
java net.sf.saxon.Transform 												-s:temp\0001-namespace-removed.xml  				-o:temp\0110-svg_update.xml  									-xsl:xsl\0110-svg_update.xsl
if errorlevel 1 exit /b !errorlevel!
rem 0002-toc-create.xsl: 입력 map의 topicref를 기준으로 정제용 목차 구조를 생성한다.
java net.sf.saxon.Transform 												-s:temp\0110-svg_update.xml    						-o:temp\0002-toc-created.xml  									-xsl:xsl\0002-toc-create.xsl
if errorlevel 1 exit /b !errorlevel!
rem 0003-bookmap-create.xsl: 목차 정보를 기준으로 후속 파일 분리에 사용할 bookmap.xml을 생성한다.
java net.sf.saxon.Transform 												-s:temp\0002-toc-created.xml  							-o:xsl\bookmap.xml  											-xsl:xsl\0003-bookmap-create.xsl
if errorlevel 1 exit /b !errorlevel!
rem 0004-topic-merge.xsl: map의 topicref가 참조하는 DITA 파일을 하나의 XML로 병합한다.
java net.sf.saxon.Transform -catalog:xsl\catalog.xml						-s:temp\0002-toc-created.xml  							-o:temp\0004-topic-merged.xml  								-xsl:xsl\0004-topic-merge.xsl
if errorlevel 1 exit /b !errorlevel!
rem map title에 ko_KR과 Quick이 모두 있으면 병합된 topic의 indexterm을 삭제한다.
rem 0004a-remove-ko-quick-indexterm.xsl: map title에 ko_KR과 Quick이 모두 있으면 모든 indexterm을 삭제한다.
java net.sf.saxon.Transform 												-s:temp\0004-topic-merged.xml 						-o:temp\0004a-ko-quick-indexterm-removed.xml 				-xsl:xsl\0004a-remove-ko-quick-indexterm.xsl
if errorlevel 1 exit /b !errorlevel!
rem 병합된 topicref 안에서 문장의 맨 앞과 맨 뒤 불필요한 공백을 제거한다.
rem 0123-trim_sentence_space.xsl: p, title, shortdesc, cmd 문장 맨 앞과 맨 뒤의 불필요한 공백을 제거한다.
java net.sf.saxon.Transform 												-s:temp\0004a-ko-quick-indexterm-removed.xml 		-o:temp\0123-sentence_space_trimmed.xml 					-xsl:xsl\0123-trim_sentence_space.xsl
if errorlevel 1 exit /b !errorlevel!

set "CURRENT_SOURCE=temp\0123-sentence_space_trimmed.xml"
rem TEXT 반영
if /I "!TEXT_DB_APPLY!"=="Y" (
    rem 0340-kus-db-apply.xsl: 선택한 TEXT DB를 기준으로 일치하는 문장을 변경하고 리포트용 상태를 표시한다.
    java net.sf.saxon.Transform 											-s:!CURRENT_SOURCE!  									-o:temp\0340-kus-db-apply.xml								-xsl:xsl\0340-kus-db-apply.xsl flag=on
    if errorlevel 1 exit /b !errorlevel!
    set "CURRENT_SOURCE=temp\0340-kus-db-apply.xml"
    echo TEXT_DB_APPLY applied: !CURRENT_SOURCE! >> %OPTION_LOG%
)

rem NOTE 반영
if /I "!NOTE_DB_APPLY!"=="Y" (
    rem 0340-note-db-apply.xsl: 선택한 NOTE DB를 기준으로 일치하는 note의 type을 변경하고 리포트용 상태를 표시한다.
    java net.sf.saxon.Transform 											-s:!CURRENT_SOURCE!  									-o:temp\0340-note-db-apply.xml								-xsl:xsl\0340-note-db-apply.xsl flag=on
    if errorlevel 1 exit /b !errorlevel!
    set "CURRENT_SOURCE=temp\0340-note-db-apply.xml"
    echo NOTE_DB_APPLY applied: !CURRENT_SOURCE! >> %OPTION_LOG%
)
rem TEXT/NOTE DB 반영 뒤 전체 문장의 공백과 문장부호 표기를 정규화한다.
rem 0290-kus-text-normalize.xsl: TEXT/NOTE DB 반영 뒤 전체 문장의 공백과 문장부호 표기를 정규화한다.
java net.sf.saxon.Transform 												-s:!CURRENT_SOURCE! 									-o:temp\0290-kus-text-normalized.xml 							-xsl:xsl\0290-kus-text-normalize.xsl
if errorlevel 1 exit /b !errorlevel!

set "CURRENT_SOURCE=temp\0290-kus-text-normalized.xml"
rem BER 반영
if /I "!BER_DB_APPLY!"=="Y" (
    rem BER 제외 DB에 등록된 문장을 찾아 BER 변경 대상에서 제외한다.
    rem 0340-kus-db-apply_ber_exclude.xsl: BER 제외 DB에 등록된 문장을 찾아 BER 변경 대상에서 제외한다.
    java net.sf.saxon.Transform 											-s:!CURRENT_SOURCE! 									-o:temp\0340-kus-db-apply_ber_exclude.xml 					-xsl:xsl\0340-kus-db-apply_ber_exclude.xsl flag=on
    if errorlevel 1 exit /b !errorlevel!
    
    rem 제외되지 않은 문장을 지역별 BER DB 기준으로 변경하고 status=ber_changed로 표시한다.
    rem 0340-kus-db-apply_ber.xsl: 제외되지 않은 문장을 지역별 BER DB 기준으로 변경하고 status=ber_changed로 표시한다.
    java net.sf.saxon.Transform 											-s:temp\0340-kus-db-apply_ber_exclude.xml 			-o:temp\0340-kus-db-apply_ber.xml 							-xsl:xsl\0340-kus-db-apply_ber.xsl flag=on
    if errorlevel 1 exit /b !errorlevel!
    set "CURRENT_SOURCE=temp\0340-kus-db-apply_ber.xml"
    echo BER_DB_APPLY applied: !CURRENT_SOURCE! >> %OPTION_LOG%
)

rem placement=break이고 align이 left 또는 right인 image를 찾아 align=center로 변경하고 리포트용으로 표시한다.
rem 0124-center_break_image.xsl: placement=break이고 align이 left 또는 right인 image를 align=center로 변경한다.
java net.sf.saxon.Transform 												-s:!CURRENT_SOURCE! 									-o:temp\0124-break_image_centered.xml						 -xsl:xsl\0124-center_break_image.xsl
if errorlevel 1 exit /b !errorlevel!
set "CURRENT_SOURCE=temp\0124-break_image_centered.xml"
rem 0130-merge_tgroup.xsl: tgroup이 2개 이상인 table의 tgroup을 하나로 병합한다.
java net.sf.saxon.Transform 												-s:!CURRENT_SOURCE!									-o:temp\0130-merge_tgroup.xml 								-xsl:xsl\0130-merge_tgroup.xsl
if errorlevel 1 exit /b !errorlevel!
rem 0160-image_attr.xsl: image href와 scale을 정리하고 불필요한 image 속성을 삭제한다.
java net.sf.saxon.Transform 												-s:temp\0130-merge_tgroup.xml						-o:temp\0160-image_attr.xml 									-xsl:xsl\0160-image_attr.xsl
if errorlevel 1 exit /b !errorlevel!
rem 0170-refinement_tag.xsl: 문서의 요소와 속성에 정제 규칙을 적용한다.
java net.sf.saxon.Transform 												-s:temp\0160-image_attr.xml 	 						-o:temp\0170-refinement_tag.xml 								-xsl:xsl\0170-refinement_tag.xsl
if errorlevel 1 exit /b !errorlevel!
rem 0180-translate_no_tagging.xsl: 영문 유지 대상 term에 translate=no 속성을 추가한다.
java net.sf.saxon.Transform 												-s:temp\0170-refinement_tag.xml 						-o:temp\0180-translate_no_tagging.xml							-xsl:xsl\0180-translate_no_tagging.xsl
if errorlevel 1 exit /b !errorlevel!

set "CURRENT_SOURCE=temp\0180-translate_no_tagging.xml"
if /I "!REMOVE_SIMPLE!"=="Y" (
    rem 0402-Remove_Simple_Operation_And_DeliveryTarget.xsl: 선택 옵션에 따라 Simple operation과 deliveryTarget을 삭제한다.
    java net.sf.saxon.Transform 											-s:!CURRENT_SOURCE!  									-o:temp\0402-remove_simple_operation_deliverytarget.xml		-xsl:xsl\0402-Remove_Simple_Operation_And_DeliveryTarget.xsl		removeSimpleOperation=!REMOVE_SIMPLE_OPERATION! removeDeliveryTarget=!REMOVE_DELIVERY_TARGET!
    if errorlevel 1 exit /b !errorlevel!
    set "CURRENT_SOURCE=temp\0402-remove_simple_operation_deliverytarget.xml"
    echo REMOVE_SIMPLE_OPERATION=!REMOVE_SIMPLE_OPERATION! applied: !CURRENT_SOURCE! >> %OPTION_LOG%
    echo REMOVE_DELIVERY_TARGET=!REMOVE_DELIVERY_TARGET! applied: !CURRENT_SOURCE! >> %OPTION_LOG%
)
if /I "!DELETE_DRAFT!"=="Y" (
    rem 0401-remove_review_Delete_Draft_Comment.xsl: 선택 옵션에 따라 draft-comment와 review 관련 작업 정보를 삭제한다.
    java net.sf.saxon.Transform 											-s:!CURRENT_SOURCE!  									-o:temp\0401-remove_review_Delete_Draft_Comment.xml		-xsl:xsl\0401-remove_review_Delete_Draft_Comment.xsl
    if errorlevel 1 exit /b !errorlevel!
    set "CURRENT_SOURCE=temp\0401-remove_review_Delete_Draft_Comment.xml"
    echo DELETE_DRAFT applied: !CURRENT_SOURCE! >> %OPTION_LOG%
)

rem 파일명 변경 모드에 따라 ID/XREF를 정리한 후 최종 파일명으로 보고서를 생성한다.	
rem 0005-namespace-remove.xsl: DB와 기본 정제가 끝난 병합 XML의 namespace를 다시 정리한다.
java net.sf.saxon.Transform 												-s:!CURRENT_SOURCE!   								-o:temp\0005-namespace-remove.xml  							-xsl:xsl\0005-namespace-remove.xsl
if errorlevel 1 exit /b !errorlevel!
if /I "!FILE_NAME_MODE!"=="T00000" (
    rem 0006-id-clean.xsl: t0000 파일명 변경 모드에 맞춰 요소 ID와 파일 참조용 ID를 정리한다.
    java net.sf.saxon.Transform 											-s:temp\0005-namespace-remove.xml  					-o:temp\0006-id-clean.xml  									-xsl:xsl\0006-id-clean.xsl
    if errorlevel 1 exit /b !errorlevel!
    rem 0007-xref-clean.xsl: t0000 파일명 변경 모드에 맞춰 xref href를 정리한다.
    java net.sf.saxon.Transform 											-s:temp\0006-id-clean.xml  							-o:temp\0007-xref-clean.xml  									-xsl:xsl\0007-xref-clean.xsl
    if errorlevel 1 exit /b !errorlevel!
    rem 0008-related-links.xsl: t0000 파일명 변경 결과를 기준으로 related-links를 생성한다.
    java net.sf.saxon.Transform 											-s:temp\0007-xref-clean.xml  							-o:temp\0008-related-links.xml  								-xsl:xsl\0008-related-links.xsl
    if errorlevel 1 exit /b !errorlevel!
) else if /I "!FILE_NAME_MODE!"=="TITLE_PREFIX" (
    rem 0006-id-clean_TitleFileNamePrefix.xsl: 제목 기반 파일명 모드에 맞춰 요소 ID와 파일 참조용 ID를 정리한다.
    java net.sf.saxon.Transform 											-s:temp\0005-namespace-remove.xml  					-o:temp\0006-id-clean_TitleFileNamePrefix.xml  					-xsl:xsl\0006-id-clean_TitleFileNamePrefix.xsl titleFileNamePrefix=Y
    if errorlevel 1 exit /b !errorlevel!
    rem 0007-xref-clean_TitleFileNamePrefix.xsl: 제목 기반 파일명 모드에 맞춰 xref href를 정리한다.
    java net.sf.saxon.Transform 											-s:temp\0006-id-clean_TitleFileNamePrefix.xml  			-o:temp\0007-xref-clean_TitleFileNamePrefix.xml  				-xsl:xsl\0007-xref-clean_TitleFileNamePrefix.xsl titleFileNamePrefix=Y
    if errorlevel 1 exit /b !errorlevel!
    rem 0008-related-links_TitleFileNamePrefix.xsl: 제목 기반 파일명 변경 결과를 기준으로 related-links를 생성한다.
    java net.sf.saxon.Transform 											-s:temp\0007-xref-clean_TitleFileNamePrefix.xml  		-o:temp\0008-related-links_TitleFileNamePrefix.xml  			-xsl:xsl\0008-related-links_TitleFileNamePrefix.xsl
    if errorlevel 1 exit /b !errorlevel!
) else (
    rem 0006-id-clean_NotFileNameChange.xsl: 기존 파일명을 유지하면서 요소 ID와 파일 참조용 ID를 정리한다.
    java net.sf.saxon.Transform 											-s:temp\0005-namespace-remove.xml  					-o:temp\0006-id-clean_NotFileNameChange.xml  				-xsl:xsl\0006-id-clean_NotFileNameChange.xsl
    if errorlevel 1 exit /b !errorlevel!
    rem 0007-xref-clean_NotFileNameChange.xsl: 기존 파일명을 유지하면서 xref href를 정리한다.
    java net.sf.saxon.Transform 											-s:temp\0006-id-clean_NotFileNameChange.xml  		-o:temp\0007-xref-clean_NotFileNameChange.xml  				-xsl:xsl\0007-xref-clean_NotFileNameChange.xsl
    if errorlevel 1 exit /b !errorlevel!
    rem 0008-related-links_NotFileNameChange.xsl: 기존 파일명을 유지한 결과를 기준으로 related-links를 생성한다.
    java net.sf.saxon.Transform 											-s:temp\0007-xref-clean_NotFileNameChange.xml  		-o:temp\0008-related-links_NotFileNameChange.xml  			-xsl:xsl\0008-related-links_NotFileNameChange.xsl
    if errorlevel 1 exit /b !errorlevel!
)

if /I "!FILE_NAME_MODE!"=="T00000" (
    set "CURRENT_SOURCE=temp\0008-related-links.xml"
) else if /I "!FILE_NAME_MODE!"=="TITLE_PREFIX" (
    set "CURRENT_SOURCE=temp\0008-related-links_TitleFileNamePrefix.xml"
) else (
    set "CURRENT_SOURCE=temp\0008-related-links_NotFileNameChange.xml"
)

rem ID/XREF/related-links 정리가 끝난 최종 구조를 기준으로 검출 항목을 표시한다.
rem 0120-mark_empty_topic.xsl: title만 있거나 하위 내용이 없는 DITA를 찾아 리포트용으로 표시한다.
java net.sf.saxon.Transform 												-s:!CURRENT_SOURCE! 									-o:temp\0120-empty_topic_marked.xml 						-xsl:xsl\0120-mark_empty_topic.xsl
if errorlevel 1 exit /b !errorlevel!
rem 0121-mark_empty_tag.xsl: 속성과 내용이 모두 없는 빈 태그를 찾아 리포트용으로 표시한다.
java net.sf.saxon.Transform 												-s:temp\0120-empty_topic_marked.xml 					-o:temp\0121-empty_tag_marked.xml 							-xsl:xsl\0121-mark_empty_tag.xsl
if errorlevel 1 exit /b !errorlevel!
rem 0122-mark_li_direct_text.xsl: li 직접 텍스트와 step 내부 cmd 누락을 찾아 리포트용으로 표시한다.
java net.sf.saxon.Transform 												-s:temp\0121-empty_tag_marked.xml 					-o:temp\0122-li_direct_text_marked.xml 						-xsl:xsl\0122-mark_li_direct_text.xsl
if errorlevel 1 exit /b !errorlevel!
rem 0125-mark_image_server_href.xsl: http 또는 https 서버 주소를 사용하는 image href를 찾아 리포트용으로 표시한다.
java net.sf.saxon.Transform 												-s:temp\0122-li_direct_text_marked.xml					 -o:temp\0125-image_server_href_marked.xml					 -xsl:xsl\0125-mark_image_server_href.xsl
if errorlevel 1 exit /b !errorlevel!
rem 0126-mark_invalid_xref_href.xsl: 경로나 하위 element ID가 포함된 비정상 xref href를 찾아 리포트용으로 표시한다.
java net.sf.saxon.Transform 												-s:temp\0125-image_server_href_marked.xml 			-o:temp\0126-invalid_xref_href_marked.xml 						-xsl:xsl\0126-mark_invalid_xref_href.xsl
if errorlevel 1 exit /b !errorlevel!

rem Detect image href values whose extension is not .eps without modifying the href.
rem 0127-mark_non_eps_image_href.xsl: 확장자가 eps가 아닌 image href를 찾아 리포트용으로 표시한다(현재 미실행).
rem java net.sf.saxon.Transform -s:temp\0126-invalid_xref_href_marked.xml -o:temp\0127-non_eps_image_href_marked.xml -xsl:xsl\0127-mark_non_eps_image_href.xsl
rem if errorlevel 1 exit /b !errorlevel!

rem 0009-dita-rebeautify.xsl: 검출 정보가 포함된 병합 XML을 최종 리포트와 파일 분리에 적합하게 정렬한다.
java net.sf.saxon.Transform 												-s:temp\0126-invalid_xref_href_marked.xml				 -o:temp\0009-dita-rebeautify.xml 								-xsl:xsl\0009-dita-rebeautify.xsl
if errorlevel 1 exit /b !errorlevel!

echo REPORT_SOURCE=temp\0009-dita-rebeautify.xml >> %OPTION_LOG%
if /I "!BER_DB_APPLY!"=="Y" (
    rem 검출이 끝난 최종 구조를 기준으로 BER 변경 상세 리포트 XML을 생성한다.
    rem 0410-make-change-report_ber.xsl: 최종 구조와 BER 상태를 기준으로 BER 변경 상세 리포트 XML을 생성한다.
    java net.sf.saxon.Transform 											-s:temp\0009-dita-rebeautify.xml 						-o:temp\excel-change-report.xml 								-xsl:xsl\0410-make-change-report_ber.xsl
    if errorlevel 1 exit /b !errorlevel!
    rem BER 변경 상세 리포트 XML을 Excel 파일로 변환한다.
    cscript //nologo "%ROOT%xsl\Convert_Xml_To_Excel.vbs"
    if errorlevel 1 exit /b !errorlevel!
    rem 변환된 Excel 파일을 BER_변경_리포트.xlsx 이름으로 저장한다.
    copy /y temp\excel-change-report.xlsx temp\BER_변경_리포트.xlsx >NUL
    if errorlevel 1 exit /b !errorlevel!
)

rem 0190-make-transform-report-excel.xsl: 선택 옵션과 정제·검출 결과를 모아 결과 리포트 XML을 생성한다.
java net.sf.saxon.Transform 												-s:temp\0009-dita-rebeautify.xml						-o:temp\transform_report_excel.xml								-xsl:xsl\0190-make-transform-report-excel.xsl		fileNameMode=!FILE_NAME_MODE! inputType=!INPUT_TYPE! outputType=!OUTPUT_TYPE! removeSimple=!REMOVE_SIMPLE_OPERATION! removeDeliveryTarget=!REMOVE_DELIVERY_TARGET! deleteDraft=!DELETE_DRAFT! textDbApply=!TEXT_DB_APPLY! noteDbApply=!NOTE_DB_APPLY! berDbApply=!BER_DB_APPLY!
if errorlevel 1 exit /b !errorlevel!

rem 0400-remove_review.xsl: 리포트 생성이 끝난 XML에서 modified, status, hash 등 작업용 정보를 삭제한다.
java net.sf.saxon.Transform -catalog:xsl\catalog.xml						-s:temp\0009-dita-rebeautify.xml						-o:temp\0400-remove_review.xml  								-xsl:xsl\0400-remove_review.xsl
if errorlevel 1 exit /b !errorlevel!
	
rem 0010-rechapterize.xsl: 정제가 끝난 병합 XML을 최종 ditamap과 개별 DITA 파일로 다시 분리한다.
java net.sf.saxon.Transform 												-s:temp\0400-remove_review.xml 						-o:xsl\dummy.xml												-xsl:xsl\0010-rechapterize.xsl
if errorlevel 1 exit /b !errorlevel!

rem 금칙어 QC 보고서 옵션이 켜진 경우 추가 정규화와 Excel 보고서 병합까지 수행한다.
if /I "!FORBIDDEN_QC_REPORT!"=="Y" (
    rem 29-kus-text-normalize.xsl: 금칙어 QC 검사를 위해 문장 텍스트를 검사 기준에 맞게 정규화한다.
    java net.sf.saxon.Transform 											-s:temp\0400-remove_review.xml						-o:temp\qc-29-kus-text-normalized.xml							-xsl:xsl\29-kus-text-normalize.xsl
    if errorlevel 1 exit /b !errorlevel!
    rem 30-kus-inline-normalize.xsl: 금칙어 QC 검사를 위해 인라인 요소 앞뒤 공백을 정규화한다.
    java net.sf.saxon.Transform 											-s:temp\qc-29-kus-text-normalized.xml					-o:temp\qc-30-kus-inline-normalized.xml						-xsl:xsl\30-kus-inline-normalize.xsl
    if errorlevel 1 exit /b !errorlevel!
    rem 50-insert-forbidden-ph.xsl: 금칙어 DB와 일치하는 내용을 찾아 QC용 ph 표시를 추가한다.
    java net.sf.saxon.Transform 											-s:temp\qc-30-kus-inline-normalized.xml				-o:temp\qc-50-inserted-forbidden-ph.xml						-xsl:xsl\50-insert-forbidden-ph.xsl
    if errorlevel 1 exit /b !errorlevel!
    rem 31-kus-beautify2.xsl: 금칙어 표시가 추가된 XML의 요소와 공백을 QC 처리에 맞게 정렬한다.
    java net.sf.saxon.Transform 											-s:temp\qc-50-inserted-forbidden-ph.xml				-o:temp\qc-31-kus-beautified.xml								-xsl:xsl\31-kus-beautify2.xsl
    if errorlevel 1 exit /b !errorlevel!
    del /q topics\*.dita 2>nul
    rem 21-topicalize.xsl: 금칙어 QC용 병합 XML을 검사 가능한 개별 DITA 구조로 분리한다.
    java net.sf.saxon.Transform 											-s:temp\qc-31-kus-beautified.xml						-o:xsl\dummy.xml												-xsl:xsl\21-topicalize.xsl
    if errorlevel 1 exit /b !errorlevel!	
    rem 51-collect-forbidden.xsl: 표시된 금칙어를 수집해 3rd_party 검사 자료를 생성한다.
    java net.sf.saxon.Transform 			-catalog:xsl\catalog.xml			-s:temp\qc-31-kus-beautified.xml						-o:xsl\dummy.xml												-xsl:xsl\51-collect-forbidden.xsl
    if errorlevel 1 exit /b !errorlevel!	
    rem 52-excel-for-3rd-party.xsl: 수집한 금칙어 결과를 Excel용 XML 리포트로 변환한다.
    java net.sf.saxon.Transform 			-catalog:xsl\catalog.xml			-s:topics\3rd_party\extract.xml							-o:temp\Forbidden_Report.xml									-xsl:xsl\52-excel-for-3rd-party.xsl
    if errorlevel 1 exit /b !errorlevel!
    rem check_forbidden_make_html.xsl: 수집한 금칙어 결과를 확인용 HTML 리포트로 변환한다.
    java net.sf.saxon.Transform 			-catalog:xsl\catalog.xml			-s:topics\3rd_party\extract.xml							-o:temp\Forbidden_Report.html									-xsl:xsl\check_forbidden_make_html.xsl
    if errorlevel 1 exit /b !errorlevel!
    java -jar lib\ant-launcher.jar -lib lib -f build_qc_lint.xml excel-report
    if errorlevel 1 exit /b !errorlevel!
    rem 0191-merge-qc-report-sheets.xsl: 금칙어와 문장 QC 결과 시트를 기존 결과 리포트에 병합한다.
    java net.sf.saxon.Transform 											-s:temp\transform_report_excel.xml						-o:temp\transform_report_excel_merged.xml					-xsl:xsl\0191-merge-qc-report-sheets.xsl		forbiddenReport=../temp/Forbidden_Report.xml qcLintReport=../temp/QC_LINT_Report.xml
    if errorlevel 1 exit /b !errorlevel!
    copy /y temp\transform_report_excel_merged.xml temp\transform_report_excel.xml >NUL
    if errorlevel 1 exit /b !errorlevel!
)

rem 변환 결과 보고서를 Excel 파일로 만들고 최종 bookmap.xml을 작업 폴더로 복사한다.
cscript //nologo "%ROOT%xsl\Convert_Xml_To_Excel-revision.vbs"
if errorlevel 1 exit /b !errorlevel!

copy "%~dp0xsl\bookmap.xml" "%~dp0bookmap.xml" /Y > NUL
if errorlevel 1 exit /b !errorlevel!

rd /q/s topics

if exist "topics_2" (
    if not exist "topics" mkdir "topics"
    xcopy "topics_2\*.*" "topics\" /E /I /Y > NUL
)

echo Done.
pause
