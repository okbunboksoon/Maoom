@echo off

set SAXON=%~dp0
set CLASSPATH=%SAXON%lib\saxon-ee-10.0.jar;%CLASSPATH%
set CLASSPATH=%SAXON%lib\xml-resolver-1.2.jar;%CLASSPATH%

rem set CLASSPATH=C:\Users\adobe\Desktop\DITATransformOnPC\dita-ot-3.7.4\lib\Saxon-HE-10.6.jar;%CLASSPATH%
rem set CLASSPATH=C:\Users\adobe\Desktop\DITATransformOnPC\dita-ot-3.7.4\lib\xml-resolver-1.2.jar;%CLASSPATH%

echo Please wait a moment!
echo Processing... 

java net.sf.saxon.Transform -catalog:xsl\catalog.xml 	-s:topics\legal_KIA-SP3-ICE-HEV-en_GB-2027.ditamap			-o:temp\output.xml  		-xsl:xsl\65-make-legal-hash.xsl

echo Done.
pause