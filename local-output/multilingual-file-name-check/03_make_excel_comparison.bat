@echo off
setlocal EnableExtensions EnableDelayedExpansion

rem ===== paths =====
set "ROOT=%~dp0"

set CLASSPATH=%ROOT%lib\Saxon-HE-10.6.jar;%CLASSPATH%
set CLASSPATH=%ROOT%lib\xml-resolver-1.2.jar;%CLASSPATH%

echo Please wait a moment!
echo Processing...

java net.sf.saxon.Transform 			-s:"%ROOT%temp\excel_after.xml"		 -o:"%ROOT%temp\excel_comparison.xml" 				-xsl:"%ROOT%xsl\0301-excel-comparison.xsl" 		before="%ROOT%temp/excel_before.xml"
java net.sf.saxon.Transform   		-s:temp\excel_comparison.xml    		 -o:temp\normalized.xml   								-xsl:xsl\0400-normalize.xsl
java net.sf.saxon.Transform 			-s:temp\normalized.xml   		  		 -o:temp\test2.xml  									-xsl:xsl\0401-highlight.xsl
java net.sf.saxon.Transform 			-s:temp\test2.xml   		  		 -o:temp\Product_Equipment_List_Comparison.xml  			-xsl:xsl\0402-product-equipment-list-comparison.xsl

cscript //nologo "%ROOT%xsl\Convert_Xml_To_Excel_comparison.vbs"

del temp\excel_after.xml temp\excel_before.xml temp\excel_comparison.xml temp\normalized.xml > nul
del temp\Product_Equipment_List_Comparison.xml temp\product-spec-comparison.log temp\test2.xml > nul

echo Done.
pause
