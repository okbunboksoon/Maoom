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
java net.sf.saxon.Transform -catalog:xsl\catalog.xml 						-s:xsl\dummy.xml  										-o:xsl\dummy.xml  												-xsl:xsl\0000-doctype-remove.xsl
if errorlevel 1 exit /b !errorlevel!
java net.sf.saxon.Transform 												-s:temp\0000-doctype-removed.xml  					-o:temp\0001-namespace-removed.xml  						-xsl:xsl\0001-namespace-remove.xsl
if errorlevel 1 exit /b !errorlevel!
java net.sf.saxon.Transform 												-s:temp\0001-namespace-removed.xml  				-o:temp\0110-svg_update.xml  									-xsl:xsl\0110-svg_update.xsl
if errorlevel 1 exit /b !errorlevel!
java net.sf.saxon.Transform 												-s:temp\0110-svg_update.xml    						-o:temp\0002-toc-created.xml  									-xsl:xsl\0002-toc-create.xsl
if errorlevel 1 exit /b !errorlevel!
java net.sf.saxon.Transform 												-s:temp\0002-toc-created.xml  							-o:xsl\bookmap.xml  											-xsl:xsl\0003-bookmap-create.xsl
if errorlevel 1 exit /b !errorlevel!
java net.sf.saxon.Transform -catalog:xsl\catalog.xml						-s:temp\0002-toc-created.xml  							-o:temp\0004-topic-merged.xml  								-xsl:xsl\0004-topic-merge.xsl
if errorlevel 1 exit /b !errorlevel!
rem 병합된 topicref 안에서 문장의 맨 앞과 맨 뒤 불필요한 공백을 제거한다.
java net.sf.saxon.Transform -s:temp\0004-topic-merged.xml -o:temp\0123-sentence_space_trimmed.xml -xsl:xsl\0123-trim_sentence_space.xsl
if errorlevel 1 exit /b !errorlevel!
rem placement=break이고 align이 left 또는 right인 image를 찾아 align=center로 변경하고 리포트용으로 표시한다.
java net.sf.saxon.Transform -s:temp\0123-sentence_space_trimmed.xml -o:temp\0124-break_image_centered.xml -xsl:xsl\0124-center_break_image.xsl
if errorlevel 1 exit /b !errorlevel!
set "CURRENT_SOURCE=temp\0124-break_image_centered.xml"
if /I "!TEXT_DB_APPLY!"=="Y" (
    java net.sf.saxon.Transform 											-s:!CURRENT_SOURCE!  									-o:temp\0340-kus-db-apply.xml								-xsl:xsl\0340-kus-db-apply.xsl flag=on
    if errorlevel 1 exit /b !errorlevel!
    set "CURRENT_SOURCE=temp\0340-kus-db-apply.xml"
    echo TEXT_DB_APPLY applied: !CURRENT_SOURCE! >> %OPTION_LOG%
)

