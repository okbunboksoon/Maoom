<xsl:stylesheet 
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xmlns:String="java:java.lang.String"
	xmlns:Integer="java:java.lang.Integer"
    xmlns:md="java:java.security.MessageDigest"
    exclude-result-prefixes="xs String Integer md"
    version="2.0">

	<xsl:output method="xml" indent="no" omit-xml-declaration="yes"/>
	<xsl:strip-space elements="*" />

	<!-- 260406 US EG 분기점 -->
	<xsl:template match="map">
	    <pairs>
	        <!-- 지역 구분 -->
	        <xsl:attribute name="region">
	            <xsl:variable name="title" select="upper-case(normalize-space(title))"/>
	            <xsl:choose>
	                <!-- 1순위: US 포함 → US -->
	                <xsl:when test="contains($title, 'US')">
	                    <xsl:text>US</xsl:text>
	                </xsl:when>
	                <xsl:when test="contains($title, 'EXCLUDE')">
	                    <xsl:text>exclude</xsl:text>
	                </xsl:when>
	                <!-- 나머지 → EU -->
	                <xsl:otherwise>
	                    <xsl:text>EU</xsl:text>
	                </xsl:otherwise>
	            </xsl:choose>
	        </xsl:attribute>

	        <!-- 기존 로직 유지 -->
	        <xsl:for-each select="//pair">
	            <xsl:apply-templates select="."/>
	        </xsl:for-each>

	        <xsl:text>&#xA;</xsl:text>
	    </pairs>
	</xsl:template>

	<xsl:template match="pair">
		<xsl:variable name="hash">
			<xsl:call-template name="hash">
				<xsl:with-param name="old" select="old"/>
			</xsl:call-template>
		</xsl:variable>
		<xsl:text>&#xA;&#x9;</xsl:text>
		<xsl:copy>
			<xsl:attribute name="hash">
				<xsl:value-of select="$hash"/>
			</xsl:attribute>
			<xsl:apply-templates/>
			<xsl:text>&#xA;&#x9;</xsl:text>
		</xsl:copy>
	</xsl:template>

	<xsl:template match="pair[ancestor::pair] | pair[descendant::note] | pair[descendant::ul] | pair[old[count(p) &gt; 1]]">
	</xsl:template>

	<xsl:template match="image | xref">
		<xsl:copy/>
	</xsl:template>

	<xsl:template match="uicontrol | tm | term">
		<xsl:copy>
			<xsl:apply-templates/>
		</xsl:copy>
	</xsl:template>

	<xsl:template match="old | new">
		<xsl:text>&#xA;&#x9;&#x9;</xsl:text>
		<xsl:copy>
			<xsl:attribute name="file">
				<xsl:value-of select="ancestor::topicref[1]/@href"/>
			</xsl:attribute>
			<xsl:apply-templates select="@* | node()"/>
		</xsl:copy>
	</xsl:template>

	<xsl:template match="text()" priority="20">
		<xsl:value-of select="if ( ancestor::old ) then replace(., '(\d+\s*)–(\s*\d+)', '$1 ~ $2') else ."/>
	</xsl:template>

	<xsl:template match="@* | node()">
		<xsl:copy>
			<xsl:apply-templates select="@* | node()"/>
		</xsl:copy>
	</xsl:template>

	<xsl:template name="hash">
		<xsl:param name="old"/>
		<xsl:variable name="Str">
			<xsl:call-template name="seed">
				<xsl:with-param name="nodes" select="$old/node()"/>
			</xsl:call-template>
		</xsl:variable>
		<xsl:variable name="Inst" select="md:getInstance('SHA-256')" />
		<xsl:variable name="IntSeq" select="md:digest($Inst, String:getBytes($Str, 'utf-8'))" />
		<xsl:variable name="HexStr">
			<xsl:for-each select="$IntSeq">
				<xsl:variable name="hexHash" select="upper-case(Integer:toHexString(.))"/>
				<xsl:value-of select="if ( . &lt; 256 ) then substring(concat('0', $hexHash), string-length($hexHash)) else $hexHash"/>
			</xsl:for-each>
		</xsl:variable>
		<xsl:value-of select="$HexStr"/>
	</xsl:template>

	<xsl:template name="seed">
		<xsl:param name="nodes"/>
		<xsl:for-each select="$nodes">
			<xsl:value-of select="name()"/>
			<xsl:value-of select="replace(., '(\d+\s*)–(\s*\d+)', '$1 ~ $2')"/>
		</xsl:for-each>
	</xsl:template>

</xsl:stylesheet>