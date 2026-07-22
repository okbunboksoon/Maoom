<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="2.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:o="urn:schemas-microsoft-com:office:office"
    xmlns:x="urn:schemas-microsoft-com:office:excel"
	xmlns:xs="http://www.w3.org/2001/XMLSchema"
	xmlns:html="http://www.w3.org/TR/REC-html40"
	xmlns="urn:schemas-microsoft-com:office:spreadsheet"
    xmlns:ss="urn:schemas-microsoft-com:office:spreadsheet"
    xpath-default-namespace="urn:schemas-microsoft-com:office:spreadsheet"
    exclude-result-prefixes="xs o x ss html">

    <xsl:output method="xml" encoding="UTF-8" indent="yes"/>
    <xsl:strip-space elements="*"/>

	<xsl:variable name="MAX_COLS" select="xs:integer(/ss:Workbook/ss:Worksheet/ss:Table/@ss:ExpandedColumnCount) + 1" />

    <!-- 기본: identity transform -->
    <xsl:template match="@* | node()">
        <xsl:copy>
            <xsl:apply-templates select="@* | node()"/>
        </xsl:copy>
    </xsl:template>
	
    <xsl:template match="ss:Cell/@ss:Index | ss:Table/@ss:ExpandedColumnCount">
    </xsl:template>

    <!-- Row 처리 -->
    <xsl:template match="ss:Row">
        <xsl:element name="Row" namespace="urn:schemas-microsoft-com:office:spreadsheet">
            <xsl:variable name="processedCells">
                <xsl:for-each select="ss:Cell">
                    <xsl:variable name="pos" select="position()"/>
                    <xsl:variable name="index" select="@ss:Index"/>

                    <xsl:if test="$index">
                        <xsl:call-template name="emit-empty-cells">
                            <xsl:with-param name="count" select="if ( preceding-sibling::*[1][@ss:Index] ) then $index - preceding-sibling::*[1]/@ss:Index - 1 else $index - $pos"/>
                        </xsl:call-template>
                    </xsl:if>
				<!-- 아래 1~2개 셀 중 LeftText가 있는지 검사 -->
				<xsl:variable name="belowHasLeftText"
				    select="following-sibling::ss:Cell[position() = 1 or position() = 2]
				            [@ss:StyleID='LeftText' or @ss:StyleID='LeftTextBgYellow']"/>
				<xsl:choose>
				    <!-- ① 아래쪽 1~2칸 안에 LeftText 있으면: Bordered 적용  -->
				    <xsl:when test="$belowHasLeftText">
				        <Cell>
				            <xsl:apply-templates select="@* | node()"/>
				        </Cell>
				    </xsl:when>
				    <!-- ② 현재 셀에 이미 스타일 있으면 (LeftText 포함): 원래 스타일 유지 -->
				    <xsl:when test="@ss:StyleID">
				        <Cell ss:StyleID="{@ss:StyleID}">
				            <xsl:apply-templates select="@* | node()"/>
				        </Cell>
				    </xsl:when>
				    <!-- ③ 그 외: Bordered 적용 -->
				    <xsl:otherwise>
				        <Cell ss:StyleID="Bordered">
				            <xsl:apply-templates select="@* | node()"/>
				        </Cell>
				    </xsl:otherwise>
				</xsl:choose>
                </xsl:for-each>
            </xsl:variable>

            <!-- 지금까지 몇 개의 셀이 만들어졌는지 계산 -->
            <xsl:variable name="currentCount" select="count($processedCells/Cell)"/>

            <!-- 목표(MAX_COLS)까지 부족한 셀 수 -->
            <xsl:variable name="remaining" select="$MAX_COLS - $currentCount"/>

            <!-- 지금까지 만든 셀 출력 -->
            <xsl:copy-of select="$processedCells/Cell"/>

            <!-- 부족한 만큼 뒤에 빈 Cell 채우기 -->
            <xsl:if test="$remaining &gt; 0">
                <xsl:call-template name="emit-empty-cells">
                    <xsl:with-param name="count" select="$remaining"/>
                </xsl:call-template>
            </xsl:if>
        </xsl:element>
    </xsl:template>

    <xsl:template name="emit-empty-cells">
        <xsl:param name="count"/>
        <xsl:if test="$count &gt; 0">
            <Cell>
                <ss:Data ss:Type="String"/>
            </Cell>
            <xsl:call-template name="emit-empty-cells">
                <xsl:with-param name="count" select="$count - 1"/>
            </xsl:call-template>
        </xsl:if>
    </xsl:template>

</xsl:stylesheet>
