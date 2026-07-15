Option Explicit

Dim fso, scriptFolder, tempFolder, folder, file
Dim xmlPath, xlsxPath

Set fso = CreateObject("Scripting.FileSystemObject")

' 현재 스크립트 위치
scriptFolder = fso.GetParentFolderName(WScript.ScriptFullName)

' temp 폴더
tempFolder = fso.BuildPath(scriptFolder, "..\temp")

' 폴더 객체
Set folder = fso.GetFolder(tempFolder)

' xml 전체 반복
For Each file In folder.Files

    If LCase(fso.GetExtensionName(file.Name)) = "xml" Then

        xmlPath = file.Path

        ' xml → xlsx 이름 변환
        xlsxPath = fso.BuildPath(tempFolder, fso.GetBaseName(file.Name) & ".xlsx")

        Call ConvertXmlToExcel(xmlPath, xlsxPath)

    End If

Next

MsgBox "완료!"



' =========================
' XML → Excel 변환 함수
' =========================
Sub ConvertXmlToExcel(xmlPath, xlsxPath)

    Dim excel, wb

    Set excel = CreateObject("Excel.Application")
    excel.Visible = False
    excel.DisplayAlerts = False

    ' XML 열기
    Set wb = excel.Workbooks.Open(xmlPath)

    ' XLSX 저장
    wb.SaveAs xlsxPath, 51  ' 51 = xlOpenXMLWorkbook

    wb.Close False
    excel.Quit

    Set wb = Nothing
    Set excel = Nothing

End Sub