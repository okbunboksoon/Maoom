<xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

	<xsl:import href="indentation1.xsl"/>
	
	<xsl:output method="xml" indent="no" omit-xml-declaration="yes"/>
	<xsl:strip-space elements="*" />
	<xsl:preserve-space elements="p"/>

	<xsl:template match="map">
		<xsl:copy>
			<xsl:apply-templates select="@* | node()"/>
			<xsl:text>&#x0A;</xsl:text>
		</xsl:copy>
	</xsl:template>

</xsl:stylesheet>