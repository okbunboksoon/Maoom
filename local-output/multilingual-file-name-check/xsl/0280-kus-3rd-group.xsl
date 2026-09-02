<xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

	<xsl:output method="xml" indent="no" omit-xml-declaration="yes"/>
	<xsl:strip-space elements="*" />

	<xsl:template match="@* | node()">
		<xsl:copy>
			<xsl:apply-templates select="@* | node()"/>
		</xsl:copy>
	</xsl:template>

	<xsl:template match="@* | node()" mode="old">
		<xsl:copy>
			<xsl:apply-templates select="@* | node()" mode="old"/>
		</xsl:copy>
	</xsl:template>

	<xsl:template match="@* | node()" mode="new">
		<xsl:copy>
			<xsl:apply-templates select="@* | node()" mode="new"/>
		</xsl:copy>
	</xsl:template>

	<xsl:template match="old" mode="old">
		<xsl:apply-templates mode="old"/>
	</xsl:template>

	<xsl:template match="new" mode="new">
		<xsl:apply-templates mode="new"/>
	</xsl:template>

	<xsl:template match="new" mode="old">
	</xsl:template>

	<xsl:template match="old" mode="new">
	</xsl:template>

	<xsl:template match="*[count(node())=2][new][old]">
		<pair>
			<old><xsl:apply-templates select="node()" mode="old"/></old>
			<new><xsl:apply-templates select="node()" mode="new"/></new>
		</pair>
	</xsl:template>

	<xsl:template match="*[text()][new][old]">
		<pair>
			<old><xsl:apply-templates select="node()" mode="old"/></old>
			<new><xsl:apply-templates select="node()" mode="new"/></new>
		</pair>
	</xsl:template>

	<xsl:template match="old[following-sibling::node()[1][name()='new']]">
		<pair>
			<old><xsl:apply-templates select="node()" mode="old"/></old>
			<new><xsl:apply-templates select="following-sibling::node()[1]" mode="new"/></new>
		</pair>
	</xsl:template>

	<xsl:template match="new[preceding-sibling::node()[1][name()='old']]">
	</xsl:template>

	<xsl:template match="*[old][not(new)]">
		<pair>
			<old><xsl:apply-templates select="node()" mode="old"/></old>
			<new><xsl:apply-templates select="node()" mode="new"/></new>
		</pair>
	</xsl:template>

</xsl:stylesheet>