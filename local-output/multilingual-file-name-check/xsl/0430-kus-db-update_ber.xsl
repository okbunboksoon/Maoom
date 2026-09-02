<xsl:stylesheet version="2.0" 
	xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
	
	<xsl:output method="xml" indent="no" encoding="UTF-8" omit-xml-declaration="yes"/>
	<xsl:strip-space elements="*"/>
	
	<!-- 외부 업데이트 리스트 로드 -->
	<!--
	<xsl:variable name="updateList" select="document('../temp/asis-tobe.xml')/pairs/pair"/>
	-->
	<xsl:param name="region"/>
	<xsl:variable name="filePath" select="concat('../temp/asis-tobe_', $region, '.xml')"/>
	<xsl:variable name="updateDoc" select="if (doc-available($filePath)) then document($filePath) else ()"/>
	<xsl:variable name="updateList" select="$updateDoc/pairs/pair"/>
	<xsl:template match="/pairs">
		<pairs>
			<xsl:text>&#xA;</xsl:text>
			<!-- 기존 DB 기준 반복 -->
			<xsl:for-each select="pair">
				<xsl:variable name="curHash" select="@hash"/>
				<xsl:variable name="matchedPair" select="$updateList[@hash = $curHash]"/>
				<xsl:variable name="newToAdd" select="$matchedPair/new"/>
				<xsl:variable name="existingNewTexts" select="new/normalize-space(.)"/>
				<xsl:text>&#x9;</xsl:text>
				<pair>
					<xsl:copy-of select="@hash"/>
					<xsl:text>&#xA;&#x9;&#x9;</xsl:text>
					<xsl:copy-of select="old"/>
					<!-- 기존 new 복사 -->
					<xsl:for-each select="new">
						<xsl:text>&#xA;&#x9;&#x9;</xsl:text>
						<xsl:copy-of select="."/>
					</xsl:for-each>
					<!-- 외부 new가 있고, 기존 new에 같은 텍스트 없으면 추가 -->
					<xsl:if test="$newToAdd and not(normalize-space($newToAdd) = $existingNewTexts)">
						<xsl:text>&#xA;&#x9;&#x9;</xsl:text>
						<new>
							<xsl:apply-templates select="$newToAdd/node()"/>
						</new>
					</xsl:if>
					<xsl:text>&#xA;&#x9;</xsl:text>
				</pair>
				<xsl:text>&#xA;</xsl:text>
			</xsl:for-each>
			<!-- 새로 들어온 hash면 맨 아래에 추가 -->
			<xsl:for-each select="$updateList[not(@hash = current()/pair/@hash)]">
				<xsl:text>&#x9;</xsl:text>
				<pair>
					<xsl:copy-of select="@*"/>
					<xsl:text>&#xA;&#x9;&#x9;</xsl:text>
					<xsl:copy-of select="old"/>
					<xsl:text>&#xA;&#x9;&#x9;</xsl:text>
					<xsl:copy-of select="new"/>
					<xsl:text>&#xA;&#x9;</xsl:text>
				</pair>
				<xsl:text>&#xA;</xsl:text>
			</xsl:for-each>
		</pairs>
	</xsl:template>
	<!-- 텍스트 노드 그대로 출력 -->
	<xsl:template match="text()">
		<xsl:value-of select="."/>
	</xsl:template>
	<!-- inline 태그 복사 (원하는 태그 추가 가능) -->
	<xsl:template match="uicontrol | tm | term | ph | b | i | xref">
		<xsl:copy>
			<xsl:apply-templates select="@* | node()"/>
		</xsl:copy>
	</xsl:template>
</xsl:stylesheet>