if /I "!NOTE_DB_APPLY!"=="Y" (
    java net.sf.saxon.Transform 											-s:!CURRENT_SOURCE!  									-o:temp\0340-note-db-apply.xml								-xsl:xsl\0340-note-db-apply.xsl flag=on
    if errorlevel 1 exit /b !errorlevel!
    set "CURRENT_SOURCE=temp\0340-note-db-apply.xml"
    echo NOTE_DB_APPLY applied: !CURRENT_SOURCE! >> %OPTION_LOG%
)
java net.sf.saxon.Transform 												-s:!CURRENT_SOURCE!									-o:temp\0130-merge_tgroup.xml 								-xsl:xsl\0130-merge_tgroup.xsl
if errorlevel 1 exit /b !errorlevel!
java net.sf.saxon.Transform 												-s:temp\0130-merge_tgroup.xml						-o:temp\0160-image_attr.xml 									-xsl:xsl\0160-image_attr.xsl
if errorlevel 1 exit /b !errorlevel!
java net.sf.saxon.Transform 												-s:temp\0160-image_attr.xml 	 						-o:temp\0170-refinement_tag.xml 								-xsl:xsl\0170-refinement_tag.xsl
if errorlevel 1 exit /b !errorlevel!
java net.sf.saxon.Transform 												-s:temp\0170-refinement_tag.xml 						-o:temp\0180-translate_no_tagging.xml							-xsl:xsl\0180-translate_no_tagging.xsl
if errorlevel 1 exit /b !errorlevel!
set "CURRENT_SOURCE=temp\0180-translate_no_tagging.xml"
rem 단순 작업/납품 대상 삭제 옵션은 요청된 범위만 CURRENT_SOURCE에 이어서 반영한다.
if /I "!REMOVE_SIMPLE!"=="Y" (
    java net.sf.saxon.Transform 											-s:!CURRENT_SOURCE!  									-o:temp\0402-remove_simple_operation_deliverytarget.xml		-xsl:xsl\0402-Remove_Simple_Operation_And_DeliveryTarget.xsl 			removeSimpleOperation=!REMOVE_SIMPLE_OPERATION! removeDeliveryTarget=!REMOVE_DELIVERY_TARGET!
    if errorlevel 1 exit /b !errorlevel!
    set "CURRENT_SOURCE=temp\0402-remove_simple_operation_deliverytarget.xml"
    echo REMOVE_SIMPLE_OPERATION=!REMOVE_SIMPLE_OPERATION! applied: !CURRENT_SOURCE! >> %OPTION_LOG%
    echo REMOVE_DELIVERY_TARGET=!REMOVE_DELIVERY_TARGET! applied: !CURRENT_SOURCE! >> %OPTION_LOG%
)
if /I "!DELETE_DRAFT!"=="Y" (
    java net.sf.saxon.Transform 											-s:!CURRENT_SOURCE!  									-o:temp\0401-remove_review_Delete_Draft_Comment.xml		-xsl:xsl\0401-remove_review_Delete_Draft_Comment.xsl
    if errorlevel 1 exit /b !errorlevel!
    set "CURRENT_SOURCE=temp\0401-remove_review_Delete_Draft_Comment.xml"
    echo DELETE_DRAFT applied: !CURRENT_SOURCE! >> %OPTION_LOG%
)

