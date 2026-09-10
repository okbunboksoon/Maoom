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

    <!-- p 같은 하위 태그로 감싸지 않고 li 바로 아래에 작성된 실제 텍스트를 리포트용으로 표시한다. -->
    <xsl:template match="li[text()[normalize-space() != '']]">
        <xsl:copy>
            <xsl:apply-templates select="@* except @modified"/>
            <xsl:attribute name="modified"
                select="normalize-space(string-join((@modified, 'li-direct-text'), ' '))"/>
            <xsl:apply-templates select="node()"/>
        </xsl:copy>
    </xsl:template>

    <!-- task의 step에 필수 cmd 자식 요소가 없으면 구조 오류로 표시한다. -->
    <xsl:template match="step[not(cmd)]">
        <xsl:copy>
            <xsl:apply-templates select="@* except @modified"/>
            <xsl:attribute name="modified"
                select="normalize-space(string-join((@modified, 'step-missing-cmd'), ' '))"/>
            <xsl:apply-templates select="node()"/>
        </xsl:copy>
    </xsl:template>

</xsl:stylesheet>
