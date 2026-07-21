<xsl:stylesheet version="2.0" 
	xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
	xmlns:xs="http://www.w3.org/2001/XMLSchema"
	exclude-result-prefixes="xs">

	<xsl:import href="indentation1.xsl"/>
	<xsl:key name="oids" match="*[@oid]" use="@oid"/>

	<xsl:output method="xml" indent="no" omit-xml-declaration="yes"/>
	<xsl:strip-space elements="*"/>
	<xsl:preserve-space elements="p"/>

	<xsl:template match="map">
		<xsl:copy>
			<xsl:apply-templates select="@* | node()"/>
			<xsl:text>&#x0A;</xsl:text>
		</xsl:copy>
	</xsl:template>

	<xsl:template match="map/topicref">
		<xsl:text>&#x0A;&#x09;</xsl:text>
		<xsl:copy>

			<xsl:apply-templates select="@* except @href"/>

			<!-- 기존 -->
			<!-- <xsl:attribute name="href" select="concat(*[1]/@nid, '.dita')"/> -->

			<!-- 수정 -->
			<xsl:attribute name="href" select="concat(*[1]/@oid, '.dita')"/>

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
						<xsl:attribute name="href"
							select="concat($topicOid, '.dita#', $topicOid)"/>
					</xsl:when>

					<!-- 수정 2-2: topic + element 참조 유지 -->
					<xsl:when test="contains($frag, '/') and key('oids', $elementOid)">
						<xsl:attribute name="href"
							select="concat($topicOid, '.dita#', $topicOid, '/', $elementOid)"/>
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
		<xsl:attribute name="id" select="."/>
	</xsl:template>

	<xsl:template match="@nid"/>

</xsl:stylesheet>