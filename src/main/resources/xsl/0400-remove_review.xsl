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
	
	<!-- 작업용 속성 제거
		modified 속성에 누적된 empty, li-direct-text 등의 리포트용 토큰도
		Excel 리포트 생성이 끝난 뒤 modified 속성과 함께 모두 제거한다. -->
	<xsl:template match="@modified | @status | @hash | @report-indexterm-removed | @report-indexterm-action"/>

	<!-- hash PI 제거 -->
	<xsl:template match="processing-instruction('hash')"/>
	
</xsl:stylesheet>
