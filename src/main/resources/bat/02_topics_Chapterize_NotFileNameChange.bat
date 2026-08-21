@echo off
setlocal enabledelayedexpansion

set SAXON=%~dp0
set CLASSPATH=%SAXON%lib\saxon-he-12.4.jar;%CLASSPATH%
set CLASSPATH=%SAXON%lib\xmlresolver-5.2.2.jar;%CLASSPATH%

if not exist temp mkdir temp

set "FILE_NAME_CHANGE=N"
set "REMOVE_SIMPLE=N"
set "DELETE_DRAFT=N"

for %%A in (%*) do (
    if /I "%%A"=="FILE_NAME_CHANGE=Y" set "FILE_NAME_CHANGE=Y"
    if /I "%%A"=="REMOVE_SIMPLE=Y" set "REMOVE_SIMPLE=Y"
    if /I "%%A"=="DELETE_DRAFT=Y" set "DELETE_DRAFT=Y"
)

rem ===============================================
rem comment
rem ===============================================
set LOG=temp\filename_check.log
set ERROR_FOUND=0

echo ===== File Name Check ===== > %LOG%

for %%F in (topics\*.dita topics\*.ditamap) do (
    set "fname=%%~nxF"

rem comment
    if not "!fname!"=="!fname: =!" (
        echo [SPACE] %%F >> %LOG%
        set ERROR_FOUND=1
    )

rem comment
    set "check=!fname: =!"

    for /f "tokens=* delims=ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789._-" %%A in ("!check!") do (
        if not "%%A"=="" (
            echo [NON_ASCII] !fname! >> %LOG%
            set ERROR_FOUND=1
        )
    )
)

rem comment
if !ERROR_FOUND!==1 (
    echo.
    echo File name error detected. Batch stopped.
    echo Check log: %LOG%
    pause
    exit /b
)

rem ===============================================
rem comment
rem ===============================================
cd /d "%~dp0"

if exist "xsl\dummy.xml" del /f /q "xsl\dummy.xml"

echo ^<?xml version="1.0" encoding="UTF-8"?^> > "xsl\dummy.xml"
echo ^<dummy/^> >> "xsl\dummy.xml"

rem ===============================================
rem comment
rem ===============================================
echo Please wait a moment!
echo Processing... 

