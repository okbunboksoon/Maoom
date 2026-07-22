<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0"
  xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
  xmlns:o="urn:schemas-microsoft-com:office:office"
  xmlns:x="urn:schemas-microsoft-com:office:excel"
  xmlns:ss="urn:schemas-microsoft-com:office:spreadsheet">

  <xsl:output method="xml" encoding="UTF-8" indent="yes"/>
  <xsl:strip-space elements="*"/>

  <!-- 첫 토큰만 꺼내는 헬퍼 -->
  <xsl:template name="first-token">
    <xsl:param name="s"/>
    <xsl:variable name="t" select="normalize-space($s)"/>
    <xsl:choose>
      <xsl:when test="contains($t,' ')">
        <xsl:value-of select="substring-before($t,' ')"/>
      </xsl:when>
      <xsl:otherwise>
        <xsl:value-of select="$t"/>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>

  <!-- 1행 = 헤더 -->
  <xsl:variable name="header" select="/table/row[1]"/>

  <xsl:template match="/">
    <?mso-application progid="Excel.Sheet"?>
    <Workbook xmlns="urn:schemas-microsoft-com:office:spreadsheet"
              xmlns:o="urn:schemas-microsoft-com:office:office"
              xmlns:x="urn:schemas-microsoft-com:office:excel"
              xmlns:ss="urn:schemas-microsoft-com:office:spreadsheet"
              xmlns:html="http://www.w3.org/TR/REC-html40">

      <!-- 스타일 -->
      <Styles>
        <Style ss:ID="Default" ss:Name="Normal">
          <Alignment ss:Horizontal="Center" ss:Vertical="Center"/>
          <Font ss:FontName="맑은 고딕" ss:Size="10"/>
        </Style>
        <Style ss:ID="YellowCell">
          <Interior ss:Color="#FFFF99" ss:Pattern="Solid"/>
        </Style>
        <Style ss:ID="RedText">
          <Font ss:Color="#FF0000" ss:Bold="1"/>
        </Style>
        <Style ss:ID="RedOnYellow">
          <Interior ss:Color="#FFFF99" ss:Pattern="Solid"/>
          <Font ss:Color="#FF0000" ss:Bold="1"/>
        </Style>
        <Style ss:ID="LeftText">
		  <Alignment ss:Horizontal="Left" ss:Vertical="Center" ss:WrapText="1"/>
		  <Font ss:FontName="맑은 고딕" ss:Size="10"/>
		</Style>
		<Style ss:ID="YellowLeft">
		  <Alignment ss:Horizontal="Left" ss:Vertical="Center" ss:WrapText="1"/>
		  <Interior ss:Color="#FFFF99" ss:Pattern="Solid"/>
		  <Font ss:FontName="맑은 고딕" ss:Size="10"/>
		</Style>
      </Styles>

      <Worksheet ss:Name="Sheet1">
        <Table>
          <!-- 열 너비 -->
          <Column ss:Width="100"/> <!-- A: ITEM NO -->
          <Column ss:Width="200"/> <!-- B: DESCRIPTION -->

          <!-- 헤더 행 -->
          <Row>
            <Cell><Data ss:Type="String"><xsl:value-of select="$header/item"/></Data></Cell>
            <Cell><Data ss:Type="String"><xsl:value-of select="$header/desc"/></Data></Cell>
          
          <!-- C ~ (마지막-2) 까지: 기존 그대로 출력 -->
		<xsl:for-each select="$header/cell[position() &lt; last()-1]">
		  <Cell>
		    <Data ss:Type="String">
		      <xsl:value-of select="."/>
		    </Data>
		  </Cell>
		</xsl:for-each>

		<!-- 끝에서 2번째 열: 중요항목 -->
		<Cell>
		  <Data ss:Type="String">중요항목</Data>
		</Cell>

		<!-- 마지막 열: 편집자 -->
		<Cell>
		  <Data ss:Type="String">편집자</Data>
		</Cell>
		</Row>
          <!-- 데이터 행 (2행부터) -->
          <xsl:for-each select="/table/row[position() &gt; 1]">
            <!-- 행 삭제 규칙: C열부터 값이 전부 비면 스킵 -->
            <xsl:if test="string(item) != ''">
              <xsl:variable name="r" select="."/>
              <xsl:variable name="row-is-yellow" select="@color='yellow'"/>

              <Row>
                <!-- A: ITEM NO -->
                <xsl:variable name="valA" select="string(item)"/>
                <Cell>
			  <xsl:attribute name="ss:StyleID">
			    <xsl:choose>
			      <xsl:when test="$row-is-yellow">YellowLeft</xsl:when>   <!-- 노란 줄이면 왼쪽정렬+노란배경 -->
			      <xsl:otherwise>LeftText</xsl:otherwise>                 <!-- 그 외에는 왼쪽정렬 -->
			    </xsl:choose>
			  </xsl:attribute>
                  <Data ss:Type="String"><xsl:value-of select="$valA"/></Data>
                </Cell>

                <!-- B: DESCRIPTION -->
			<xsl:variable name="valB" select="string(desc)"/>
			<Cell>
			  <xsl:attribute name="ss:StyleID">
			    <xsl:choose>
			      <xsl:when test="$row-is-yellow">YellowLeft</xsl:when>   <!-- 노란 줄이면 왼쪽정렬+노란배경 -->
			      <xsl:otherwise>LeftText</xsl:otherwise>                 <!-- 그 외에는 왼쪽정렬 -->
			    </xsl:choose>
			  </xsl:attribute>
			  <Data ss:Type="String"><xsl:value-of select="$valB"/></Data>
			</Cell>

                <!-- C ~ (마지막-2): 노란 행이면 노란 배경 -->
