<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="2.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:o="urn:schemas-microsoft-com:office:office"
    xmlns:x="urn:schemas-microsoft-com:office:excel"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xmlns:html="http://www.w3.org/TR/REC-html40"
    xmlns:ss="urn:schemas-microsoft-com:office:spreadsheet"
    xmlns="urn:schemas-microsoft-com:office:spreadsheet"
    xpath-default-namespace="urn:schemas-microsoft-com:office:spreadsheet"
    exclude-result-prefixes="xs o x html">

    <xsl:output method="xml" indent="yes"/>
    <xsl:strip-space elements="*"/>

    <!-- 헤더 -->
    <xsl:variable name="cells"
        select="/ss:Workbook/ss:Worksheet/ss:Table/ss:Row[1]/ss:Cell/ss:Data"/>

    <!-- AFTER -->
    <xsl:variable name="left-start" select="index-of($cells,'DESCRIPTION')[1] + 1"/>
    <xsl:variable name="left-end"   select="index-of($cells,'중요항목')[1] - 1"/>

    <!-- BEFORE -->
    <xsl:variable name="right-start" select="index-of($cells,'DESCRIPTION')[2] + 1"/>
    <xsl:variable name="right-end"   select="index-of($cells,'중요항목')[2] - 1"/>

    <!-- 신규 컬럼 인덱스 -->
    <xsl:variable name="new-columns" as="xs:integer*">
        <xsl:for-each select="$cells[position() ge $left-start and position() le $left-end]">
            <xsl:variable name="afterName" select="normalize-space(.)"/>
            <xsl:variable name="pos" select="position() + $left-start - 1"/>
            <xsl:if test="not($afterName = $cells[position() ge $right-start and position() le $right-end])">
                <xsl:sequence select="$pos"/>
            </xsl:if>
        </xsl:for-each>
    </xsl:variable>

    <!-- 기본 복사 -->
    <xsl:template match="@* | node()">
        <xsl:copy>
            <xsl:apply-templates select="@* | node()"/>
        </xsl:copy>
    </xsl:template>

    <!-- Row 처리 (Row 속성도 유지!) -->
  <xsl:template match="ss:Row">
    <ss:Row>
        <xsl:apply-templates select="@*"/>

        <!-- Table 기준 행 위치 계산 -->
        <xsl:variable name="rowPos"
            select="count(preceding-sibling::ss:Row) + 1"/>

        <xsl:variable name="rowLast"
            select="count(parent::ss:Table/ss:Row)"/>

        <xsl:variable name="rowType">
            <xsl:choose>
                <xsl:when test="$rowPos = 1">First</xsl:when>
                <xsl:when test="$rowPos = $rowLast">Last</xsl:when>
                <xsl:otherwise>Middle</xsl:otherwise>
            </xsl:choose>
        </xsl:variable>

        <xsl:for-each select="ss:Cell">
            <xsl:variable name="pos" select="position()"/>
            <xsl:variable name="origStyle" select="string(@ss:StyleID)"/>

            <xsl:choose>

                <xsl:when test="$pos = $new-columns">

                    <xsl:choose>

                        <xsl:when test="$origStyle='BgYellow'">
                            <ss:Cell ss:StyleID="{concat('BgYellow_',$rowType)}">
                                <xsl:apply-templates select="@* except @ss:StyleID"/>
                                <xsl:apply-templates select="node()"/>
                            </ss:Cell>
                        </xsl:when>

                        <xsl:when test="$origStyle='NewK'">
                            <ss:Cell ss:StyleID="{concat('NewK_',$rowType)}">
                                <xsl:apply-templates select="@* except @ss:StyleID"/>
                                <xsl:apply-templates select="node()"/>
                            </ss:Cell>
                        </xsl:when>

                        <xsl:when test="$origStyle='DiffK'">
                            <ss:Cell ss:StyleID="{concat('DiffK_',$rowType)}">
                                <xsl:apply-templates select="@* except @ss:StyleID"/>
                                <xsl:apply-templates select="node()"/>
                            </ss:Cell>
                        </xsl:when>
                        
                        <xsl:when test="$origStyle='Bordered'">
					    <ss:Cell ss:StyleID="{concat('Bordered_',$rowType)}">
					        <xsl:apply-templates select="@* except @ss:StyleID"/>
					        <xsl:apply-templates select="node()"/>
					    </ss:Cell>
					</xsl:when>
                        
                        <xsl:when test="not(@ss:StyleID)">
					    <ss:Cell ss:StyleID="{concat('Default_',$rowType)}">
					        <xsl:apply-templates select="@*"/>
					        <xsl:apply-templates select="node()"/>
					    </ss:Cell>
					</xsl:when>

                        <xsl:otherwise>
                            <xsl:copy-of select="."/>
                        </xsl:otherwise>

                    </xsl:choose>

                </xsl:when>

                <xsl:otherwise>
                    <xsl:copy-of select="."/>
                </xsl:otherwise>

            </xsl:choose>
        </xsl:for-each>

    </ss:Row>
</xsl:template>

</xsl:stylesheet>