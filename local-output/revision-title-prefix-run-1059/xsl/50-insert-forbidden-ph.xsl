<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="2.0"
  xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
  xmlns:xs="http://www.w3.org/2001/XMLSchema"
  exclude-result-prefixes="xs">

  <!-- ===== 0) 환경 변수 ===== -->
	<xsl:variable name="mapTitle"
              select="normalize-space(/map/title)"/>

	<!-- 차종(EV/ICE) -->
	<xsl:variable name="cartype"
	  select="
	    if (
	         contains($mapTitle, '_EV_')
	         or contains($mapTitle, '-EV-')
	         or contains($mapTitle, '-EV_')
	         or contains($mapTitle, '_EV-')
	         or contains($mapTitle, '-EV')
	         or contains($mapTitle, 'EV-')
	         and not(contains($mapTitle, 'HEV'))
         	    and not(contains($mapTitle, 'PHEV'))
	       )
	      then 'EV'
	      else 'ICE'
	  "/>

	<!-- 언어(KO/US/CA/EG) -->
	<xsl:variable name="lang"
	  select="
	    if (
	         contains($mapTitle, '_Ko_')
	         or contains($mapTitle, 'KO_')
	         or contains($mapTitle, 'KO-')
	         or contains($mapTitle, 'Ko-')
	         or contains($mapTitle, 'ko_KR')
	         or contains($mapTitle, '-ko_KR-')
	       )
	       then 'KO'

	    else if (
	         contains($mapTitle, '_US_')
	         or contains($mapTitle, 'en_US')
	         or contains($mapTitle, '-en_US-')
	       )
	       then 'US'

	    else if (
	         contains($mapTitle, '_CA_')
	         or contains($mapTitle, 'en_CA')
	         or contains($mapTitle, '-en_CA-')
	       )
	       then 'CA'

	    else 'EG'
	  "/>

	<!-- 친환경 여부(HEV/PHEV) -->
	<xsl:variable name="hybrid"
	  select="
	    if (contains($mapTitle, '_HEV_')  or contains($mapTitle, '-HEV-'))
	      then 'HEV'
	    else if (contains($mapTitle, '_PHEV_') or contains($mapTitle, '-PHEV-'))
	      then 'PHEV'
	    else 'no'
	  "/>

	<!-- 금칙어 파일명 -->
	<xsl:variable name="forbiddenFile"
	  select="concat($cartype, '_forbidden_', $lang, '.xml')"/>

  <!-- ===== 1) 사전/예외 로드 + 안정화 ===== -->
  <xsl:variable name="dict"
    select="if (doc-available($forbiddenFile)) then document($forbiddenFile)/list else ()"/>

  <xsl:variable name="exception_sentences" select="$dict/exception/item/text()"/>
  <xsl:variable name="forbidden_except"     select="$dict/forbidden/item[@except]"/>

  <!-- 금칙어 기본 정리 -->
  <xsl:variable name="raw-items"
    select="for $t in $dict/forbidden/item/text() return normalize-space($t)"/>
  <xsl:variable name="items-nonempty" select="$raw-items[. ne '']"/>

  <!-- 1) 메타문자 이스케이프: \  및  . ^ $ | ? * + ( ) [ ] { } -->
  <xsl:variable name="items-escaped" as="xs:string*">
    <xsl:for-each select="$items-nonempty">
      <xsl:variable name="t1" select="replace(., '\\', '\\\\')"/>
      <!-- 그룹 앞에 역슬래시 붙이기: '\\$1' (백슬래시 + 매칭문자) -->
      <xsl:variable name="t2" select="replace($t1, '([.\^\$\|\?\*\+\(\)\[\]\{\}])', '\\$1')"/>
      <xsl:sequence select="$t2"/>
    </xsl:for-each>
  </xsl:variable>

  <xsl:variable name="NBSP" select="codepoints-to-string(160)"/>
  <xsl:variable name="SPACE_ALT" select="concat('(\s|', $NBSP, ')')"/>

  <!-- 항목 내부의 공백을 패턴으로 치환: replace(치환) 금지 → tokenize+join -->
  <xsl:variable name="items-space-aware" as="xs:string*">
    <xsl:for-each select="$items-escaped">
      <xsl:variable name="parts" select="tokenize(., ' ')"/>
      <xsl:sequence select="string-join($parts, $SPACE_ALT)"/>
    </xsl:for-each>
  </xsl:variable>

  <!-- 포함관계 방지: 길이 내림차순 OR -->
  <xsl:variable name="items-sorted-or">
    <xsl:for-each select="$items-space-aware">
      <xsl:sort select="string-length(.)" data-type="number" order="descending"/>
      <xsl:value-of select="."/>
      <xsl:if test="position() != last()">|</xsl:if>
    </xsl:for-each>
  </xsl:variable>

  <!-- 한국어 조사(선택) : XSD 정규식 → 일반 그룹 () 사용 -->
	<xsl:variable name="kr-particle"
	select="'(을|를|이|가|은|는|의|에|에서|으로|로|와|과|도|만|까지|부터|이나|나|라도|이라도|에게|에게서|한테|께|께서|으로부터|로부터|로서|로써|처럼|대로|조차|조차도|밖에|마다|뿐|이라|라|이며|이든|든지|든지간에|이라든지|라든지|이란|란)?'"/>
	 
	 <!-- select="'([\p{IsHangulSyllables}]{1,3})?'"/> -->
	<!-- \p{IsHangulSyllables} -->
	<!-- 한글캐릭터 -> 으로|로|을|를|은|는|이|가|의|에|에서|에게서|에게|께서|께|와|과|도|만|보다|처럼|부터|까지|마다|뿐|하고 -->
  <!-- 유니코드 경계 + 조사 허용 -->
  <xsl:variable name="forbidden_words_regex"
    select="if (normalize-space($items-sorted-or) ne '')
            then concat('(^|[^\p{L}\p{N}])(', $items-sorted-or, ')(', $kr-particle, ')?([^\p{L}\p{N}]|$)') else ''"/>

  <!-- ===== 2) 기본 복사 ===== -->
  <xsl:template match="@* | node()">
    <xsl:copy>
      <xsl:apply-templates select="@* | node()"/>
    </xsl:copy>
  </xsl:template>

