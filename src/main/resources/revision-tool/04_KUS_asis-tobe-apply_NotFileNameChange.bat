@echo off

set SAXON=%~dp0
set CLASSPATH=%SAXON%lib\saxon-ee-10.0.jar;%CLASSPATH%
set CLASSPATH=%SAXON%lib\xml-resolver-1.2.jar;%CLASSPATH%

echo Please wait a moment!
echo Processing... 

cd /d "%~dp0"

if exist "xsl\dummy.xml" del /f /q "xsl\dummy.xml"

echo ^<?xml version="1.0" encoding="UTF-8"?^> > "xsl\dummy.xml"
echo ^<dummy/^> >> "xsl\dummy.xml"

java net.sf.saxon.Transform -catalog:xsl\catalog.xml 		-s:xsl\dummy.xml  							-o:xsl\dummy.xml  							-xsl:xsl\09-doctype-remove.xsl
java net.sf.saxon.Transform 								-s:temp\09-doctype-removed.xml  			-o:temp\10-namespace-removed.xml  		-xsl:xsl\10-namespace-remove.xsl
java net.sf.saxon.Transform 								-s:temp\10-namespace-removed.xml  		-o:temp\11-toc-created.xml  				-xsl:xsl\11-toc-create.xsl
java net.sf.saxon.Transform 								-s:temp\11-toc-created.xml  				-o:xsl\bookmap.xml  						-xsl:xsl\12-bookmap-create.xsl
java net.sf.saxon.Transform -catalog:xsl\catalog.xml		-s:temp\11-toc-created.xml  				-o:temp\13-topic-merged.xml  				-xsl:xsl\13-topic-merge.xsl
java net.sf.saxon.Transform 								-s:temp\13-topic-merged.xml				-o:temp\29-kus-text-normalized.xml		-xsl:xsl\29-kus-text-normalize.xsl
java net.sf.saxon.Transform 								-s:temp\29-kus-text-normalized.xml			-o:temp\30-kus-inline-normalized.xml		-xsl:xsl\30-kus-inline-normalize.xsl

rem 260331 eu us 분기 설정 = 34-kus-db-apply.xsl 
java net.sf.saxon.Transform								-s:temp\30-kus-inline-normalized.xml		-o:temp\34-kus-db-applied_exclude.xml  	-xsl:xsl\34-kus-db-apply_ber_exclude.xsl  flag=on
java net.sf.saxon.Transform								-s:temp\34-kus-db-applied_exclude.xml		-o:temp\34-kus-db-applied.xml  			-xsl:xsl\34-kus-db-apply_ber.xsl  flag=on

java net.sf.saxon.Transform								-s:temp\34-kus-db-applied.xml				-o:temp\35-kus-beautified.xml  			-xsl:xsl\31-kus-beautify.xsl
java net.sf.saxon.Transform 								-s:temp\35-kus-beautified.xml		 		-o:temp\14-namespace-removed.xml 	 	-xsl:xsl\14-namespace-remove.xsl
java net.sf.saxon.Transform 								-s:temp\14-namespace-removed.xml  		-o:temp\15-id-cleaned.xml  				-xsl:xsl\15-id-clean_NotFileNameChange.xsl
java net.sf.saxon.Transform 								-s:temp\15-id-cleaned.xml  				-o:temp\16-xref-cleaned.xml  				-xsl:xsl\16-xref-clean_NotFileNameChange.xsl
java net.sf.saxon.Transform 								-s:temp\16-xref-cleaned.xml  				-o:temp\17-related-links.xml  				-xsl:xsl\17-related-links_NotFileNameChange.xsl
java net.sf.saxon.Transform								-s:temp\17-related-links.xml  				-o:xsl\dummy.xml  							-xsl:xsl\05-topicalize_2.xsl
java net.sf.saxon.Transform 								-s:temp\17-related-links.xml  				-o:xsl\dummy.xml  							-xsl:xsl\08-dita-beautify.xsl

rem 260331 eu us 분기 설정 = 41-make-change-report.xsl
java net.sf.saxon.Transform 								-s:temp\17-related-links.xml  				-o:temp\excel-change-report.xml  			-xsl:xsl\41-make-change-report_ber.xsl

cscript //nologo "%ROOT%xsl\Convert_Xml_To_Excel.vbs"

del temp\09-doctype-removed.xml temp\10-namespace-removed.xml temp\11-toc-created.xml temp\13-topic-merged.xml temp\14-namespace-removed.xml temp\15-id-cleaned.xml temp\34-kus-db-applied_exclude.xml  > NUL
del temp\16-xref-cleaned.xml temp\17-related-links.xml temp\29-kus-text-normalized.xml temp\30-kus-inline-normalized.xml temp\34-kus-db-applied.xml temp\35-kus-beautified.xml > NUL
del temp\excel-change-report.xml > NUL
rem rd /q/s temp
echo Done.
pause