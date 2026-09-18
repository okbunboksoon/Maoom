<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="3.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

    <xsl:output method="xml" indent="no" omit-xml-declaration="no"/>
    <xsl:mode on-no-match="shallow-copy"/>

    <!--
        conbody, taskbody, refbody, section 바로 아래에 하위 태그로 감싸지 않은 실제 텍스트가 있으면 원문은 유지하고 modified에 검출 토큰을 추가
    -->
    <xsl:template match="
        *[local-name() = ('conbody', 'taskbody', 'refbody', 'section')]
         [text()[normalize-space(.) != '']]
    ">
        <xsl:copy>
            <xsl:apply-templates select="@* except @modified"/>
            <xsl:attribute name="modified"
                select="normalize-space(string-join((
                    tokenize(normalize-space(string(@modified)), '\s+'),
                    if (contains-token(@modified, 'body-direct-text'))
                    then ()
                    else 'body-direct-text'
                ), ' '))"/>
            <xsl:apply-templates select="node()"/>
        </xsl:copy>
    </xsl:template>

</xsl:stylesheet>
