<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xs="http://www.w3.org/2001/XMLSchema" exclude-result-prefixes="xs">
	<!-- 중요 항목 리스트 -->
	<xsl:variable name="important_items" select="document('important_items.xml')/important/item"/>
	<!-- Author lookup -->
	<xsl:variable name="LOOKUP" select="document('Author.xml')/lookup/row"/>
	<xsl:output method="xml" indent="yes" omit-xml-declaration="yes"/>
	<xsl:strip-space elements="*"/>
	<!-- table 루트 처리 -->
	<xsl:template match="/table">
		<table>
			<xsl:for-each-group select="row" group-by="
                    if (normalize-space(item) != '')
                    then normalize-space(item)
                    else normalize-space(
                        preceding-sibling::row[normalize-space(item) != ''][1]/item
                    )
                ">
				<!-- 대표 row -->
				<xsl:variable name="first" select="current-group()[1]"/>
				<!-- 대표 row 기준 cell 개수 -->
				<xsl:variable name="cell-count" select="count($first/cell)"/>
				<!-- 편집자 찾기 -->
				<xsl:variable name="matchedName" select="
                        $LOOKUP[
                            normalize-space(ItemNo)
                            =
                            normalize-space($first/item)
                        ]/Name
                    "/>
				<row>
					<!-- 그룹 안에 # 있으면 노란색 -->
					<xsl:if test="
                        some $c in current-group()/cell
                        satisfies contains(normalize-space($c), '#')
                    ">
						<xsl:attribute name="color">yellow</xsl:attribute>
					</xsl:if>
					<!-- item / desc -->
					<xsl:copy-of select="$first/item"/>
					<xsl:copy-of select="$first/desc"/>
					<!-- 가변 cell 병합 -->
					<xsl:for-each select="1 to $cell-count">
						<cell>
							<xsl:value-of select="
                                    normalize-space(string-join(for $r in current-group() return normalize-space($r/cell[position() = current()]), ' '))"/>
						</cell>
					</xsl:for-each>
					<!-- 중요항목 -->
					<xsl:choose>
						<xsl:when test="
                            some $x in $important_items
                            satisfies normalize-space($x)
                            =
                            normalize-space($first/item)
                        ">
							<cell important="yes">V</cell>
						</xsl:when>
						<xsl:otherwise>
							<cell/>
						</xsl:otherwise>
					</xsl:choose>
					<!-- 편집자 -->
					<cell>
						<xsl:value-of select="$matchedName"/>
					</cell>
				</row>
			</xsl:for-each-group>
		</table>
	</xsl:template>
</xsl:stylesheet>