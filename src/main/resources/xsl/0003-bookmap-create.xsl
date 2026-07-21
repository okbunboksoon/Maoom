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
		<xsl:text>&#x0A;</xsl:text>
		<bookmap mapname="{@mapname}">
			<xsl:apply-templates select="@xml:lang"/>
			<xsl:apply-templates select="topicref"/>
			<xsl:text>&#x0A;</xsl:text>
		</bookmap>
	</xsl:template>

	<xsl:template match="map/topicref">
		<xsl:text>&#x0A;&#x09;</xsl:text>
		<chapter filename="{@filename}"/>
	</xsl:template>

</xsl:stylesheet>