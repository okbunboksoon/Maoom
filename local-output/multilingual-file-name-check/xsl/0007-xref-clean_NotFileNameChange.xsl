<xsl:stylesheet version="2.0" 
	xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
	xmlns:xs="http://www.w3.org/2001/XMLSchema"
	xmlns:ss="urn:schemas-microsoft-com:office:spreadsheet"
	exclude-result-prefixes="xs ss">

	<xsl:import href="indentation1.xsl"/>
	<xsl:key name="oids" match="*[@oid]" use="@oid"/>
	<!-- title 기준 파일명 prefix 옵션 -->
	<xsl:param name="titleFileNamePrefix" select="'N'"/>
	<!-- 다국어 변환 XML 입력에서는 원본 ditamap 이름을 파라미터로 넘겨 파일명 prefix 기준으로 사용한다. -->
	<xsl:param name="mapName" select="''"/>
	<!-- 다국어 변환 BAT에서 넘기는 언어명. 정제에서는 비어 있어 기존 title 기준으로 처리한다. -->
	<xsl:param name="langName" select="''"/>
	<xsl:variable name="langMap" select="document('lang_code_map.xml')"/>
	<xsl:variable name="target-lang-code" select="string($langMap//ss:Row[ss:Cell[1]/ss:Data = $langName]/ss:Cell[2]/ss:Data)"/>
	<xsl:variable name="target-lang-file" select="replace($target-lang-code, '-', '_')"/>
	<!-- ditamap/title 값 추출. XML 다국어 변환 중간 map에는 title이 없어 mapname을 fallback으로 사용한다. -->
	<xsl:variable name="map-title" select="if (normalize-space($mapName) != '') then replace(normalize-space($mapName), '\.ditamap$', '') else if (normalize-space(/*/title[1]) != '') then normalize-space(/*/title[1]) else replace(string(/*/@mapname), '\.ditamap$', '')"/>
	<!-- title 값을 하이픈 기준 토큰으로 분리 -->
	<xsl:variable name="map-title-tokens" select="tokenize($map-title, '-')"/>
	<!-- KIA 토큰과 PE/PE2 토큰을 제외한 title 기반 파일명 prefix 생성 -->
	<xsl:variable name="filename-prefix-parts" select="$map-title-tokens[normalize-space(.) != '' and not(upper-case(.) = ('KIA', 'PE', 'PE2'))]"/>
	<xsl:variable name="filename-prefix" select="if (upper-case($titleFileNamePrefix) = 'Y' and exists($filename-prefix-parts)) then concat(string-join($filename-prefix-parts, '-'), '-') else ''"/>

	<xsl:output method="xml" indent="no" omit-xml-declaration="yes"/>
	<xsl:strip-space elements="*"/>
	<xsl:preserve-space elements="p"/>

	<xsl:template match="map">
		<xsl:copy>
			<xsl:apply-templates select="@* | node()"/>
			<xsl:text>&#x0A;</xsl:text>
		</xsl:copy>
	</xsl:template>

	<xsl:template match="topicref">
		<xsl:text>&#x0A;&#x09;</xsl:text>
		<xsl:copy>

			<xsl:apply-templates select="@* except @href"/>

			<!-- 기존 -->
			<!-- <xsl:attribute name="href" select="concat(*[1]/@nid, '.dita')"/> -->

			<!-- 수정 -->
			<xsl:attribute name="href" select="concat(if ($filename-prefix != '' and *[1]/@nid) then *[1]/@nid else concat($filename-prefix, *[1]/@oid), '.dita')"/>

			<xsl:apply-templates select="node()"/>
			<xsl:text>&#x0A;&#x09;</xsl:text>
		</xsl:copy>
	</xsl:template>

