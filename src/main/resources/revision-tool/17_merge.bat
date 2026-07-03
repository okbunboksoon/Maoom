@echo off

set SAXON=%~dp0
set CLASSPATH=%SAXON%lib\saxon-ee-10.0.jar;%CLASSPATH%
set CLASSPATH=%SAXON%lib\xml-resolver-1.2.jar;%CLASSPATH%



echo Please wait a moment!
echo Processing... 

java net.sf.saxon.Transform -catalog:xsl\catalog.xml 	-s:xsl\dummy.xml  					-o:xsl\dummy.xml  					-xsl:xsl\09-doctype-remove.xsl
java net.sf.saxon.Transform 							-s:temp\09-doctype-removed.xml  	-o:temp\10-namespace-removed.xml  	-xsl:xsl\10-namespace-remove.xsl
java net.sf.saxon.Transform 							-s:temp\10-namespace-removed.xml  	-o:temp\11-toc-created.xml  		-xsl:xsl\11-toc-create.xsl
java net.sf.saxon.Transform -catalog:xsl\catalog.xml	-s:temp\11-toc-created.xml  		-o:temp\13-topic-merged.xml  		-xsl:xsl\13-topic-merge2.xsl
java net.sf.saxon.Transform 							-s:temp\13-topic-merged.xml  		-o:temp\full_merged.xml  			-xsl:xsl\57-merge-beautify.xsl

del temp\09-doctype-removed.xml temp\10-namespace-removed.xml temp\11-toc-created.xml temp\13-topic-merged.xml > NUL

echo Done.
pause