rem comment
java net.sf.saxon.Transform -catalog:xsl\catalog.xml 	-s:xsl\dummy.xml  										-o:xsl\dummy.xml  										-xsl:xsl\0000-doctype-remove.xsl
rem comment
java net.sf.saxon.Transform 							-s:temp\0000-doctype-removed.xml  					-o:temp\0001-namespace-removed.xml  				-xsl:xsl\0001-namespace-remove.xsl
rem comment
java net.sf.saxon.Transform 							-s:temp\0001-namespace-removed.xml 					-o:temp\0100-metadata_Insert.xml 	 					-xsl:xsl\0100-metadata_Insert.xsl
rem comment
java net.sf.saxon.Transform 							-s:temp\0100-metadata_Insert.xml 						-o:temp\0110-svg_update.xml  							-xsl:xsl\0110-svg_update.xsl
rem comment
java net.sf.saxon.Transform 							-s:temp\0110-svg_update.xml    						-o:temp\0002-toc-created.xml  							-xsl:xsl\0002-toc-create.xsl
rem comment
java net.sf.saxon.Transform 							-s:temp\0002-toc-created.xml  							-o:xsl\bookmap.xml  									-xsl:xsl\0003-bookmap-create.xsl
rem comment
java net.sf.saxon.Transform -catalog:xsl\catalog.xml	-s:temp\0002-toc-created.xml  							-o:temp\0004-topic-merged.xml  						-xsl:xsl\0004-topic-merge.xsl
rem comment
java net.sf.saxon.Transform 							-s:temp\0004-topic-merged.xml						-o:temp\0130-merge_tgroup.xml 						-xsl:xsl\0130-merge_tgroup.xsl
rem comment
java net.sf.saxon.Transform 							-s:temp\0130-merge_tgroup.xml						-o:temp\0160-image_attr.xml 							-xsl:xsl\0160-image_attr.xsl
rem comment
java net.sf.saxon.Transform 							-s:temp\0160-image_attr.xml 	 						-o:temp\0170-refinement_tag.xml 						-xsl:xsl\0170-refinement_tag.xsl
rem comment
java net.sf.saxon.Transform 							-s:temp\0170-refinement_tag.xml 						-o:temp\0180-translate_no_tagging.xml					 -xsl:xsl\0180-translate_no_tagging.xsl
set "CURRENT_SOURCE=temp\0180-translate_no_tagging.xml"
if /I "!REMOVE_SIMPLE!"=="Y" (
    rem comment
    java net.sf.saxon.Transform 						-s:!CURRENT_SOURCE!  									-o:temp\0402-remove_simple_operation_deliverytarget.xml	-xsl:xsl\0402-Remove_Simple_Operation_And_DeliveryTarget.xsl
    set "CURRENT_SOURCE=temp\0402-remove_simple_operation_deliverytarget.xml"
)
if /I "!DELETE_DRAFT!"=="Y" (
    rem comment
    java net.sf.saxon.Transform 						-s:!CURRENT_SOURCE!  									-o:temp\0401-remove_review_Delete_Draft_Comment.xml		-xsl:xsl\0401-remove_review_Delete_Draft_Comment.xsl
    set "CURRENT_SOURCE=temp\0401-remove_review_Delete_Draft_Comment.xml"
)
rem comment
java net.sf.saxon.Transform -catalog:xsl\catalog.xml	-s:!CURRENT_SOURCE!  									-o:temp\0400-remove_review.xml  						-xsl:xsl\0400-remove_review.xsl
rem comment
java net.sf.saxon.Transform 							-s:temp\0400-remove_review.xml   						-o:temp\0005-namespace-remove.xml  					-xsl:xsl\0005-namespace-remove.xsl
if /I "!FILE_NAME_CHANGE!"=="Y" (
    rem comment
    java net.sf.saxon.Transform 						-s:temp\0005-namespace-remove.xml  					-o:temp\0006-id-clean.xml  							-xsl:xsl\0006-id-clean.xsl
    rem comment
    java net.sf.saxon.Transform 						-s:temp\0006-id-clean.xml  							-o:temp\0007-xref-clean.xml  							-xsl:xsl\0007-xref-clean.xsl
    rem comment
    java net.sf.saxon.Transform 						-s:temp\0007-xref-clean.xml  							-o:temp\0008-related-links.xml  						-xsl:xsl\0008-related-links.xsl
) else (
    rem comment
    java net.sf.saxon.Transform 						-s:temp\0005-namespace-remove.xml  					-o:temp\0006-id-clean_NotFileNameChange.xml  			-xsl:xsl\0006-id-clean_NotFileNameChange.xsl
    rem comment
    java net.sf.saxon.Transform 						-s:temp\0006-id-clean_NotFileNameChange.xml  			-o:temp\0007-xref-clean_NotFileNameChange.xml  		-xsl:xsl\0007-xref-clean_NotFileNameChange.xsl
    rem comment
    java net.sf.saxon.Transform 						-s:temp\0007-xref-clean_NotFileNameChange.xml  		-o:temp\0008-related-links_NotFileNameChange.xml  		-xsl:xsl\0008-related-links_NotFileNameChange.xsl
)
rem comment
if /I "!FILE_NAME_CHANGE!"=="Y" (
    java net.sf.saxon.Transform 						-s:temp\0008-related-links.xml  						-o:temp\0009-dita-rebeautify.xml  						-xsl:xsl\0009-dita-rebeautify.xsl
) else (
    java net.sf.saxon.Transform 						-s:temp\0008-related-links_NotFileNameChange.xml  		-o:temp\0009-dita-rebeautify.xml  						-xsl:xsl\0009-dita-rebeautify.xsl
)
rem comment
java net.sf.saxon.Transform 							-s:temp\0009-dita-rebeautify.xml 						-o:xsl\dummy.xml										-xsl:xsl\0010-rechapterize.xsl

copy "%~dp0xsl\bookmap.xml" "%~dp0bookmap.xml" /Y > NUL

rd /q/s topics
rem rd /q/s temp
echo Done.
pause
