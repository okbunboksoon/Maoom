<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="2.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

    <xsl:output method="xml" indent="no"/>
    <xsl:strip-space elements="*"/>
    <xsl:preserve-space elements="p"/>

    <xsl:variable name="ruIntro" select="document('RU_Intro.dita')/*[1]"/>
    <xsl:variable name="abbreviationAppendix" select="document('RU_Abbreviation_Appendix.xml')/*[1]"/>
    <xsl:variable name="introTitle" select="'&#x412;&#x432;&#x435;&#x434;&#x435;&#x43D;&#x438;&#x435;'"/>
    <xsl:variable name="compressorTitle" select="'&#x422;&#x430;&#x431;&#x43B;&#x438;&#x447;&#x43A;&#x430;&#x20;&#x43A;&#x43E;&#x43C;&#x43F;&#x440;&#x435;&#x441;&#x441;&#x43E;&#x440;&#x430;&#x20;&#x43A;&#x43E;&#x43D;&#x434;&#x438;&#x446;&#x438;&#x43E;&#x43D;&#x435;&#x440;&#x430;'"/>
    <xsl:variable name="compressorRefrigerantTitle" select="'&#x422;&#x430;&#x431;&#x43B;&#x438;&#x447;&#x43A;&#x430;&#x20;&#x43A;&#x43E;&#x43C;&#x43F;&#x440;&#x435;&#x441;&#x441;&#x43E;&#x440;&#x430;&#x20;&#x43A;&#x43E;&#x43D;&#x434;&#x438;&#x446;&#x438;&#x43E;&#x43D;&#x435;&#x440;&#x430;&#x2F;&#x442;&#x430;&#x431;&#x43B;&#x438;&#x447;&#x43A;&#x430;&#x20;&#x441;&#x20;&#x443;&#x43A;&#x430;&#x437;&#x430;&#x43D;&#x438;&#x435;&#x43C;&#x20;&#x445;&#x43B;&#x430;&#x434;&#x430;&#x433;&#x435;&#x43D;&#x442;&#x430;'"/>

    <xsl:template match="@* | node()">
        <xsl:copy>
            <xsl:apply-templates select="@* | node()"/>
        </xsl:copy>
    </xsl:template>

    <xsl:template match="chapter[matches(@filename, '^\d+_Intro\.xml$')]/topicref[1][normalize-space(*[1]/title) = $introTitle]">
        <xsl:variable name="parentTopicId"
            select="if (*[1]/@id) then string(*[1]/@id) else replace(@href, '\.dita$', '')"/>
        <xsl:variable name="parentHref" select="concat(@href, '#', $parentTopicId)"/>

        <xsl:copy>
            <xsl:apply-templates select="@* | node()"/>
            <xsl:if test="not(topicref[@href = 'RU_Intro.dita'])">
                <topicref type="concept" href="RU_Intro.dita">
                    <xsl:apply-templates select="$ruIntro" mode="ru-intro">
                        <xsl:with-param name="parentHref" select="$parentHref"/>
                    </xsl:apply-templates>
                </topicref>
            </xsl:if>
        </xsl:copy>
    </xsl:template>

    <xsl:template match="@* | node()" mode="ru-intro">
        <xsl:param name="parentHref"/>
        <xsl:copy>
            <xsl:apply-templates select="@* | node()" mode="ru-intro">
                <xsl:with-param name="parentHref" select="$parentHref"/>
            </xsl:apply-templates>
        </xsl:copy>
    </xsl:template>

    <xsl:template match="link[@role = 'parent']/@href" mode="ru-intro">
        <xsl:param name="parentHref"/>
        <xsl:attribute name="href" select="$parentHref"/>
    </xsl:template>
    <xsl:template match="chapter[matches(@filename, '^\d+_Abbreviation\.xml$') or topicref[1][normalize-space(*[1]/title) = '&#x421;&#x43E;&#x43A;&#x440;&#x430;&#x449;&#x435;&#x43D;&#x438;&#x435;']]/topicref[1]">
        <xsl:choose>
            <xsl:when test="matches(../@filename, '^\d+_Abbreviation\.xml$') or normalize-space(*[1]/title) = '&#x421;&#x43E;&#x43A;&#x440;&#x430;&#x449;&#x435;&#x43D;&#x438;&#x435;'">
                <xsl:variable name="compressorSpec"
                    select="(/*//topicref[*[1]/title[normalize-space(.) = $compressorTitle or normalize-space(.) = $compressorRefrigerantTitle]]/*[1]/conbody//*[self::fig or self::table])[1]"/>
                <xsl:apply-templates select="$abbreviationAppendix" mode="ru-abbreviation">
                    <xsl:with-param name="compressorSpec" select="$compressorSpec"/>
                </xsl:apply-templates>
            </xsl:when>
            <xsl:otherwise>
                <xsl:copy>
                    <xsl:apply-templates select="@* | node()"/>
                </xsl:copy>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>

    <xsl:template match="@* | node()" mode="ru-abbreviation">
        <xsl:param name="compressorSpec"/>
        <xsl:copy>
            <xsl:apply-templates select="@* | node()" mode="ru-abbreviation">
                <xsl:with-param name="compressorSpec" select="$compressorSpec"/>
            </xsl:apply-templates>
        </xsl:copy>
    </xsl:template>

    <xsl:template match="concept[@id = 'OM_14_5_Appendix_RU']/conbody/table[1]" mode="ru-abbreviation">
        <xsl:param name="compressorSpec"/>
        <xsl:choose>
            <xsl:when test="$compressorSpec">
                <xsl:copy-of select="$compressorSpec"/>
            </xsl:when>
            <xsl:otherwise>
                <xsl:copy>
                    <xsl:apply-templates select="@* | node()" mode="ru-abbreviation">
                        <xsl:with-param name="compressorSpec" select="$compressorSpec"/>
                    </xsl:apply-templates>
                </xsl:copy>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>

</xsl:stylesheet>
