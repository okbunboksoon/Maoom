@echo off

set SAXON=%~dp0
set CLASSPATH=%SAXON%lib\saxon-ee-10.0.jar;%CLASSPATH%
set CLASSPATH=%SAXON%lib\xml-resolver-1.2.jar;%CLASSPATH%
set INDEX_LEVEL=%1
if "%INDEX_LEVEL%"=="" set /p INDEX_LEVEL=Index level [default 10]: 
if "%INDEX_LEVEL%"=="" set INDEX_LEVEL=10

echo Please wait a moment!
echo Processing... 

java net.sf.saxon.Transform -catalog:xsl\catalog.xml 		-s:xsl\dummy.xml  							-o:xsl\dummy.xml  							-xsl:xsl\0000-doctype-remove.xsl
java net.sf.saxon.Transform 								-s:temp\0000-doctype-removed.xml  		-o:temp\10-namespace-removed.xml	  	-xsl:xsl\0001-namespace-remove.xsl
java net.sf.saxon.Transform 								-s:temp\10-namespace-removed.xml  		-o:temp\11-toc-created.xml  				-xsl:xsl\0002-toc-create.xsl
java net.sf.saxon.Transform 								-s:temp\11-toc-created.xml  				-o:xsl\bookmap.xml  						-xsl:xsl\0003-bookmap-create.xsl
java net.sf.saxon.Transform -catalog:xsl\catalog.xml		-s:temp\11-toc-created.xml  				-o:temp\13-topic-merged.xml  				-xsl:xsl\0004-topic-merge.xsl
java net.sf.saxon.Transform								-s:temp\13-topic-merged.xml				-o:temp\31-kus-beautified.xml  			-xsl:xsl\0310-kus-beautify.xsl
java net.sf.saxon.Transform								-s:temp\31-kus-beautified.xml				-o:temp\35-indexterm-extracted.xml  		-xsl:xsl\0350-indexterm-extract.xsl indexLevel=%INDEX_LEVEL%
java net.sf.saxon.Transform								-s:temp\35-indexterm-extracted.xml		-o:temp\excel.xml							-xsl:xsl\0360-excel-xml-create.xsl
cscript //nologo xsl\Convert_Xml_To_Excel_index.vbs

del temp\0000-doctype-removed.xml temp\10-namespace-removed.xml temp\11-toc-created.xml temp\13-topic-merged.xml temp\31-kus-beautified.xml
del temp\35-indexterm-extracted.xml temp\excel.xml

echo Done.
pause
