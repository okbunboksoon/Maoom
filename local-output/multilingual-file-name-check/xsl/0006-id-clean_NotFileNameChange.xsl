<xsl:stylesheet version="2.0" 
	xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
	xmlns:xs="http://www.w3.org/2001/XMLSchema"
	xmlns:ss="urn:schemas-microsoft-com:office:spreadsheet"
	exclude-result-prefixes="xs ss">

	<xsl:import href="indentation1.xsl"/>
	<!-- title 기준 파일명 prefix 옵션 -->
	<xsl:param name="titleFileNamePrefix" select="'N'"/>
	<!-- 다국어 변환 XML 입력에서는 원본 ditamap 이름을 파라미터로 넘겨 파일명 prefix 기준으로 사용한다. -->
	<xsl:param name="mapName" select="''"/>
	<!-- 다국어 변환 BAT에서 넘기는 언어명. 정제에서는 비어 있어 기존 title/mapname 기준으로 처리한다. -->
	<xsl:param name="langName" select="''"/>
	<xsl:variable name="langMap" select="document('lang_code_map.xml')"/>
	<xsl:variable name="target-lang-code" select="string($langMap//ss:Row[ss:Cell[1]/ss:Data = $langName]/ss:Cell[2]/ss:Data)"/>
	<xsl:variable name="target-lang-file" select="replace($target-lang-code, '-', '_')"/>
	<!-- ditamap/title 값 추출. XML 다국어 변환 중간 map에는 title이 없어 mapname을 fallback으로 사용한다. -->
	<xsl:variable name="map-title" select="if (normalize-space($mapName) != '') then replace(normalize-space($mapName), '\.ditamap$', '') else if (normalize-space(/*/title[1]) != '') then normalize-space(/*/title[1]) else replace(string(/*/@mapname), '\.ditamap$', '')"/>
	<!-- title 값을 하이픈 기준 토큰으로 분리 -->
	<xsl:variable name="map-title-tokens" select="tokenize($map-title, '-')"/>
	<!-- KIA 토큰과 PE/PE2 토큰을 제외한 title 기반 파일명 prefix 생성 -->
	<xsl:variable name="filename-prefix-parts" select="$map-title-tokens[normalize-space(.) != '' and not(upper-case(.) = ('KIA', 'PE', 'PE2'))]"/>
	<xsl:variable name="filename-prefix" select="if (upper-case($titleFileNamePrefix) = 'Y' and exists($filename-prefix-parts)) then concat(string-join($filename-prefix-parts, '-'), '-') else ''"/>
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
	<!-- topic 계열 요소 : 기존 파일명 또는 title 기반 prefix + t0000 형식 파일명 부여. 1레벨 챕터 topic만 -CH01을 붙인다. -->
	<xsl:template match="concept | task | reference">
	    <xsl:variable name="depth" select="count(ancestor::*)"/>
		<xsl:variable name="index" select="count(preceding::concept) + count(preceding::task) + count(preceding::reference)"/>
		<xsl:variable name="current-topicref" select="ancestor::topicref[1]"/>
		<xsl:variable name="chapter-number" select="if ($current-topicref[parent::map]) then format-number(count($current-topicref/preceding-sibling::topicref) + 1, '00') else ''"/>
	    <!-- 부모 topicref href -->
	    <xsl:variable name="href" select="ancestor::topicref[1]/@href"/>
	    <!-- .dita 제거 -->
	    <xsl:variable name="file-id" select="replace($href, '\.dita$', '')"/>
		<xsl:variable name="numbered-file-id" select="concat('t', format-number($index, '0000'))"/>
	    <xsl:variable name="base-file-id" select="if ($filename-prefix != '') then $numbered-file-id else if (starts-with($file-id, $filename-prefix)) then substring($file-id, string-length($filename-prefix) + 1) else $file-id"/>
		<xsl:variable name="final-topic-id" select="concat($filename-prefix, $base-file-id, if ($filename-prefix != '' and $chapter-number != '') then concat('-CH', $chapter-number) else '')"/>
		<xsl:variable name="original-topic-id" select="if (@id) then @id else if (title/@id) then title/@id else $base-file-id"/>
	    <xsl:call-template name="indentation">
	        <xsl:with-param name="depth" select="$depth"/>
	    </xsl:call-template>
	    <xsl:copy>
	        <!-- 기존 @id 제거 유지 -->
	        <xsl:apply-templates select="@* except @id"/>
	        <!-- nid = 최종 topic id -->
	        <xsl:if test="$base-file-id">
	            <xsl:attribute name="nid" select="$final-topic-id"/>
	        </xsl:if>
	        <!-- oid = 기존 참조 확인용 id -->
	        <xsl:if test="$original-topic-id">
	            <xsl:attribute name="oid" select="$original-topic-id"/>
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
