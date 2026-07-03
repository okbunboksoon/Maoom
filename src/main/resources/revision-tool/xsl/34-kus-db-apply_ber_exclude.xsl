<xsl:stylesheet 
	xmlns:xsl="http://www.w3.org/1999/XSL/Transform" 
	xmlns:xs="http://www.w3.org/2001/XMLSchema" 
	xmlns:String="java:java.lang.String" 
	xmlns:Integer="java:java.lang.Integer" 
	xmlns:md="java:java.security.MessageDigest" 
	exclude-result-prefixes="xs String Integer md" 
	version="2.0">

	<!-- ===== 파라미터 / DB 로드 ===== -->
	<xsl:param name="flag" select="'off'"/>
	<xsl:variable name="db" select="document('asis-tobe_exclude.xml')"/>

	<!-- ===== key (성능 개선) ===== -->
	<xsl:key name="kPair" match="pair" use="@hash"/>

	<!-- ===== 출력 옵션 ===== -->
	<xsl:output method="xml" indent="no" omit-xml-declaration="yes"/>
	<xsl:strip-space elements="*"/>

	<!-- ===== 기본 아이덴티티 ===== -->
	<xsl:template match="@* | node()">
		<xsl:copy>
			<xsl:apply-templates select="@* | node()"/>
		</xsl:copy>
	</xsl:template>

	<!-- ===== 핵심: hash 비교 후 exclude 처리 ===== -->
	<xsl:template match="p | cmd | title | shortdesc">
		<xsl:variable name="current" select="."/>
		<!-- 항상 현재 노드로부터 해시 계산 (매칭 키로만 사용) -->
		<xsl:variable name="hash" as="xs:string">
			<xsl:call-template name="hash">
				<xsl:with-param name="current" select="$current"/>
			</xsl:call-template>
		</xsl:variable>
		<xsl:variable name="pair" select="key('kPair', $hash, $db)"/>
		<xsl:copy>
			<xsl:copy-of select="@*"/>
			<xsl:if test="$pair">
				<xsl:attribute name="outputclass">
					<xsl:value-of select="
						normalize-space(
							concat(@outputclass, ' exclude')
						)
					"/>
				</xsl:attribute>
			</xsl:if>
			<!-- DB에 없으면 원문 그대로(해시 PI 없음) -->
			<xsl:apply-templates select="node()"/>
		</xsl:copy>
	</xsl:template>

	<!-- ===== 해시 계산(현재 노드 기반) ===== -->
	<xsl:template name="hash">
		<xsl:param name="current"/>
		<xsl:variable name="Str">
			<xsl:call-template name="seed">
				<xsl:with-param name="nodes" select="$current/node()"/>
			</xsl:call-template>
		</xsl:variable>
		<xsl:variable name="Inst" select="md:getInstance('SHA-256')"/>
		<xsl:variable name="IntSeq" select="md:digest($Inst, String:getBytes($Str, 'utf-8'))"/>
		<xsl:variable name="HexStr">
			<xsl:for-each select="$IntSeq">
				<xsl:variable name="hexHash" select="upper-case(Integer:toHexString(.))"/>
				<xsl:value-of select="if (. &lt; 256) then substring(concat('0', $hexHash), string-length($hexHash)) else $hexHash"/>
			</xsl:for-each>
		</xsl:variable>
		<xsl:value-of select="$HexStr"/>
	</xsl:template>
	<!-- ===== 해시 시드 만들기(텍스트 정규화 등) ===== -->
	<xsl:template name="seed">
		<xsl:param name="nodes"/>
		<xsl:for-each select="$nodes">
			<xsl:value-of select="name()"/>
			<!-- en-dash 범위 표기를 통일 -->
			<xsl:value-of select="replace(., '(\d+\s*)–(\s*\d+)', '$1 ~ $2')"/>
		</xsl:for-each>
	</xsl:template>

</xsl:stylesheet>