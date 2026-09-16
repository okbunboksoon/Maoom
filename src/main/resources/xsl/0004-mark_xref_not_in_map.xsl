<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="3.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xmlns:f="urn:revision:xref-map-check"
    exclude-result-prefixes="xs f">

    <xsl:output method="xml" indent="no" omit-xml-declaration="no"/>
    <xsl:mode on-no-match="shallow-copy"/>

    <!-- href에서 # 뒤의 fragment와 앞쪽 경로를 제외한 파일명만 반환한다. -->
    <xsl:function name="f:dita-file-name" as="xs:string">
        <xsl:param name="href" as="xs:string?"/>
        <xsl:variable name="without-fragment"
            select="substring-before(concat(normalize-space($href), '#'), '#')"/>
        <xsl:variable name="normalized-path"
            select="replace($without-fragment, '\\', '/')"/>
        <xsl:sequence select="lower-case(tokenize($normalized-path, '/')[last()])"/>
    </xsl:function>

    <!-- 병합된 map의 topicref가 참조하는 DITA 파일명 목록이다. -->
    <xsl:variable name="map-topic-files" as="xs:string*"
        select="distinct-values(
            (//*[local-name() = 'topicref'][@href]
             ! f:dita-file-name(string(@href)))[ends-with(., '.dita')]
        )"/>

    <!--
        xref의 # 뒤 fragment는 검사하지 않는다.
        xref가 가리키는 DITA 파일이 topicref 목록에 없을 때만 modified에 표시한다.
        href="#e00001" 같은 현재 문서 내부 참조와 외부 URL은 검사하지 않는다.
    -->
    <xsl:template match="*[local-name() = 'xref'][@href]">
        <xsl:variable name="href" select="normalize-space(string(@href))"/>
        <xsl:variable name="target-file" select="f:dita-file-name($href)"/>
        <xsl:variable name="is-external"
            select="lower-case(normalize-space(string(@scope))) = 'external'
                    or matches($href, '^[A-Za-z][A-Za-z0-9+.-]*:')
                    or starts-with($href, '//')"/>
        <xsl:variable name="target-is-missing"
            select="not($is-external)
                    and not(starts-with($href, '#'))
                    and ends-with($target-file, '.dita')
                    and not($target-file = $map-topic-files)"/>

        <xsl:copy>
            <xsl:apply-templates select="@* except @modified"/>
            <xsl:if test="@modified or $target-is-missing">
                <xsl:attribute name="modified"
                    select="normalize-space(string-join((
                        tokenize(normalize-space(string(@modified)), '\s+'),
                        if ($target-is-missing and not(contains-token(@modified, 'xref-target-not-in-map')))
                        then 'xref-target-not-in-map'
                        else ()
                    ), ' '))"/>
            </xsl:if>
            <xsl:apply-templates select="node()"/>
        </xsl:copy>
    </xsl:template>

</xsl:stylesheet>
