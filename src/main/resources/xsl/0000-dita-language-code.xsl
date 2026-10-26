<xsl:stylesheet version="2.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:ss="urn:schemas-microsoft-com:office:spreadsheet"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xmlns:f="urn:maoomtool:dita-language-code"
    exclude-result-prefixes="ss xs f">

    <xsl:output method="xml"
        indent="no"
        omit-xml-declaration="no"
        encoding="utf-8"/>

    <!-- 261026_DITA전용: BAT에서 전달한 언어명과 원본 DITA 파일명 -->
    <xsl:param name="langName"/>
    <xsl:param name="srcFile"/>

    <xsl:variable name="langMap" select="document('lang_code_map.xml')"/>
    <xsl:variable name="langCode"
        select="normalize-space(string($langMap//ss:Row[ss:Cell[1]/ss:Data = $langName]/ss:Cell[2]/ss:Data))"/>
    <xsl:variable name="langFile" select="replace($langCode, '-', '_')"/>

    <!-- 261026_DITA전용: en_GB와 sr_Latn_RS 형태를 모두 최종 매핑 코드로 치환한다. -->
    <xsl:function name="f:replace-file-language" as="xs:string">
        <xsl:param name="value" as="xs:string"/>
        <xsl:variable name="underscoreReplaced"
            select="replace($value, '[a-z]{2}_([A-Za-z]{4}_)?[A-Z]{2}', $langFile)"/>
        <xsl:sequence
            select="replace($underscoreReplaced, '[a-z]{2}-([A-Za-z]{4}-)?[A-Z]{2}', $langFile)"/>
    </xsl:function>

    <xsl:function name="f:doctype-public" as="xs:string">
        <xsl:param name="rootName" as="xs:string"/>
        <xsl:sequence select="
            if ($rootName = 'concept') then '-//OASIS//DTD DITA 1.3 Concept//EN'
            else if ($rootName = 'task') then '-//OASIS//DTD DITA 1.3 Task//EN'
            else if ($rootName = 'reference') then '-//OASIS//DTD DITA 1.3 Reference//EN'
            else '-//OASIS//DTD DITA 1.3 Topic//EN'"/>
    </xsl:function>

    <xsl:template match="/">
        <xsl:if test="$langCode = ''">
            <xsl:message terminate="yes">261026_DITA_ONLY: Language code mapping not found: <xsl:value-of select="$langName"/></xsl:message>
        </xsl:if>

        <xsl:variable name="newFileName" select="f:replace-file-language($srcFile)"/>
        <xsl:variable name="rootName" select="local-name(/*)"/>

        <xsl:message>261026_DITA_ONLY: <xsl:value-of select="$srcFile"/> -&gt; <xsl:value-of select="$newFileName"/></xsl:message>
        <dummy/>

        <!-- 261026_DITA전용: 별도 출력 폴더에 기록하여 실패 시 원본 작업 사본을 복구할 수 있게 한다. -->
        <xsl:result-document href="{$newFileName}"
            method="xml"
            indent="no"
            omit-xml-declaration="no"
            encoding="utf-8"
            doctype-public="{f:doctype-public($rootName)}"
            doctype-system="{concat($rootName, '.dtd')}">
            <xsl:apply-templates select="node()"/>
        </xsl:result-document>
    </xsl:template>

    <!-- 261026_DITA전용: 루트 topic ID에 실제로 언어 코드가 있을 때만 바꾼다. -->
    <xsl:template match="/*/@id">
        <xsl:attribute name="id" select="f:replace-file-language(string(.))"/>
    </xsl:template>

    <!-- 261026_DITA전용: DITA 파일명과 # 뒤 topic ID를 포함한 참조를 함께 맞춘다. -->
    <xsl:template match="@href | @conref">
        <xsl:attribute name="{name()}" select="f:replace-file-language(string(.))"/>
    </xsl:template>

    <!-- 261026_DITA전용: 기존 xml:lang만 갱신하며 없는 속성은 새로 만들지 않는다. -->
    <xsl:template match="@xml:lang">
        <xsl:attribute name="xml:lang" select="$langCode"/>
    </xsl:template>

    <xsl:template match="@* | node()">
        <xsl:copy>
            <xsl:apply-templates select="@* | node()"/>
        </xsl:copy>
    </xsl:template>

</xsl:stylesheet>
