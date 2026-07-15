Const ForReading = 1
Const ForWriting = 2

filename = WScript.Arguments.Unnamed(0)

Set objFSO = CreateObject("Scripting.FileSystemObject")
Set objFile = objFSO.OpenTextFile("source\" & filename, ForReading)

strText = objFile.ReadAll
objFile.Close

If InStr(strText, "&Appendix;") > 0 Then
	strText = Replace(strText, "&Abbreviation;", "&TEMP;")
	strText = Replace(strText, "&Appendix;", "&Abbreviation;")
	strText = Replace(strText, "&TEMP;", "&Appendix;")
End If

strNewText = Replace(strText, " PUBLIC", "")
strNewText0 = Replace(strNewText, "-//OASIS//DTD DITA BookMap V1.2 Subset for KIA_OM//EN", "")
strNewText1 = Replace(strNewText0, " """"", "")
strNewText2 = Replace(strNewText1, "SYSTEM ", "")
strNewText3 = Replace(strNewText2, "&", "<chapter filename=""&")
strNewText4 = Replace(strNewText3, ";", ";""/>")
strNewText5 = Replace(strNewText4, "bookmap xml", "bookmap mapname=""" & filename & """ xml")

Set objFile = objFSO.OpenTextFile("xsl\bookmap.xml", ForWriting)
objFile.WriteLine strNewText5

objFile.Close