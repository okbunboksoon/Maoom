<xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

	<xsl:output method="xml" indent="no" omit-xml-declaration="yes" />
	<xsl:strip-space elements="*" />

	<xsl:template match="@* | @node()">
		<xsl:copy>
			<xsl:apply-templates select="@* | node()"/>
		</xsl:copy>
	</xsl:template>

	<xsl:template match="fileset">
		<fileset date="{@date}">
			<xsl:apply-templates select="file[schematron-output/failed-assert] | file[schematron-output/successful-report]"/>
			<xsl:text>&#xA;</xsl:text>
		</fileset>
	</xsl:template>

	<xsl:template match="file">
		<xsl:text>&#xA;&#x9;</xsl:text>
		<file filename="{@name}">
			<xsl:apply-templates select="schematron-output/failed-assert | schematron-output/successful-report"/>
			<xsl:text>&#xA;&#x9;</xsl:text>
		</file>
	</xsl:template>

	<xsl:template match="failed-assert">
		<xsl:text>&#xA;&#x9;&#x9;</xsl:text>
		<detect location="{@location}">
			<xsl:apply-templates select="text"/>
		</detect>
	</xsl:template>

	<xsl:template match="successful-report">
		<xsl:text>&#xA;&#x9;&#x9;</xsl:text>
		<detect location="{@location}">
			<xsl:apply-templates select="text"/>
		</detect>
	</xsl:template>

</xsl:stylesheet>