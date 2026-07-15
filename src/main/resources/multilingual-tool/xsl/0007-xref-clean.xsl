<xsl:stylesheet version="2.0" 
	xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
	xmlns:xs="http://www.w3.org/2001/XMLSchema"
	exclude-result-prefixes="xs">

	<xsl:import href="indentation1.xsl"/>
	<xsl:key name="oids" match="*[@oid]" use="@oid" />

	<xsl:output method="xml" indent="no" omit-xml-declaration="yes"/>
	<xsl:strip-space elements="*" />
	<xsl:preserve-space elements="p" />

	<xsl:template match="map">
		<xsl:copy>
			<xsl:apply-templates select="@* | node()"/>
			<xsl:text>&#x0A;</xsl:text>
		</xsl:copy>
	</xsl:template>

	<xsl:template match="map/topicref">
		<xsl:text>&#x0A;&#x09;</xsl:text>
		<xsl:copy>
			<xsl:apply-templates select="@* except @href"/>
			<xsl:attribute name="href" select="concat(*[1]/@nid, '.dita')"/>
			<xsl:apply-templates select="node()"/>
			<xsl:text>&#x0A;&#x09;</xsl:text>
		</xsl:copy>
	</xsl:template>

	<xsl:template match="xref">
		<xsl:variable name="oid" select="if ( contains(@href, '/') ) then tokenize(@href, '/')[last()] else substring-after(@href, '#')"/>
		<xsl:copy>
			<xsl:apply-templates select="@* except (@outputclass, @href)"/>
			<xsl:if test="key('oids', $oid)">
				<xsl:choose>
					<xsl:when test="key('oids', $oid)[1][parent::topicref]">
						<xsl:variable name="topicid" select="key('oids', $oid)[1]/@nid"/>
						<xsl:attribute name="href" select="concat($topicid, '.dita#', $topicid)"/>
					</xsl:when>
					<xsl:otherwise>
						<xsl:variable name="topicid" select="key('oids', $oid)[1]/ancestor::topicref[1]/*[1]/@nid"/>
						<xsl:variable name="elementid" select="key('oids', $oid)[1]/@nid"/>
						<xsl:attribute name="href" select="concat($topicid, '.dita#', $topicid, '/', $elementid)"/>
					</xsl:otherwise>
				</xsl:choose>
			</xsl:if>
			<xsl:apply-templates select="node()"/>
		</xsl:copy>
	</xsl:template>

	<xsl:template match="@oid">
	</xsl:template>

	<xsl:template match="@nid">
		<xsl:attribute name="id" select="."/>
	</xsl:template>

</xsl:stylesheet>