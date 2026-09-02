<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="2.0"
  xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
  xmlns:xs="http://www.w3.org/2001/XMLSchema"
  xmlns:f="urn:forbidden-func"
  exclude-result-prefixes="xs f">

  <xsl:import href="indentation1.xsl"/>

  <xsl:output method="xml" indent="no" omit-xml-declaration="no"/>
  <xsl:strip-space elements="*"/>

  <!-- ===== 0) 맵 타이틀에서 차종/언어 판별 (50번과 동일) ===== -->
	<xsl:variable name="mapTitle"
	  select="normalize-space(/*[local-name()='map']/*[local-name()='title'])"/>

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
    if (contains($mapTitle, '_HEV_') or contains($mapTitle, '-HEV-'))
      then 'HEV'
    else if (contains($mapTitle, '_PHEV_') or contains($mapTitle, '-PHEV-'))
      then 'PHEV'
    else 'no'
  "/>


  <!-- 금칙어 파일명 자동 결정 -->
  <xsl:variable name="forbiddenFile" select="concat($cartype, '_forbidden_', $lang, '.xml')"/>

  <!-- ===== 1) 사전/예외 로드 ===== -->
  <xsl:variable name="dict"
    select="if (doc-available($forbiddenFile)) then document($forbiddenFile)/list else ()"/>

  <!-- 모든 DITA 파일 -->
  <xsl:variable name="files" select="collection('../topics/?select=*.dita')"/>

  <!-- =========================
       유틸: 위치 경로 / 문자열 정규화
       ========================= -->
  <xsl:function name="f:pos" as="xs:integer">
    <xsl:param name="e" as="element()"/>
    <xsl:sequence select="count($e/preceding-sibling::*[name()=name($e)]) + 1"/>
  </xsl:function>

  <xsl:function name="f:path" as="xs:string">
    <xsl:param name="e" as="element()"/>
    <xsl:variable name="chain" select="$e/ancestor-or-self::*"/>
    <xsl:sequence select="string-join(for $n in $chain return concat('/', name($n), '[', f:pos($n), ']'), '')"/>
  </xsl:function>

  <!-- 공백/비가시문자/대소문자 정규화 (사전-본문 비교용) -->
  <xsl:function name="f:norm" as="xs:string">
    <xsl:param name="s" as="xs:string?"/>
    <xsl:variable name="NBSP"  select="codepoints-to-string(160)"/>
    <xsl:variable name="NNBSP" select="codepoints-to-string(8239)"/>
    <xsl:variable name="ZWSP"  select="codepoints-to-string(8203)"/>
    <xsl:variable name="ZWNJ"  select="codepoints-to-string(8204)"/>
    <xsl:variable name="WJ"    select="codepoints-to-string(8288)"/>
    <xsl:variable name="t1" select="translate($s, concat($NBSP, $NNBSP), '  ')"/>
    <xsl:variable name="t2" select="translate($t1, concat($ZWSP, $ZWNJ, $WJ), '')"/>
    <xsl:sequence select="lower-case(normalize-space($t2))"/>
  </xsl:function>

  <!-- =========================
       1) forbidden 수집 (type/new/location/context 포함)
       2) 모든 파일 재저장하며 'forbidden' 토큰 제거 + 안전 언랩
       ========================= -->
  <xsl:template match="map">
    <dummy/>

    <!-- 1) extract.xml 생성 -->
    <xsl:result-document href="{concat('../topics/3rd_party/', 'extract.xml')}">
      <xsl:text>&#x0A;</xsl:text>
      <fileset>
        <xsl:for-each select="$files">
          <!-- 파일 이름 -->
          <xsl:variable name="fname" select="tokenize(base-uri(.), '/')[last()]"/>
          <!-- 모든 forbidden ph(토큰 포함) -->
          <xsl:variable name="hits" select=".//ph[contains(concat(' ', @outputclass, ' '), ' forbidden ')]"/>

          <xsl:if test="exists($hits)">
            <xsl:text>&#x0A;&#x09;</xsl:text>
            <file filename="{$fname}">
              <!-- ph 하나씩 수집 -->
              <xsl:for-each select="$hits">
                <xsl:variable name="old" select="normalize-space(string(.))"/>
                <xsl:variable name="oldKey" select="f:norm($old)"/>

                <!-- 사전 매칭: @old 또는 텍스트 본문(정규화 비교) -->
                <xsl:variable name="item"
                  select="(
                            $dict//item[f:norm(@old)      = $oldKey],
                            $dict//item[f:norm(string(.)) = $oldKey]
                          )[1]"/>

                <!-- 위치: 가까운 p 우선 -->
                <xsl:variable name="hostP" select="ancestor::p[1]"/>

                <xsl:text>&#x0A;&#x09;&#x09;</xsl:text>
                <detect old="{$old}"
                        new="{string(($item/@new, '')[1])}"
                        type="{string(($item/@type, '')[1])}"
                        location="{if ($hostP) then f:path($hostP) else f:path(ancestor::*[1])}">
                  <context>
                    <!-- 문단 전체 평문화(금칙어는 <span>) -->
                    <xsl:choose>
                      <xsl:when test="$hostP">
                        <xsl:apply-templates select="$hostP/node()" mode="as-text"/>
                      </xsl:when>
                      <xsl:otherwise>
                        <xsl:apply-templates select="ancestor::*[1]/node()" mode="as-text"/>
                      </xsl:otherwise>
                    </xsl:choose>
                  </context>
                </detect>
              </xsl:for-each>
              <xsl:text>&#x0A;&#x09;</xsl:text>
            </file>
          </xsl:if>
        </xsl:for-each>
        <xsl:text>&#x0A;</xsl:text>
      </fileset>
    </xsl:result-document>

    <!-- 2) 원본 파일 재저장 (cleanup 모드로 forbidden 토큰 제거 + 안전 언랩) -->
    <xsl:for-each select="$files">
      <xsl:result-document href="{concat('../topics/', tokenize(base-uri(.), '/')[last()])}">
        <xsl:apply-templates select="." mode="cleanup"/>
      </xsl:result-document>
    </xsl:for-each>
  </xsl:template>

  <!-- ========== as-text: 문단 평문화 + 금칙어 강조 ========== -->
  <xsl:template match="text()" mode="as-text" priority="2">
    <xsl:value-of select="."/>
  </xsl:template>