<xsl:template match="xref">

	<!-- 기존 (문제 있음: SP00025만 추출됨) -->
	<!--
	<xsl:variable name="oid"
		select="
			if (contains(@href, '/')) then tokenize(@href, '/')[last()]
			else if (contains(@href, '#')) then substring-after(@href, '#')
			else @href
		"/>
	-->

	<!-- 기존 수정 (문제 있음: topic만 유지되어 element 유실됨) -->
	<!--
	<xsl:variable name="topicOid"
		select="replace(tokenize(@href, '/')[last()], '\.dita.*$', '')"/>
	-->

	<!-- 수정: href 전체 -->
	<xsl:variable name="href" select="@href"/>

	<xsl:copy>
		<xsl:apply-templates select="@* except (@outputclass, @href)"/>

		<xsl:choose>
			<!-- 수정 1: 외부 경로 (../ 포함) → xref 비우기 -->
			<xsl:when test="contains($href, '../')">
				<xsl:text>###</xsl:text>
			</xsl:when>

			<!-- 수정 2: fragment 기반 처리 -->
			<xsl:when test="contains($href, '#')">

				<!-- fragment 추출 -->
				<xsl:variable name="frag" select="substring-after($href, '#')"/>

				<!-- topic / element 분리 -->
				<xsl:variable name="topicOid" select="tokenize($frag, '/')[1]"/>
				<xsl:variable name="elementOid" select="tokenize($frag, '/')[last()]"/>

				<xsl:choose>

					<!-- 기존 -->
					<!--
					<xsl:if test="key('oids', $oid)">
						<xsl:choose>
							<xsl:when test="key('oids', $oid)[1][parent::topicref]">
								<xsl:variable name="topicOid" select="key('oids', $oid)[1]/@oid"/>
								<xsl:attribute name="href" select="concat($topicOid, '.dita#', $topicOid)"/>
							</xsl:when>

							<xsl:otherwise>
								<xsl:variable name="topicOid" select="key('oids', $oid)[1]/ancestor::topicref[1]/*[1]/@oid"/>
								<xsl:variable name="elementOid" select="key('oids', $oid)[1]/@oid"/>
								<xsl:attribute name="href" select="concat($topicOid, '.dita#', $topicOid, '/', $elementOid)"/>
							</xsl:otherwise>
						</xsl:choose>
					</xsl:if>
					-->

					<!-- 수정 2-1: topic만 참조 -->
					<xsl:when test="not(contains($frag, '/')) and key('oids', $topicOid)">
						<xsl:variable name="targetTopicId" select="if (key('oids', $topicOid)[1][parent::topicref]) then key('oids', $topicOid)[1]/@nid else key('oids', $topicOid)[1]/ancestor::topicref[1]/*[1]/@nid"/>
						<xsl:attribute name="href"
							select="concat($targetTopicId, '.dita#', $targetTopicId)"/>
					</xsl:when>

					<!-- 수정 2-2: topic + element 참조 유지 -->
					<xsl:when test="contains($frag, '/') and key('oids', $elementOid)">
						<xsl:variable name="targetTopicId" select="key('oids', $elementOid)[1]/ancestor::topicref[1]/*[1]/@nid"/>
						<xsl:variable name="targetElementId" select="key('oids', $elementOid)[1]/@nid"/>
						<xsl:attribute name="href"
							select="concat($targetTopicId, '.dita#', $targetTopicId, '/', $targetElementId)"/>
					</xsl:when>

				</xsl:choose>

			</xsl:when>

			<!-- 수정 3: 그 외는 제거 -->
			<xsl:otherwise>
				<!-- 아무것도 안 넣음 -->
			</xsl:otherwise>

		</xsl:choose>

		<xsl:apply-templates select="node()"/>
	</xsl:copy>
</xsl:template>
	<!-- 기존 -->
	<!-- <xsl:template match="@oid"/> -->

	<!-- 수정 -->
	<xsl:template match="@oid">
		<xsl:attribute name="id" select="if (../@nid) then ../@nid else ."/>
	</xsl:template>

	<xsl:template match="@nid"/>

</xsl:stylesheet>
