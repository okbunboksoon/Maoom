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

	<!-- 삭제 조건과 실제 삭제 개수를 후속 Excel 리포트에서 읽을 수 있도록 map에 기록한다. -->
	<xsl:template match="map">
		<xsl:copy>
			<xsl:apply-templates select="@*"/>
			<xsl:attribute name="report-indexterm-removed"
				select="if ($remove-indexterm) then count(.//indexterm) else 0"/>
			<xsl:attribute name="report-indexterm-action"
				select="if ($remove-indexterm) then '삭제' else '유지'"/>
			<xsl:apply-templates select="node()"/>
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
