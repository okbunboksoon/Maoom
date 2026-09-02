<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="3.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:ss="urn:schemas-microsoft-com:office:spreadsheet"
    xmlns="urn:schemas-microsoft-com:office:spreadsheet"
    exclude-result-prefixes="ss">

    <xsl:output method="xml" indent="yes" encoding="UTF-8"/>
    <xsl:param name="forbiddenReport" select="'../temp/Forbidden_Report.xml'"/>
    <xsl:param name="qcLintReport" select="'../temp/QC_LINT_Report.xml'"/>

    <xsl:mode on-no-match="shallow-copy"/>

    <xsl:template match="/*[local-name() = 'Workbook']/*[local-name() = 'Worksheet'][1]/*[local-name() = 'Table'][1]">
        <xsl:copy>
            <xsl:apply-templates select="@* | node()"/>
            <xsl:call-template name="qc-summary-row">
                <xsl:with-param name="label" select="'금칙어 QC'"/>
                <xsl:with-param name="count" select="if (doc-available($forbiddenReport)) then max((count(document($forbiddenReport)/*[local-name() = 'Workbook']/*[local-name() = 'Worksheet'][1]/*[local-name() = 'Table'][1]/*[local-name() = 'Row']) - 1, 0)) else 0"/>
                <xsl:with-param name="change" select="'금칙어 리포트 검출 건수'"/>
            </xsl:call-template>
            <xsl:call-template name="qc-summary-row">
                <xsl:with-param name="label" select="'문장 검사'"/>
                <xsl:with-param name="count" select="if (doc-available($qcLintReport)) then max((count(document($qcLintReport)/*[local-name() = 'Workbook']/*[local-name() = 'Worksheet'][1]/*[local-name() = 'Table'][1]/*[local-name() = 'Row']) - 1, 0)) else 0"/>
                <xsl:with-param name="change" select="'문장 검사 리포트 검출 건수'"/>
            </xsl:call-template>
        </xsl:copy>
    </xsl:template>

    <xsl:template match="/*[local-name() = 'Workbook']">
        <xsl:copy>
            <xsl:apply-templates select="@* | node()"/>
            <xsl:call-template name="append-report-sheet">
                <xsl:with-param name="path" select="$forbiddenReport"/>
                <xsl:with-param name="sheetName" select="'금칙어'"/>
            </xsl:call-template>
            <xsl:call-template name="append-report-sheet">
                <xsl:with-param name="path" select="$qcLintReport"/>
                <xsl:with-param name="sheetName" select="'문장 검사'"/>
            </xsl:call-template>
        </xsl:copy>
    </xsl:template>

    <xsl:template name="append-report-sheet">
        <xsl:param name="path"/>
        <xsl:param name="sheetName"/>
        <xsl:choose>
            <xsl:when test="doc-available($path)">
                <xsl:apply-templates select="document($path)/*[local-name() = 'Workbook']/*[local-name() = 'Worksheet'][1]" mode="report-sheet">
                    <xsl:with-param name="sheetName" select="$sheetName" tunnel="yes"/>
                </xsl:apply-templates>
            </xsl:when>
            <xsl:otherwise>
                <Worksheet ss:Name="{$sheetName}">
                    <Table>
                        <Row>
                            <Cell><Data ss:Type="String">리포트 파일을 찾을 수 없습니다.</Data></Cell>
                        </Row>
                    </Table>
                </Worksheet>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>

    <xsl:template match="*[local-name() = 'Worksheet']" mode="report-sheet">
        <xsl:param name="sheetName" tunnel="yes"/>
        <xsl:copy>
            <xsl:apply-templates select="@* except @ss:Name" mode="report-sheet"/>
            <xsl:attribute name="ss:Name" select="$sheetName"/>
            <xsl:apply-templates select="node()" mode="report-sheet"/>
        </xsl:copy>
    </xsl:template>

    <xsl:template match="*[local-name() = 'Table']" mode="report-sheet">
        <xsl:copy>
            <xsl:apply-templates
                select="@*[
                    not(namespace-uri() = 'urn:schemas-microsoft-com:office:spreadsheet'
                        and local-name() = ('ExpandedColumnCount', 'ExpandedRowCount'))
                    and not(namespace-uri() = 'urn:schemas-microsoft-com:office:excel'
                        and local-name() = ('FullColumns', 'FullRows'))
                ]"
                mode="report-sheet"/>
            <xsl:apply-templates select="node()" mode="report-sheet"/>
        </xsl:copy>
    </xsl:template>

    <xsl:template match="*[local-name() = 'Column']" mode="report-sheet">
        <xsl:param name="sheetName" tunnel="yes"/>
        <xsl:variable name="columnNo" select="count(preceding-sibling::*[local-name() = 'Column']) + 1"/>
        <Column>
            <xsl:apply-templates select="@* except @ss:Width" mode="report-sheet"/>
            <xsl:attribute name="ss:Width">
                <xsl:choose>
                    <xsl:when test="$sheetName = '금칙어' and $columnNo = 1">55</xsl:when>
                    <xsl:when test="$sheetName = '금칙어' and $columnNo = 2">130</xsl:when>
                    <xsl:when test="$sheetName = '금칙어' and $columnNo = 3">500</xsl:when>
                    <xsl:when test="$sheetName = '금칙어' and $columnNo = 4">90</xsl:when>
                    <xsl:when test="$sheetName = '금칙어' and $columnNo = 5">220</xsl:when>
                    <xsl:when test="$sheetName = '금칙어' and $columnNo = 6">500</xsl:when>
                    <xsl:when test="$sheetName = '금칙어' and $columnNo = 7">1000</xsl:when>
                    <xsl:when test="$sheetName = '문장 검사' and $columnNo = 1">55</xsl:when>
                    <xsl:when test="$sheetName = '문장 검사' and $columnNo = 2">130</xsl:when>
                    <xsl:when test="$sheetName = '문장 검사' and $columnNo = 3">500</xsl:when>
                    <xsl:when test="$sheetName = '문장 검사' and $columnNo = 4">500</xsl:when>
                    <xsl:when test="$sheetName = '문장 검사' and $columnNo = 5">1000</xsl:when>
                    <xsl:otherwise>120</xsl:otherwise>
                </xsl:choose>
            </xsl:attribute>
        </Column>
    </xsl:template>

    <xsl:template match="*[local-name() = 'Row']" mode="report-sheet">
        <Row>
            <xsl:apply-templates select="@* except @ss:Height" mode="report-sheet"/>
            <xsl:attribute name="ss:Height">30</xsl:attribute>
            <xsl:apply-templates select="node()" mode="report-sheet"/>
        </Row>
    </xsl:template>

    <xsl:template match="*[local-name() = 'Cell']" mode="report-sheet">
        <xsl:param name="sheetName" tunnel="yes"/>
        <xsl:variable name="rowNo" select="count(parent::*[local-name() = 'Row']/preceding-sibling::*[local-name() = 'Row']) + 1"/>
        <xsl:variable name="cellNo" select="count(preceding-sibling::*[local-name() = 'Cell']) + 1"/>
        <Cell>
            <xsl:apply-templates select="@* except @ss:StyleID" mode="report-sheet"/>
            <xsl:attribute name="ss:StyleID">
                <xsl:choose>
                    <xsl:when test="$rowNo = 1">Header</xsl:when>
                    <xsl:when test="$sheetName = '금칙어' and $cellNo = (1, 2, 4)">Center</xsl:when>
                    <xsl:when test="$sheetName = '문장 검사' and $cellNo = 1">Center</xsl:when>
                    <xsl:otherwise>Wrap</xsl:otherwise>
                </xsl:choose>
            </xsl:attribute>
            <xsl:apply-templates select="node()" mode="report-sheet"/>
        </Cell>
    </xsl:template>

	<xsl:template match="*[local-name() = 'WorksheetOptions']" mode="report-sheet">
	    <WorksheetOptions xmlns="urn:schemas-microsoft-com:office:excel">
	        <xsl:apply-templates select="@* | node()[not(local-name() = 'Zoom')]" mode="report-sheet"/>
	        <Zoom>85</Zoom>
	    </WorksheetOptions>
	</xsl:template>

    <xsl:template match="@ss:StyleID" mode="report-sheet"/>

    <xsl:template name="qc-summary-row">
        <xsl:param name="label"/>
        <xsl:param name="count"/>
        <xsl:param name="change"/>
        <Row ss:Height="30">
            <Cell ss:StyleID="Center"><Data ss:Type="String"><xsl:value-of select="$label"/></Data></Cell>
            <Cell ss:StyleID="Center"><Data ss:Type="Number"><xsl:value-of select="$count"/></Data></Cell>
            <Cell ss:StyleID="Wrap"><Data ss:Type="String"><xsl:value-of select="$change"/></Data></Cell>
        </Row>
    </xsl:template>

    <xsl:mode name="report-sheet" on-no-match="shallow-copy"/>

</xsl:stylesheet>
