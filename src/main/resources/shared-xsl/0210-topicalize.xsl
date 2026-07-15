<xsl:stylesheet version="2.0" 
	xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
	xmlns:xs="http://www.w3.org/2001/XMLSchema"
	exclude-result-prefixes="xs">

	<xsl:output method="xml" indent="no" omit-xml-declaration="no"/>
	<xsl:strip-space elements="*" />
	<xsl:preserve-space elements="p"/>

	<xsl:template match="@* | node()"> 
		<xsl:copy>
			<xsl:apply-templates select="@* | node()"/>
		</xsl:copy>
	</xsl:template>

	<xsl:template match="map">
		<dummy/>
		<xsl:apply-templates select=".//topicref"/>
	</xsl:template>

	<xsl:template match="topicref">
		<xsl:choose>
			<xsl:when test="@href = parent::topicref/@href">
			</xsl:when>
			<xsl:otherwise>
				<xsl:result-document href="{concat('../topics/', @href)}">
					<xsl:apply-templates select="*[name()!='topicref']"/>
				</xsl:result-document>
			</xsl:otherwise>
		</xsl:choose>
	</xsl:template>

    <xsl:template match="indexterm//text()" priority="20">
		<xsl:value-of select="normalize-space(.)"/>
    </xsl:template>

    <xsl:template match="titlealts">
    </xsl:template>

</xsl:stylesheet>