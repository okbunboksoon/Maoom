@echo off

set SAXON=%~dp0
set CLASSPATH=%SAXON%lib\saxon-ee-10.0.jar;%CLASSPATH%
set CLASSPATH=%SAXON%lib\xml-resolver-1.2.jar;%CLASSPATH%

echo Please wait a moment!
echo Processing...

cd /d "%~dp0"

java net.sf.saxon.Transform -catalog:xsl\catalog.xml -s:topics\LM-template.xml -o:temp\LM-ditamap.ditamap -xsl:xsl\66-make-legal-ditamap.xsl

echo Done.
pause
