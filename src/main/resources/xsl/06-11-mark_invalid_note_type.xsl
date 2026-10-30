<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="3.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    exclude-result-prefixes="xs">

    <xsl:output method="xml" indent="no" omit-xml-declaration="no"/>
    <xsl:mode on-no-match="shallow-copy"/>

    <!--
        note 내용을 변경하지 않고 다음 두 항목을 modified에 표시한다.
        1. type 속성값이 notice인 note
        2. type 속성이 없거나 값이 비어 있는 note
    -->
    <xsl:template match="*[local-name() = 'note']">
        <xsl:variable name="old-tokens" as="xs:string*"
            select="tokenize(normalize-space(string(@modified)), '\s+')
                    [. != '' and . != ('note-type-notice', 'note-type-missing')]"/>
        <xsl:variable name="new-tokens" as="xs:string*"
            select="(
                if (lower-case(normalize-space(string(@type))) = 'notice')
                then 'note-type-notice' else (),
                if (not(@type) or normalize-space(string(@type)) = '')
                then 'note-type-missing' else ()
            )"/>

        <xsl:copy>
            <xsl:apply-templates select="@* except @modified"/>
            <xsl:if test="exists($old-tokens) or exists($new-tokens)">
                <xsl:attribute name="modified"
                    select="string-join(distinct-values(($old-tokens, $new-tokens)), ' ')"/>
            </xsl:if>
            <xsl:apply-templates select="node()"/>
        </xsl:copy>
    </xsl:template>

</xsl:stylesheet>
