<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="3.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

    <xsl:output method="xml" indent="no" omit-xml-declaration="no"/>
    <xsl:mode on-no-match="shallow-copy"/>

    <!--
        menucascade 구조를 수정하지 않고 다음 두 오류를 modified에 표시한다.
        1. uicontrol만 존재하며 모든 uicontrol의 내용이 비어 있는 경우
        2. menucascade 바로 아래에 uicontrol로 감싸지 않은 텍스트가 있는 경우
    -->
    <xsl:template match="*[local-name() = 'menucascade']">
        <xsl:variable name="controls" select="*[local-name() = 'uicontrol']"/>
        <xsl:variable name="has-direct-text"
            select="exists(text()[normalize-space(.) != ''])"/>
        <xsl:variable name="has-empty-uicontrol-only"
            select="exists($controls)
                    and (every $control in $controls satisfies normalize-space(string($control)) = '')
                    and not($has-direct-text)"/>
        <xsl:variable name="new-tokens" select="(
            if ($has-empty-uicontrol-only) then 'menucascade-empty-uicontrol' else (),
            if ($has-direct-text) then 'menucascade-direct-text' else ()
        )"/>

        <xsl:copy>
            <xsl:apply-templates select="@* except @modified"/>
            <xsl:if test="@modified or exists($new-tokens)">
                <xsl:attribute name="modified"
                    select="normalize-space(string-join((
                        tokenize(normalize-space(string(@modified)), '\s+'),
                        $new-tokens
                    ), ' '))"/>
            </xsl:if>
            <xsl:apply-templates select="node()"/>
        </xsl:copy>
    </xsl:template>

</xsl:stylesheet>
