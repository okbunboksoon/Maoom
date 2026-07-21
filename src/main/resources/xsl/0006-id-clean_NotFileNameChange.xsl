<xsl:stylesheet version="2.0" 
	xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
	xmlns:xs="http://www.w3.org/2001/XMLSchema"
	exclude-result-prefixes="xs">

	<xsl:import href="indentation1.xsl"/>
	<!--
	  출력 방식 설정
	  - XML 선언(<?xml ...?>) 제거
	  - indent="no" : 들여쓰기는 indentation 템플릿에서만 제어
	  - strip-space : 요소 사이의 불필요한 공백 노드 제거
	  - preserve-space : <p> 내부 텍스트 공백은 문장 의미 유지 위해 보존
	-->
	<xsl:output method="xml" indent="no" omit-xml-declaration="yes"/>
	<xsl:strip-space elements="*" />
	<xsl:preserve-space elements="p" />
	
	<!--  map 루트 유지 -->
	<xsl:template match="map">
		<xsl:copy>
			<xsl:apply-templates select="@* | node()"/>
			<xsl:text>&#x0A;</xsl:text>
		</xsl:copy>
	</xsl:template>

	<!-- topic이 아닌데 @id 있는 요소 : e00000 부여 (기존 로직 유지) -->
	<xsl:template match="*[@id][name()!='concept'][name()!='task'][name()!='reference']">
		<xsl:variable name="index" select="count(preceding::*[@id]) + count(ancestor::*[@id])"/>
		<xsl:variable name="depth" select="count(ancestor::*)"/>
		<xsl:call-template name="indentation">
			<xsl:with-param name="depth" select="$depth"/>
		</xsl:call-template>
		<xsl:copy>
			<xsl:apply-templates select="@* except @id"/>
			<xsl:attribute name="nid" select="concat('e', format-number($index, '00000'))"/>
			<xsl:attribute name="oid" select="@id"/>
			<xsl:apply-templates select="node()"/>
			<xsl:call-template name="indentation">
				<xsl:with-param name="depth" select="$depth"/>
			</xsl:call-template>
		</xsl:copy>
	</xsl:template>

<!--
	<xsl:template match="concept | task | reference">
		<xsl:variable name="index" select="count(preceding::concept) + count(preceding::task) + count(preceding::reference)"/>
		<xsl:variable name="depth" select="count(ancestor::*)"/>
		<xsl:call-template name="indentation">
			<xsl:with-param name="depth" select="$depth"/>
		</xsl:call-template>
		<xsl:copy>
			<xsl:apply-templates select="@* except @id"/>
			<xsl:attribute name="nid" select="concat('t', format-number($index, '00000'))"/>
			<xsl:choose>
				<xsl:when test="@id">
					<xsl:attribute name="oid" select="@id"/>
				</xsl:when>
				<xsl:when test="title/@id">
					<xsl:attribute name="oid" select="title/@id"/>
				</xsl:when>
				<xsl:otherwise>
				</xsl:otherwise>
			</xsl:choose>
			<xsl:apply-templates select="node()"/>
			<xsl:call-template name="indentation">
				<xsl:with-param name="depth" select="$depth"/>
			</xsl:call-template>
		</xsl:copy>
	</xsl:template>
-->
	<xsl:template match="concept | task | reference">
	    <xsl:variable name="depth" select="count(ancestor::*)"/>
	    <!-- 부모 topicref href -->
	    <xsl:variable name="href" select="ancestor::topicref[1]/@href"/>
	    <!-- .dita 제거 -->
	    <xsl:variable name="file-id" select="replace($href, '\.dita$', '')"/>
	    <xsl:call-template name="indentation">
	        <xsl:with-param name="depth" select="$depth"/>
	    </xsl:call-template>
	    <xsl:copy>
	        <!-- 기존 @id 제거 유지 -->
	        <xsl:apply-templates select="@* except @id"/>
	        <!-- nid = 기존 id -->
	        <xsl:if test="@id">
	            <xsl:attribute name="nid" select="@id"/>
	        </xsl:if>
	        <!-- oid = 파일명 -->
	        <xsl:if test="$file-id">
	            <xsl:attribute name="oid" select="$file-id"/>
	        </xsl:if>
	        <xsl:apply-templates select="node()"/>
	        <xsl:call-template name="indentation">
	            <xsl:with-param name="depth" select="$depth"/>
	        </xsl:call-template>
	    </xsl:copy>
	</xsl:template>

	<!-- 남아있는 @id는 출력에서 제거 -->
	<xsl:template match="@id">
	</xsl:template>

	<!-- topicref는 type 속성만 추가(기존 로직 유지) -->
	<xsl:template match="topicref">
		<xsl:variable name="depth" select="count(ancestor::*)"/>
		<xsl:call-template name="indentation">
			<xsl:with-param name="depth" select="$depth"/>
		</xsl:call-template>
		<xsl:copy>
			<xsl:apply-templates select="@*"/>
			<xsl:attribute name="type" select="*[1]/name()"/>
			<xsl:apply-templates select="node()"/>
			<xsl:call-template name="indentation">
				<xsl:with-param name="depth" select="$depth"/>
			</xsl:call-template>
		</xsl:copy>
	</xsl:template>


</xsl:stylesheet>