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

	<xsl:template match="/*">
		<xsl:copy>
			<xsl:apply-templates select="@*"/>
			<xsl:attribute name="report-draft-comment-removed" select="count(.//draft-comment)"/>
			<xsl:attribute name="report-review-outputclass-cleaned" select="count(.//@outputclass[tokenize(normalize-space(.), '\s+') = 'review'])"/>
			<xsl:attribute name="report-legal-outputclass-cleaned" select="count(.//@outputclass[tokenize(normalize-space(.), '\s+') = 'legal'])"/>
			<xsl:attribute name="report-review-legal-outputclass-cleaned" select="count(.//@outputclass[tokenize(normalize-space(.), '\s+') = ('review', 'legal')])"/>
			<xsl:apply-templates select="node()"/>
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
	
	<!-- draft-comment 태그 통째 삭제 -->
	<xsl:template match="draft-comment"/>
	
</xsl:stylesheet>
