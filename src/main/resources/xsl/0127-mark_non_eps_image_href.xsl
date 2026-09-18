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

    <!-- 값이 있는 image href가 .eps 확장자가 아니면 수정하지 않고 리포트용 토큰만 추가한다. -->
    <xsl:template match="image[@href][normalize-space(@href) != ''][not(matches(lower-case(normalize-space(@href)), '\.eps([?#].*)?$'))]">
        <xsl:copy>
            <xsl:apply-templates select="@* except @modified"/>
            <xsl:attribute name="modified"
                select="normalize-space(string-join((@modified, 'image-href-non-eps'), ' '))"/>
            <xsl:apply-templates select="node()"/>
        </xsl:copy>
    </xsl:template>

</xsl:stylesheet>
