<xsl:stylesheet version="2.0"
  xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
  xmlns="urn:schemas-microsoft-com:office:spreadsheet"
  xmlns:o="urn:schemas-microsoft-com:office:office"
  xmlns:x="urn:schemas-microsoft-com:office:excel"
  xmlns:ss="urn:schemas-microsoft-com:office:spreadsheet"
  xmlns:html="http://www.w3.org/TR/REC-html40">

  <xsl:output method="xml" indent="no" omit-xml-declaration="no"/>

  <xsl:template match="/">
    <xsl:processing-instruction name="mso-application">progid="Excel.Sheet"</xsl:processing-instruction>
    <Workbook>
      <DocumentProperties xmlns="urn:schemas-microsoft-com:office:office">
        <Version>16.00</Version>
      </DocumentProperties>
      <OfficeDocumentSettings xmlns="urn:schemas-microsoft-com:office:office">
        <AllowPNG/>
      </OfficeDocumentSettings>
      <ExcelWorkbook xmlns="urn:schemas-microsoft-com:office:excel">
        <WindowHeight>17325</WindowHeight>
        <WindowWidth>32767</WindowWidth>
        <WindowTopX>32767</WindowTopX>
        <WindowTopY>32767</WindowTopY>
        <ProtectStructure>False</ProtectStructure>
        <ProtectWindows>False</ProtectWindows>
      </ExcelWorkbook>

      <Styles>
        <Style ss:ID="Default" ss:Name="Normal">
          <Alignment ss:Vertical="Center"/>
          <Borders/>
          <Font ss:FontName="맑은 고딕" x:CharSet="129" x:Family="Modern" ss:Size="11" ss:Color="#000000"/>
          <Interior/>
          <NumberFormat/>
          <Protection/>
        </Style>
        <Style ss:ID="sHead">
          <Alignment ss:Horizontal="Center" ss:Vertical="Center"/>
          <Font ss:Bold="1"/>
          <Interior ss:Color="#92D050" ss:Pattern="Solid"/>
        </Style>
        <Style ss:ID="sLeft">
          <Alignment ss:Horizontal="Left" ss:Vertical="Center"/>
        </Style>
        <Style ss:ID="sCenter">
          <Alignment ss:Horizontal="Center" ss:Vertical="Center"/>
        </Style>
        <Style ss:ID="sWrap">
          <Alignment ss:Horizontal="Left" ss:Vertical="Center" ss:WrapText="1"/>
        </Style>
      </Styles>

      <Worksheet ss:Name="Forbidden Report">
        <Table ss:ExpandedColumnCount="7" ss:ExpandedRowCount="65535" x:FullColumns="1" x:FullRows="1"
               ss:DefaultColumnWidth="54" ss:DefaultRowHeight="16.5">
          <!-- 열 폭 -->
          <Column ss:StyleID="sLeft" ss:Width="58.5"/>    <!-- A: No -->
          <Column ss:StyleID="sLeft" ss:Width="90.75"/>   <!-- B: Filename -->
          <Column ss:StyleID="sLeft" ss:Width="348.75"/>	  <!-- C: Location   -->
          <Column ss:StyleID="sLeft" ss:Width="100"/>  	 <!-- D: TYPE -->
          <Column ss:StyleID="sLeft" ss:Width="160"/>  	 <!-- E: OLD -->
          <Column ss:StyleID="sLeft" ss:Width="160"/>  	 <!-- F: NEW -->
          <Column ss:StyleID="sWrap" ss:Width="800"/>   <!-- G: context (wrap) -->

          <!-- 헤더 -->
          <Row>
            <Cell ss:StyleID="sHead"><Data ss:Type="String">No.</Data></Cell>
            <Cell ss:StyleID="sHead"><Data ss:Type="String">Filename</Data></Cell>
            <Cell ss:StyleID="sHead"><Data ss:Type="String">Location</Data></Cell>
            <Cell ss:StyleID="sHead"><Data ss:Type="String">분류</Data></Cell>
            <Cell ss:StyleID="sHead"><Data ss:Type="String">변경 전</Data></Cell>
            <Cell ss:StyleID="sHead"><Data ss:Type="String">변경 후</Data></Cell>
            <Cell ss:StyleID="sHead"><Data ss:Type="String">Context</Data></Cell>
          </Row>

          <!-- 본문 -->
          <xsl:for-each select="fileset/file">
            <xsl:apply-templates select="."/>
          </xsl:for-each>
        </Table>

        <WorksheetOptions xmlns="urn:schemas-microsoft-com:office:excel">
          <PageSetup>
            <Header x:Margin="0.3"/>
            <Footer x:Margin="0.3"/>
            <PageMargins x:Bottom="0.75" x:Left="0.7" x:Right="0.7" x:Top="0.75"/>
          </PageSetup>
          <Unsynced/>
          <Selected/>
          <Panes>
            <Pane>
              <Number>3</Number>
              <ActiveRow>1</ActiveRow>
              <ActiveCol>1</ActiveCol>
            </Pane>
          </Panes>
          <ProtectObjects>False</ProtectObjects>
          <ProtectScenarios>False</ProtectScenarios>
        </WorksheetOptions>
      </Worksheet>
    </Workbook>
  </xsl:template>

  <!-- 파일 단위 행 생성 -->
  <xsl:template match="file">
    <xsl:variable name="filename" select="@filename"/>
    <xsl:for-each select="detect">
      <Row ss:AutoFitHeight="0">
        <!-- A: No. (헤더 제외하고 1부터) -->
        <Cell ss:StyleID="sCenter">
          <Data ss:Type="Number"><xsl:value-of select="count(preceding::detect) + 1"/></Data>
        </Cell>
        <!-- B: Filename (같은 파일은 첫 행만 표시) -->
        <Cell ss:StyleID="sCenter">
          <Data ss:Type="String">
            <xsl:if test="position() = 1">
              <xsl:value-of select="$filename"/>
            </xsl:if>
          </Data>
        </Cell>
          <!-- C: Location -->
		<Cell ss:StyleID="sLeft">
		    <Data ss:Type="String"><xsl:value-of select="@location"/></Data>
		  </Cell>
        <!-- D: TYPE -->
        <Cell ss:StyleID="sCenter"><Data ss:Type="String"><xsl:value-of select="@type"/></Data></Cell>
        <!-- E: OLD -->
        <Cell ss:StyleID="sCenter"><Data ss:Type="String"><xsl:value-of select="@old"/></Data></Cell>
        <!-- F: NEW -->
        <Cell ss:StyleID="sCenter"><Data ss:Type="String"><xsl:value-of select="@new"/></Data></Cell>
        <!-- G: context (span은 빨간색으로 표시) -->
        <Cell ss:StyleID="sWrap">
          <Data ss:Type="String">
            <xsl:apply-templates select="context/node()"/>
          </Data>
        </Cell>
      </Row>
    </xsl:for-each>
  </xsl:template>

  <!-- context 안의 강조 span은 빨간색 (가장 우선) -->
  <xsl:template match="span" priority="3">
    <Font xmlns="http://www.w3.org/TR/REC-html40" html:Color="#FF0000">
      <xsl:value-of select="."/>
    </Font>
  </xsl:template>

  <!-- 텍스트 노드 출력 (span 다음 우선) -->
  <xsl:template match="text()" priority="2">
    <xsl:value-of select="."/>
  </xsl:template>

  <!-- 캐치올: 요소/주석/PI만 처리하고 자식으로 내려감 (우선순위 낮게) -->
  <xsl:template match="node()" priority="-1">
    <xsl:apply-templates/>
  </xsl:template>

</xsl:stylesheet>
