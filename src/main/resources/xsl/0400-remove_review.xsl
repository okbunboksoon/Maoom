<xsl:stylesheet version="2.0"
	xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

	<xsl:output method="xml" indent="no" omit-xml-declaration="no"/>
	<xsl:strip-space elements="*"/>
	<xsl:preserve-space elements="p"/>

	<xsl:template match="@* | node()">
		<xsl:copy>
			<xsl:apply-templates select="@* | node()"/>
		</xsl:copy>
	</xsl:template>

	<!-- outputclass에서 'review'만 제거 -->
	<xsl:template match="@outputclass">
		<xsl:variable name="tokens" select="tokenize(normalize-space(.), '\s+')"/>
		<!-- review + legal 제거 -->
		<xsl:variable name="filtered" select="$tokens[. != 'review' and . != 'legal']"/>
		<xsl:if test="exists($filtered)">
			<xsl:attribute name="outputclass" select="string-join($filtered, ' ')"/>
		</xsl:if>
	</xsl:template>
	
	<!-- modified 속성 제거 -->
	<xsl:template match="@modified"/>

	<!-- hash PI 제거 -->
	<xsl:template match="processing-instruction('hash')"/>
	
</xsl:stylesheet>
