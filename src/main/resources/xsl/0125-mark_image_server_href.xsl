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

    <!-- http 또는 https 서버 주소로 작성된 image href는 수정하지 않고 리포트용 토큰만 추가한다. -->
    <xsl:template match="image[matches(@href, '^https?://', 'i')]">
        <xsl:copy>
            <xsl:apply-templates select="@* except @modified"/>
            <xsl:attribute name="modified"
                select="normalize-space(string-join((@modified, 'image-server-href'), ' '))"/>
            <xsl:apply-templates select="node()"/>
        </xsl:copy>
    </xsl:template>

</xsl:stylesheet>