<xsl:template match="ph[contains(concat(' ', @outputclass, ' '), ' forbidden ')]"
              mode="as-text" priority="2">

  <!-- 금칙어 텍스트 -->
  <xsl:variable name="txt" select="string(.)"/>

  <!-- 다음 형제 노드의 첫 글자 -->
  <xsl:variable name="next" select="string(following-sibling::node()[1])"/>
  <xsl:variable name="nextFirst"
    select="if ($next != '') then substring($next, 1, 1) else ''"/>

  <!-- '조사'로 시작하는 경우는 공백 넣지 않기 -->
  <xsl:variable name="isJosaFirst"
    select="contains(' 를 을 이 가 은 는 의 에 와 과 도 만 로 서 부 까 ',
                     concat(' ', $nextFirst, ' '))"/>

  <!-- 다음 글자가 글자/숫자이고, 조사 시작이 아닐 때만 공백 필요 -->
  <xsl:variable name="needSpace"
    select="matches($nextFirst, '\p{L}|\p{N}')
            and not($isJosaFirst)"/>

  <span><xsl:value-of select="$txt"/></span>
  <xsl:if test="$needSpace">
    <xsl:text> </xsl:text>
  </xsl:if>
</xsl:template>

  <xsl:template match="node()" mode="as-text" priority="-1">
    <xsl:apply-templates select="node()" mode="as-text"/>
  </xsl:template>

  <!-- ===== DOCTYPE + 본문 복사 (일반 변환용: 기존 유지) ===== -->
  <xsl:template match="concept | task | reference">
    <xsl:text>&#x0A;</xsl:text>
    <xsl:choose>
      <xsl:when test="name() = 'concept'">
        <xsl:text disable-output-escaping="yes">&lt;!DOCTYPE concept PUBLIC &quot;-//OASIS//DTD DITA 1.3 Concept//EN&quot; &quot;concept.dtd&quot;&gt;</xsl:text>
      </xsl:when>
      <xsl:when test="name() = 'task'">
        <xsl:text disable-output-escaping="yes">&lt;!DOCTYPE task PUBLIC &quot;-//OASIS//DTD DITA 1.3 Task//EN&quot; &quot;task.dtd&quot;&gt;</xsl:text>
      </xsl:when>
      <xsl:when test="name() = 'reference'">
        <xsl:text disable-output-escaping="yes">&lt;!DOCTYPE reference PUBLIC &quot;-//OASIS//DTD DITA 1.3 Reference//EN&quot; &quot;reference.dtd&quot;&gt;</xsl:text>
      </xsl:when>
    </xsl:choose>
    <xsl:text>&#x0A;</xsl:text>
    <xsl:copy>
      <xsl:apply-templates select="@* | node()"/>
      <xsl:text>&#x0A;</xsl:text>
    </xsl:copy>
  </xsl:template>

  <!-- 본문 변환 시 forbidden 태그 자체는 제거(내용은 유지) -->
  <xsl:template match="ph[@outputclass='forbidden']">
    <xsl:apply-templates/>
  </xsl:template>

  <!-- ===== cleanup 모드: 파일 재저장 시 'forbidden' 토큰 제거 + 안전 언랩 ===== -->

  <!-- (A) NEW: ph[@outputclass='forbidden'] 안전 언랩 + 경계 공백 “필요할 때만” 보충 -->
  <xsl:template match="ph[@outputclass='forbidden']" mode="cleanup" priority="5">
    <xsl:variable name="txt" select="string(.)"/>
    <xsl:variable name="prevText" select="string(preceding-sibling::node()[1])"/>
    <xsl:variable name="nextText" select="string(following-sibling::node()[1])"/>
    <xsl:variable name="prevLast" select="if ($prevText!='') then substring($prevText, string-length($prevText), 1) else ''"/>
    <xsl:variable name="nextFirst" select="if ($nextText!='') then substring($nextText, 1, 1) else ''"/>
    <xsl:variable name="curFirst" select="if ($txt!='') then substring($txt,1,1) else ''"/>
    <xsl:variable name="curLast"  select="if ($txt!='') then substring($txt,string-length($txt),1) else ''"/>

    <xsl:variable name="needBefore"
      select="not(matches($prevLast, '\s')) and
              matches($prevLast, '\p{L}|\p{N}') and
              matches($curFirst, '\p{L}|\p{N}')"/>

    <xsl:variable name="needAfter"
      select="not(matches($nextFirst, '\s')) and
              matches($curLast, '\p{L}|\p{N}') and
              matches($nextFirst, '\p{L}|\p{N}')"/>

    <xsl:if test="$needBefore"><xsl:text> </xsl:text></xsl:if>
    <xsl:value-of select="$txt"/>
    <xsl:if test="$needAfter"><xsl:text> </xsl:text></xsl:if>
  </xsl:template>

  <!-- (B) NEW: 전처리에서 <forbidden> 요소명을 쓴 경우도 동일 처리 -->
  <xsl:template match="forbidden" mode="cleanup" priority="5">
    <xsl:variable name="txt" select="string(.)"/>
    <xsl:variable name="prevText" select="string(preceding-sibling::node()[1])"/>
    <xsl:variable name="nextText" select="string(following-sibling::node()[1])"/>
    <xsl:variable name="prevLast" select="if ($prevText!='') then substring($prevText, string-length($prevText), 1) else ''"/>
    <xsl:variable name="nextFirst" select="if ($nextText!='') then substring($nextText, 1, 1) else ''"/>
    <xsl:variable name="curFirst" select="if ($txt!='') then substring($txt,1,1) else ''"/>
    <xsl:variable name="curLast"  select="if ($txt!='') then substring($txt,string-length($txt),1) else ''"/>

    <xsl:variable name="needBefore"
      select="not(matches($prevLast, '\s')) and
              matches($prevLast, '\p{L}|\p{N}') and
              matches($curFirst, '\p{L}|\p{N}')"/>

    <xsl:variable name="needAfter"
      select="not(matches($nextFirst, '\s')) and
              matches($curLast, '\p{L}|\p{N}') and
              matches($nextFirst, '\p{L}|\p{N}')"/>

    <xsl:if test="$needBefore"><xsl:text> </xsl:text></xsl:if>
    <xsl:value-of select="$txt"/>
    <xsl:if test="$needAfter"><xsl:text> </xsl:text></xsl:if>
  </xsl:template>

  <!-- (2) outputclass에서 'forbidden' 토큰만 제거 (원본 유지) -->
  <xsl:template match="ph/@outputclass[contains(concat(' ', ., ' '), ' forbidden ')]" mode="cleanup">
    <xsl:variable name="kept"
      select="normalize-space(string-join(
                for $t in tokenize(., '\s+')
                return if ($t = 'forbidden') then () else $t,
              ' '))"/>
    <xsl:if test="$kept != ''">
      <xsl:attribute name="outputclass"><xsl:value-of select="$kept"/></xsl:attribute>
    </xsl:if>
    <!-- 남는 토큰이 없으면 속성 자체 제거 -->
  </xsl:template>

  <!-- (3) 기본: 그대로 복사 -->
  <xsl:template match="@* | node()" mode="cleanup">
    <xsl:copy>
      <xsl:apply-templates select="@* | node()" mode="cleanup"/>
    </xsl:copy>
  </xsl:template>

  <!-- (4) 루트에서 DOCTYPE도 유지 -->
  <xsl:template match="concept | task | reference" mode="cleanup">
    <xsl:text>&#x0A;</xsl:text>
    <xsl:choose>
      <xsl:when test="self::concept">
        <xsl:text disable-output-escaping="yes">&lt;!DOCTYPE concept PUBLIC &quot;-//OASIS//DTD DITA 1.3 Concept//EN&quot; &quot;concept.dtd&quot;&gt;</xsl:text>
      </xsl:when>
      <xsl:when test="self::task">
        <xsl:text disable-output-escaping="yes">&lt;!DOCTYPE task PUBLIC &quot;-//OASIS//DTD DITA 1.3 Task//EN&quot; &quot;task.dtd&quot;&gt;</xsl:text>
      </xsl:when>
      <xsl:when test="self::reference">
        <xsl:text disable-output-escaping="yes">&lt;!DOCTYPE reference PUBLIC &quot;-//OASIS//DTD DITA 1.3 Reference//EN&quot; &quot;reference.dtd&quot;&gt;</xsl:text>
      </xsl:when>
    </xsl:choose>
    <xsl:text>&#x0A;</xsl:text>
    <xsl:copy>
      <xsl:apply-templates select="@* | node()" mode="cleanup"/>
      <xsl:text>&#x0A;</xsl:text>
    </xsl:copy>
  </xsl:template>

</xsl:stylesheet>
