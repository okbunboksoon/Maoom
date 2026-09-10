<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="3.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

    <xsl:output method="xml" indent="no" omit-xml-declaration="no"/>

    <!-- 기본적으로 모든 노드와 속성을 원본 그대로 복사한다. -->
    <xsl:template match="@* | node()">
        <xsl:copy>
            <xsl:apply-templates select="@* | node()"/>
        </xsl:copy>
    </xsl:template>

    <!-- placement=break이고 align이 left 또는 right인 image를 가운데 정렬하고 리포트용 토큰을 추가한다. -->
    <xsl:template match="image[@placement = 'break' and @align = ('left', 'right')]">
        <xsl:copy>
            <xsl:apply-templates select="@* except (@align, @modified)"/>
            <xsl:attribute name="align">center</xsl:attribute>
            <xsl:attribute name="modified"
                select="normalize-space(string-join((@modified, 'image-break-align-centered'), ' '))"/>
            <xsl:apply-templates select="node()"/>
        </xsl:copy>
    </xsl:template>

</xsl:stylesheet>
