<xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

    <xsl:output method="xml" indent="no" omit-xml-declaration="no"/>
    <xsl:strip-space elements="*"/>
    <xsl:preserve-space elements="p"/>

    <!-- 기본적으로 모든 노드와 속성은 원본 그대로 복사한다. -->
    <xsl:template match="@* | node()">
        <xsl:copy>
            <xsl:apply-templates select="@* | node()"/>
        </xsl:copy>
    </xsl:template>

    <!-- 본문 내용이 없는 말단 topicref를 찾아 리포트용 속성을 추가한다. -->
    <xsl:template match="*[local-name() = 'topicref']">
        <!-- topicref 안에 병합된 실제 토픽 요소를 찾는다. -->
        <xsl:variable name="topic" select="*[local-name() = ('topic', 'concept', 'task', 'reference')][1]"/>
        <!-- conbody/taskbody/refbody/body가 없거나 비어 있으면 본문 없는 토픽으로 본다. -->
        <xsl:variable name="body" select="$topic/*[local-name() = ('body', 'conbody', 'taskbody', 'refbody')][1]"/>
        <xsl:variable name="has-body-content"
            select="
                exists($body)
                and exists($body/node()[not(self::text()[normalize-space() = ''])])
            "/>
        <!-- title/prolog/related-links만 있거나 빈 body만 있으면 빈 토픽으로 본다. -->
        <xsl:variable name="is-empty-topic"
            select="
                exists($topic)
                and count(*[local-name() = ('topic', 'concept', 'task', 'reference')]) = 1
                and not(*[local-name() = 'topicref'])
                and exists($topic/*[local-name() = 'title'])
                and not($topic/*[not(local-name() = ('title', 'prolog', 'related-links', 'body', 'conbody', 'taskbody', 'refbody'))])
                and not($has-body-content)
                and not($topic/text()[normalize-space() != ''])
            "/>

        <xsl:copy>
            <!-- 기존 표시 속성이 있으면 새 판단 결과로 다시 쓴다. -->
            <xsl:apply-templates select="@* except @modified"/>
            <xsl:if test="$is-empty-topic">
                <xsl:attribute name="modified">Y</xsl:attribute>
            </xsl:if>
            <xsl:apply-templates select="node()"/>
        </xsl:copy>
    </xsl:template>

</xsl:stylesheet>
