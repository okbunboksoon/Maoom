<xsl:stylesheet version="2.0" 
		xmlns:xsl="http://www.w3.org/1999/XSL/Transform" 
		xmlns:xs="http://www.w3.org/2001/XMLSchema" 
		xmlns:f="urn:regex-func" exclude-result-prefixes="xs f">
	
	<xsl:output method="xml" indent="no"/>
	
	<xsl:strip-space elements="*"/>
	<xsl:preserve-space elements=""/>
	<xsl:template match="text()[normalize-space() = '']"/>
	
	<xsl:variable name="terms-doc" select="if (doc-available('1020-translate_no.xml')) then document('1020-translate_no.xml') else ()"/>	
	
	<!-- 외부 사전 단어 로드 -->
	<xsl:variable name="terms" select="$terms-doc/terms/term/normalize-space(.)" />

    <!-- 기본: 모든 노드를 그대로 복사 -->
    <xsl:template match="@* | node()">
        <xsl:copy>
            <xsl:apply-templates select="@* | node()"/>
        </xsl:copy>
    </xsl:template>
    
	<!-- 1. uicontrol 속성 제거 -->
	<xsl:template match="uicontrol/@translate | uicontrol/@outputclass"  priority="10"/>
	
	<!-- 2. <b> 안에 든 텍스트가 외부 사전 단어이면 태그 제거 -->
	<xsl:template match="b[normalize-space(.) = $terms or matches(normalize-space(.), '^[A-LR]$')]"  priority="5">
	    <xsl:apply-templates select="node()"/>
	</xsl:template>

	<!-- 3. <uicontrol> 안의 텍스트가 외부 사전 단어이면 태그 제거 / 메뉴케스케이드 안이면 제외 -->
	<xsl:template match="uicontrol[normalize-space(.) = $terms and not(ancestor::menucascade)]"  priority="5">
	    <xsl:apply-templates select="node()"/>
	</xsl:template>

	<!-- 4. 그 외의 b 태그는 그대로 유지 -->
	<xsl:template match="b | uicontrol"  priority="1">
	    <xsl:copy>
	        <xsl:apply-templates select="@* | node()"/>
	    </xsl:copy>
	</xsl:template>

	<!-- 5. 기존 태그 제거 -->
	<xsl:template match="term | i"  priority="1">
		<xsl:value-of select="normalize-space(.)"/>
	</xsl:template>

	<!-- 6.  퓨즈테이블 속성 추가 -->
	<xsl:template match="concept[title='Fuse/relay panel description']//sectiondiv//table//tbody//row/entry[1]/p[not(@translate)]">
		<xsl:copy>
			<xsl:attribute name="translate">no</xsl:attribute>
			<xsl:apply-templates select="@* | node()"/>
		</xsl:copy>
	</xsl:template>
	
	<!-- 7. tbody 안의 entry p b 가 알파벳 리스트면 term으로 변환 -->
	<xsl:template match="tbody//entry/p[b[matches(normalize-space(.), '^([A-Z]\s*,\s*)*[A-Z]$')]]"  priority="50">
	    <p>
	        <term translate="no">
	            <xsl:value-of select="normalize-space(b)"/>
	        </term>
	    </p>
	</xsl:template>
	
	<!-- 8. I R 들어가져있는 P 속성 추가 -->
	<!-- <xsl:template match="tbody//entry/p[matches(normalize-space(.), '^(I|R|A|B|C|D|E|F|G|H|K|L)$')]"> 기존 테이블 추가 전--> 
	<!-- 테이블 제거하고 넣기
		<xsl:template match="tbody//entry/p[matches(normalize-space(.), '^(I|R|A|B|C|D|E|F|G|H|K|L)$') and 
							not( ancestor::table[1]/preceding-sibling::title[1] [matches(normalize-space(.), '^(타이어 속도 등급|Tire speed rating|Tire size designation)$')])]">
	-->
	<xsl:template match="tbody//entry/p[matches(normalize-space(.), '^(I|R|A|B|C|D|E|F|G|H|K|L|S|T|U|V|W|Y|ZR)$') and 
							not(ancestor::*[self::concept or self::task or self::reference][1]/title[matches(normalize-space(.), '^타이어 에너지 소비 효율 등급$')])]">

	    	<p>
	    		<term translate="no">
	        		<xsl:apply-templates select="@* | node()"/>
	        	</term>
	    </p>
	</xsl:template>
	
	<!-- 9. P안에 A: / A: 문장 일경우  -->
	<xsl:template match="p[matches(normalize-space(.), '^[A-LR]:.*')]">
	    <p>
	        <xsl:apply-templates select="@*"/>
	        <!-- 1) 글자(label)만 추출 -->
	        <xsl:variable name="label" select="replace(normalize-space(.), '^([A-LR]).*$', '$1')"/>
	        <!-- 2) 뒤에 콜론 있는지 확인 -->
	        <xsl:variable name="hasColon" select="matches(normalize-space(.), '^[A-LR]:')"/>
	        <!-- 3) 라벨 출력: <term translate='no'>A</term> -->
	        <term translate="no">
	            <xsl:value-of select="$label"/>
	        </term>
	        <!-- 4) 콜론 있는 경우만 ':' 출력 -->
	        <xsl:if test="$hasColon">
	            <xsl:text>:</xsl:text>
	        </xsl:if>
	        <!-- 5) 라벨 뒤 문장이 있으면 출력 -->
	        <xsl:variable name="rest" select="replace(normalize-space(.), '^[A-LR]:?\s*(.*)$', '$1')"/>
	        <xsl:if test="$rest != ''">
	            <xsl:text> </xsl:text>
	            <xsl:value-of select="$rest"/>
	        </xsl:if>
	    </p>
	</xsl:template>
	
	<!-- 3-1. 그 외의 uicontrol 태그는 그대로 유지 -->
	<!--
	<xsl:template match="uicontrol">
	    <xsl:copy>
	        <xsl:apply-templates select="@* | node()"/>
	    </xsl:copy>
	</xsl:template>
	-->

	<!--
	<xsl:template match="b[matches(normalize-space(.), '^[A-LR]$')]">
	    <xsl:apply-templates select="node()"/>
	</xsl:template>
	-->
	<!-- 1. 기존 태그 제거 -->
	<!--	
	<xsl:template match="term | i"  priority="10">
		<xsl:value-of select="normalize-space(.)"/>
	</xsl:template>
	-->

	<!-- 2. menucascade 제외 uicontrol ->term 수정 -->
	<!--
    <xsl:template match="uicontrol[not(ancestor::menucascade)]">
        <term>
            <xsl:apply-templates select="@* | node()"/>
        </term>
    </xsl:template>
    -->
    	
	<!-- 2.  컨텐츠가 A ~ R일 경우 term유지 -->
	<!--
	<xsl:template match="term[matches(normalize-space(.), '^[A-LR]$')]" priority="20">
	    <xsl:copy>
	        <xsl:apply-templates select="@* | node()"/>
	    </xsl:copy>
	</xsl:template>
	-->
</xsl:stylesheet>