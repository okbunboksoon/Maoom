<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="2.0"
  xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
  xmlns:ss="urn:schemas-microsoft-com:office:spreadsheet"
  xmlns:xs="http://www.w3.org/2001/XMLSchema"
  xmlns:f="urn:f"
  xmlns:saxon="http://saxon.sf.net/"
  exclude-result-prefixes="ss xs f saxon">

  <xsl:output method="xml" indent="yes" encoding="UTF-8"/>
  <xsl:strip-space elements="*"/>

  <xsl:param name="CONCEPT_ID" as="xs:string" select="'t00000'"/>
  <xsl:param name="TITLE" as="xs:string" select="'Foreword'"/>

  <!-- ====== 셀 찾기 함수 ====== -->
  <xsl:function name="f:cell-at" as="element(ss:Cell)?">
    <xsl:param name="row" as="element(ss:Row)"/>
    <xsl:param name="n" as="xs:integer"/>
    <xsl:sequence select="$row/ss:Cell[$n]"/>
  </xsl:function>

  <!-- ====== 문자열을 XML fragment로 파싱 ====== -->
 <!--
  <xsl:function name="f:to-nodes" as="node()*">
    <xsl:param name="s" as="xs:string"/>
-->
    <!-- wrapper 필수 -->
   <!--
    <xsl:variable name="wrapped"
      select="concat('&lt;w&gt;', $s, '&lt;/w&gt;')" />

    <xsl:sequence select="saxon:parse($wrapped)/w/node()"/>
  </xsl:function>
-->
<xsl:function name="f:to-nodes" as="item()*">
    <xsl:param name="s" as="xs:string"/>

    <xsl:choose>

        <!-- 실제 XML 태그 형태일 때만 parse -->
        <xsl:when test="
            matches(
                normalize-space($s),
                '^&lt;/?[A-Za-z][A-Za-z0-9:_-]*(\s+[^&gt;]*)?&gt;'
            )
        ">

            <xsl:variable name="wrapped"
                select="concat('&lt;w&gt;', $s, '&lt;/w&gt;')" />

            <xsl:sequence
                select="saxon:parse($wrapped)/w/node()"/>

        </xsl:when>

        <!-- 일반 문자열 -->
        <xsl:otherwise>
            <xsl:value-of select="$s"/>
        </xsl:otherwise>

    </xsl:choose>

</xsl:function>

  <!-- ====== 메인 ====== -->
  <xsl:template match="/">
    <concept id="{$CONCEPT_ID}">
      <title><xsl:value-of select="$TITLE"/></title>
      <conbody>

        <xsl:for-each select="(//ss:Worksheet)[1]//ss:Table/ss:Row">

          <xsl:variable name="Araw"
            select="string(f:cell-at(.,1)/ss:Data)"/>

          <xsl:variable name="Braw"
            select="string(f:cell-at(.,2)/ss:Data)"/>

          <xsl:if test="normalize-space($Araw) != '' 
                        or normalize-space($Braw) != ''">

            <p>
              <draft-comment>
                <xsl:sequence select="f:to-nodes($Araw)"/>
              </draft-comment>

              <ph outputclass="new">
                <xsl:sequence select="f:to-nodes($Braw)"/>
              </ph>
            </p>

          </xsl:if>

        </xsl:for-each>

      </conbody>
    </concept>
  </xsl:template>

</xsl:stylesheet>