<xsl:template match="text()[matches(., 'WKIA_')]" priority="30">
  <xsl:analyze-string select="." regex="WKIA_[A-Z0-9_]+">
    <xsl:matching-substring>
      <ph outputclass="forbidden">
        <xsl:value-of select="."/>
      </ph>
    </xsl:matching-substring>
    <xsl:non-matching-substring>
      <xsl:value-of select="."/>
    </xsl:non-matching-substring>
  </xsl:analyze-string>
</xsl:template>

  <!-- ===== 3) 텍스트 검사 ===== -->
  <xsl:template match="text()" priority="20">
    <xsl:variable name="containingText" select="."/>

    <xsl:choose>
      <!-- 문장 전체 예외: 정확 일치 시 통과 -->
      <xsl:when test="$containingText = $exception_sentences">
        <xsl:value-of select="$containingText"/>
      </xsl:when>

      <!-- 사전 없음/빈: 그대로 통과 -->
      <xsl:when test="not($forbidden_words_regex)">
        <xsl:value-of select="$containingText"/>
      </xsl:when>

      <!-- 분석: (1)좌경계 (2)히트단어 (3)선택 조사 (4)우경계 -->
      <xsl:otherwise>
        <xsl:analyze-string select="$containingText" regex="{$forbidden_words_regex}">
          <xsl:matching-substring>
            <xsl:variable name="hit"      select="regex-group(2)"/>
            <xsl:variable name="particle" select="regex-group(3)"/>
            <xsl:variable name="allowHybrid"
              select="$hybrid = tokenize($forbidden_except[text()=$hit]/@except, ' ')"/>

            <xsl:choose>
              <!-- 하이브리드 예외 허용 -->
              <xsl:when test="$allowHybrid">
                <xsl:value-of select="regex-group(1)"/>
                <xsl:value-of select="$hit"/>
                <xsl:value-of select="$particle"/>
                <xsl:value-of select="regex-group(4)"/>
              </xsl:when>

              <!-- 금칙어 마킹 -->
              <xsl:otherwise>
                <xsl:value-of select="regex-group(1)"/>
                <ph outputclass="forbidden"><xsl:value-of select="$hit"/></ph>
                <xsl:value-of select="$particle"/>
                <xsl:value-of select="regex-group(4)"/>
              </xsl:otherwise>
            </xsl:choose>
          </xsl:matching-substring>

          <xsl:non-matching-substring>
            <xsl:value-of select="."/>
          </xsl:non-matching-substring>
        </xsl:analyze-string>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>

</xsl:stylesheet>
