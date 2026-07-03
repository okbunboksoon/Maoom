<xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

	<xsl:output method="xml" indent="no" omit-xml-declaration="yes"/>
	<xsl:strip-space elements="*" />

	<xsl:template match="@* | node()">
		<xsl:copy>
			<xsl:apply-templates select="@* | node()"/>
		</xsl:copy>
	</xsl:template>

	<xsl:template match="b">
		<xsl:if test="preceding-sibling::node()">
			<xsl:text>&#x20;</xsl:text>
		</xsl:if>
		<xsl:copy>
			<xsl:apply-templates select="@* | node()"/>
		</xsl:copy>
	</xsl:template>

	<xsl:template match="text()" priority="20">
		<xsl:if test="not(matches(., '^[:.,?)]')) and preceding-sibling::node()">
			<xsl:if test="preceding-sibling::node()[1][name()!='indexterm']">
				<xsl:text>&#x20;</xsl:text>
			</xsl:if>
		</xsl:if>
		<xsl:copy/>
		<xsl:if test="not(matches(., '[(]$')) and following-sibling::node()[1][name()!='indexterm']">
			<xsl:text>&#x20;</xsl:text>
		</xsl:if>
	</xsl:template>

	<xsl:template match="comment()" priority="20">
	</xsl:template>

</xsl:stylesheet>