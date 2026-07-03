<xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

	<xsl:import href="kus-normalize-text.xsl"/>
	<xsl:variable name="bookmap" select="document('bookmap.xml')/bookmap"/>
	
	<xsl:output method="xml" indent="no" omit-xml-declaration="yes"/>
	<xsl:strip-space elements="*" />
	<xsl:preserve-space elements="p"/>

	<xsl:template match="map">
		<xsl:copy>
			<xsl:apply-templates select="@* | node()"/>
		</xsl:copy>
	</xsl:template>

	<xsl:template match="map/topicref">
		<xsl:variable name="chapnum" select="count(preceding-sibling::topicref) + 1"/>
		<topicref>
			<xsl:apply-templates select="@*"/>
			<xsl:attribute name="filename" select="$bookmap/chapter[$chapnum]/@filename"/>
			<xsl:attribute name="chapnum" select="format-number($chapnum, '00')"/>
			<xsl:apply-templates select="node()"/>
		</topicref>
	</xsl:template>

</xsl:stylesheet>