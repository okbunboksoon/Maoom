<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="2.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

    <xsl:output method="xml" indent="no"/>
    <xsl:strip-space elements="*"/>
    <xsl:preserve-space elements="p"/>

    <xsl:variable name="consumerInfoTitle" select="'Informations pour le consommateur'"/>

    <xsl:template match="@* | node()">
        <xsl:copy>
            <xsl:apply-templates select="@* | node()"/>
        </xsl:copy>
    </xsl:template>

    <xsl:template match="p[contains(., '819-420-4300') and ancestor::topicref[concept/title[normalize-space(.) = $consumerInfoTitle]]]">
        <xsl:copy>
            <xsl:apply-templates select="@*"/>
            <xsl:text>T&#xE9;l&#xE9;phone&#xA0;: T&#xE9;l&#xE9;phone&#xA0;: 819-420-4300 (r&#xE9;gion d&#x2019;Ottawa-Gatineau ou international)</xsl:text>
        </xsl:copy>
    </xsl:template>

    <xsl:template match="p[normalize-space(.) = 'http://www.tc.gc.ca/recalls' and ancestor::topicref[concept/title[normalize-space(.) = $consumerInfoTitle]]]">
        <xsl:copy>
            <xsl:apply-templates select="@*"/>
            <xsl:text>http://www.tc.gc.ca/rappels</xsl:text>
        </xsl:copy>
    </xsl:template>
</xsl:stylesheet>
