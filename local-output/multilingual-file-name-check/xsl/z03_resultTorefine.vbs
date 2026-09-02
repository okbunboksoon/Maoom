Option Explicit

' ==============================
' 경로 설정: (이 VBS가 xsl 폴더에 있고) 상위\temp 사용
' ==============================
Dim fso, scriptFolder, parentFolder, tempFolder
Set fso = CreateObject("Scripting.FileSystemObject")
scriptFolder  = fso.GetParentFolderName(WScript.ScriptFullName)
parentFolder  = fso.GetParentFolderName(scriptFolder)
tempFolder    = fso.BuildPath(parentFolder, "temp")

If Not fso.FolderExists(tempFolder) Then
  WScript.Echo "temp 폴더가 없습니다: " & tempFolder
  WScript.Quit 1
End If

' 입력/출력 파일
Dim ExcelFile, XmlFile
ExcelFile = fso.BuildPath(tempFolder, "result.xlsx")  ' 입력
XmlFile   = fso.BuildPath(tempFolder, "refined.xml")  ' 출력

' ==============================
' Excel 열기 (비표시/경고숨김)
' ==============================
Dim xl, wb, ws
Set xl = CreateObject("Excel.Application")
xl.Visible = False
xl.DisplayAlerts = False

On Error Resume Next
Set wb = xl.Workbooks.Open(ExcelFile)
If Err.Number <> 0 Then
  WScript.Echo " Excel 열기 실패: " & ExcelFile
  xl.Quit
  WScript.Quit 1
End If
On Error GoTo 0

Set ws = wb.Sheets(1)

' ==============================
' 마지막 행/열
'  - UsedRange 기반(간단)
' ==============================
Dim lastRow, lastCol
lastRow = ws.UsedRange.Rows.Count
lastCol = ws.UsedRange.Columns.Count

' ==============================
' XML(UTF-8) 파일 생성: ADODB.Stream
' ==============================
Dim stm: Set stm = CreateObject("ADODB.Stream")
stm.Type = 2                 ' adTypeText
stm.Charset = "utf-8"        ' UTF-8로 실제 기록
stm.Open

' XML 헤더 및 루트 시작
stm.WriteText "<?xml version=""1.0"" encoding=""UTF-8""?>", 1    ' adWriteLine
stm.WriteText "<table>", 1

' ==============================
' 행/열 루프
'   1열=item, 2열=desc, 3열~lastCol = cell*
' ==============================
Dim i, j, cellValue, itemVal, descVal
For i = 1 To lastRow
  stm.WriteText "  <row>", 1

  ' item
  itemVal = NormalizeText(ws.Cells(i, 1).Value)
  stm.WriteText "    <item>" & EscapeXml(itemVal) & "</item>", 1

  ' desc
  descVal = NormalizeText(ws.Cells(i, 2).Value)
  stm.WriteText "    <desc>" & EscapeXml(descVal) & "</desc>", 1

  ' C열 이후
  For j = 3 To lastCol
    cellValue = ws.Cells(i, j).Value
    If IsEmpty(cellValue) Or IsNull(cellValue) Or cellValue = "" Then
      stm.WriteText "    <cell/>", 1
    Else
      stm.WriteText "    <cell>" & EscapeXml(NormalizeText(cellValue)) & "</cell>", 1
    End If
  Next

  stm.WriteText "  </row>", 1
Next

stm.WriteText "</table>", 1

' 저장(덮어쓰기)
stm.SaveToFile XmlFile, 2    ' adSaveCreateOverWrite
stm.Close
Set stm = Nothing

' ==============================
' Excel 정리
' ==============================
wb.Close False
xl.Quit
Set ws = Nothing
Set wb = Nothing
Set xl = Nothing

' ==============================
' 유틸: XML 이스케이프
' ==============================
Function EscapeXml(str)
  If IsNull(str) Then
    EscapeXml = ""
  Else
    str = CStr(str)
    str = Replace(str, "&", "&amp;")
    str = Replace(str, "<", "&lt;")
    str = Replace(str, ">", "&gt;")
    str = Replace(str, """", "&quot;")
    str = Replace(str, "'", "&apos;")
    EscapeXml = str
  End If
End Function

' ==============================
' 유틸: 텍스트 정규화(탭/개행 → 공백, 다중 공백 축약)
' ==============================
Function NormalizeText(str)
  Dim re
  If IsNull(str) Then
    NormalizeText = ""
    Exit Function
  End If
  str = CStr(str)
  str = Replace(str, vbTab, " ")
  str = Replace(str, vbCr,  " ")
  str = Replace(str, vbLf,  " ")
  str = Trim(str)

  Set re = New RegExp
  re.Global  = True
  re.Pattern = "\s+"
  NormalizeText = re.Replace(str, " ")
End Function
