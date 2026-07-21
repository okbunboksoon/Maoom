<xsl:stylesheet version="2.0" 
	xmlns:xsl="http://www.w3.org/1999/XSL/Transform" 
	xmlns:xs="http://www.w3.org/2001/XMLSchema" 
	exclude-result-prefixes="xs">
	
	<xsl:output method="xml" indent="yes" encoding="UTF-8"/>
	<xsl:strip-space elements="*"/>
	
	<!--  기본 복사  -->
	<xsl:template match="@* | node()">
		<xsl:copy>
			<xsl:apply-templates select="@* | node()"/>
		</xsl:copy>
	</xsl:template>
	
	<!--  map 처리  -->
	<xsl:template match="map">
		<!-- title 기준 -->
		<xsl:variable name="base" select="normalize-space(title)"/>
		<!-- 토큰 분해 -->
		<xsl:variable name="tokens" select="tokenize($base,'-')"/>
		<xsl:variable name="count" select="count($tokens)"/>
		<xsl:variable name="platform" select="$tokens[1]"/>
		<xsl:choose>
			<!-- 첫 토큰이 KIA일 때만 metadata 삽입 -->
			<xsl:when test="upper-case($platform) = 'KIA'">
				<xsl:variable name="model" select="$tokens[2]"/>
				<xsl:variable name="year" select="$tokens[$count]"/>
				<xsl:variable name="lang" select="$tokens[$count - 1]"/>
				<!-- 가운데 fuel 조합 -->
				<xsl:variable name="baseRaw" select="string-join($tokens[position() &gt; 2 and position() &lt; $count - 1], '-')"/>
				<xsl:copy>
					<xsl:apply-templates select="@*"/>
					<!-- title 유지 -->
					<xsl:apply-templates select="title"/>
					<!-- topicmeta 재생성 -->
					<topicmeta>
						<metadata>
							<data name="platform">
								<xsl:value-of select="$platform"/>
							</data>
							<data name="base">
								<xsl:value-of select="$baseRaw"/>
							</data>
							<data name="year">
								<xsl:value-of select="$year"/>
							</data>
							<data name="lang">
								<xsl:value-of select="$lang"/>
							</data>
							<data name="model">
								<xsl:value-of select="$model"/>
							</data>
						</metadata>
					</topicmeta>
					<!-- 기존 topicmeta 제거하고 나머지 유지 -->
					<xsl:apply-templates select="node()[not(self::title or self::topicmeta)]"/>
				</xsl:copy>
			</xsl:when>
			<!-- KIA 아니면 원본 그대로 유지 -->
			<xsl:otherwise>
				<xsl:copy>
					<xsl:apply-templates select="@* | node()"/>
				</xsl:copy>
			</xsl:otherwise>
		</xsl:choose>
	</xsl:template>
</xsl:stylesheet>