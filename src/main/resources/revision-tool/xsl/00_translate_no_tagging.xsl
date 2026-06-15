<xsl:stylesheet version="2.0" 
		xmlns:xsl="http://www.w3.org/1999/XSL/Transform" 
		xmlns:xs="http://www.w3.org/2001/XMLSchema" 
		xmlns:f="urn:regex-func" exclude-result-prefixes="xs f">
	
	<xsl:output method="xml" indent="no"/>
	<xsl:strip-space elements="*"/>
	<xsl:preserve-space elements=""/>
	<xsl:variable name="terms-doc" select="if (doc-available('translate_no.xml')) then document('translate_no.xml') else ()"/>	
	
	<xsl:template match="p/text()[normalize-space() = '']" priority="5"/>
	
	<!-- 정규식 메타문자 이스케이프 함수 -->
	<xsl:function name="f:quote-regex" as="xs:string">
		<xsl:param name="s" as="xs:string"/>
		
		<!-- 역슬래시 먼저 -->
		<xsl:variable name="step1" select="replace($s, '\\', '\\\\')"/>
		
		<!-- . ^ $ | ? * + ( ) [ ] { } 이스케이프 -->
		<xsl:sequence select="replace($step1, '([.\^$\|\?\*\+\(\)\[\]\{\}])', '\\$1')"/>
	</xsl:function>
	
	<!-- 긴 용어 먼저 정렬해서 alternation 구성 -->
	<xsl:variable name="terms-alt" as="xs:string">
		<xsl:variable name="sorted" as="xs:string*">
			<xsl:for-each select="$terms-doc/terms/term[normalize-space(.)!='']">
				<xsl:sort select="string-length(.)" data-type="number" order="descending"/>
				<xsl:sequence select="string(.)"/>
			</xsl:for-each>
		</xsl:variable>
		<xsl:sequence select="string-join(for $t in $sorted return f:quote-regex($t), '|')"/>
	</xsl:variable>
	
	<!-- 경계 + 전체 정규식 (기존 컨셉 유지) -->
	<xsl:variable name="boundary-before" as="xs:string" select="'(^|\s+|\p{Ps})'"/>
	<xsl:variable name="boundary-after" as="xs:string" select="'(\s+|\p{P}|\p{Pe}|$)'"/>
	<xsl:variable name="terms-regex" as="xs:string" select="if (normalize-space($terms-alt)!='')
            then concat($boundary-before, '(', $terms-alt, ')', $boundary-after)
            else ''"/>
	
	<!-- map 그대로 통과 -->
	<xsl:template match="map">
		<map>
			<xsl:apply-templates select="@* | node()"/>
			<xsl:text>&#x0A;</xsl:text>
		</map>
	</xsl:template>
	
	<!-- 기본: 모든 노드를 그대로 복사 -->
	<xsl:template match="@* | node()">
		<xsl:copy>
			<xsl:apply-templates select="@* | node()"/>
		</xsl:copy>
	</xsl:template>
	
	<!-- <uicontrol>DOOR</uicontrol> → DOOR만 TERM 처리 -->
	<xsl:template match="uicontrol[normalize-space(.)='DOOR']" priority="20">
	    <term translate="no">
	        <xsl:value-of select="normalize-space(.)"/>
	    </term>
	</xsl:template>
	
	<!-- DOOR mode → DOOR만 TERM 처리 -->
	<xsl:template match="text()[contains(., 'DOOR mode')]" priority="10">
	    <xsl:analyze-string select="." regex="(DOOR)(\s+mode)">
	        <xsl:matching-substring>
	            <term translate="no">
	                <xsl:value-of select="regex-group(1)"/>
	            </term>
	            <xsl:value-of select="regex-group(2)"/>
	        </xsl:matching-substring>

	        <xsl:non-matching-substring>
	            <xsl:value-of select="."/>
	        </xsl:non-matching-substring>
	    </xsl:analyze-string>
	</xsl:template>
	
	<!-- 콜론(:) 앞 한 글자만 TERM 처리 -->
	<xsl:template match="text()[matches(., '(^|\s)[A-Z]\s*:')]" priority="5">
	    <xsl:analyze-string 
	        select="." 
	        regex="(^|\s)([A-Z])\s*:">
	        
	        <xsl:matching-substring>
	            <!-- 앞 공백 -->
	            <xsl:value-of select="regex-group(1)"/>
	            <term translate="no">
	                <xsl:value-of select="regex-group(2)"/>
	            </term>

	            <!-- :는 태깅하지 않고 그대로 -->
	            <xsl:text>:</xsl:text>
	        </xsl:matching-substring>
	        <xsl:non-matching-substring>
	            <xsl:value-of select="."/>
	        </xsl:non-matching-substring>
	    </xsl:analyze-string>
	</xsl:template>

	<!-- 3. 텍스트: 용어 자동 태깅 (translate="no") -->
	<!-- or ancestor::menucascade -->
		<xsl:template match="text()[not(
		        ancestor::p[@translate='no'] 
		        or ancestor::p[parent::entry[1] and ancestor::sectiondiv and ancestor::concept[title='Fuse/relay panel description']] 
		        or ancestor::p[parent::entry and ancestor::reference[title='Bulb wattage ']] 
		        or ancestor::concept[title='Abbreviation'] 
		        or ancestor::term[@translate='no'] 
		        or ancestor::menucascade
		        or ancestor::uicontrol
		        or ancestor::title 
		        or ancestor::indexterm
		)]" priority="1">
		<xsl:choose>
			<xsl:when test="$terms-regex != ''">
				<xsl:analyze-string select="." regex="{$terms-regex}">
					<xsl:matching-substring>
						<xsl:value-of select="regex-group(1)"/>
						<term translate="no">
							<xsl:value-of select="regex-group(2)"/>
						</term>
						<xsl:value-of select="regex-group(3)"/>
					</xsl:matching-substring>
					<xsl:non-matching-substring>
						<xsl:value-of select="."/>
					</xsl:non-matching-substring>
				</xsl:analyze-string>
			</xsl:when>
			<xsl:otherwise>
				<xsl:value-of select="."/>
			</xsl:otherwise>
		</xsl:choose>
	</xsl:template>

	<!-- uicontrol이 용어 목록과 정확히 일치하면 term으로 변환 -->
	<!--
	<xsl:template match="uicontrol[normalize-space(.) = $terms-doc/terms/term]" priority="20">
	    <term translate="no">
	        <xsl:value-of select="normalize-space(.)"/>
	    </term>
	</xsl:template>-->
</xsl:stylesheet>
