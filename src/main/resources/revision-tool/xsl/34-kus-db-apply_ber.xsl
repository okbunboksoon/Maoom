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
	<xsl:variable name="mapTitle" select="normalize-space(string((/map/title)[1]))"/>
	<xsl:variable name="isNA" select="contains(upper-case($mapTitle), 'US') or contains(upper-case($mapTitle), 'CA') or contains(upper-case($mapTitle), 'MX')"/>
	<xsl:variable name="dbPath" select="if ($isNA) then 'asis-tobe_us.xml' else 'asis-tobe_eu.xml'"/>
	<xsl:variable name="db" select="document($dbPath)"/>
	
	<!-- ===== 출력 옵션 ===== -->
	<xsl:output method="xml" indent="no" omit-xml-declaration="yes"/>
	<xsl:strip-space elements="*"/>
	
	<!-- ===== 기본 아이덴티티 ===== -->
	<xsl:template match="@* | node()">
		<xsl:copy>
			<xsl:apply-templates select="@* | node()"/>
		</xsl:copy>
	</xsl:template>
	
	<!-- ===== 본문 치환: 텍스트는 DB/new, 인라인은 원문 재사용 ===== -->
	<!--<xsl:template match="p | cmd | title | shortdesc">-->
	<xsl:template match="p[not(@outputclass='exclude')] 
                   | cmd[not(@outputclass='exclude')] 
                   | title[not(@outputclass='exclude')] 
                   | shortdesc[not(@outputclass='exclude')]">
                   
		<xsl:variable name="current" select="."/>
		<!-- 항상 현재 노드로부터 해시 계산 (매칭 키로만 사용) -->
		<xsl:variable name="hash">
			<xsl:call-template name="hash">
				<xsl:with-param name="current" select="$current"/>
			</xsl:call-template>
		</xsl:variable>
		<xsl:variable name="pair" select="$db/pairs/pair[@hash=$hash]"/>
		<xsl:copy>
			<xsl:copy-of select="@*"/>
			<xsl:choose>
				<!-- DB에 <new>가 있으면 치환 + 해시 PI 출력 -->
				<xsl:when test="$pair/new">
					<xsl:if test="$flag = 'on'">
						<xsl:attribute name="status">changed</xsl:attribute>
					</xsl:if>
					<!-- 해시 PI는 '매칭된 경우에만' 출력 -->
					<xsl:processing-instruction name="hash">
						<xsl:value-of select="$hash"/>
					</xsl:processing-instruction>
					<!-- DB/new 기반 병합 출력 -->
					<xsl:for-each select="$pair/new[last()]/node()">
						<xsl:apply-templates select="." mode="merge">
							<xsl:with-param name="current" select="$current"/>
						</xsl:apply-templates>
					</xsl:for-each>
				</xsl:when>
				<!-- DB에 없으면 원문 그대로(해시 PI 없음) -->
				<xsl:otherwise>
					<xsl:apply-templates select="node()"/>
				</xsl:otherwise>
				<!-- DB에 없어도 hash 기록 -->
				<!--
				<xsl:otherwise>
			<xsl:processing-instruction name="hash">
				<xsl:value-of select="$hash"/>
			</xsl:processing-instruction>
			<xsl:apply-templates select="node()"/>
		</xsl:otherwise>
		-->
			</xsl:choose>
		</xsl:copy>
	</xsl:template>
	<!-- ===== 병합 모드: 텍스트/요소 공통 처리 ===== -->
	<!-- 텍스트는 DB/new 그대로 -->
	<xsl:template match="text()" mode="merge">
		<xsl:value-of select="."/>
	</xsl:template>
	<!-- 요소(인라인 포함)는 '같은 이름의 N번째'를 원문에서 재사용 -->
	<xsl:template match="*" mode="merge">
		<xsl:param name="current"/>
		<!-- DB/new 안에서의 타입/순번 -->
		<xsl:variable name="type" select="name(.)"/>
		<xsl:variable name="n" select="count(preceding-sibling::*[name()=$type]) + 1"/>
		<!-- 원문에서도 같은 이름의 n번째 요소 -->
		<xsl:variable name="orig" select="$current/*[name()=$type][$n]"/>
		<xsl:choose>
			<xsl:when test="$orig">
				<!-- 원문 요소 복사(속성/자식 보존) -->
				<xsl:copy-of select="$orig"/>
			</xsl:when>
			<xsl:otherwise>
				<!-- 대응 없으면 DB/new 요소 그대로 -->
				<xsl:copy-of select="."/>
			</xsl:otherwise>
		</xsl:choose>
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
