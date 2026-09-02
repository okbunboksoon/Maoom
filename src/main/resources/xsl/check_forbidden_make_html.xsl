<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
	<xsl:output method="html" indent="yes" encoding="UTF-8"/>
	<xsl:strip-space elements="*"/>
	<!-- HTML 기본 구조 + 스타일 -->
	<xsl:template match="/fileset">
		<html>
			<head>
				<meta charset="UTF-8"/>
				<style>
          :root {
            --bg: #f5f7fa;
            --panel: #ffffff;
            --line: #d8dee8;
            --line-soft: #e8edf3;
            --head: #eaf0f7;
            --text: #1f2937;
            --muted: #64748b;
            --accent: #be123c;
            --accent-bg: #ffe4e6;
            --change-bg: #ecfdf5;
            --hover: #fff7d6;
          }
          * { box-sizing: border-box; }
          body {
            margin: 0;
            background: var(--bg);
            color: var(--text);
            font-family: "Malgun Gothic", "맑은 고딕", Arial, sans-serif;
            font-size: 13px;
            line-height: 1.45;
          }
          .report-header {
            position: sticky;
            top: 0;
            z-index: 20;
            padding: 14px 18px 12px;
            background: var(--panel);
            border-bottom: 1px solid var(--line);
            box-shadow: 0 1px 3px rgba(15, 23, 42, .06);
          }
          h2 {
            margin: 0 0 10px;
            font-size: 18px;
            font-weight: 700;
          }
          .summary {
            display: flex;
            gap: 8px;
            flex-wrap: wrap;
          }
          .badge {
            display: inline-flex;
            align-items: center;
            min-height: 26px;
            padding: 4px 10px;
            border: 1px solid var(--line);
            border-radius: 4px;
            background: #f8fafc;
            font-weight: 700;
          }
          .badge span {
            margin-left: 6px;
            color: var(--accent);
          }
          .table-wrap { padding: 14px; }
          table {
            width: 100%;
            border-collapse: collapse;
            table-layout: fixed;
            background: var(--panel);
          }
          th, td {
            border: 1px solid var(--line-soft);
            padding: 7px 8px;
            vertical-align: top;
            word-break: break-word;
          }
          th {
            position: sticky;
            top: 75px;
            z-index: 10;
            background: var(--head);
            border-color: var(--line);
            text-align: left;
            font-weight: 700;
          }
          tbody tr:nth-child(even) { background: #fafbfc; }
          tbody tr:hover { background: var(--hover); }
          .filename {
            color: #075985;
            font-family: Consolas, "Courier New", monospace;
            font-weight: 700;
            white-space: nowrap;
          }
          .location {
            color: var(--muted);
            font-family: Consolas, "Courier New", monospace;
            font-size: 12px;
          }
          .type {
            color: #9a3412;
            font-weight: 700;
          }
          .before {
            color: var(--accent);
            font-weight: 800;
          }
          .after {
            background: var(--change-bg);
            font-weight: 700;
          }
          .sentence { line-height: 1.55; }
          span {
            display: inline;
            padding: 1px 3px;
            border-radius: 3px;
            background: var(--accent-bg);
            color: var(--accent);
            font-weight: 800;
          }
        </style>
			</head>
			<body>
				<div class="report-header">
					<h2>금칙어 검출 결과</h2>
					<div class="summary">
						<div class="badge">검출 <span><xsl:value-of select="count(file/detect)"/></span></div>
						<div class="badge">파일 <span><xsl:value-of select="count(file[detect])"/></span></div>
						<div class="badge">금칙어 <span><xsl:value-of select="count(file/detect[@type='금칙어'])"/></span></div>
						<div class="badge">용어 변경 <span><xsl:value-of select="count(file/detect[@type='용어 변경'])"/></span></div>
					</div>
				</div>
				<div class="table-wrap">
					<table>
						<colgroup>
							<col style="width:150px"/>
							<col style="width:260px"/>
							<col style="width:95px"/>
							<col style="width:150px"/>
							<col style="width:180px"/>
							<col/>
						</colgroup>
						<thead>
							<tr>
								<th>파일명</th>
								<th>위치</th>
								<th>분류</th>
								<th>변경 전</th>
								<th>변경 후</th>
								<th>문장</th>
							</tr>
						</thead>
						<tbody>
							<xsl:apply-templates select="file"/>
						</tbody>
					</table>
				</div>
			</body>
		</html>
	</xsl:template>
	<!-- ===========================
       파일별 detect 항목 출력
       =========================== -->
	<xsl:template match="file">
		<xsl:for-each select="detect">
			<tr>
				<td class="filename">
					<xsl:value-of select="../@filename"/>
				</td>
				<td class="location">
					<xsl:value-of select="@location"/>
				</td>
				<td class="type">
					<xsl:value-of select="@type"/>
				</td>
				<td class="before">
					<xsl:value-of select="@old"/>
				</td>
				<td class="after">
					<xsl:value-of select="@new"/>
				</td>
				<td class="sentence">
					<!-- context 안의 내용 처리 -->
					<xsl:apply-templates select="context/node()"/>
				</td>
			</tr>
		</xsl:for-each>
	</xsl:template>
	<!--  강조 span → 빨간 글씨 -->
	<xsl:template match="span" priority="3">
		<span style="color:red; font-weight:bold;">
			<xsl:value-of select="."/>
		</span>
	</xsl:template>
	<!-- 일반 텍스트 노드 -->
	<xsl:template match="text()" priority="2">
		<xsl:value-of select="."/>
	</xsl:template>
	<!-- 그 외 모든 노드 -->
	<xsl:template match="node()" priority="-1">
		<xsl:apply-templates/>
	</xsl:template>
</xsl:stylesheet>
