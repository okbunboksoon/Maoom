<xsl:stylesheet version="2.0" 
	xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
	xmlns:xs="http://www.w3.org/2001/XMLSchema"
	exclude-result-prefixes="xs">

	<xsl:import href="indentation1.xsl"/>

	<xsl:output method="xml" indent="no" omit-xml-declaration="no"/>
	<xsl:strip-space elements="*" />
	<xsl:preserve-space elements="p"/>

	<xsl:template match="map">
		<dummy/>
		<xsl:for-each select="topicref">
			<xsl:result-document href="{concat('../chapter/', @filename)}">
				<xsl:apply-templates select="."/>
			</xsl:result-document>
		</xsl:for-each>
	</xsl:template>

	<xsl:template match="map/topicref">
		<xsl:text>&#x0A;</xsl:text>
		<chapter filename="{@filename}" chapnum="{@chapnum}">
			<xsl:text>&#x0A;&#x09;</xsl:text>
			<topicref>
				<xsl:apply-templates select="@* except (@filename, @chapnum)"/>
				<xsl:apply-templates select="node()"/>
				<xsl:text>&#x0A;&#x09;</xsl:text>
			</topicref>
			<xsl:text>&#x0A;</xsl:text>
		</chapter>
	</xsl:template>

</xsl:stylesheet>