<xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

	<xsl:output method="xml" indent="no" omit-xml-declaration="yes"/>
	<xsl:strip-space elements="*" />
	<xsl:preserve-space elements="p" />

	<xsl:template match="@* | node()">
		<xsl:copy>
			<xsl:apply-templates select="@* | node()"/>
		</xsl:copy>
	</xsl:template>

	<xsl:template match="old[.=''][following-sibling::node()[1][name()='new'][.='']]">
	</xsl:template>

	<xsl:template match="new[.=''][preceding-sibling::node()[1][name()='old'][.='']]">
	</xsl:template>

	<xsl:template match="draft-comment[matches(@props, 'Grammar')][preceding-sibling::*[1][name()='ph'][@outputclass='new']]" priority="30">
		<old><xsl:apply-templates/></old>
	</xsl:template>

	<xsl:template match="ph[@outputclass='new'][following-sibling::*[1][name()='draft-comment'][matches(@props, 'Grammar')]]" priority="30">
		<new><xsl:apply-templates/></new>
	</xsl:template>

	<xsl:template match="draft-comment[matches(@otherprops, 'Improving expression')][preceding-sibling::*[name()='p'][@outputclass='new']]" priority="30">
		<old><xsl:apply-templates/></old>
	</xsl:template>

	<xsl:template match="p[@outputclass='new'][following-sibling::*[name()='draft-comment'][matches(@otherprops, 'Improving expression')]]" priority="30">
		<new><xsl:apply-templates/></new>
	</xsl:template>

	<xsl:template match="draft-comment[matches(@otherprops, 'Image design')]" priority="30">
	</xsl:template>

	<xsl:template match="draft-comment[matches(@props, 'Merging with previous paragraph')]" priority="60">
	</xsl:template>

	<xsl:template match="*[@outputclass='new']">
		<new><xsl:apply-templates/></new>
	</xsl:template>

</xsl:stylesheet>