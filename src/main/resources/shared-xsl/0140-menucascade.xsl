<xsl:stylesheet version="2.0" 
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    exclude-result-prefixes="xs">

    <xsl:import href="indentation1.xsl"/>

    <xsl:output method="xml" indent="no" omit-xml-declaration="yes" />
    <xsl:strip-space elements="*" />
    <xsl:preserve-space elements="p"/>

    <xsl:template match="map">
        <map>
            <xsl:apply-templates select="@* | node()"/>
            <xsl:text>&#x0A;</xsl:text>
        </map>
    </xsl:template>

    <!-- p 템플릿: 기존 로직 유지 + 결과를 변수에 담아 2차 정리 -->
    <xsl:template match="p">
        <xsl:variable name="depth" select="count(ancestor::*)"/>
        <xsl:if test="not(parent::p) and not(parent::title)">
            <xsl:call-template name="indentation">
                <xsl:with-param name="depth" select="$depth"/>
            </xsl:call-template>
        </xsl:if>

        <p>
            <!-- 1) 기존 menucascade 생성 로직을 통째로 $built에 담음 -->
            <xsl:variable name="built">
                <xsl:for-each-group select="node()" group-adjacent="boolean(self::uicontrol)">
                    <xsl:choose>
                        <xsl:when test="current-grouping-key()">
                            <xsl:choose>
                                <xsl:when test="count(current-group()) &gt; 1">
                                    <menucascade>
                                        <xsl:apply-templates select="current-group()" />
                                    </menucascade>
                                </xsl:when>
                                <xsl:otherwise>
                                    <xsl:apply-templates select="current-group()" />
                                </xsl:otherwise>
                            </xsl:choose>
                        </xsl:when>
                        <xsl:otherwise>
                            <xsl:apply-templates select="current-group()"/>
                        </xsl:otherwise>
                    </xsl:choose>
                </xsl:for-each-group>
            </xsl:variable>

            <!-- 2) 두 번째 패스: [[...]] 테두리 정리 -->
            <xsl:apply-templates select="$built/node()" mode="clean-edges"/>

            <xsl:if test="image[@placement='break'] or note or ul or ol or fig or figgroup">
                <xsl:call-template name="indentation">
                    <xsl:with-param name="depth" select="$depth"/>
                </xsl:call-template>
            </xsl:if>
        </p>
    </xsl:template>

    <!-- ========== [[ ]] 테두리 정리용 모드 ========== -->

    <!-- 기본 복사(정체성) 템플릿 -->
    <xsl:template match="@*|node()" mode="clean-edges">
        <xsl:copy>
            <xsl:apply-templates select="@*|node()" mode="clean-edges"/>
        </xsl:copy>
    </xsl:template>

    <!-- [양쪽 모두] menucascade에 인접한 텍스트: 앞쪽의 ']]' + 뒤쪽의 '[[' 모두 제거 -->
    <xsl:template
        match="text()[
                 preceding-sibling::*[1][self::menucascade]
                 and
                 following-sibling::*[1][self::menucascade]
              ]"
        mode="clean-edges"
        priority="3">
        <xsl:value-of select="replace(replace(., '^\s*\]\]\s*', ''), '\s*\[\[\s*$', '')"/>
    </xsl:template>

    <!-- [오른쪽만] 바로 뒤에 menucascade가 오는 텍스트: 끝부분의 '[[' 제거 -->
    <xsl:template
        match="text()[following-sibling::*[1][self::menucascade]]"
        mode="clean-edges"
        priority="2">
        <xsl:value-of select="replace(., '\s*\[\[\s*$', '')"/>
    </xsl:template>

    <!-- [왼쪽만] 바로 앞에 menucascade가 오는 텍스트: 시작부분의 ']]' 제거 -->
    <xsl:template
        match="text()[preceding-sibling::*[1][self::menucascade]]"
        mode="clean-edges"
        priority="2">
        <xsl:value-of select="replace(., '^\s*\]\]\s*', '')"/>
    </xsl:template>

</xsl:stylesheet>
