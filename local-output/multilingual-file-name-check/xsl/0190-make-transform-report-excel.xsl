<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="3.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:xs="http://www.w3.org/2001/XMLSchema"
                xmlns:ss="urn:schemas-microsoft-com:office:spreadsheet"
                xmlns="urn:schemas-microsoft-com:office:spreadsheet"
                exclude-result-prefixes="xs">

    <xsl:output method="xml" indent="yes" encoding="UTF-8"/>
    <xsl:param name="fileNameMode" select="'DEFAULT'"/>
    <xsl:param name="inputType" select="''"/>
    <xsl:param name="outputType" select="''"/>
    <xsl:param name="removeSimple" select="'N'"/>
    <xsl:param name="removeDeliveryTarget" select="'N'"/>
    <xsl:param name="deleteDraft" select="'N'"/>
    <xsl:param name="textDbApply" select="'N'"/>
    <xsl:param name="noteDbApply" select="'N'"/>

    <xsl:template match="/">
        <!-- 최종 정제 산출물의 modified/status/report-* 흔적을 읽어 사용자용 요약 엑셀을 만든다. -->
        <xsl:variable name="final" select="/"/>
        <xsl:variable name="map-title" select="normalize-space(/*/title[1])"/>
        <xsl:variable name="map-title-tokens" select="tokenize($map-title, '-')"/>
        <xsl:variable name="model-name" select="replace($map-title-tokens[2], '_(ICE|HEV|PHEV|PE2|PE)$', '')"/>
        <xsl:variable name="language-token-underscore" select="($map-title-tokens[matches(., '^[a-z]{2,3}_[A-Z]{2}$')])[1]"/>
        <xsl:variable name="language-position" select="(for $i in 1 to count($map-title-tokens) return if (matches($map-title-tokens[$i], '^[a-z]{2,3}$') and matches($map-title-tokens[$i + 1], '^[A-Z]{2}$')) then $i else ())[1]"/>
        <xsl:variable name="language-token" select="if ($language-token-underscore) then $language-token-underscore else if ($language-position) then concat($map-title-tokens[$language-position], '_', $map-title-tokens[$language-position + 1]) else ''"/>
        <xsl:variable name="language-code" select="if (lower-case($language-token) = 'fr_ca') then 'FRC' else if (lower-case($language-token) = ('ca_es', 'eu_es', 'gl_es')) then concat(tokenize($language-token, '_')[1], substring(tokenize($language-token, '_')[last()], 1, 1)) else tokenize($language-token, '_')[last()]"/>
        <xsl:variable name="first-topic-file" select="replace(tokenize((//*[local-name() = 'topicref'][normalize-space(@href) != ''][1]/@href, '')[1], '/')[last()], '\.dita$', '')"/>
        <xsl:variable name="title-prefix-sample" select="if ($model-name and $language-code and $first-topic-file != '') then concat($model-name, '_', $language-code, '_', $first-topic-file, '.dita') else ''"/>
        <xsl:variable name="file-name-format">
            <xsl:choose>
                <xsl:when test="$fileNameMode = 'TITLE_PREFIX' and $title-prefix-sample != ''">
                    <xsl:value-of select="$title-prefix-sample"/>
                </xsl:when>
                <xsl:when test="$fileNameMode = 'T0000'">t0000.dita</xsl:when>
                <xsl:otherwise>기존 파일명 유지</xsl:otherwise>
            </xsl:choose>
        </xsl:variable>
        <xsl:variable name="file-name-mode-label">
            <xsl:choose>
                <xsl:when test="$fileNameMode = 'TITLE_PREFIX'">CL4m_HEV-en_GB-2027-CH00 형식</xsl:when>
                <xsl:when test="$fileNameMode = 'T0000'">t0000 형식</xsl:when>
                <xsl:otherwise>기존 파일명 유지</xsl:otherwise>
            </xsl:choose>
        </xsl:variable>
        <xsl:processing-instruction name="mso-application">progid="Excel.Sheet"</xsl:processing-instruction>
        <Workbook>
            <Styles>
                <Style ss:ID="Header">
                    <Font ss:Bold="1"/>
                    <Alignment ss:Horizontal="Center" ss:Vertical="Center"/>
                    <Interior ss:Color="#D9EAF7" ss:Pattern="Solid"/>
                </Style>
                <Style ss:ID="Center">
                    <Alignment ss:Horizontal="Center" ss:Vertical="Center"/>
                </Style>
                <Style ss:ID="Wrap">
                    <Alignment ss:Vertical="Center" ss:WrapText="1"/>
                </Style>
            </Styles>

            <Worksheet ss:Name="요약">
                <Table>
                    <Column ss:Width="180"/>
                    <Column ss:Width="70"/>
                    <Column ss:Width="420"/>
                    <Row ss:Height="30">
                        <Cell ss:StyleID="Header"><Data ss:Type="String">항목</Data></Cell>
                        <Cell ss:StyleID="Header"><Data ss:Type="String">개수</Data></Cell>
                        <Cell ss:StyleID="Header"><Data ss:Type="String">수정 내용</Data></Cell>
                    </Row>
                    <xsl:call-template name="info-row">
                        <xsl:with-param name="label" select="'map title'"/>
                        <xsl:with-param name="value" select="$map-title"/>
                    </xsl:call-template>
                    <xsl:call-template name="info-row">
                        <xsl:with-param name="label" select="'파일명 형식'"/>
                        <xsl:with-param name="value" select="$file-name-format"/>
                    </xsl:call-template>
                    <xsl:call-template name="info-row">
                        <xsl:with-param name="label" select="'파일명 변경 옵션'"/>
                        <xsl:with-param name="value" select="$file-name-mode-label"/>
                    </xsl:call-template>
                    <xsl:call-template name="info-row">
                        <xsl:with-param name="label" select="'InputType'"/>
                        <xsl:with-param name="value" select="if (normalize-space($inputType) != '') then $inputType else '-'"/>
                    </xsl:call-template>
                    <xsl:call-template name="info-row">
                        <xsl:with-param name="label" select="'OutputType'"/>
                        <xsl:with-param name="value" select="if (normalize-space($outputType) != '') then $outputType else '-'"/>
                    </xsl:call-template>
                    <xsl:call-template name="info-row">
                        <xsl:with-param name="label" select="'deliveryTarget 삭제 옵션'"/>
                        <xsl:with-param name="value" select="if ($removeDeliveryTarget = 'Y') then '적용' else '미적용'"/>
                    </xsl:call-template>
                    <xsl:call-template name="info-row">
                        <xsl:with-param name="label" select="'Simple operation 삭제 옵션'"/>
                        <xsl:with-param name="value" select="if ($removeSimple = 'Y') then '적용' else '미적용'"/>
                    </xsl:call-template>
                    <xsl:call-template name="info-row">
                        <xsl:with-param name="label" select="'draft-comment 삭제 옵션'"/>
                        <xsl:with-param name="value" select="if ($deleteDraft = 'Y') then '적용' else '미적용'"/>
                    </xsl:call-template>
                    <xsl:call-template name="info-row">
                        <xsl:with-param name="label" select="'문장 DB 적용 옵션'"/>
                        <xsl:with-param name="value" select="if ($textDbApply = 'Y') then '적용' else '미적용'"/>
                    </xsl:call-template>
                    <xsl:call-template name="info-row">
                        <xsl:with-param name="label" select="'note type DB 적용 옵션'"/>
                        <xsl:with-param name="value" select="if ($noteDbApply = 'Y') then '적용' else '미적용'"/>
                    </xsl:call-template>
                    <xsl:call-template name="modified-row">
                        <xsl:with-param name="final" select="$final"/>
                        <xsl:with-param name="label" select="'tgroup 병합 대상 table'"/>
                        <xsl:with-param name="key" select="'table-merge'"/>
                        <xsl:with-param name="change" select="'tgroup이 2개 이상인 table을 하나의 tgroup으로 병합'"/>
                    </xsl:call-template>
                    <xsl:call-template name="modified-row">
                        <xsl:with-param name="final" select="$final"/>
                        <xsl:with-param name="label" select="'image href 파일명 치환'"/>
                        <xsl:with-param name="key" select="'image-href-renamed'"/>
                        <xsl:with-param name="change" select="'dark_symbol DB 기준으로 image 파일명을 치환'"/>
                    </xsl:call-template>
                    <xsl:call-template name="modified-row">
                        <xsl:with-param name="final" select="$final"/>
                        <xsl:with-param name="label" select="'inline image scale 설정'"/>
                        <xsl:with-param name="key" select="'image-scale-inline'"/>
                        <xsl:with-param name="change" select="'placement=inline인 image에 scale=15를 설정'"/>
                    </xsl:call-template>
                    <xsl:call-template name="modified-row">
                        <xsl:with-param name="final" select="$final"/>
                        <xsl:with-param name="label" select="'break image scale 설정'"/>
                        <xsl:with-param name="key" select="'image-scale-break'"/>
                        <xsl:with-param name="change" select="'placement=break인 image에 scale=95를 설정'"/>
                    </xsl:call-template>
                    <xsl:call-template name="modified-row">
                        <xsl:with-param name="final" select="$final"/>
                        <xsl:with-param name="label" select="'image 속성 삭제'"/>
                        <xsl:with-param name="key" select="'image-attr-removed'"/>
                        <xsl:with-param name="change" select="'image의 width, height, xoffset, yoffset, id 및 inline image의 outputclass 삭제'"/>
                    </xsl:call-template>
                    <xsl:call-template name="modified-row">
                        <xsl:with-param name="final" select="$final"/>
                        <xsl:with-param name="label" select="'term translate=no 추가'"/>
                        <xsl:with-param name="keys" select="('refinement-term-translate-no', 'auto-term-translate-no')"/>
                        <xsl:with-param name="change" select="'영문 유지 단어 term translate=no 추가'"/>
                    </xsl:call-template>
                    <xsl:call-template name="summary-row">
                        <xsl:with-param name="label" select="'Simple operation 삭제'"/>
                        <xsl:with-param name="count" select="number((($final/*/@report-simple-operation-removed), 0)[1])"/>
                        <xsl:with-param name="change" select="'section title=Simple operation 삭제한 수'"/>
                    </xsl:call-template>
                    <xsl:call-template name="summary-row">
                        <xsl:with-param name="label" select="'deliveryTarget 속성 삭제'"/>
                        <xsl:with-param name="count" select="number((($final/*/@report-deliverytarget-removed), 0)[1])"/>
                        <xsl:with-param name="change" select="'deliveryTarget 삭제한 수'"/>
                    </xsl:call-template>
                    <xsl:call-template name="summary-row">
                        <xsl:with-param name="label" select="'draft-comment 삭제'"/>
                        <xsl:with-param name="count" select="number((($final/*/@report-draft-comment-removed), 0)[1])"/>
                        <xsl:with-param name="change" select="'draft-comment 삭제한 수'"/>
                    </xsl:call-template>
                    <xsl:call-template name="summary-row">
                        <xsl:with-param name="label" select="'outputclass=review 삭제'"/>
                        <xsl:with-param name="count" select="number((($final/*/@report-review-outputclass-cleaned), 0)[1])"/>
                        <xsl:with-param name="change" select="'outputclass의 review 삭제한 수'"/>
                    </xsl:call-template>
                    <xsl:call-template name="summary-row">
                        <xsl:with-param name="label" select="'outputclass=legal 삭제'"/>
                        <xsl:with-param name="count" select="number((($final/*/@report-legal-outputclass-cleaned), 0)[1])"/>
                        <xsl:with-param name="change" select="'outputclass의 legal 삭제한 수'"/>
                    </xsl:call-template>
                    <xsl:call-template name="summary-row">
                        <xsl:with-param name="label" select="'text 문장 변경'"/>
                        <xsl:with-param name="count" select="count($final//*[self::p or self::cmd or self::title or self::shortdesc][@status = 'Textchanged'])"/>
                        <xsl:with-param name="change" select="'asis-tobe DB 기준으로 변경된 텍스트 수'"/>
                    </xsl:call-template>
                    <xsl:call-template name="summary-row">
                        <xsl:with-param name="label" select="'note type 변경'"/>
                        <xsl:with-param name="count" select="count($final//note[@status = 'changed'])"/>
                        <xsl:with-param name="change" select="'note_db 기준으로 type이 변경된 note 수'"/>
                    </xsl:call-template>
                </Table>
                <WorksheetOptions xmlns="urn:schemas-microsoft-com:office:excel">
                    <Zoom>85</Zoom>
                </WorksheetOptions>
            </Worksheet>
        </Workbook>
    </xsl:template>

    <xsl:template name="modified-row">
        <xsl:param name="final" as="document-node()"/>
        <xsl:param name="label" as="xs:string"/>
        <xsl:param name="key" as="xs:string?"/>
        <xsl:param name="keys" as="xs:string*" select="$key"/>
        <xsl:param name="change" as="xs:string"/>
        <!-- 여러 XSL에서 누적한 modified 토큰 중 해당 기능의 토큰만 세어 변경 건수를 계산한다. -->
        <xsl:call-template name="summary-row">
            <xsl:with-param name="label" select="$label"/>
            <xsl:with-param name="count" select="count($final//*[@modified][some $token in tokenize(@modified, '\s+') satisfies $token = $keys])"/>
            <xsl:with-param name="change" select="$change"/>
        </xsl:call-template>
    </xsl:template>

    <xsl:template name="info-row">
        <xsl:param name="label" as="xs:string"/>
        <xsl:param name="value" as="xs:string"/>
        <Row ss:Height="30">
            <Cell ss:StyleID="Center"><Data ss:Type="String"><xsl:value-of select="$label"/></Data></Cell>
            <Cell ss:StyleID="Center"><Data ss:Type="String">-</Data></Cell>
            <Cell ss:StyleID="Wrap"><Data ss:Type="String"><xsl:value-of select="$value"/></Data></Cell>
        </Row>
    </xsl:template>

    <xsl:template name="summary-row">
        <xsl:param name="label" as="xs:string"/>
        <xsl:param name="count" as="xs:numeric"/>
        <xsl:param name="change" as="xs:string"/>
        <Row ss:Height="30">
            <Cell ss:StyleID="Center"><Data ss:Type="String"><xsl:value-of select="$label"/></Data></Cell>
            <Cell ss:StyleID="Center"><Data ss:Type="Number"><xsl:value-of select="$count"/></Data></Cell>
            <Cell ss:StyleID="Wrap"><Data ss:Type="String"><xsl:value-of select="$change"/></Data></Cell>
        </Row>
    </xsl:template>

</xsl:stylesheet>
