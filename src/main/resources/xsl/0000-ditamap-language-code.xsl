<xsl:stylesheet version="2.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:ss="urn:schemas-microsoft-com:office:spreadsheet"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xmlns:f="urn:maoomtool:ditamap-language-code"
    exclude-result-prefixes="ss xs f">

    <xsl:output method="xml"
        indent="no"
        omit-xml-declaration="no"
        encoding="utf-8"/>

    <xsl:param name="langName"/>
    <xsl:param name="srcFile"/>

    <xsl:variable name="langMap" select="document('lang_code_map.xml')"/>
    <xsl:variable name="langCode"
        select="normalize-space(string($langMap//ss:Row[ss:Cell[1]/ss:Data = $langName]/ss:Cell[2]/ss:Data))"/>
    <xsl:variable name="langFile" select="replace($langCode, '-', '_')"/>

    <xsl:function name="f:replace-language" as="xs:string">
        <xsl:param name="value" as="xs:string"/>
        <xsl:variable name="underscoreReplaced"
            select="replace($value, '[a-z]{2}_([A-Za-z]{4}_)?[A-Z]{2}', $langFile)"/>
        <xsl:sequence
            select="replace($underscoreReplaced, '[a-z]{2}(-[A-Za-z]{4})?-[A-Z]{2}', $langCode)"/>
    </xsl:function>

    <xsl:template match="/">
        <xsl:if test="$langCode = ''">
            <xsl:message terminate="yes">DITAMAP_LANGUAGE_CODE: Language code mapping not found: <xsl:value-of select="$langName"/></xsl:message>
        </xsl:if>

        <xsl:variable name="newFileName" select="f:replace-language($srcFile)"/>
        <xsl:message>DITAMAP_LANGUAGE_CODE: <xsl:value-of select="$srcFile"/> -&gt; <xsl:value-of select="$newFileName"/></xsl:message>
        <dummy/>

        <xsl:result-document href="{$newFileName}"
            method="xml"
            indent="no"
            omit-xml-declaration="no"
            encoding="utf-8"
            doctype-public="-//OASIS//DTD DITA 1.3 Map//EN"
            doctype-system="technicalContent/dtd/map.dtd">
            <xsl:apply-templates select="node()"/>
        </xsl:result-document>
    </xsl:template>

    <!-- 파일 참조, ID 등 모든 속성에 포함된 기존 언어 코드를 대상 코드로 맞춘다. -->
    <xsl:template match="@*">
        <xsl:attribute name="{name()}" namespace="{namespace-uri()}"
            select="f:replace-language(string(.))"/>
    </xsl:template>

    <!-- 기존 xml:lang은 파일명용 밑줄이 아닌 표준 하이픈 코드를 사용한다. -->
    <xsl:template match="@xml:lang" priority="2">
        <xsl:attribute name="xml:lang" select="$langCode"/>
    </xsl:template>

    <xsl:template match="map/title/text() | data[@name='lang']/text()">
        <xsl:value-of select="f:replace-language(string(.))"/>
    </xsl:template>

    <xsl:template match="node()">
        <xsl:copy>
            <xsl:apply-templates select="@* | node()"/>
        </xsl:copy>
    </xsl:template>

</xsl:stylesheet>