rem 파일명 변경 모드에 따라 ID/XREF를 정리한 후 최종 파일명으로 보고서를 생성한다.	
java net.sf.saxon.Transform 												-s:!CURRENT_SOURCE!   								-o:temp\0005-namespace-remove.xml  							-xsl:xsl\0005-namespace-remove.xsl
if errorlevel 1 exit /b !errorlevel!
if /I "!FILE_NAME_MODE!"=="T00000" (
    java net.sf.saxon.Transform 											-s:temp\0005-namespace-remove.xml  					-o:temp\0006-id-clean.xml  									-xsl:xsl\0006-id-clean.xsl
    if errorlevel 1 exit /b !errorlevel!
    java net.sf.saxon.Transform 											-s:temp\0006-id-clean.xml  							-o:temp\0007-xref-clean.xml  									-xsl:xsl\0007-xref-clean.xsl
    if errorlevel 1 exit /b !errorlevel!
    java net.sf.saxon.Transform 											-s:temp\0007-xref-clean.xml  							-o:temp\0008-related-links.xml  								-xsl:xsl\0008-related-links.xsl
    if errorlevel 1 exit /b !errorlevel!
) else if /I "!FILE_NAME_MODE!"=="TITLE_PREFIX" (
    java net.sf.saxon.Transform 											-s:temp\0005-namespace-remove.xml  					-o:temp\0006-id-clean_TitleFileNamePrefix.xml  					-xsl:xsl\0006-id-clean_TitleFileNamePrefix.xsl titleFileNamePrefix=Y
    if errorlevel 1 exit /b !errorlevel!
    java net.sf.saxon.Transform 											-s:temp\0006-id-clean_TitleFileNamePrefix.xml  			-o:temp\0007-xref-clean_TitleFileNamePrefix.xml  				-xsl:xsl\0007-xref-clean_TitleFileNamePrefix.xsl titleFileNamePrefix=Y
    if errorlevel 1 exit /b !errorlevel!
    java net.sf.saxon.Transform 											-s:temp\0007-xref-clean_TitleFileNamePrefix.xml  		-o:temp\0008-related-links_TitleFileNamePrefix.xml  			-xsl:xsl\0008-related-links_TitleFileNamePrefix.xsl
    if errorlevel 1 exit /b !errorlevel!
) else (
    java net.sf.saxon.Transform 											-s:temp\0005-namespace-remove.xml  					-o:temp\0006-id-clean_NotFileNameChange.xml  				-xsl:xsl\0006-id-clean_NotFileNameChange.xsl
    if errorlevel 1 exit /b !errorlevel!
    java net.sf.saxon.Transform 											-s:temp\0006-id-clean_NotFileNameChange.xml  		-o:temp\0007-xref-clean_NotFileNameChange.xml  				-xsl:xsl\0007-xref-clean_NotFileNameChange.xsl
    if errorlevel 1 exit /b !errorlevel!
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
java net.sf.saxon.Transform -s:!CURRENT_SOURCE! -o:temp\0120-empty_topic_marked.xml -xsl:xsl\0120-mark_empty_topic.xsl
if errorlevel 1 exit /b !errorlevel!
java net.sf.saxon.Transform -s:temp\0120-empty_topic_marked.xml -o:temp\0121-empty_tag_marked.xml -xsl:xsl\0121-mark_empty_tag.xsl
if errorlevel 1 exit /b !errorlevel!
java net.sf.saxon.Transform -s:temp\0121-empty_tag_marked.xml -o:temp\0122-li_direct_text_marked.xml -xsl:xsl\0122-mark_li_direct_text.xsl
if errorlevel 1 exit /b !errorlevel!
java net.sf.saxon.Transform -s:temp\0122-li_direct_text_marked.xml -o:temp\0125-image_server_href_marked.xml -xsl:xsl\0125-mark_image_server_href.xsl
if errorlevel 1 exit /b !errorlevel!
java net.sf.saxon.Transform -s:temp\0125-image_server_href_marked.xml -o:temp\0126-invalid_xref_href_marked.xml -xsl:xsl\0126-mark_invalid_xref_href.xsl
if errorlevel 1 exit /b !errorlevel!
rem Detect image href values whose extension is not .eps without modifying the href.
rem java net.sf.saxon.Transform -s:temp\0126-invalid_xref_href_marked.xml -o:temp\0127-non_eps_image_href_marked.xml -xsl:xsl\0127-mark_non_eps_image_href.xsl
rem if errorlevel 1 exit /b !errorlevel!
java net.sf.saxon.Transform -s:temp\0126-invalid_xref_href_marked.xml -o:temp\0009-dita-rebeautify.xml -xsl:xsl\0009-dita-rebeautify.xsl
if errorlevel 1 exit /b !errorlevel!
echo REPORT_SOURCE=temp\0009-dita-rebeautify.xml >> %OPTION_LOG%
java net.sf.saxon.Transform 												-s:temp\0009-dita-rebeautify.xml						-o:temp\transform_report_excel.xml								-xsl:xsl\0190-make-transform-report-excel.xsl fileNameMode=!FILE_NAME_MODE! inputType=!INPUT_TYPE! outputType=!OUTPUT_TYPE! removeSimple=!REMOVE_SIMPLE_OPERATION! removeDeliveryTarget=!REMOVE_DELIVERY_TARGET! deleteDraft=!DELETE_DRAFT! textDbApply=!TEXT_DB_APPLY! noteDbApply=!NOTE_DB_APPLY!
if errorlevel 1 exit /b !errorlevel!

java net.sf.saxon.Transform -catalog:xsl\catalog.xml						-s:temp\0009-dita-rebeautify.xml						-o:temp\0400-remove_review.xml  								-xsl:xsl\0400-remove_review.xsl
if errorlevel 1 exit /b !errorlevel!
	
java net.sf.saxon.Transform 												-s:temp\0400-remove_review.xml 						-o:xsl\dummy.xml												-xsl:xsl\0010-rechapterize.xsl
if errorlevel 1 exit /b !errorlevel!

rem 금칙어 QC 보고서 옵션이 켜진 경우 추가 정규화와 Excel 보고서 병합까지 수행한다.
if /I "!FORBIDDEN_QC_REPORT!"=="Y" (
    java net.sf.saxon.Transform 											-s:temp\0400-remove_review.xml						-o:temp\qc-29-kus-text-normalized.xml							-xsl:xsl\29-kus-text-normalize.xsl
    if errorlevel 1 exit /b !errorlevel!
    java net.sf.saxon.Transform 											-s:temp\qc-29-kus-text-normalized.xml					-o:temp\qc-30-kus-inline-normalized.xml						-xsl:xsl\30-kus-inline-normalize.xsl
    if errorlevel 1 exit /b !errorlevel!
    java net.sf.saxon.Transform 											-s:temp\qc-30-kus-inline-normalized.xml				-o:temp\qc-50-inserted-forbidden-ph.xml						-xsl:xsl\50-insert-forbidden-ph.xsl
    if errorlevel 1 exit /b !errorlevel!
    java net.sf.saxon.Transform 											-s:temp\qc-50-inserted-forbidden-ph.xml				-o:temp\qc-31-kus-beautified.xml								-xsl:xsl\31-kus-beautify2.xsl
    if errorlevel 1 exit /b !errorlevel!
    del /q topics\*.dita 2>nul
    java net.sf.saxon.Transform 											-s:temp\qc-31-kus-beautified.xml						-o:xsl\dummy.xml												-xsl:xsl\21-topicalize.xsl
    if errorlevel 1 exit /b !errorlevel!	
    java net.sf.saxon.Transform 			-catalog:xsl\catalog.xml			-s:temp\qc-31-kus-beautified.xml						-o:xsl\dummy.xml												-xsl:xsl\51-collect-forbidden.xsl
    if errorlevel 1 exit /b !errorlevel!	
    java net.sf.saxon.Transform 			-catalog:xsl\catalog.xml			-s:topics\3rd_party\extract.xml							-o:temp\Forbidden_Report.xml									-xsl:xsl\52-excel-for-3rd-party.xsl
    if errorlevel 1 exit /b !errorlevel!
    java net.sf.saxon.Transform 			-catalog:xsl\catalog.xml			-s:topics\3rd_party\extract.xml							-o:temp\Forbidden_Report.html									-xsl:xsl\check_forbidden_make_html.xsl
    if errorlevel 1 exit /b !errorlevel!
    java -jar lib\ant-launcher.jar -lib lib -f build_qc_lint.xml excel-report
    if errorlevel 1 exit /b !errorlevel!
    java net.sf.saxon.Transform 											-s:temp\transform_report_excel.xml						-o:temp\transform_report_excel_merged.xml					-xsl:xsl\0191-merge-qc-report-sheets.xsl forbiddenReport=../temp/Forbidden_Report.xml qcLintReport=../temp/QC_LINT_Report.xml
    if errorlevel 1 exit /b !errorlevel!
    copy /y temp\transform_report_excel_merged.xml temp\transform_report_excel.xml >NUL
    if errorlevel 1 exit /b !errorlevel!
)

rem 변환 결과 보고서를 Excel 파일로 만들고 최종 bookmap.xml을 작업 폴더로 복사한다.
cscript //nologo "%ROOT%xsl\Convert_Xml_To_Excel-revision.vbs"
if errorlevel 1 exit /b !errorlevel!

copy "%~dp0xsl\bookmap.xml" "%~dp0bookmap.xml" /Y > NUL
if errorlevel 1 exit /b !errorlevel!

rem rd /q/s topics

rem if exist "topics_2" (
rem     if not exist "topics" mkdir "topics"
rem     xcopy "topics_2\*.*" "topics\" /E /I /Y > NUL
rem )

echo Done.
pause
