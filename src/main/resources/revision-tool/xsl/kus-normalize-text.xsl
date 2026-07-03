<xsl:stylesheet version="2.0" 
	xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
	xmlns:xs="http://www.w3.org/2001/XMLSchema"
	exclude-result-prefixes="xs">

	<xsl:template match="@* | node()"> 
		<xsl:copy>
			<xsl:apply-templates select="@* | node()"/>
		</xsl:copy>
	</xsl:template>

	<xsl:template match="text()" priority="20"> 
		<xsl:value-of select="replace(replace(replace(replace(replace(replace(replace(replace(replace(replace(replace(normalize-space(.), '\s+([:.,?])', '$1'), '\s*\(\s+', ' ('), '\s+\)', ')'), '(\w)\(', '$1 ('), '\s+\(s\)', '(s)'), '(\w)&gt;', '$1 &gt;'), '(\d+)~(\d+)', '$1 ~ $2'), '(\d+) - (\d+)', '$1 ~ $2'), ' ~ ', '–'), '&#x22;(.*?)&#x22;', '“$1”'), '''s', '’s')"/>
	</xsl:template>

	<xsl:template match="draft-comment[matches(@props, 'not change on')]">
	</xsl:template>

	<xsl:template match="draft-comment[matches(@props, 'Remove empty titlealts globally')]">
	</xsl:template>

	<xsl:template match="draft-comment[matches(@otherprops, 'Question for Kia/Maoom')]">
	</xsl:template>

	<xsl:template match="draft-comment/@props">
		<xsl:attribute name="props">
			<xsl:value-of select="normalize-space(.)"/>
		</xsl:attribute>
	</xsl:template>

</xsl:stylesheet>