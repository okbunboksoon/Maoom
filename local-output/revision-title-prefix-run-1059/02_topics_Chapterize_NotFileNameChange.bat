@echo off
setlocal enabledelayedexpansion

set SAXON=%~dp0
set CLASSPATH=%SAXON%lib\saxon-ee-10.0.jar;%CLASSPATH%
set CLASSPATH=%SAXON%lib\xml-resolver-1.2.jar;%CLASSPATH%

if not exist temp mkdir temp

set "FILE_NAME_CHANGE=N"
set "FILE_NAME_MODE=DEFAULT"
set "TITLE_FILE_NAME_PREFIX=N"
set "INPUT_TYPE="
set "OUTPUT_TYPE="
set "REMOVE_SIMPLE=N"
set "REMOVE_SIMPLE_OPERATION=N"
set "REMOVE_DELIVERY_TARGET=N"
set "DELETE_DRAFT=N"
set "TEXT_DB_APPLY=N"
set "NOTE_DB_APPLY=N"
set "FORBIDDEN_QC_REPORT=N"

echo %* | findstr /I /C:"FILE_NAME_CHANGE=Y" >NUL && (
    set "FILE_NAME_CHANGE=Y"
    set "FILE_NAME_MODE=T00000"
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

java net.sf.saxon.Transform -catalog:xsl\catalog.xml 	-s:xsl\dummy.xml  										-o:xsl\dummy.xml  										-xsl:xsl\0000-doctype-remove.xsl
java net.sf.saxon.Transform 							-s:temp\0000-doctype-removed.xml  					-o:temp\0001-namespace-removed.xml  				-xsl:xsl\0001-namespace-remove.xsl
java net.sf.saxon.Transform 							-s:temp\0001-namespace-removed.xml  				-o:temp\0110-svg_update.xml  							-xsl:xsl\0110-svg_update.xsl
java net.sf.saxon.Transform 							-s:temp\0110-svg_update.xml    						-o:temp\0002-toc-created.xml  							-xsl:xsl\0002-toc-create.xsl
java net.sf.saxon.Transform 							-s:temp\0002-toc-created.xml  							-o:xsl\bookmap.xml  									-xsl:xsl\0003-bookmap-create.xsl
java net.sf.saxon.Transform -catalog:xsl\catalog.xml	-s:temp\0002-toc-created.xml  							-o:temp\0004-topic-merged.xml  						-xsl:xsl\0004-topic-merge.xsl
set "CURRENT_SOURCE=temp\0004-topic-merged.xml"
if /I "!TEXT_DB_APPLY!"=="Y" (
    java net.sf.saxon.Transform 						-s:!CURRENT_SOURCE!  									-o:temp\0340-kus-db-apply.xml						-xsl:xsl\0340-kus-db-apply.xsl flag=on
    if errorlevel 1 exit /b !errorlevel!
    set "CURRENT_SOURCE=temp\0340-kus-db-apply.xml"
    echo TEXT_DB_APPLY applied: !CURRENT_SOURCE! >> %OPTION_LOG%
)

if /I "!NOTE_DB_APPLY!"=="Y" (
    java net.sf.saxon.Transform 						-s:!CURRENT_SOURCE!  									-o:temp\0340-note-db-apply.xml						-xsl:xsl\0340-note-db-apply.xsl flag=on
    if errorlevel 1 exit /b !errorlevel!
    set "CURRENT_SOURCE=temp\0340-note-db-apply.xml"
    echo NOTE_DB_APPLY applied: !CURRENT_SOURCE! >> %OPTION_LOG%
)
java net.sf.saxon.Transform 							-s:!CURRENT_SOURCE!						-o:temp\0130-merge_tgroup.xml 						-xsl:xsl\0130-merge_tgroup.xsl
java net.sf.saxon.Transform 							-s:temp\0130-merge_tgroup.xml						-o:temp\0160-image_attr.xml 							-xsl:xsl\0160-image_attr.xsl
java net.sf.saxon.Transform 							-s:temp\0160-image_attr.xml 	 						-o:temp\0170-refinement_tag.xml 						-xsl:xsl\0170-refinement_tag.xsl
java net.sf.saxon.Transform 							-s:temp\0170-refinement_tag.xml 						-o:temp\0180-translate_no_tagging.xml					 -xsl:xsl\0180-translate_no_tagging.xsl
set "CURRENT_SOURCE=temp\0180-translate_no_tagging.xml"
if /I "!REMOVE_SIMPLE!"=="Y" (
    java net.sf.saxon.Transform 						-s:!CURRENT_SOURCE!  									-o:temp\0402-remove_simple_operation_deliverytarget.xml	-xsl:xsl\0402-Remove_Simple_Operation_And_DeliveryTarget.xsl removeSimpleOperation=!REMOVE_SIMPLE_OPERATION! removeDeliveryTarget=!REMOVE_DELIVERY_TARGET!
    set "CURRENT_SOURCE=temp\0402-remove_simple_operation_deliverytarget.xml"
    echo REMOVE_SIMPLE_OPERATION=!REMOVE_SIMPLE_OPERATION! applied: !CURRENT_SOURCE! >> %OPTION_LOG%
    echo REMOVE_DELIVERY_TARGET=!REMOVE_DELIVERY_TARGET! applied: !CURRENT_SOURCE! >> %OPTION_LOG%
)
if /I "!DELETE_DRAFT!"=="Y" (
    java net.sf.saxon.Transform 						-s:!CURRENT_SOURCE!  									-o:temp\0401-remove_review_Delete_Draft_Comment.xml		-xsl:xsl\0401-remove_review_Delete_Draft_Comment.xsl
    set "CURRENT_SOURCE=temp\0401-remove_review_Delete_Draft_Comment.xml"
    echo DELETE_DRAFT applied: !CURRENT_SOURCE! >> %OPTION_LOG%
)

echo REPORT_SOURCE=!CURRENT_SOURCE! >> %OPTION_LOG%
java net.sf.saxon.Transform 							-s:!CURRENT_SOURCE!									-o:temp\transform_report_excel.xml					-xsl:xsl\0190-make-transform-report-excel.xsl fileNameMode=!FILE_NAME_MODE! inputType=!INPUT_TYPE! outputType=!OUTPUT_TYPE! removeSimple=!REMOVE_SIMPLE_OPERATION! removeDeliveryTarget=!REMOVE_DELIVERY_TARGET! deleteDraft=!DELETE_DRAFT! textDbApply=!TEXT_DB_APPLY! noteDbApply=!NOTE_DB_APPLY!

java net.sf.saxon.Transform -catalog:xsl\catalog.xml	-s:!CURRENT_SOURCE!  									-o:temp\0400-remove_review.xml  						-xsl:xsl\0400-remove_review.xsl

java net.sf.saxon.Transform 							-s:temp\0400-remove_review.xml   						-o:temp\0005-namespace-remove.xml  					-xsl:xsl\0005-namespace-remove.xsl
if /I "!FILE_NAME_MODE!"=="T00000" (
    java net.sf.saxon.Transform 						-s:temp\0005-namespace-remove.xml  					-o:temp\0006-id-clean.xml  							-xsl:xsl\0006-id-clean.xsl
    java net.sf.saxon.Transform 						-s:temp\0006-id-clean.xml  							-o:temp\0007-xref-clean.xml  							-xsl:xsl\0007-xref-clean.xsl
    java net.sf.saxon.Transform 						-s:temp\0007-xref-clean.xml  							-o:temp\0008-related-links.xml  						-xsl:xsl\0008-related-links.xsl
) else if /I "!FILE_NAME_MODE!"=="TITLE_PREFIX" (
    java net.sf.saxon.Transform 						-s:temp\0005-namespace-remove.xml  					-o:temp\0006-id-clean_NotFileNameChange.xml  			-xsl:xsl\0006-id-clean_NotFileNameChange.xsl titleFileNamePrefix=Y
    java net.sf.saxon.Transform 						-s:temp\0006-id-clean_NotFileNameChange.xml  			-o:temp\0007-xref-clean_NotFileNameChange.xml  		-xsl:xsl\0007-xref-clean_NotFileNameChange.xsl titleFileNamePrefix=Y
    java net.sf.saxon.Transform 						-s:temp\0007-xref-clean_NotFileNameChange.xml  		-o:temp\0008-related-links_NotFileNameChange.xml  		-xsl:xsl\0008-related-links_NotFileNameChange.xsl
) else (
    java net.sf.saxon.Transform 						-s:temp\0005-namespace-remove.xml  					-o:temp\0006-id-clean_NotFileNameChange.xml  			-xsl:xsl\0006-id-clean_NotFileNameChange.xsl
    java net.sf.saxon.Transform 						-s:temp\0006-id-clean_NotFileNameChange.xml  			-o:temp\0007-xref-clean_NotFileNameChange.xml  		-xsl:xsl\0007-xref-clean_NotFileNameChange.xsl
    java net.sf.saxon.Transform 						-s:temp\0007-xref-clean_NotFileNameChange.xml  		-o:temp\0008-related-links_NotFileNameChange.xml  		-xsl:xsl\0008-related-links_NotFileNameChange.xsl
)
if /I "!FILE_NAME_MODE!"=="T00000" (
    java net.sf.saxon.Transform 						-s:temp\0008-related-links.xml  						-o:temp\0009-dita-rebeautify.xml  						-xsl:xsl\0009-dita-rebeautify.xsl
) else (
    java net.sf.saxon.Transform 						-s:temp\0008-related-links_NotFileNameChange.xml  		-o:temp\0009-dita-rebeautify.xml  						-xsl:xsl\0009-dita-rebeautify.xsl
)
java net.sf.saxon.Transform 							-s:temp\0009-dita-rebeautify.xml 						-o:xsl\dummy.xml										-xsl:xsl\0010-rechapterize.xsl

if /I "!FORBIDDEN_QC_REPORT!"=="Y" (
    java net.sf.saxon.Transform 						-s:temp\0009-dita-rebeautify.xml						-o:temp\qc-29-kus-text-normalized.xml				-xsl:xsl\29-kus-text-normalize.xsl
    if errorlevel 1 exit /b !errorlevel!
    java net.sf.saxon.Transform 						-s:temp\qc-29-kus-text-normalized.xml					-o:temp\qc-30-kus-inline-normalized.xml				-xsl:xsl\30-kus-inline-normalize.xsl
    if errorlevel 1 exit /b !errorlevel!
    java net.sf.saxon.Transform 						-s:temp\qc-30-kus-inline-normalized.xml					-o:temp\qc-50-inserted-forbidden-ph.xml				-xsl:xsl\50-insert-forbidden-ph.xsl
    if errorlevel 1 exit /b !errorlevel!
    java net.sf.saxon.Transform 						-s:temp\qc-50-inserted-forbidden-ph.xml					-o:temp\qc-31-kus-beautified.xml					-xsl:xsl\31-kus-beautify2.xsl
    if errorlevel 1 exit /b !errorlevel!
    del /q topics\*.dita 2>nul
    java net.sf.saxon.Transform 						-s:temp\qc-31-kus-beautified.xml						-o:xsl\dummy.xml									-xsl:xsl\21-topicalize.xsl
    if errorlevel 1 exit /b !errorlevel!
    java net.sf.saxon.Transform -catalog:xsl\catalog.xml	-s:temp\qc-31-kus-beautified.xml						-o:xsl\dummy.xml									-xsl:xsl\51-collect-forbidden.xsl
    if errorlevel 1 exit /b !errorlevel!
    java net.sf.saxon.Transform -catalog:xsl\catalog.xml	-s:topics\3rd_party\extract.xml						-o:temp\Forbidden_Report.xml						-xsl:xsl\52-excel-for-3rd-party.xsl
    if errorlevel 1 exit /b !errorlevel!
    java net.sf.saxon.Transform -catalog:xsl\catalog.xml	-s:topics\3rd_party\extract.xml						-o:temp\Forbidden_Report.html						-xsl:xsl\check_forbidden_make_html.xsl
    if errorlevel 1 exit /b !errorlevel!
    java -jar lib\ant-launcher.jar -lib lib -f build_qc_lint.xml excel-report
    if errorlevel 1 exit /b !errorlevel!
    java net.sf.saxon.Transform 						-s:temp\transform_report_excel.xml					-o:temp\transform_report_excel_merged.xml			-xsl:xsl\0191-merge-qc-report-sheets.xsl forbiddenReport=../temp/Forbidden_Report.xml qcLintReport=../temp/QC_LINT_Report.xml
    if errorlevel 1 exit /b !errorlevel!
    copy /y temp\transform_report_excel_merged.xml temp\transform_report_excel.xml >NUL
    if errorlevel 1 exit /b !errorlevel!
)

cscript //nologo "%ROOT%xsl\Convert_Xml_To_Excel-revision.vbs"

copy "%~dp0xsl\bookmap.xml" "%~dp0bookmap.xml" /Y > NUL

rd /q/s topics
echo Done.
pause
