<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="3.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

    <xsl:output method="xml" indent="no" omit-xml-declaration="no"/>

    <xsl:key name="element-by-id" match="*[@id]" use="@id"/>

    <!-- 기본적으로 모든 노드와 속성을 원본 그대로 복사한다. -->
    <xsl:template match="@* | node()">
        <xsl:copy>
            <xsl:apply-templates select="@* | node()"/>
        </xsl:copy>
    </xsl:template>

    <!-- .dita# 앞에 경로가 있거나 fragment 뒤에 /하위ID가 있는 xref를 유형별 토큰으로 표시한다. -->
    <xsl:template match="xref[contains(lower-case(@href), '.dita#')][contains(substring-before(lower-case(@href), '.dita#'), '/') or contains(substring-after(lower-case(@href), '.dita#'), '/')]">
        <xsl:variable name="has-path"
            select="contains(substring-before(lower-case(@href), '.dita#'), '/')"/>
        <xsl:variable name="fragment"
            select="substring-after(lower-case(@href), '.dita#')"/>
        <xsl:variable name="element-id"
            select="tokenize($fragment, '/')[last()]"/>
        <!-- fn01 같은 이름뿐 아니라 실제 fn 요소를 가리키는 e00000 형식 ID도 오류에서 제외한다. -->
        <xsl:variable name="targets-fn"
            select="starts-with($element-id, 'fn') or exists(key('element-by-id', $element-id)[self::fn])"/>
        <xsl:variable name="has-element-id"
            select="contains($fragment, '/') and not($targets-fn)"/>
        <xsl:copy>
            <xsl:apply-templates select="@* except @modified"/>
            <xsl:attribute name="modified"
                select="normalize-space(string-join((
                    @modified,
                    if ($has-path) then 'xref-href-path-invalid' else (),
                    if ($has-element-id) then 'xref-href-element-id-invalid' else ()
                ), ' '))"/>
            <xsl:apply-templates select="node()"/>
        </xsl:copy>
    </xsl:template>

</xsl:stylesheet>