<xsl:for-each select="$header/cell[position() &lt; last()-1]">
  <xsl:variable name="i" select="position()"/>
  <xsl:variable name="c" select="$r/cell[$i]"/>
  <xsl:variable name="txt" select="string($c)"/>

  <Cell>
    <xsl:choose>
      <xsl:when test="$row-is-yellow and $c/@important='yes'">
        <xsl:attribute name="ss:StyleID">RedOnYellow</xsl:attribute>
      </xsl:when>
      <xsl:when test="$row-is-yellow">
        <xsl:attribute name="ss:StyleID">YellowCell</xsl:attribute>
      </xsl:when>
      <xsl:when test="$c/@important='yes'">
        <xsl:attribute name="ss:StyleID">RedText</xsl:attribute>
      </xsl:when>
    </xsl:choose>

    <Data ss:Type="String">
      <xsl:call-template name="first-token">
        <xsl:with-param name="s" select="$txt"/>
      </xsl:call-template>
    </Data>
  </Cell>
</xsl:for-each>


<!-- 중요항목 열 (마지막-1) -->
<xsl:variable name="importantC" select="$r/cell[last()-1]"/>
<xsl:variable name="importantTxt" select="string($importantC)"/>

<Cell>
  <!-- 노란 줄이어도 YellowCell 적용 안 함 -->
  <xsl:if test="$importantC/@important='yes'">
    <xsl:attribute name="ss:StyleID">RedText</xsl:attribute>
  </xsl:if>

  <Data ss:Type="String">
    <xsl:call-template name="first-token">
      <xsl:with-param name="s" select="$importantTxt"/>
    </xsl:call-template>
  </Data>
</Cell>


<!-- 편집자 열 (마지막) -->
<xsl:variable name="editorC" select="$r/cell[last()]"/>
<xsl:variable name="editorTxt" select="string($editorC)"/>

<Cell>
  <Data ss:Type="String">
    <xsl:value-of select="$editorTxt"/>
  </Data>
</Cell>
              </Row>
            </xsl:if>
          </xsl:for-each>

        </Table>
      </Worksheet>
    </Workbook>
  </xsl:template>
</xsl:stylesheet>
