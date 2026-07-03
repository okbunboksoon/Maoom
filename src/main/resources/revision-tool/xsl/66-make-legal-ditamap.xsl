<xsl:stylesheet version="2.0" 
	xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
	xmlns:xs="http://www.w3.org/2001/XMLSchema"
	exclude-result-prefixes="xs">

	<xsl:output method="xml" indent="yes" omit-xml-declaration="no"/>
	<xsl:strip-space elements="*" />
	<xsl:preserve-space elements="p"/>

	<xsl:key name="topicref-by-legalid" match="topicref[*[@veh-legalid]]" use="*[@veh-legalid]/@veh-legalid"/>
	<xsl:key name="topicref-by-href" match="topicref[@href]" use="@href"/>
	<xsl:variable name="merged-doc" select="document('../temp/full_merged.xml')"/>

	<xsl:template match="@* | node()"> 
		<xsl:copy>
			<xsl:apply-templates select="@* | node()"/>
		</xsl:copy>
	</xsl:template>

	<xsl:template match="map">
		<xsl:text>&#x0A;</xsl:text>
		<xsl:text disable-output-escaping="yes">&lt;!DOCTYPE map PUBLIC &quot;-//OASIS//DTD DITA 1.3 Map//EN&quot; &quot;technicalContent/dtd/map.dtd&quot; []&gt;</xsl:text>
		<xsl:text>&#x0A;</xsl:text>
		<xsl:processing-instruction name="path2rootmap-uri">./</xsl:processing-instruction>
		<xsl:text>&#x0A;</xsl:text>
		<map xmlns:ditaarch="http://dita.oasis-open.org/architecture/2005/" cascade="merge" xml:lang="en-US">
			<xsl:apply-templates select="@* | node()"/>
			<xsl:text>&#x0A;</xsl:text>
		</map>
	</xsl:template>

	<xsl:template match="topicref">
		<xsl:copy>
			<xsl:choose>
				<xsl:when test="@href">
					<xsl:variable name="href" select="@href"/>
					<xsl:apply-templates select="@href"/>
					<xsl:attribute name="type">
						<xsl:value-of select="key('topicref-by-href', $href, $merged-doc)[1]/*/name()"/>
					</xsl:attribute>
				</xsl:when>
				<xsl:when test="@legal">
					<xsl:variable name="legal" select="@legal"/>
					<xsl:attribute name="href">
						<xsl:value-of select="key('topicref-by-legalid', $legal, $merged-doc)[1]/@href"/>
					</xsl:attribute>
					<xsl:attribute name="type">
						<xsl:value-of select="key('topicref-by-legalid', $legal, $merged-doc)[1]/*/name()"/>
					</xsl:attribute>
				</xsl:when>
			</xsl:choose>
			<xsl:apply-templates select="@* except (@href, @legal)"/>
			<xsl:apply-templates select="*"/>
		</xsl:copy>
	</xsl:template>

</xsl:stylesheet>