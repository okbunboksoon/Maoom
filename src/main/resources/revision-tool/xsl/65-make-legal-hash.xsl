<xsl:stylesheet 
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xmlns:String="java:java.lang.String"
	xmlns:Integer="java:java.lang.Integer"
    xmlns:md="java:java.security.MessageDigest"
    exclude-result-prefixes="xs String Integer md"
    version="2.0">

	<xsl:output method="xml" indent="no" omit-xml-declaration="yes"/>
    <xsl:strip-space elements="*"/>

    <xsl:template match="@* | node()">
    	<xsl:copy>
			<xsl:apply-templates select="@* | node()"/>
    	</xsl:copy>
    </xsl:template>

	<xsl:template match="topicref[not(starts-with(@href, 'legal'))]">
		<xsl:variable name="Str" select="document(@href)/*/title"/>
		<xsl:variable name="context" select="generate-id()"/>

		<xsl:variable name="hash_1st">
			<xsl:call-template name="hash">
				<xsl:with-param name="Str" select="concat($Str, $context)"/>
			</xsl:call-template>
		</xsl:variable>
		<xsl:variable name="hash_2nd">
			<xsl:call-template name="hash">
				<xsl:with-param name="Str" select="$hash_1st"/>
			</xsl:call-template>
		</xsl:variable>

    	<xsl:copy>
			<xsl:apply-templates select="@*"/>
			<xsl:attribute name="veh-legalid">
				<xsl:value-of select="concat('_', substring($hash_2nd, 1, 12))"/>
			</xsl:attribute>
			<topicmeta>
				<navtitle>
					<xsl:value-of select="if ( string-length($Str) &gt; 59 ) then concat(substring($Str, 1, 59), '...') else substring($Str, 1, 60)"/>
				</navtitle>
				<linktext>
					<xsl:value-of select="$Str"/>
				</linktext>
			</topicmeta>
			<xsl:apply-templates select="node()[not(name()='topicmeta')]"/>
    	</xsl:copy>

	</xsl:template>

	<xsl:template name="hash">
		<xsl:param name="Str"/>
		<xsl:variable name="Inst" select="md:getInstance('SHA-256')" />
		<xsl:variable name="IntSeq" select="md:digest($Inst, String:getBytes($Str, 'utf-8'))" />
		<xsl:variable name="HexStr">
			<xsl:for-each select="$IntSeq">
				<xsl:variable name="hexHash" select="upper-case(Integer:toHexString(.))"/>
				<xsl:value-of select="if ( . &lt; 256 ) then substring(concat('0', $hexHash), string-length($hexHash)) else $hexHash"/>
			</xsl:for-each>
		</xsl:variable>
		<xsl:value-of select="$HexStr"/>
	</xsl:template>

</xsl:stylesheet>