<xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

	<xsl:output method="xml" indent="no" omit-xml-declaration="no"/>
	<xsl:strip-space elements="*" />
	<xsl:preserve-space elements="p"/>

	<xsl:variable name="files" select="collection('../topics/?select=*.ditamap')"/>

	<xsl:template match="@* | node()"> 
		<xsl:copy>
			<xsl:apply-templates select="@* | node()"/>
		</xsl:copy>
	</xsl:template>

	<xsl:template match="dummy">
		<xsl:result-document href="{dummy.xml}">
			<dummy/>
		</xsl:result-document>
		<xsl:result-document href="{concat('../temp/', '09-doctype-removed.xml')}">
			<xsl:for-each select="$files">
				<xsl:apply-templates select="/map">
					<xsl:with-param name="mapname" select="tokenize(base-uri(.), '/')[last()]"/>
				</xsl:apply-templates>
			</xsl:for-each>
		</xsl:result-document>
	</xsl:template>

	<xsl:template match="map">
		<xsl:param name="mapname" />
		<xsl:copy>
			<xsl:apply-templates select="@*"/>
			<xsl:attribute name="mapname" select="$mapname"/>
			<xsl:apply-templates select="node()"/>
		</xsl:copy>
	</xsl:template>

	<xsl:template match="comment()" priority="20">
		<xsl:comment>
			<xsl:value-of select="normalize-space(.)"/>
		</xsl:comment>
	</xsl:template>

</xsl:stylesheet>
