<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="3.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    exclude-result-prefixes="xs">

    <xsl:output method="xml" indent="no" omit-xml-declaration="no"/>

    <!-- 기본적으로 모든 노드와 속성을 원본 그대로 복사한다. -->
    <xsl:template match="@* | node()">
        <xsl:copy>
            <xsl:apply-templates select="@* | node()"/>
        </xsl:copy>
    </xsl:template>

    <!-- 실제로 문장 앞뒤 공백이 제거되는 요소에 리포트 집계용 토큰을 추가한다. -->
    <xsl:template match="*[local-name() = ('p', 'title', 'shortdesc', 'cmd')]">
        <!-- 인라인 요소 안쪽이 아니라 문장 요소의 직접적인 첫/마지막 내용 노드만 검사한다. -->
        <xsl:variable name="content-nodes" as="node()*"
            select="node()[self::* or self::text()[normalize-space() != '']]"/>
        <xsl:variable name="is-trimmed" as="xs:boolean"
            select="
                exists($content-nodes)
                and (
                    $content-nodes[1][self::text() and matches(., '^\s+')]
                    or $content-nodes[last()][self::text() and matches(., '\s+$')]
                )
            "/>
        <xsl:copy>
            <xsl:apply-templates select="@* except @modified"/>
            <xsl:choose>
                <xsl:when test="$is-trimmed">
                    <xsl:attribute name="modified"
                        select="normalize-space(string-join((@modified, 'sentence-space-trimmed'), ' '))"/>
                </xsl:when>
                <xsl:otherwise>
                    <xsl:apply-templates select="@modified"/>
                </xsl:otherwise>
            </xsl:choose>
            <xsl:apply-templates select="node()"/>
        </xsl:copy>
    </xsl:template>

    <!--
        p, title, shortdesc, cmd 문장의 첫 실제 텍스트 앞 공백과
        마지막 실제 텍스트 뒤 공백만 제거한다.
        인라인 태그 전후와 문장 내부의 필요한 공백은 그대로 유지한다.
    -->
    <xsl:template match="text()[parent::*[local-name() = ('p', 'title', 'shortdesc', 'cmd')]]">
        <xsl:variable name="container"
            select="parent::*"/>
        <xsl:variable name="content-nodes" as="node()*"
            select="$container/node()[self::* or self::text()[normalize-space() != '']]"/>
        <xsl:variable name="trimmed-start"
            select="if (exists($content-nodes) and (. is $content-nodes[1])) then replace(., '^\s+', '') else string(.)"/>
        <xsl:value-of
            select="if (exists($content-nodes) and (. is $content-nodes[last()])) then replace($trimmed-start, '\s+$', '') else $trimmed-start"/>
    </xsl:template>

</xsl:stylesheet>
