<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="2.0"
  xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
  xmlns:xs="http://www.w3.org/2001/XMLSchema"
  xmlns:String="java:java.lang.String"
  xmlns:Integer="java:java.lang.Integer"
  xmlns:md="java:java.security.MessageDigest"
  exclude-result-prefixes="xs String Integer md">

  <xsl:output method="xml" indent="no" omit-xml-declaration="yes"/>
  <xsl:strip-space elements="*"/>

  <!-- QSG DB -->
  <xsl:param name="flag" select="'off'"/>
  <xsl:param name="TARGET_LANG" select="''"/>
  <xsl:variable name="db" select="document('QSG_DB.xml')"/>

  <!-- title | p 처리: TARGET_LANG가 있으면 해당 언어 term으로 치환 -->
  <xsl:template match="title | p" priority="2">
    <!-- 1) 가장 가까운 xml:lang 원문 그대로 -->
    <xsl:variable name="rawLang" select="ancestor-or-self::*[@xml:lang][1]/@xml:lang"/>
    <xsl:variable name="effectiveLang" select="if (normalize-space($TARGET_LANG) != '') then $TARGET_LANG else string($rawLang)"/>

    <!-- 2) 해시 생성 -->
    <xsl:variable name="hash">
      <xsl:call-template name="hash">
        <xsl:with-param name="notes" select="descendant-or-self::text()"/>
      </xsl:call-template>
    </xsl:variable>

    <!-- 3) DB에서 해시로 entry를 찾고, 대상 언어 코드로 term 고르기 -->
    <xsl:variable name="dbEntry" select="$db/dictionary/entry[@hash=$hash]"/>
    <xsl:variable name="dbTerm"  select="$dbEntry/term[@lang=$effectiveLang]"/>

    <!-- 4) 출력 -->
    <xsl:copy>
      <xsl:apply-templates select="@*"/>
      <xsl:attribute name="hash">
        <xsl:value-of select="$hash"/>
      </xsl:attribute>

      <xsl:choose>
        <!-- 완전일치 항목이 있으면 그 값으로 치환 -->
        <xsl:when test="$dbTerm">
          <xsl:value-of select="$dbTerm"/>
        </xsl:when>
        <!-- 없으면 원문 유지 -->
        <xsl:otherwise>
          <xsl:apply-templates select="node()"/>
        </xsl:otherwise>
      </xsl:choose>
    </xsl:copy>
  </xsl:template>

  <!-- 기본 복사 -->
  <xsl:template match="@* | node()">
    <xsl:copy>
      <xsl:apply-templates select="@* | node()"/>
    </xsl:copy>
  </xsl:template>


  <!-- 블록 내부 텍스트만 추출(기존 유지) -->
  <xsl:template match="p | ul | ol | li | fig | figgroup | title" priority="1">
    <xsl:apply-templates select="node()"/>
  </xsl:template>

  <!-- 해시 생성 -->
  <xsl:template name="hash">
    <xsl:param name="notes"/>
    <xsl:variable name="Str">
      <xsl:call-template name="seed">
        <xsl:with-param name="nodes" select="$notes"/>
      </xsl:call-template>
    </xsl:variable>
    <xsl:variable name="Inst"   select="md:getInstance('SHA-256')"/>
    <xsl:variable name="IntSeq" select="md:digest($Inst, String:getBytes($Str, 'utf-8'))"/>
    <xsl:variable name="HexStr">
      <xsl:for-each select="$IntSeq">
        <xsl:variable name="hexHash" select="upper-case(Integer:toHexString(.))"/>
        <xsl:value-of select="if (. &lt; 256) then substring(concat('0', $hexHash), string-length($hexHash)) else $hexHash"/>
      </xsl:for-each>
    </xsl:variable>
    <xsl:value-of select="$HexStr"/>
  </xsl:template>

  <!-- 해시 seed: 텍스트 + 일부 인라인 태그명 포함 -->
  <xsl:template name="seed">
    <xsl:param name="nodes"/>
    <xsl:for-each select="$nodes">
      <xsl:choose>
        <!-- 텍스트의 en dash 범위를 ' ~ '로 정규화 -->
        <xsl:when test="self::text()">
          <xsl:value-of select="replace(., '(\d+\s*)&#x2013;(\s*\d+)', '$1 ~ $2')"/>
        </xsl:when>
        <!-- 인라인 태그: 태그명 포함 후 자식 순회 -->
        <xsl:when test="self::uicontrol or self::tm or self::term or self::xref or self::image">
          <xsl:value-of select="name()"/>
          <xsl:call-template name="seed">
            <xsl:with-param name="nodes" select="node()"/>
          </xsl:call-template>
        </xsl:when>
        <!-- 기타 무시 -->
        <xsl:otherwise/>
      </xsl:choose>
    </xsl:for-each>
  </xsl:template>

</xsl:stylesheet>
