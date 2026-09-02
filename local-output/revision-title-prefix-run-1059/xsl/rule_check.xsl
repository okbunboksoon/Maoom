<xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

  <xsl:output method="xml" indent="no" omit-xml-declaration="yes" />
  <xsl:strip-space elements="*" />

	<xsl:template match="fileset">
		<fileset date="{@date}">
			 <xsl:apply-templates select="file"/>
		</fileset>
	</xsl:template>
	
	<xsl:template match="file">
		<file filename="{@filename}">
			<xsl:apply-templates select="detect"/>
		</file>
	</xsl:template>
  <xsl:template match="detect">
    <xsl:variable name="errorType" select="substring-before(., '###')" />
    <xsl:variable name="sentence" select="substring-after(., '###')" />

    <xsl:variable name="highlighted">
      <xsl:choose>
        
        <!-- 두 문장이 붙어 있습니다 -->
        <xsl:when test="contains($errorType, '두 문장이 붙어 있습니다')">
          <xsl:value-of
            select="replace($sentence, '((니다|시오))([.]?)([가-힣])', '$1$3&lt;span&gt;$4&lt;/span&gt;')" disable-output-escaping="yes"/>
        </xsl:when>

        <!-- 콜론 앞뒤 공백 오류 -->
        <xsl:when test="contains($errorType, '콜론 앞에는 공백이 없고')">
          <xsl:variable name="colonPattern" select="'\s+:|:[^\s]'" />
          <xsl:value-of select="replace($sentence, $colonPattern, '&lt;span&gt;$0&lt;/span&gt;')" disable-output-escaping="yes"/>
        </xsl:when>

        <!-- 물결표 앞뒤 공백 -->
        <xsl:when test="contains($errorType, '물결표 앞과 뒤에')">
          <xsl:value-of select="replace($sentence, '\s+~\s+', '&lt;span&gt;$0&lt;/span&gt;')" disable-output-escaping="yes"/>
        </xsl:when>

        <!-- 엔대시 앞뒤 공백 -->
        <xsl:when test="contains($errorType, '엔대시 앞과 뒤에')">
          <xsl:value-of select="replace($sentence, '\s+–\s+', '&lt;span&gt;$0&lt;/span&gt;')" disable-output-escaping="yes"/>
        </xsl:when>

        <!-- 이 단위 앞에 공백이 있어야 합니다 -->
        <xsl:when test="contains($errorType, '이 단위 앞에 공백이 있어야')">
          <xsl:value-of select="replace($sentence, '\d+(km/h|kPa|kgf·m|m|cm|mm|km|kg|liters|cc|mph|bar|psi|lbf·ft|ft|in|inches|miles|mi|lb\.|lbs\.|gallons)', '&lt;span&gt;$0&lt;/span&gt;')" disable-output-escaping="yes"/>
        </xsl:when>

        <!-- 이 단위 앞에 공백이 없어야 합니다 -->
        <xsl:when test="contains($errorType, '이 단위 앞에 공백이 없어야')">
          <xsl:value-of select="replace($sentence, '\d+\s+(%|°|\$|℃|℉)', '&lt;span&gt;$0&lt;/span&gt;')" disable-output-escaping="yes"/>
        </xsl:when>

        <!-- ℓ 대신 L -->
        <xsl:when test="contains($errorType, '부피 단위는 ℓ이 아니라')">
          <xsl:value-of select="replace($sentence, '\d+\s?(ℓ)', '&lt;span&gt;$0&lt;/span&gt;')" disable-output-escaping="yes"/>
        </xsl:when>

        <!-- 4자리 이상 수는 쉼표를 넣어야 합니다 -->
        <xsl:when test="contains($errorType, '4자리 이상 수는')">
          <xsl:value-of select="replace($sentence, '\s\d{4,4}(\d{1,3})*\s', '&lt;span&gt;$0&lt;/span&gt;')" disable-output-escaping="yes"/>
        </xsl:when>

        <!-- 의성어 앞뒤에 큰따옴표가 있어야 합니다 -->
        <xsl:when test="contains($errorType, '의성어 앞뒤에 큰따옴표가')">
          <xsl:value-of select="replace($sentence, '(^|[^&quot;])\s*(딸깍|찰칵|삐|삐삐)\s*([^&quot;]|$)', '&lt;span&gt;$0&lt;/span&gt;')" disable-output-escaping="yes"/>
        </xsl:when>

        <!-- 그 외 -->
        <xsl:otherwise>
          <xsl:value-of select="$sentence"/>
        </xsl:otherwise>

      </xsl:choose>
    </xsl:variable>

    <!-- 결과 출력 -->
    <detect location="{@location}">
      <xsl:value-of select="$errorType"/>
      <xsl:text>###</xsl:text>
      <xsl:copy-of select="$highlighted"/>
    </detect>
  </xsl:template>

</xsl:stylesheet>