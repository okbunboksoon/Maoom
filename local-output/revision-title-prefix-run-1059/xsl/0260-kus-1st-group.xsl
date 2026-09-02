<xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

	<xsl:output method="xml" indent="no" omit-xml-declaration="yes"/>
	<xsl:strip-space elements="*" />

	<xsl:template match="@* | node()">
		<xsl:copy>
			<xsl:apply-templates select="@* | node()"/>
		</xsl:copy>
	</xsl:template>

	<!-- pairing -->
	<xsl:template match="draft-comment[following-sibling::*[1][@outputclass='new']]">
		<old><xsl:apply-templates/></old>
	</xsl:template>

	<xsl:template match="*[@outputclass='new'][preceding-sibling::*[1][name()='draft-comment']]">
		<new><xsl:apply-templates/></new>
	</xsl:template>

	<xsl:template match="draft-comment[following-sibling::*[1][not(@outputclass='new')]]">
	</xsl:template>

	<xsl:template match="p[parent::draft-comment]">
		<xsl:apply-templates/>
	</xsl:template>

	<!-- deleting-->
	<xsl:template match="draft-comment[matches(@otherprops, 'Question Kia R/D Review')][following-sibling::*[1][@outputclass='new'][.='']]" priority="40">
	</xsl:template>

	<xsl:template match="draft-comment[matches(@otherprops, 'Question KUS Legal Review')][following-sibling::*[1][@outputclass='new'][.='']]" priority="40">
	</xsl:template>

	<xsl:template match="draft-comment[matches(@otherprops, 'Improving expression')][following-sibling::*[1][@outputclass='new'][.='']]" priority="40">
	</xsl:template>

	<xsl:template match="draft-comment[matches(@otherprops, 'Topic Structure')][following-sibling::*[1][@outputclass='new']]" priority="31">
	</xsl:template>

	<xsl:template match="draft-comment[matches(@otherprops, 'Improving expression')][.=''][following-sibling::*[1][@outputclass='new']]" priority="30">
	</xsl:template>

	<xsl:template match="ph[.=''][@outputclass='new'][preceding-sibling::*[1][name()='draft-comment'][.='']]" priority="30">
	</xsl:template>

	<xsl:template match="draft-comment[matches(@otherprops, 'Question for Kia/Maoom')]" priority="30">
	</xsl:template>

	<xsl:template match="draft-comment[matches(@otherprops, 'Topic structure')]" priority="30">
	</xsl:template>

	<xsl:template match="draft-comment[matches(@otherprops, 'Element [cC]hoice')]" priority="30">
	</xsl:template>

	<xsl:template match="draft-comment[matches(@props, 'not change on')]" priority="20">
	</xsl:template>

	<xsl:template match="draft-comment[matches(@props, 'Remove empty titlealts globally')]" priority="20">
	</xsl:template>

	<xsl:template match="comment()" priority="20">
	</xsl:template>

</xsl:stylesheet>