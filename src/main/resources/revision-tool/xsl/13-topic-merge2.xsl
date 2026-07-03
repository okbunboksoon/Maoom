<xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

	<xsl:output method="xml" indent="yes" omit-xml-declaration="no"/>
	<xsl:strip-space elements="*" />
	<xsl:preserve-space elements="p"/>

	<xsl:template match="@* | node()">
		<xsl:copy>
			<xsl:apply-templates select="@* | node()"/>
		</xsl:copy>
	</xsl:template>

	<xsl:template match="map">
		<xsl:copy>
			<xsl:apply-templates select="@* except @mapname"/>
			<xsl:apply-templates select="node()"/>
		</xsl:copy>
	</xsl:template>

	<xsl:template match="topicref">
		<xsl:copy>
			<xsl:apply-templates select="@*"/>
			<xsl:apply-templates select="document(concat('../topics/', @href))"/>
			<xsl:apply-templates/>
		</xsl:copy>
	</xsl:template>

	<xsl:template match="related-links">
		<xsl:copy>
			<xsl:apply-templates select="@* | node()"/>
		</xsl:copy>
	</xsl:template>

	<xsl:template match="indexterm/text()">
		<xsl:value-of select="replace(replace(., '\s+', '&#x20;'), '\s$', '')"/>
	</xsl:template>

	<xsl:template match="text()" priority="20"> 
		<xsl:value-of select="replace(., '\s+', '&#x20;')"/>
	</xsl:template>

	<xsl:template match="processing-instruction('path2rootmap-uri')"> 
	</xsl:template>

</xsl:stylesheet>