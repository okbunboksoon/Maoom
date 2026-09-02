Option Explicit

' ==============================
' 실행 인자: k1 k3 k6 ... (사양군 선택)
' - 인자 없으면 헤더 영역(startCol~endCol)의 모든 사양군 열 자동 선택
' ==============================
Dim args: Set args = WScript.Arguments

Dim keys, i, allKeysMode
If args.Count = 0 Then
  ' 인자를 안 주면: 헤더 영역(startCol~endCol)에 있는 사양군 열을 전부 자동 감지해서 사용
  allKeysMode = True
Else
  ' 인자를 주면: 지정한 키만 사용 (예: k1 k3 k7)
  allKeysMode = False
  ReDim keys(args.Count-1)
  For i = 0 To args.Count - 1
    keys(i) = LCase(Trim(CStr(args(i))))
  Next
End If

' ==============================
' 기본 경로 설정
' ==============================
Dim fso: Set fso = CreateObject("Scripting.FileSystemObject")
Dim scriptFullPath: scriptFullPath = WScript.ScriptFullName
Dim scriptFolder: scriptFolder = fso.GetParentFolderName(scriptFullPath)
Dim parentFolder: parentFolder = fso.GetParentFolderName(scriptFolder)
Dim tempFolder: tempFolder = fso.BuildPath(parentFolder, "temp")

If Not fso.FolderExists(tempFolder) Then
  WScript.Echo "상위 temp 폴더를 찾을 수 없습니다: " & tempFolder
  WScript.Quit 1
End If

Dim excelFile: excelFile = ""
Dim f, lowerName, ext
For Each f In fso.GetFolder(tempFolder).Files
  ext = LCase(fso.GetExtensionName(f.Name))
  If ext = "xlsx" Or ext = "xls" Then
    lowerName = LCase(f.Name)
    If lowerName <> "result.xlsx" And Left(lowerName, 2) <> "~$" Then
      excelFile = f.Path
      Exit For
    End If
  End If
Next

If excelFile = "" Then
  WScript.Echo "temp 폴더에서 입력용 .xlsx/.xls 파일을 찾을 수 없습니다."
  WScript.Quit 1
End If

Dim xl: Set xl = CreateObject("Excel.Application")
xl.Visible = False
xl.DisplayAlerts = False

Dim wb: Set wb = xl.Workbooks.Open(excelFile)
Dim ws: Set ws = wb.Sheets(1)

' ==============================
' 사양군 헤더 구간 설정
' ==============================
Dim mergedRange, startCol, endCol, headerRow, tryRow, dataStartRow

' 기존 로직: H5 병합 영역만 사양군 구간으로 사용
'Set mergedRange = ws.Range("H5").MergeArea
'startCol = mergedRange.Column
'endCol   = startCol + mergedRange.Columns.Count - 1
'headerRow = mergedRange.Row + 1

Function CleanText(s)
  Dim t: t = CStr(s)
  t = Replace(t, ChrW(160), " ")
  t = Replace(t, ChrW(8239), " ")
  t = Replace(t, ChrW(8203), "")
  CleanText = LCase(Trim(t))
End Function

Function MergeAreaHasSpecTitle(rng)
  MergeAreaHasSpecTitle = (InStr(CleanText(rng.Cells(1, 1).Value), CleanText(ChrW(&HC0AC) & ChrW(&HC591) & ChrW(&HAD70))) > 0)
End Function

Set mergedRange = ws.Range("H5").MergeArea
If Not MergeAreaHasSpecTitle(mergedRange) Then
  Set mergedRange = ws.Range("H3").MergeArea
End If

startCol = mergedRange.Column
endCol   = startCol + mergedRange.Columns.Count - 1
headerRow = mergedRange.Row + mergedRange.Rows.Count

' ==============================
' 주변 행(r, r+1, r-1, r+2, r-2)에서 헤더 찾기
' ==============================
Dim keepCols(), keepHeadTexts(), keepCount, pass, want, c, headerVal, rawHead
ReDim keepCols(-1): ReDim keepHeadTexts(-1): keepCount = 0

