<xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

	<xsl:import href="indentation1.xsl"/>

	<xsl:output method="xml" indent="no" omit-xml-declaration="no"/>
	<xsl:strip-space elements="*" />

	<xsl:template match="map">
		<dummy/>
		<xsl:result-document href="{concat('../topics/', @mapname)}">
			<xsl:text>&#x0A;</xsl:text>
			<xsl:text disable-output-escaping="yes">&lt;!DOCTYPE map PUBLIC &quot;-//OASIS//DTD DITA 1.3 Map//EN&quot; &quot;technicalContent/dtd/map.dtd&quot; []&gt;</xsl:text>
			<xsl:text>&#x0A;</xsl:text>
			<xsl:processing-instruction name="path2rootmap-uri">./</xsl:processing-instruction>
			<xsl:text>&#x0A;</xsl:text>
			<map xmlns:ditaarch="http://dita.oasis-open.org/architecture/2005/">
				<xsl:apply-templates select="@* except @mapname"/>
				<xsl:text>&#x0A;&#x09;</xsl:text>
				<title>
					<xsl:value-of select="substring-before(@mapname, '.ditamap')"/>
				</title>
				<xsl:apply-templates select="node()"/>
				<xsl:text>&#x0A;</xsl:text>
			</map>
		</xsl:result-document>
	</xsl:template>

	<xsl:template match="topicref">
		<xsl:variable name="depth" select="count(ancestor::*)"/>
		<xsl:call-template name="indentation">
			<xsl:with-param name="depth" select="$depth"/>
		</xsl:call-template>
		<xsl:copy>
			<xsl:apply-templates select="@*"/>
			<xsl:choose>
				<xsl:when test="topicmeta/navtitle = 'Abbreviation'">
					<xsl:attribute name="outputclass">abbr</xsl:attribute>
				</xsl:when>
				<xsl:when test="topicmeta/navtitle = 'Appendix'">
					<xsl:attribute name="outputclass">appx</xsl:attribute>
				</xsl:when>
			</xsl:choose>
			<xsl:apply-templates select="node()"/>
			<xsl:call-template name="indentation">
				<xsl:with-param name="depth" select="$depth"/>
			</xsl:call-template>
		</xsl:copy>
	</xsl:template>

</xsl:stylesheet>