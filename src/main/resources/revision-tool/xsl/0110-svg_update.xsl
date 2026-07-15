<xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xs="http://www.w3.org/2001/XMLSchema" exclude-result-prefixes="xs">
	<xsl:output method="xml" indent="yes" encoding="UTF-8"/>
	<xsl:strip-space elements="*"/>
	<!-- navtitle 매핑 로드 -->
	<xsl:variable name="MAP" select="document(resolve-uri('1000-navtitle-to-svg.xml', static-base-uri()))/mappings"/>
	<xsl:key name="kNav" match="item" use="lower-case(normalize-space(@nav))"/>
	<!-- 기본 복사 -->
	<xsl:template match="@* | node()">
		<xsl:copy>
			<xsl:apply-templates select="@* | node()"/>
		</xsl:copy>
	</xsl:template>
	<!-- 1레벨 topicmeta 정합성 강제 보정 -->
	<xsl:template match="/map/topicref/topicmeta">
		<xsl:variable name="navKey" select="lower-case(normalize-space(navtitle))"/>
		<xsl:variable name="hit" select="key('kNav', $navKey, $MAP)"/>
		<!-- 매핑된 올바른 svg -->
		<xsl:variable name="expectedSvg" select="string($hit/@svg)"/>
		<!-- 기존 href -->
		<xsl:variable name="oldHref" select="normalize-space(string((data[@name='wh-tile']/data[@name='image']/@href)[1]))"/>
		<!-- 기존 파일명만 추출 -->
		<xsl:variable name="oldFile" select="tokenize($oldHref,'/')[last()]"/>
		<!-- 교체 필요 여부 -->
		<xsl:variable name="needFix" select="exists($hit) and (not(data[@name='wh-tile']) or not($oldFile = $expectedSvg))"/>
		<xsl:copy>
			<xsl:apply-templates select="@*"/>
			<xsl:apply-templates select="navtitle"/>
			<xsl:apply-templates select="linktext"/>
			<!-- svg 교체 또는 신규 생성 -->
			<xsl:choose>
				<!-- 교체 필요 -->
				<xsl:when test="$needFix">
					<data name="wh-tile">
						<data name="image" format="svg">
							<xsl:attribute name="href">
								<xsl:text>./oxygen-webhelp/template/images/</xsl:text>
								<xsl:value-of select="$expectedSvg"/>
							</xsl:attribute>
							<data name="attr-width" value="64"/>
							<data name="attr-height" value="64"/>
						</data>
					</data>
				</xsl:when>
				<!-- 이미 정상 -->
				<xsl:otherwise>
					<xsl:apply-templates select="data[@name='wh-tile']"/>
				</xsl:otherwise>
			</xsl:choose>
			<!-- 기타 노드 유지 -->
			<xsl:apply-templates select="node()[not(self::navtitle or self::linktext or self::data[@name='wh-tile'])]"/>
		</xsl:copy>
	</xsl:template>
	<!-- 1레벨 topicref에 topicmeta 없으면 자동 생성 -->
	<xsl:template match="/map/topicref[not(topicmeta)]">
		<xsl:copy>
			<xsl:apply-templates select="@*"/>
			<!-- 파일명 기반 navtitle 생성 -->
			<xsl:variable name="base" select="replace(tokenize(@href,'/')[last()], '\.dita$', '')"/>
			<xsl:variable name="titleText" select="replace($base,'_',' ')"/>
			<xsl:variable name="hit" select="key('kNav',
                    lower-case(normalize-space($titleText)),
                    $MAP)"/>
			<topicmeta>
				<navtitle>
					<xsl:value-of select="$titleText"/>
				</navtitle>
				<linktext>
					<xsl:value-of select="$titleText"/>
				</linktext>
				<xsl:if test="$hit">
					<data name="wh-tile">
						<data name="image" format="svg">
							<xsl:attribute name="href">
								<xsl:text>./oxygen-webhelp/template/images/</xsl:text>
								<xsl:value-of select="$hit/@svg"/>
							</xsl:attribute>
							<data name="attr-width" value="64"/>
							<data name="attr-height" value="64"/>
						</data>
					</data>
				</xsl:if>
			</topicmeta>
			<xsl:apply-templates select="node()"/>
		</xsl:copy>
	</xsl:template>
</xsl:stylesheet>