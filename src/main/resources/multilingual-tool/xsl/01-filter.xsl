<xsl:stylesheet version="2.0" 
	xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
	xmlns:xs="http://www.w3.org/2001/XMLSchema"
	exclude-result-prefixes="xs">

	<xsl:import href="indentation1.xsl"/>

	<xsl:output method="xml" indent="no" omit-xml-declaration="yes" />
	<xsl:strip-space elements="*" />
	<xsl:preserve-space elements="p"/>

	<xsl:template match="map">
		<map>
			<xsl:apply-templates select="@* | node()"/>
			<xsl:text>&#x0A;</xsl:text>
		</map>
	</xsl:template>

	<xsl:template match="chapter">
		<xsl:text>&#x0A;&#x09;</xsl:text>
		<xsl:copy>
			<xsl:apply-templates select="@*"/>
			<xsl:attribute name="chapnum" select="format-number(count(preceding-sibling::chapter) + 1, '00')"/>
			<xsl:apply-templates/>
			<xsl:text>&#x0A;&#x09;</xsl:text>
		</xsl:copy>
	</xsl:template>

	<xsl:template match="image/@placement">
		<xsl:choose>
			<xsl:when test="ancestor::figgroup">
				<xsl:attribute name="placement">break</xsl:attribute>
			</xsl:when>
			<xsl:otherwise>
				<xsl:attribute name="placement">inline</xsl:attribute>
				<xsl:attribute name="outputclass">nolabel</xsl:attribute>
			</xsl:otherwise>
		</xsl:choose>
	</xsl:template>

	<xsl:template match="image/@href">
		<xsl:attribute name="href">
			<xsl:value-of select="concat('images/', replace(replace(tokenize(., '/')[last()], '\.EPS', '.eps'), '\.eps', '.jpg'))"/>
		</xsl:attribute>
	</xsl:template>

	<xsl:template match="image/@width">
		<xsl:choose>
			<xsl:when test="ancestor::figgroup">
			</xsl:when>
			<xsl:otherwise>
				<xsl:attribute name="width" select="."/>
			</xsl:otherwise>
		</xsl:choose>
	</xsl:template>

	<xsl:template match="image/@scale | image/@xoffset | image/@yoffset | fig/@expanse">
	</xsl:template>

	<xsl:template match="@outputclass">
		<xsl:choose>
			<xsl:when test=". = 'callout'">
				<xsl:attribute name="outputclass" select='.'/>
			</xsl:when>
			<xsl:when test=". = 'overview'">
				<xsl:attribute name="outputclass" select="."/>
			</xsl:when>
			<xsl:when test=". = 'continue'">
				<xsl:choose>
					<xsl:when test="not(parent::ol/preceding-sibling::ol[@outputclass='callout'])">
					</xsl:when>
					<xsl:when test="not(parent::ol/parent::*/ol[@outputclass='callout'])">
					</xsl:when>
					<xsl:otherwise>
						<xsl:attribute name="outputclass">callout_continue</xsl:attribute>
					</xsl:otherwise>
				</xsl:choose>
			</xsl:when>
			<xsl:when test=". = 'formatc' or . = 'formatc_none'">
				<xsl:attribute name="outputclass" select="."/>
			</xsl:when>
			<xsl:when test=". = 'thin_only'">
				<xsl:attribute name="deliveryTarget" select="."/>
			</xsl:when>
			<xsl:when test=". = 'full_only'">
				<xsl:attribute name="deliveryTarget" select="."/>
			</xsl:when>
			<xsl:when test=". = 'Anywhere'">
				<xsl:attribute name="base">Anywhere</xsl:attribute>
			</xsl:when>
			<xsl:when test=". = 'Top'">
				<xsl:attribute name="base">Top</xsl:attribute>
			</xsl:when>
			<xsl:otherwise>
			</xsl:otherwise>
		</xsl:choose>
	</xsl:template>

	<xsl:template match="@colname | @namest | @nameend">
		<xsl:attribute name="{name()}" select="concat('col', .)"/>
	</xsl:template>

	<xsl:template match="@colwidth">
		<xsl:attribute name="{name()}" select="concat(substring-before(., 'in'), '*')"/>
	</xsl:template>

	<xsl:template match="xref">
		<xsl:choose>
			<xsl:when test="preceding-sibling::node()[1][not(ends-with(., '&#x20;'))]">
				<xsl:text>&#x20;</xsl:text>
				<xsl:copy>
					<xsl:apply-templates select="@* | node()"/>
				</xsl:copy>
			</xsl:when>
			<xsl:otherwise>
				<xsl:copy>
					<xsl:apply-templates select="@* | node()"/>
				</xsl:copy>
			</xsl:otherwise>
		</xsl:choose>
	</xsl:template>

</xsl:stylesheet>