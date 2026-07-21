<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="2.0" 
	xmlns:xsl="http://www.w3.org/1999/XSL/Transform" 
	xmlns:xs="http://www.w3.org/2001/XMLSchema" 
	exclude-result-prefixes="xs">
	
	<xsl:import href="indentation1.xsl"/>
	<xsl:output method="xml" indent="no" omit-xml-declaration="yes"/>
	<xsl:strip-space elements="*"/>
	<xsl:preserve-space elements="p"/>

	<!-- map은 그대로 -->
	<xsl:template match="map">
		<map>
			<xsl:apply-templates select="@* | node()"/>
			<xsl:text>&#x0A;</xsl:text>
		</map>
	</xsl:template>

	<xsl:template match="image/@href">

		<!-- 원본 파일명 (확장자 제거) -->
		<xsl:variable name="original"
			select="substring-before(tokenize(. , '/')[last()], '.')"/>

		<!-- 원본 확장자 그대로 -->
		<xsl:variable name="rawExt"
			select="substring-after(tokenize(. , '/')[last()], '.')"/>

		<!-- 치환 테이블 -->
		<xsl:variable name="replacements"
			select="document('1010-replace_dark_symbol.xml')/replacements"/>

		<!-- 치환된 이름 -->
		<xsl:variable name="newName" select="
			if ($replacements/replace[@from = $original])
			then $replacements/replace[@from = $original]/@to
			else $original
		"/>

		<!-- 실제 치환 발생 시만 modified 추가 -->
		<xsl:if test="$newName != $original">
			<xsl:attribute name="modified">image-attr</xsl:attribute>
		</xsl:if>

		<!-- 확장자 -->
		<xsl:variable name="ext" select="lower-case($rawExt)"/>

		<!-- 경로 유지 + 파일명만 치환 -->
		<xsl:attribute name="href">
			<xsl:value-of select="
				replace(
					.,
					concat($original, '\.', $rawExt),
					concat($newName, '.', $ext)
				)
			"/>
		</xsl:attribute>

		<!-- placement 기준 scale 강제 -->
		<xsl:variable name="pl" select="lower-case(string(../@placement))"/>

		<xsl:choose>
			<xsl:when test="$pl = 'inline'">
				<xsl:attribute name="scale">15</xsl:attribute>
			</xsl:when>

			<xsl:when test="$pl = 'break'">
				<xsl:attribute name="scale">95</xsl:attribute>
			</xsl:when>

			<!-- inline/break 아닌 경우 유지 -->
		</xsl:choose>

	</xsl:template>

	<!-- 5개 속성 제거 -->
	<xsl:template
		match="image/@width
		     | image/@height
		     | image/@xoffset
		     | image/@yoffset
		     | image/@id"/>

	<!-- inline image outputclass 제거 -->
	<xsl:template match="image[@placement = 'inline']/@outputclass"/>

</xsl:stylesheet>