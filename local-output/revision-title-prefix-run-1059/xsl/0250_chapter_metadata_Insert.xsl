<xsl:stylesheet version="2.0" 
	xmlns:xsl="http://www.w3.org/1999/XSL/Transform" 
	xmlns:xs="http://www.w3.org/2001/XMLSchema" 
	exclude-result-prefixes="xs">
	
	<xsl:output method="xml" indent="yes" encoding="UTF-8"/>
	<!--	<xsl:strip-space elements="*"/>		-->

	<!-- 기본 복사 -->
	<xsl:template match="@* | node()">
		<xsl:copy>
			<xsl:apply-templates select="@* | node()"/>
		</xsl:copy>
	</xsl:template>

	<!-- map 처리 -->
	<xsl:template match="map">

		<!-- mapname에서 .ditamap 제거 -->
		<xsl:variable name="mapname"
			select="replace(@mapname, '\.ditamap$', '')"/>

		<!-- 토큰 분해 -->
		<xsl:variable name="tokens"
			select="tokenize($mapname, '-')"/>

		<xsl:variable name="count"
			select="count($tokens)"/>

		<xsl:variable name="platform"
			select="$tokens[1]"/>

		<xsl:choose>

			<!-- 첫 토큰이 KIA일 때만 metadata 삽입 -->
			<xsl:when test="upper-case($platform) = 'KIA'">

				<xsl:variable name="model" select="$tokens[2]"/>
				<xsl:variable name="year" select="$tokens[$count]"/>
				<xsl:variable name="lang" select="$tokens[$count - 1]"/>

				<!-- 가운데 fuel 조합 -->
				<xsl:variable name="baseRaw"
					select="string-join($tokens[position() &gt; 2 and position() &lt; $count - 1], '-')"/>

				<xsl:copy>
					<xsl:apply-templates select="@*"/>
					
					<!-- 리플렛 표지용 메타데이터 추가 -->
					<xsl:text>&#10;&#9;&#9;&#9;</xsl:text>
					<topicmeta>
						<xsl:text>&#10;&#9;&#9;&#9;&#9;</xsl:text>
						<metadata>

							<xsl:text>&#10;&#9;&#9;&#9;&#9;&#9;</xsl:text>
							<data name="platform"><xsl:value-of select="$platform"/></data>

							<xsl:text>&#10;&#9;&#9;&#9;&#9;&#9;</xsl:text>
							<data name="base"><xsl:value-of select="$baseRaw"/></data>

							<xsl:text>&#10;&#9;&#9;&#9;&#9;&#9;</xsl:text>
							<data name="year"><xsl:value-of select="$year"/></data>

							<xsl:text>&#10;&#9;&#9;&#9;&#9;&#9;</xsl:text>
							<data name="lang"><xsl:value-of select="$lang"/></data>

							<xsl:text>&#10;&#9;&#9;&#9;&#9;&#9;</xsl:text>
							<data name="model"><xsl:value-of select="$model"/></data>

							<xsl:text>&#10;&#9;&#9;&#9;&#9;&#9;</xsl:text>
							<data name="modelName"/>

							<xsl:text>&#10;&#9;&#9;&#9;&#9;&#9;</xsl:text>
							<data name="publicationNo"/>

							<xsl:text>&#10;&#9;&#9;&#9;&#9;&#9;</xsl:text>
							<data name="content"/>

							<xsl:text>&#10;&#9;&#9;&#9;&#9;</xsl:text>
						</metadata>
						<xsl:text>&#10;&#9;&#9;&#9;</xsl:text>
					</topicmeta>

					<!-- 기존 topicmeta 제외 -->
					<xsl:apply-templates
						select="node()[not(self::topicmeta)]"/>

				</xsl:copy>

			</xsl:when>

			<!-- KIA 아니면 원본 유지 -->
			<xsl:otherwise>
				<xsl:copy>
					<xsl:apply-templates select="@* | node()"/>
				</xsl:copy>
			</xsl:otherwise>

		</xsl:choose>

	</xsl:template>

</xsl:stylesheet>