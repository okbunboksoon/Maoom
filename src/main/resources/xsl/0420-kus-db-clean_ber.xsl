<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="2.0">

	<xsl:output method="xml" indent="no" omit-xml-declaration="yes"/>
	<xsl:strip-space elements="*" />

	<!-- 기본 복사 -->
	<xsl:template match="@* | node()">
		<xsl:copy>
			<xsl:apply-templates select="@* | node()"/>
		</xsl:copy>
	</xsl:template>
	
	<!-- 260406 US EG 분기점 -->
    	<!-- pairs 내부 처리 -->
	<xsl:template match="pairs">
	    <xsl:variable name="region" select="lower-case(@region)"/>
	    
	    <xsl:message>path = temp/asis-tobe_<xsl:value-of select="$region"/>.xml</xsl:message>
	    
        <xsl:result-document href="temp/asis-tobe_{$region}.xml">
		<xsl:copy>
			<xsl:apply-templates select="@* | node()"/>
			<xsl:text>&#xA;</xsl:text>
		</xsl:copy>
	 </xsl:result-document>
	</xsl:template>
 
    <!-- pair 처리 -->
	<xsl:template match="pair">
		<xsl:text>&#xA;&#x9;</xsl:text>
		<xsl:copy>
			<xsl:apply-templates select="@*"/>
			<xsl:apply-templates select="old"/>
			<xsl:apply-templates select="new[1]"/>
			<xsl:text>&#xA;&#x9;</xsl:text>
		</xsl:copy>
	</xsl:template>

    <!-- old/new -->
	<xsl:template match="old | new">
		<xsl:text>&#xA;&#x9;&#x9;</xsl:text>
		<xsl:copy>
			<xsl:apply-templates/>
		</xsl:copy>
	</xsl:template>

</xsl:stylesheet>