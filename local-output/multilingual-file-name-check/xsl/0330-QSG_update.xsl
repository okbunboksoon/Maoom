<xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
	<xsl:output method="xml" indent="yes" encoding="UTF-8"/>
	
	<!-- 외부 업데이트 리스트 로드 -->
	<xsl:variable name="updateList" select="document('../temp/QSG_update_list.xml')/dictionary/entry"/>
	
	<!-- QSG_db에 존재하는 hash 모음 -->
	<xsl:variable name="existingHashes" select="/dictionary/entry/@hash"/>
	
	  <!-- note 매칭 및 업데이트: 기존 entry에 없는 lang만 추가 + 없는 hash는 새로 추가 -->
  <xsl:template match="/dictionary">
    <dictionary>
      <!-- 1) 기존 DB entry 출력 + 업데이트 term 병합 -->
      <xsl:for-each select="entry">
        <xsl:variable name="h" select="@hash"/>
        <xsl:variable name="updEntry" select="$updateList[@hash = $h]"/>

        <entry hash="{$h}">
          <!-- (a) 기존 term 복사 (같은 lang 중복이면 첫 것만 유지) -->
          <xsl:for-each-group select="term"
                              group-by="upper-case(normalize-space(@lang))">
            <term lang="{current-group()[1]/@lang}">
              <xsl:value-of select="normalize-space(current-group()[1])"/>
            </term>
          </xsl:for-each-group>

          <!-- (b) 업데이트 term 중, DB에 아직 없는 lang만 추가 -->
          <xsl:variable name="existingLangs" select="distinct-values(term/normalize-space(@lang))"/>
          <xsl:for-each select="$updEntry/term">
            <xsl:variable name="Lnorm" select="upper-case(normalize-space(@lang))"/>
            <xsl:if test="not($Lnorm = $existingLangs)">
              <term lang="{normalize-space(@lang)}">
                <xsl:value-of select="normalize-space(.)"/>
              </term>
            </xsl:if>
          </xsl:for-each>
        </entry>
      </xsl:for-each>

      <!-- 2) DB에 없는 hash(entry)는 통째로 추가 -->
      <xsl:for-each select="
        for $h in distinct-values($updateList/@hash)
        return $updateList[@hash = $h][1][not($h = $existingHashes)]
      ">
        <xsl:copy>
          <xsl:copy-of select="@*"/>
          <xsl:apply-templates select="node()"/>
        </xsl:copy>
      </xsl:for-each>
    </dictionary>
  </xsl:template>

  <!-- 기본 복사 템플릿 유지 -->
  <xsl:template match="@* | node()">
    <xsl:copy>
      <xsl:apply-templates select="@* | node()"/>
    </xsl:copy>
  </xsl:template>

</xsl:stylesheet>
