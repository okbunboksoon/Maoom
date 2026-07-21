<xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

	<xsl:variable name="bookmap" select="document('../xsl/bookmap.xml')/bookmap"/>

	<xsl:output method="xml" indent="no" omit-xml-declaration="yes"/>
	<xsl:strip-space elements="*" />
	<xsl:preserve-space elements="p" />

	<xsl:template match="@* | node()"> 
		<xsl:copy>
			<xsl:apply-templates select="@* | node()"/>
		</xsl:copy>
	</xsl:template>

	<xsl:template match="bookmap">
		<map cascade="merge">
			<xsl:apply-templates select="@*"/>
			<xsl:for-each select="$bookmap/chapter">
				<xsl:apply-templates select="document(concat('../chapter/', @filename))/chapter"/>
			</xsl:for-each>
		</map>
	</xsl:template>

	<xsl:template match="indexterm/text()">
		<xsl:value-of select="replace(replace(., '\s+', '&#x20;'), '\s$', '')"/>
	</xsl:template>

	<xsl:template match="text()" priority="20"> 
		<xsl:value-of select="replace(., '\s+', '&#x20;')"/>
	</xsl:template>

	<xsl:template match="topicref">
		<xsl:copy>
			<xsl:apply-templates select="@* | node()"/>
		</xsl:copy>
	</xsl:template>

</xsl:stylesheet>