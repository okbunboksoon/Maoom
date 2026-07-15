<xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

	<xsl:output method="xml" indent="no" omit-xml-declaration="yes"/>
	<xsl:strip-space elements="*" />
	<xsl:preserve-space elements="p" />

	<xsl:template match="@* | node()"> 
		<xsl:copy>
			<xsl:apply-templates select="@* | node()"/>
		</xsl:copy>
	</xsl:template>

	<xsl:template match="//processing-instruction()"> 
	</xsl:template>

	<xsl:template match="bookmap">
		<map cascade="merge" mapname="{replace(@mapname, '\.xml', '.ditamap')}">
			<xsl:apply-templates select="@* except @mapname"/>
			<xsl:for-each select="chapter">
				<chapter filename="{@filename}" xml:lang="{parent::bookmap/@xml:lang}">
					<xsl:apply-templates select="document(concat('../source/', @filename))"/>
				</chapter>
			</xsl:for-each>
		</map>
	</xsl:template>

	<xsl:template match="topicref[count(child::node()) = 0]">
	</xsl:template>

	<xsl:template match="topicref[name(*[1])='topicref']">
		<xsl:apply-templates/>
	</xsl:template>

	<xsl:template match="topicref/@outputclass">
	</xsl:template>

	<xsl:template match="frontmatter | draftintro | backmatter | chapter | appendix">
		<xsl:apply-templates/>
	</xsl:template>

	<xsl:template match="booklists | indexlist">
	</xsl:template>

	<xsl:template match="concept/title | task/title | reference/title">
		<xsl:copy>
			<xsl:apply-templates select="@* | node()"/>
		</xsl:copy>
		<titlealts/>
	</xsl:template>

	<xsl:template match="ol">
		<xsl:copy>
			<xsl:apply-templates select="@*"/>
			<xsl:if test="not(@outputclass) and following-sibling::ol[@outputclass='continue']">
				<xsl:attribute name="outputclass" select="'callout'"/>
			</xsl:if>
			<xsl:if test="not(@outputclass) and parent::*/following-sibling::*[1]/ol[@outputclass='continue']">
				<xsl:attribute name="outputclass" select="'callout'"/>
			</xsl:if>
			<xsl:apply-templates select="node()"/>
		</xsl:copy>
	</xsl:template>

	<xsl:template match="text()" priority="20"> 
		<xsl:value-of select="replace(., '\s+', '&#x20;')"/>
	</xsl:template>

</xsl:stylesheet>