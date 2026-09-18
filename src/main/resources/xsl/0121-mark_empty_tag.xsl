<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="3.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

    <xsl:output method="xml" indent="no" omit-xml-declaration="no"/>
    <xsl:strip-space elements="*"/>
    <xsl:preserve-space elements="p"/>

    <!-- 기본적으로 모든 노드와 속성을 원본 그대로 복사한다. -->
    <xsl:template match="@* | node()">
        <xsl:copy>
            <xsl:apply-templates select="@* | node()"/>
        </xsl:copy>
    </xsl:template>

    <!-- 속성이 없고 요소·실제 텍스트 내용도 없는 모든 빈 태그를 리포트용으로 표시한다. -->
    <xsl:template match="*[local-name() != 'entry' and not(@*) and not(*) and not(text()[normalize-space() != ''])]">
        <xsl:copy>
            <xsl:attribute name="modified"
                select="normalize-space(string-join((@modified, 'empty'), ' '))"/>
            <xsl:apply-templates select="node()"/>
        </xsl:copy>
    </xsl:template>

</xsl:stylesheet>
