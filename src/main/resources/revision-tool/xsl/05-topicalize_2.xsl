<xsl:stylesheet version="2.0" 
	xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
	xmlns:xs="http://www.w3.org/2001/XMLSchema"
	exclude-result-prefixes="xs">

	<xsl:output method="xml" indent="no" omit-xml-declaration="no"/>
	<xsl:strip-space elements="*" />
	<xsl:preserve-space elements="p"/>

	<!-- hash 속성만 제거 -->
	<xsl:template match="@*">
	  <xsl:if test="not(name() = ('hash', 'status'))">
	    <xsl:copy/>
	  </xsl:if>
	</xsl:template>
	
	<!-- 260410 특정문장 속성 제거 -->
	<xsl:template match="@outputclass">
	  <xsl:if test="not(. = 'exclude')">
	    <xsl:copy/>
	  </xsl:if>
	</xsl:template>

	<!-- 기본 복사 -->
	<xsl:template match="node()"> 
		<xsl:copy>
			<xsl:apply-templates select="@* | node()"/>
		</xsl:copy>
	</xsl:template>

	<xsl:template match="map">
		<dummy/>
		<xsl:apply-templates select=".//topicref"/>
	</xsl:template>

	<xsl:template match="topicref">
		<xsl:choose>
			<xsl:when test="@href = parent::topicref/@href">
			</xsl:when>
			<xsl:otherwise>
				<xsl:result-document href="{concat('../topics/', @href)}">
					<xsl:apply-templates select="*[name()!='topicref']"/>
				</xsl:result-document>
			</xsl:otherwise>
		</xsl:choose>
	</xsl:template>

</xsl:stylesheet>
