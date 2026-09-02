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

	<xsl:template match="image">
		<!-- 원본 파일명 (확장자 제거) -->
		<xsl:variable name="original" select="substring-before(tokenize(@href , '/')[last()], '.')"/>
		<!-- 원본 확장자 그대로 -->
		<xsl:variable name="rawExt" select="substring-after(tokenize(@href , '/')[last()], '.')"/>
		<!-- 치환 테이블: 1010-replace_dark_symbol.xml 대신 최신 DB 원본에서 생성되는 파일명으로 통일 -->
		<xsl:variable name="replacements" select="document('replace_dark_symbol.xml')/replacements"/>
		<!-- 치환된 이름 -->
		<xsl:variable name="newName" select="
			if ($replacements/replace[@from = $original])
			then $replacements/replace[@from = $original]/@to
			else $original
		"/>
		<xsl:variable name="pl" select="lower-case(string(@placement))"/>
		<xsl:variable name="removedAttr" select="@width or @height or @xoffset or @yoffset or @id or (@placement = 'inline' and @outputclass)"/>
		<xsl:variable name="modifiedTokens" select="string-join((
			@modified,
			if ($newName != $original) then 'image-href-renamed' else (),
			if ($pl = 'inline') then 'image-scale-inline' else (),
			if ($pl = 'break') then 'image-scale-break' else (),
			if ($removedAttr) then 'image-attr-removed' else ()
		), ' ')"/>

		<xsl:copy>
			<xsl:apply-templates select="@* except (@modified | @href | @scale | @width | @height | @xoffset | @yoffset | @id | @outputclass[../@placement = 'inline'])"/>
			<xsl:if test="normalize-space($modifiedTokens) != ''">
				<xsl:attribute name="modified" select="normalize-space($modifiedTokens)"/>
			</xsl:if>
			<xsl:attribute name="href">
				<xsl:value-of select="
					replace(
						@href,
						concat($original, '\.', $rawExt),
						concat($newName, '.', lower-case($rawExt))
					)
				"/>
			</xsl:attribute>
			<xsl:choose>
				<xsl:when test="$pl = 'inline'">
					<xsl:attribute name="scale">15</xsl:attribute>
				</xsl:when>
				<xsl:when test="$pl = 'break'">
					<xsl:attribute name="scale">95</xsl:attribute>
				</xsl:when>
			</xsl:choose>
			<xsl:apply-templates select="node()"/>
		</xsl:copy>
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
