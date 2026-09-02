<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="2.0">

	<xsl:output method="xml" indent="no" omit-xml-declaration="yes"/>
	<xsl:strip-space elements="*" />

	<xsl:template match="pairs">
		<pairs>
			<xsl:copy-of select="@*"/>
			<xsl:for-each-group select="pair" group-by="@hash">
				<xsl:variable name="pair">
					<pair>
						<xsl:attribute name="hash" select="current-grouping-key()"/>
						<xsl:apply-templates select="current-group()[1]/old"/>
						<xsl:apply-templates select="current-group()/new"/>
					</pair>
				</xsl:variable>
				<xsl:apply-templates select="$pair"/>
			</xsl:for-each-group>
			<xsl:text>&#xA;</xsl:text>
		</pairs>
	</xsl:template>

	<xsl:template match="pair">
		<xsl:text>&#xA;&#x9;</xsl:text>
		<xsl:copy>
			<xsl:apply-templates select="@*"/>
			<xsl:if test="deep-equal(old/node(), new/node())">
				<xsl:attribute name="status">same</xsl:attribute>
			</xsl:if>
			<xsl:if test="count(new) &gt; 1">
				<xsl:attribute name="status">multi</xsl:attribute>
			</xsl:if>
			<xsl:apply-templates select="*"/>
			<xsl:text>&#xA;&#x9;</xsl:text>
		</xsl:copy>
	</xsl:template>

	<xsl:template match="old | new">
		<xsl:text>&#xA;&#x9;&#x9;</xsl:text>
		<xsl:copy>
			<xsl:apply-templates select="@* | node()"/>
		</xsl:copy>
	</xsl:template>

	<xsl:template match="@* | node()">
		<xsl:copy>
			<xsl:apply-templates select="@* | node()"/>
		</xsl:copy>
	</xsl:template>

</xsl:stylesheet>