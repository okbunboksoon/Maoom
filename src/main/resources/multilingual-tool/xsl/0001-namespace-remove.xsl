<xsl:stylesheet version="2.0" 
	xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
	xmlns:ditaarch="http://dita.oasis-open.org/architecture/2005/"
	xpath-default-namespace="http://dita.oasis-open.org/architecture/2005/"
	exclude-result-prefixes="ditaarch">

	<xsl:output method="xml" indent="no" omit-xml-declaration="no"/>
	<xsl:strip-space elements="*" />
	<xsl:preserve-space elements="p"/>

	<xsl:template match="*">
		<xsl:element name="{local-name()}">
			<xsl:apply-templates select="@* | node()"/>
		</xsl:element>
	</xsl:template>

	<xsl:template match="@*">
		<xsl:attribute name="{local-name()}">
			<xsl:value-of select="."/>
		</xsl:attribute>
	</xsl:template>

	<xsl:template match="@xml:lang">
		<xsl:copy/>
	</xsl:template>

	<xsl:template match="comment() | text() | processing-instruction()">
		<xsl:copy/>
	</xsl:template>

	<xsl:template match="processing-instruction('path2rootmap-uri')">
	</xsl:template>

	<xsl:template match="processing-instruction('mapname')">
	</xsl:template>

</xsl:stylesheet>