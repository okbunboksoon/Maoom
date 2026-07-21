<xsl:stylesheet version="2.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:ss="urn:schemas-microsoft-com:office:spreadsheet"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    exclude-result-prefixes="ss xs">

    <xsl:output method="xml"
        indent="no"
        omit-xml-declaration="no"
        encoding="utf-8"/>

    <!-- BAT에서 넘기는 언어명 -->
    <xsl:param name="langName"/>

    <!-- BAT에서 넘기는 원본 파일명 -->
    <xsl:param name="srcFile"/>

    <!-- lang_code_map.xml 로드 -->
    <xsl:variable name="langMap" select="document('lang_code_map.xml')"/>

    <!-- A열(langName)과 같은 행의 B열 값 -->
    <xsl:variable name="langCode"
        select="$langMap//ss:Row[ss:Cell[1]/ss:Data = $langName]/ss:Cell[2]/ss:Data"/>

    <!-- 파일명용 코드: ar-AA -> ar_AA -->
    <xsl:variable name="langFile" select="replace($langCode, '-', '_')"/>

    <!-- 메타데이터 생성용 파일명 토큰 -->
    <xsl:variable name="sourceBase" select="replace($srcFile, '\.ditamap$', '')"/>
    <xsl:variable name="tokens" select="tokenize($sourceBase, '-')"/>
    <xsl:variable name="tokenCount" select="count($tokens)"/>
    <xsl:variable name="platform" select="$tokens[1]"/>
    <xsl:variable name="model" select="$tokens[2]"/>
    <xsl:variable name="year" select="$tokens[$tokenCount]"/>
    <xsl:variable name="baseRaw"
        select="string-join(
            $tokens[
                position() &gt; 2
                and position() &lt; $tokenCount - 1
                and upper-case(.) != 'PE'
            ],
            '-'
        )"/>

    <xsl:template match="/">
        <!-- 파일명 안의 언어코드 치환 -->
        <xsl:variable name="newFileName">
            <xsl:choose>
                <!-- en_00 같은 형식 -->
                <xsl:when test="matches($srcFile, '[a-z]{2}_[A-Z]{2}')">
                    <xsl:value-of select="replace($srcFile, '[a-z]{2}_[A-Z]{2}', $langFile)"/>
                </xsl:when>

                <!-- en-GB 같은 형식 -->
                <xsl:when test="matches($srcFile, '[a-z]{2}-[A-Z]{2}')">
                    <xsl:value-of select="replace($srcFile, '[a-z]{2}-[A-Z]{2}', $langCode)"/>
                </xsl:when>

                <!-- 언어코드 패턴이 없으면 원본 파일명 유지 -->
                <xsl:otherwise>
                    <xsl:value-of select="$srcFile"/>
                </xsl:otherwise>
            </xsl:choose>
        </xsl:variable>

        <!-- 디버깅 로그 -->
        <xsl:message>LANGNAME=<xsl:value-of select="$langName"/></xsl:message>
        <xsl:message>LANGCODE=<xsl:value-of select="$langCode"/></xsl:message>
        <xsl:message>SRCFILE=<xsl:value-of select="$srcFile"/></xsl:message>
        <xsl:message>OUTFILE=<xsl:value-of select="$newFileName"/></xsl:message>

        <dummy/>

        <!-- 결과 파일 저장 -->
        <xsl:result-document href="{concat('../temp/', $newFileName)}"
            method="xml"
            indent="no"
            omit-xml-declaration="no"
            encoding="utf-8">

            <!-- XML declaration 다음에 DOCTYPE 강제 출력 -->
            <xsl:text>&#x0A;</xsl:text>
            <xsl:text disable-output-escaping="yes">&lt;!DOCTYPE map PUBLIC &quot;-//OASIS//DTD DITA 1.3 Map//EN&quot; &quot;technicalContent/dtd/map.dtd&quot;&gt;</xsl:text>
            <xsl:text>&#x0A;</xsl:text>

            <!-- DOCTYPE 다음에 PI 강제 출력 -->
            <xsl:processing-instruction name="path2rootmap-uri">./</xsl:processing-instruction>
            <xsl:text>&#x0A;</xsl:text>

            <!-- 루트 요소만 복사 -->
            <xsl:apply-templates select="/*"/>
        </xsl:result-document>
    </xsl:template>

    <!-- map 언어코드 변경 및 최상위 metadata 생성 -->
    <xsl:template match="map">
        <xsl:copy>
            <xsl:apply-templates select="@* except @xml:lang"/>
            <xsl:attribute name="xml:lang">
                <xsl:value-of select="$langCode"/>
            </xsl:attribute>

            <xsl:text>&#10;&#9;</xsl:text>
            <xsl:apply-templates select="title"/>

            <!-- KIA 파일일 때만 map 최상위 metadata 삽입 -->
            <xsl:if test="upper-case($platform) = 'KIA'">
                <xsl:text>&#10;&#9;</xsl:text>
                <topicmeta>
                    <xsl:text>&#10;&#9;&#9;</xsl:text>
                    <metadata>
                        <xsl:text>&#10;&#9;&#9;&#9;</xsl:text>
                        <data name="platform"><xsl:value-of select="$platform"/></data>

                        <xsl:text>&#10;&#9;&#9;&#9;</xsl:text>
                        <data name="base"><xsl:value-of select="$baseRaw"/></data>

                        <xsl:text>&#10;&#9;&#9;&#9;</xsl:text>
                        <data name="year"><xsl:value-of select="$year"/></data>

                        <xsl:text>&#10;&#9;&#9;&#9;</xsl:text>
                        <data name="lang"><xsl:value-of select="$langFile"/></data>

                        <xsl:text>&#10;&#9;&#9;&#9;</xsl:text>
                        <data name="model"><xsl:value-of select="$model"/></data>

                        <xsl:text>&#10;&#9;&#9;&#9;</xsl:text>
                        <data name="modelName"/>

                        <xsl:text>&#10;&#9;&#9;&#9;</xsl:text>
                        <data name="publicationNo"/>

                        <xsl:text>&#10;&#9;&#9;&#9;</xsl:text>
                        <data name="content"/>

                        <xsl:text>&#10;&#9;&#9;</xsl:text>
                    </metadata>
                    <xsl:text>&#10;&#9;</xsl:text>
                </topicmeta>
            </xsl:if>

            <!-- 기존 map 최상위 topicmeta는 교체하고 나머지는 유지 -->
            <xsl:variable name="remainingNodes"
                select="node()[not(self::title or self::topicmeta or self::text()[normalize-space() = ''])]"/>
            <xsl:if test="exists($remainingNodes)">
                <xsl:text>&#10;&#9;</xsl:text>
                <xsl:for-each select="$remainingNodes">
                    <xsl:if test="position() gt 1">
                        <xsl:text>&#10;&#9;</xsl:text>
                    </xsl:if>
                    <xsl:apply-templates select="."/>
                </xsl:for-each>
                <xsl:text>&#10;</xsl:text>
            </xsl:if>
        </xsl:copy>
    </xsl:template>

    <!-- title 내부 언어코드 변경 -->
    <xsl:template match="map/title/text()">
        <xsl:choose>
            <!-- en_00 같은 형식 -->
            <xsl:when test="matches(., '[a-z]{2}_[A-Z]{2}')">
                <xsl:value-of select="replace(., '[a-z]{2}_[A-Z]{2}', $langFile)"/>
            </xsl:when>

            <!-- en-GB 같은 형식 -->
            <xsl:when test="matches(., '[a-z]{2}-[A-Z]{2}')">
                <xsl:value-of select="replace(., '[a-z]{2}-[A-Z]{2}', $langCode)"/>
            </xsl:when>

            <!-- 언어코드 패턴이 없으면 원본 유지 -->
            <xsl:otherwise>
                <xsl:value-of select="."/>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>

	<xsl:template match="data[@name='lang']/text()">
	    <xsl:value-of select="$langFile"/>
	</xsl:template>

    <!-- 기본 복사 -->
    <xsl:template match="@* | node()">
        <xsl:copy>
            <xsl:apply-templates select="@* | node()"/>
        </xsl:copy>
    </xsl:template>

</xsl:stylesheet>
