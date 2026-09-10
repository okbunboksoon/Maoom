<xsl:stylesheet version="2.0"
	xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

	<xsl:output method="xml" indent="no" omit-xml-declaration="yes"/>
	<xsl:strip-space elements="*"/>
	<xsl:preserve-space elements="p"/>

	<!-- map/title에 ko_KR과 Quick이 모두 있을 때만 indexterm 전체를 삭제한다. -->
	<xsl:variable name="map-title" select="normalize-space(/*[self::map]/title[1])"/>
	<xsl:variable name="remove-indexterm"
		select="contains($map-title, 'ko_KR') and contains(lower-case($map-title), 'quick')"/>

	<xsl:template match="@* | node()">
		<xsl:copy>
			<xsl:apply-templates select="@* | node()"/>
		</xsl:copy>
	</xsl:template>

	<xsl:template match="indexterm">
		<xsl:if test="not($remove-indexterm)">
			<xsl:copy>
				<xsl:apply-templates select="@* | node()"/>
			</xsl:copy>
		</xsl:if>
	</xsl:template>

</xsl:stylesheet>
