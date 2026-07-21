Option Explicit

' === 현재 스크립트 경로 기준 설정 ===
Dim fso, scriptFolder, xmlPath, xlsxPath
Set fso = CreateObject("Scripting.FileSystemObject")
scriptFolder = fso.GetParentFolderName(WScript.ScriptFullName)

' 입력 XML / 출력 XLSX 파일 경로
xmlPath  = fso.BuildPath(scriptFolder, "..\temp\excel-change-report.xml")
xlsxPath = fso.BuildPath(scriptFolder, "..\temp\excel-change-report.xlsx")

' === 엑셀 객체 생성 ===
Dim xl, wb
Set xl = CreateObject("Excel.Application")
xl.Visible = False

On Error Resume Next
Set wb = xl.Workbooks.Open(xmlPath)

If Err.Number <> 0 Then
    WScript.Echo "엑셀 파일을 열 수 없습니다: " & xmlPath
    xl.Quit
    WScript.Quit 1
End If
On Error GoTo 0

' === XLSX 형식으로 저장 (51 = xlOpenXMLWorkbook) ===
wb.SaveAs xlsxPath, 51   ' .xlsx 형식으로 저장

' === 정리 ===
wb.Close False
xl.Quit

Set wb = Nothing
Set xl = Nothing
Set fso = Nothing
