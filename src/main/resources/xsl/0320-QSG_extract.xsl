<xsl:stylesheet version="2.0"
  xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
  xmlns:ss="urn:schemas-microsoft-com:office:spreadsheet"
  xmlns:String="java:java.lang.String"
  xmlns:Integer="java:java.lang.Integer"
  xmlns:md="java:java.security.MessageDigest"
  exclude-result-prefixes="ss String Integer md">

  <!-- XML 출력 형식 설정 -->
  <xsl:output method="xml" indent="yes"/>

  <!-- 헤더 행: 첫 번째 Row에서 언어 정보 추출 -->
  <xsl:variable name="headers" select="//ss:Row[1]/ss:Cell/ss:Data"/>

  <!-- 루트 템플릿 -->
  <xsl:template match="/">
    <dictionary>
      <xsl:for-each select="//ss:Row[position() &gt; 1]">

        <!-- 첫 번째 셀 텍스트 추출 -->
        <xsl:variable name="seedText" select="normalize-space(ss:Cell[1]/ss:Data)"/>

        <!-- SHA-256 해시 생성 -->
        <xsl:variable name="hash">
          <xsl:call-template name="hash">
            <xsl:with-param name="text" select="$seedText"/>
          </xsl:call-template>
        </xsl:variable>

        <!-- <entry> 생성 -->
        <entry hash="{$hash}">
          <xsl:for-each select="ss:Cell">
            <xsl:variable name="col" select="position()"/>
            <term>
              <xsl:attribute name="lang">
                <xsl:value-of select="upper-case($headers[$col])"/>
              </xsl:attribute>
              <xsl:value-of select="normalize-space(ss:Data)"/>
            </term>
          </xsl:for-each>
        </entry>

      </xsl:for-each>
    </dictionary>
  </xsl:template>

  <!-- 해시 생성 템플릿 -->
  <xsl:template name="hash">
    <xsl:param name="text"/>
    <xsl:variable name="Str">
      <xsl:call-template name="seed">
        <xsl:with-param name="nodes" select="$text"/>
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

  <!-- 문자열/노드 모두 안전하게 처리하는 seed 템플릿 -->
  <xsl:template name="seed">
    <xsl:param name="nodes"/>

    <!-- 문자열일 경우 직접 정제해서 출력 -->
    <xsl:if test="not($nodes instance of node())">
      <xsl:value-of select="replace($nodes, '(\d+\s*)–(\s*\d+)', '$1 ~ $2')"/>
    </xsl:if>

    <!-- 노드인 경우는 기존 로직 사용 -->
    <xsl:for-each select="$nodes[. instance of node()]">
      <xsl:choose>
        <xsl:when test="self::text()">
          <xsl:value-of select="replace(., '(\d+\s*)–(\s*\d+)', '$1 ~ $2')"/>
        </xsl:when>
        <xsl:when test="self::uicontrol or self::tm or self::term or self::xref or self::image">
          <xsl:value-of select="name()"/>
          <xsl:call-template name="seed">
            <xsl:with-param name="nodes" select="node()"/>
          </xsl:call-template>
        </xsl:when>
        <xsl:otherwise/>
      </xsl:choose>
    </xsl:for-each>
  </xsl:template>

</xsl:stylesheet>