For pass = 0 To 4
  If pass = 0 Then tryRow = headerRow
  If pass = 1 Then tryRow = headerRow + 1
  If pass = 2 Then tryRow = headerRow - 1
  If pass = 3 Then tryRow = headerRow + 2
  If pass = 4 Then tryRow = headerRow - 2

  ReDim keepCols(-1): ReDim keepHeadTexts(-1): keepCount = 0

  If allKeysMode Then
    ' 인자를 안 준 경우: 헤더 영역의 비어 있지 않은 모든 셀을 사양군 열로 사용
    For c = startCol To endCol
      rawHead   = CStr(ws.Cells(tryRow, c).Value)
      headerVal = CleanText(rawHead)
      If headerVal <> "" Then
        ReDim Preserve keepCols(keepCount)
        ReDim Preserve keepHeadTexts(keepCount)
        keepCols(keepCount)      = c
        keepHeadTexts(keepCount) = Trim(CStr(rawHead))
        keepCount = keepCount + 1
      End If
    Next
  Else
    ' 인자를 준 경우: 지정한 키(header 값과 일치하는 열)만 사용
    For i = 0 To UBound(keys)
      want = keys(i)
      For c = startCol To endCol
        rawHead   = CStr(ws.Cells(tryRow, c).Value)
        headerVal = CleanText(rawHead)
        If headerVal = want Then
          ReDim Preserve keepCols(keepCount)
          ReDim Preserve keepHeadTexts(keepCount)
          keepCols(keepCount)      = c
          keepHeadTexts(keepCount) = Trim(CStr(rawHead))
          keepCount = keepCount + 1
          Exit For
        End If
      Next
    Next
  End If

  If keepCount > 0 Then
    headerRow = tryRow
    Exit For
  End If
Next

If keepCount = 0 Then
  wb.Close False
  xl.Quit
  If allKeysMode Then
    WScript.Echo "헤더 영역에서 사양군 열을 찾지 못했습니다."
  Else
    WScript.Echo "요청한 키에 해당하는 열을 찾지 못했습니다."
  End If
  WScript.Quit 2
End If

' ==============================
' 데이터 복사
' ==============================
Dim lastRow: lastRow = ws.Cells(ws.Rows.Count, "A").End(-4162).Row
dataStartRow = headerRow + 1

Dim newWb, newWs, colList(), idx, newColIndex, col
Set newWb = xl.Workbooks.Add
Set newWs = newWb.Sheets(1)
newWs.Cells.Clear

newWs.Cells(1, 1).Value = "ITEM NO"
newWs.Cells(1, 2).Value = "DESCRIPTION"
For i = 0 To keepCount-1
  newWs.Cells(1, 3 + i).Value = keepHeadTexts(i)
Next

ReDim colList(1 + keepCount)
colList(0) = 1  ' ITEM NO (A)
colList(1) = 3  ' DESCRIPTION (C)
idx = 2
For i = 0 To keepCount-1
  colList(idx) = keepCols(i)
  idx = idx + 1
Next

newColIndex = 1
For i = 0 To UBound(colList)
  col = colList(i)
  ' 기존 로직: 데이터 시작 행을 8행으로 고정
  'ws.Range(ws.Cells(8, col), ws.Cells(lastRow, col)).Copy
  ws.Range(ws.Cells(dataStartRow, col), ws.Cells(lastRow, col)).Copy
  newWs.Cells(2, newColIndex).PasteSpecial -4163  ' xlPasteValues
  newColIndex = newColIndex + 1
Next

' ==============================
' 결과 저장
' ==============================
Dim outPath
outPath = fso.BuildPath(tempFolder, "result.xlsx")

On Error Resume Next
newWb.SaveAs outPath, 51
If Err.Number <> 0 Then
  Err.Clear
  outPath = fso.BuildPath(tempFolder, "result_" & _
    Year(Now) & Right("0" & Month(Now),2) & Right("0" & Day(Now),2) & "_" & _
    Right("0" & Hour(Now),2) & Right("0" & Minute(Now),2) & Right("0" & Second(Now),2) & ".xlsx")
  newWb.SaveAs outPath, 51
End If
On Error GoTo 0

newWb.Close True
wb.Close False
xl.Quit
