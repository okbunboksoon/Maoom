<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="2.0" 
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xmlns:String="java:java.lang.String"
    xmlns:Integer="java:java.lang.Integer"
    xmlns:md="java:java.security.MessageDigest"
    exclude-result-prefixes="xs String Integer md">

    <xsl:output method="xml" indent="no" omit-xml-declaration="yes"/>
    <xsl:strip-space elements="*"/>

    <xsl:param name="flag" select="'off'"/>
    <xsl:variable name="db" select="document('note_db.xml')"/>

    <!-- 기본 템플릿 -->
    <xsl:template match="@* | node()">
        <xsl:copy>
            <xsl:apply-templates select="@* | node()"/>
        </xsl:copy>
    </xsl:template>

    <!-- NOTE 처리 템플릿 -->
    <xsl:template match="note">
        <xsl:variable name="current" select="."/>
        <xsl:variable name="hash">
            <xsl:call-template name="hash">
                <xsl:with-param name="notes" select="descendant-or-self::text()"/>
            </xsl:call-template>
        </xsl:variable>

        <xsl:variable name="dbNote" select="$db/notes/note[@hash = $hash]"/>
        <xsl:variable name="originalType" select="@type"/>
        <xsl:variable name="dbType" select="$dbNote/@type"/>

        <xsl:copy>
            <xsl:apply-templates select="@*[name() != 'type']"/>

            <xsl:choose>
                <xsl:when test="string-length(normalize-space($dbType)) > 0 and normalize-space($originalType) != normalize-space($dbType)">
                    <xsl:attribute name="type">
                        <xsl:value-of select="normalize-space($dbType)"/>
                    </xsl:attribute>
                    <xsl:if test="$flag = 'on'">
                        <xsl:attribute name="status" select="'changed'"/>
                    </xsl:if>
                </xsl:when>
                <xsl:otherwise>
                    <xsl:copy-of select="@type"/>
                </xsl:otherwise>
            </xsl:choose>

            <xsl:attribute name="hash">
                <xsl:value-of select="$hash"/>
            </xsl:attribute>

            <xsl:apply-templates select="node()"/>
        </xsl:copy>
    </xsl:template>

    <!-- 해시 생성 템플릿 -->
    <xsl:template name="hash">
        <xsl:param name="notes"/>
        <xsl:variable name="Str">
            <xsl:call-template name="seed">
                <xsl:with-param name="nodes" select="$notes"/>
            </xsl:call-template>
        </xsl:variable>
        <xsl:variable name="Inst" select="md:getInstance('SHA-256')"/>
        <xsl:variable name="IntSeq" select="md:digest($Inst, String:getBytes($Str, 'utf-8'))"/>
        <xsl:variable name="HexStr">
            <xsl:for-each select="$IntSeq">
                <xsl:variable name="hexHash" select="upper-case(Integer:toHexString(.))"/>
                <xsl:value-of select="if (. &lt; 256) then substring(concat('0', $hexHash), string-length($hexHash)) else $hexHash"/>
            </xsl:for-each>
        </xsl:variable>
        <xsl:value-of select="$HexStr"/>
    </xsl:template>

    <!-- 해시용 seed 구성 템플릿 -->
	<xsl:template name="seed">
		<xsl:param name="nodes"/>
		<xsl:for-each select="$nodes">
			<xsl:choose>
				<!-- 텍스트: en dash 변환 포함 -->
				<xsl:when test="self::text()">
					<xsl:value-of select="replace(., '(\d+\s*)–(\s*\d+)', '$1 ~ $2')"/>
				</xsl:when>
				<!-- 인라인 태그들: 태그명 + 자식 재귀 -->
				<xsl:when test="self::uicontrol or self::tm or self::term or self::xref or self::image">
					<xsl:value-of select="name()"/>
					<xsl:call-template name="seed">
						<xsl:with-param name="nodes" select="node()"/>
					</xsl:call-template>
				</xsl:when>
				<!-- 기타 태그는 무시 -->
				<xsl:otherwise/>
			</xsl:choose>
		</xsl:for-each>
	</xsl:template>
</xsl:stylesheet>
