<xsl:stylesheet version="2.0" 
	xmlns:xsl="http://www.w3.org/1999/XSL/Transform" 
	xmlns:xs="http://www.w3.org/2001/XMLSchema" 
	exclude-result-prefixes="xs">
	
	<xsl:output method="xml" indent="no"/>
	
	<!-- 공백 정리 -->
	<xsl:strip-space elements="*"/>
	<xsl:preserve-space elements=""/>
	
	<!-- 기본 copy -->
	<xsl:template match="@* | node()">
		<xsl:copy>
			<xsl:apply-templates select="@* | node()"/>
		</xsl:copy>
	</xsl:template>
	
	<!-- 0. 전역 텍스트 정제 : 「 」만 제거 -->
	<xsl:template match="text()" priority="5">
		<xsl:value-of select="translate(., '「」', '')"/>
	</xsl:template>

	<!-- 1. p 안에서 term + → 메뉴 (b 메뉴가 없을 때만) -->
	<xsl:template match="p[term and contains(., '→') and not(b[contains(., '→')])]" priority="50">
	  <p>
	    <xsl:for-each-group select="node()"
	      group-adjacent="if (self::term or (self::text() and contains(., '→')))
	                      then 'menu'
	                      else generate-id()">

	      <xsl:choose>
	        <xsl:when test="count(current-group()[self::term]) &gt; 1">
	          <xsl:text> </xsl:text>
	          <menucascade>
	            <xsl:for-each select="current-group()[self::term]">
	              <uicontrol>
	                <xsl:value-of select="normalize-space(.)"/>
	              </uicontrol>
	            </xsl:for-each>
	          </menucascade>
	          <xsl:text> </xsl:text>
	        </xsl:when>

	        <xsl:otherwise>
	          <!-- copy-of 대신 apply-templates가 더 안전 -->
	          <xsl:apply-templates select="current-group()"/>
	        </xsl:otherwise>
	      </xsl:choose>

	    </xsl:for-each-group>
	  </p>
	</xsl:template>

	<!-- 2. b 안에 메뉴 경로가 있는 경우 -->
	<xsl:template match="b[contains(., '→')]" priority="40">
		<xsl:variable name="clean" select="translate(normalize-space(.), '()[]「」', '')"/>
		<menucascade>
			<xsl:for-each select="tokenize($clean, '→')">
				<uicontrol>
					<xsl:value-of select="normalize-space(.)"/>
				</uicontrol>
			</xsl:for-each>
		</menucascade>
	</xsl:template>

	<!-- 3. uicontrol 안에 메뉴 경로가 있는 경우 -->
	<xsl:template match="uicontrol[contains(., '→')]" priority="40">
		<xsl:variable name="clean" select="translate(normalize-space(.), '()[]「」', '')"/>
		<menucascade>
			<xsl:for-each select="tokenize($clean, '→')">
				<uicontrol>
					<xsl:value-of select="normalize-space(.)"/>
				</uicontrol>
			</xsl:for-each>
		</menucascade>
	</xsl:template>
	
	<!-- 4. 순수 텍스트 메뉴 -->
	<xsl:template match="text()[contains(., '→')
	              and not(parent::uicontrol)
	              and not(parent::b)
	              and not(parent::term)]"
	              priority="20">
		<xsl:variable name="clean" select="translate(., '[]「」', '')"/>
		<xsl:analyze-string
			select="$clean"
			regex="(\([^()]+→[^()]+\))|('?[^']+→[^']+'?)">
			<xsl:matching-substring>
				<xsl:choose>
					<!--  ( ) 있는 경우 -->
					<xsl:when test="starts-with(regex-group(0), '(')">
						<xsl:value-of select="'('"/>
						<menucascade>
							<xsl:for-each
								select="tokenize(substring(regex-group(0), 2, string-length(regex-group(0)) - 2), '→')">
								<uicontrol>
									<xsl:value-of select="normalize-space(.)"/>
								</uicontrol>
							</xsl:for-each>
						</menucascade>
						<xsl:value-of select="')'"/>
					</xsl:when>
					<!-- 괄호 없는 경우 → 기존 로직 -->
					<xsl:otherwise>
						<menucascade>
							<xsl:for-each select="tokenize(translate(regex-group(0), '''', ''), '→')">
								<uicontrol>
									<xsl:value-of select="normalize-space(.)"/>
								</uicontrol>
							</xsl:for-each>
						</menucascade>
					</xsl:otherwise>
				</xsl:choose>
			</xsl:matching-substring>
			<xsl:non-matching-substring>
				<xsl:value-of select="."/>
			</xsl:non-matching-substring>
		</xsl:analyze-string>
	</xsl:template>
</xsl:stylesheet>
