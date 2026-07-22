@echo off
setlocal EnableExtensions EnableDelayedExpansion

rem ===== paths =====
set "ROOT=%~dp0"
set "VBS2=%ROOT%xsl\z02_xtractColumns.vbs"
set "VBS3=%ROOT%xsl\z03_resultTorefine.vbs"

set CLASSPATH=%ROOT%lib\Saxon-HE-10.6.jar;%CLASSPATH%
set CLASSPATH=%ROOT%lib\xml-resolver-1.2.jar;%CLASSPATH%

echo Please wait a moment!
echo Processing...
 
set "KEYS="
set /p KEYS=Keys (e.g., k1 k3 k6, Enter=ALL): 

if defined KEYS (
  cscript //nologo "%VBS2%" %KEYS%
) else (
  cscript //nologo "%VBS2%"
)

cscript //nologo "%VBS3%"
java net.sf.saxon.Transform 		-s:temp/refined.xml 				-o:temp/onelined.xml 			-xsl:xsl\0200-oneline.xsl
java net.sf.saxon.Transform 		-s:temp/onelined.xml 			-o:temp/excel.xml 				-xsl:xsl\0300-make-excel-text.xsl

del temp\onelined.xml temp\refined.xml temp\result.xlsx

echo Done.
pause
