<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="2.0"
  xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

  <xsl:output method="html" indent="yes" encoding="UTF-8"/>

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
            --hover: #fff7d6;
          }
          * { box-sizing: border-box; }
          body {
            margin: 0;
            background: var(--bg);
            color: var(--text);
            font-family: "Malgun Gothic", "맑은 고딕", Arial, sans-serif;
            font-size: 13px;
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
            width: 150px;
            color: #075985;
            font-family: Consolas, "Courier New", monospace;
            font-weight: 700;
            white-space: nowrap;
          }
          .location {
            width: 260px;
            color: var(--muted);
            font-family: Consolas, "Courier New", monospace;
            font-size: 12px;
          }
          .error-type {
            width: 210px;
            color: #9a3412;
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
          <h2>오류 하이라이트 확인</h2>
          <div class="summary">
            <div class="badge">오류 <span><xsl:value-of select="count(file/detect)"/></span></div>
            <div class="badge">파일 <span><xsl:value-of select="count(file[detect])"/></span></div>
          </div>
        </div>
        <div class="table-wrap">
          <table>
            <colgroup>
              <col style="width:150px"/>
              <col style="width:260px"/>
              <col style="width:210px"/>
              <col/>
            </colgroup>
            <thead>
              <tr>
                <th>파일명</th>
                <th>위치</th>
                <th>오류 유형</th>
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

  <xsl:template match="file">
    <xsl:for-each select="detect">
      <tr>
        <td class="filename"><xsl:value-of select="../@filename"/></td>
        <td class="location"><xsl:value-of select="@location"/></td>
        <td class="error-type"><xsl:value-of select="substring-before(., '###')"/></td>
        <td class="sentence">
          <xsl:call-template name="highlight-span">
            <xsl:with-param name="text" select="substring-after(., '###')"/>
          </xsl:call-template>
        </td>
      </tr>
    </xsl:for-each>
  </xsl:template>

  <!--  하이라이트 처리 템플릿 -->
  <xsl:template name="highlight-span">
    <xsl:param name="text"/>
    <xsl:variable name="parts" select="tokenize($text, '&lt;/?span&gt;')"/>
    <xsl:for-each select="$parts">
      <xsl:choose>
        <!-- 홀수번째 = 강조 -->
        <xsl:when test="position() mod 2 = 0">
          <xsl:text disable-output-escaping="yes">&lt;span&gt;</xsl:text>
          <xsl:value-of select="."/>
          <xsl:text disable-output-escaping="yes">&lt;/span&gt;</xsl:text>
        </xsl:when>
        <!-- 나머지는 일반 -->
        <xsl:otherwise>
          <xsl:value-of select="."/>
        </xsl:otherwise>
      </xsl:choose>
    </xsl:for-each>
  </xsl:template>

</xsl:stylesheet>
