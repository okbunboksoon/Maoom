<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="3.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:xs="http://www.w3.org/2001/XMLSchema"
                xmlns:ss="urn:schemas-microsoft-com:office:spreadsheet"
                xmlns="urn:schemas-microsoft-com:office:spreadsheet"
                exclude-result-prefixes="xs">

    <xsl:output method="xml" indent="yes" encoding="UTF-8" byte-order-mark="yes"/>
    <xsl:param name="fileNameMode" select="'DEFAULT'"/>
    <xsl:param name="inputType" select="''"/>
    <xsl:param name="outputType" select="''"/>
    <xsl:param name="removeSimple" select="'N'"/>
    <xsl:param name="removeDeliveryTarget" select="'N'"/>
    <xsl:param name="deleteDraft" select="'N'"/>
    <xsl:param name="textDbApply" select="'N'"/>
    <xsl:param name="noteDbApply" select="'N'"/>

    <!-- 상세 리포트용 복사 모드에서는 작업용 modified 속성을 제외한다. -->
    <xsl:mode name="report-clean" on-no-match="shallow-copy"/>
    <xsl:template match="@modified" mode="report-clean"/>

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
        <!-- 첫 topicref에는 이미 최종 prefix가 적용되어 있으므로 차종/언어코드를 다시 붙이지 않는다. -->
        <xsl:variable name="title-prefix-sample" select="if ($first-topic-file != '') then concat($first-topic-file, '.dita') else ''"/>
        <xsl:variable name="file-name-format">
            <xsl:choose>
                <xsl:when test="$fileNameMode = 'TITLE_PREFIX' and $title-prefix-sample != ''">
                    <xsl:value-of select="$title-prefix-sample"/>
                </xsl:when>
                <xsl:when test="$fileNameMode = 'T00000'">t0000.dita</xsl:when>
                <xsl:otherwise>기존 파일명 유지</xsl:otherwise>
            </xsl:choose>
        </xsl:variable>
        <xsl:variable name="file-name-mode-label">
            <xsl:choose>
                <xsl:when test="$fileNameMode = 'TITLE_PREFIX'">차종-연료타입-언어코드-연식-t0000 형식</xsl:when>
                <xsl:when test="$fileNameMode = 'T00000'">t0000 형식</xsl:when>
                <xsl:otherwise>기존 파일명 유지</xsl:otherwise>
            </xsl:choose>
        </xsl:variable>
        <xsl:processing-instruction name="mso-application">progid="Excel.Sheet"</xsl:processing-instruction>
        <Workbook>
            <Styles>
                <Style ss:ID="Header">
                    <Font ss:Bold="1"/>
                    <Alignment ss:Horizontal="Center" ss:Vertical="Center"/>
                    <Interior ss:Color="#E7E6E6" ss:Pattern="Solid"/>
                    <Borders>
                        <Border ss:Position="Bottom" ss:LineStyle="Continuous" ss:Weight="1" ss:Color="#BFBFBF"/>
                        <Border ss:Position="Left" ss:LineStyle="Continuous" ss:Weight="1" ss:Color="#BFBFBF"/>
                        <Border ss:Position="Right" ss:LineStyle="Continuous" ss:Weight="1" ss:Color="#BFBFBF"/>
                        <Border ss:Position="Top" ss:LineStyle="Continuous" ss:Weight="1" ss:Color="#BFBFBF"/>
                    </Borders>
                </Style>
                <Style ss:ID="Center">
                    <Alignment ss:Horizontal="Center" ss:Vertical="Center"/>
                    <Borders>
                        <Border ss:Position="Bottom" ss:LineStyle="Continuous" ss:Weight="1" ss:Color="#D9D9D9"/>
                        <Border ss:Position="Left" ss:LineStyle="Continuous" ss:Weight="1" ss:Color="#D9D9D9"/>
                        <Border ss:Position="Right" ss:LineStyle="Continuous" ss:Weight="1" ss:Color="#D9D9D9"/>
                        <Border ss:Position="Top" ss:LineStyle="Continuous" ss:Weight="1" ss:Color="#D9D9D9"/>
                    </Borders>
                </Style>
                <Style ss:ID="Wrap">
                    <Alignment ss:Vertical="Center" ss:WrapText="1"/>
                    <Borders>
                        <Border ss:Position="Bottom" ss:LineStyle="Continuous" ss:Weight="1" ss:Color="#D9D9D9"/>
                        <Border ss:Position="Left" ss:LineStyle="Continuous" ss:Weight="1" ss:Color="#D9D9D9"/>
                        <Border ss:Position="Right" ss:LineStyle="Continuous" ss:Weight="1" ss:Color="#D9D9D9"/>
                        <Border ss:Position="Top" ss:LineStyle="Continuous" ss:Weight="1" ss:Color="#D9D9D9"/>
                    </Borders>
                </Style>
            </Styles>

            <Worksheet ss:Name="요약">
                <Table>
                    <Column ss:Width="180"/>
                    <Column ss:Width="70"/>
                    <Column ss:Width="420"/>
                    <Row ss:Height="30">
                        <Cell ss:StyleID="Header" ss:MergeAcross="2"><Data ss:Type="String">옵션 설정</Data></Cell>
                    </Row>
                    <!-- map 문서에서 추출한 제목을 요약 시트의 map title 항목에 표시한다. -->
                    <xsl:call-template name="info-row">
                        <xsl:with-param name="label" select="'map title'"/>
                        <xsl:with-param name="value" select="$map-title"/>
                    </xsl:call-template>
                    <!-- 입력 파일명에서 판별한 파일명 형식을 요약 시트에 표시한다. -->
                    <xsl:call-template name="info-row">
                        <xsl:with-param name="label" select="'파일명 형식'"/>
                        <xsl:with-param name="value" select="$file-name-format"/>
                    </xsl:call-template>
                    <!-- 실행 시 선택한 파일명 변경 방식을 사용자가 확인할 수 있도록 표시한다. -->
                    <xsl:call-template name="info-row">
                        <xsl:with-param name="label" select="'파일명 변경 옵션'"/>
                        <xsl:with-param name="value" select="$file-name-mode-label"/>
                    </xsl:call-template>
                    <!-- 전달받은 inputType 값을 표시하며, 값이 비어 있으면 하이픈(-)으로 표시한다. -->
                    <xsl:call-template name="info-row">
                        <xsl:with-param name="label" select="'InputType'"/>
                        <xsl:with-param name="value" select="if (normalize-space($inputType) != '') then $inputType else '-'"/>
                    </xsl:call-template>
                    <!-- 전달받은 outputType 값을 표시하며, 값이 비어 있으면 하이픈(-)으로 표시한다. -->
                    <xsl:call-template name="info-row">
                        <xsl:with-param name="label" select="'OutputType'"/>
                        <xsl:with-param name="value" select="if (normalize-space($outputType) != '') then $outputType else '-'"/>
                    </xsl:call-template>
                    <!-- deliveryTarget 삭제 옵션이 Y이면 적용, 그 외에는 미적용으로 표시한다. -->
                    <xsl:call-template name="info-row">
                        <xsl:with-param name="label" select="'deliveryTarget 삭제 옵션'"/>
                        <xsl:with-param name="value" select="if ($removeDeliveryTarget = 'Y') then '적용' else '미적용'"/>
                    </xsl:call-template>
                    <!-- Simple operation 삭제 옵션이 Y이면 적용, 그 외에는 미적용으로 표시한다. -->
                    <xsl:call-template name="info-row">
                        <xsl:with-param name="label" select="'Simple operation 삭제 옵션'"/>
                        <xsl:with-param name="value" select="if ($removeSimple = 'Y') then '적용' else '미적용'"/>
                    </xsl:call-template>
                    <!-- draft-comment 삭제 옵션이 Y이면 적용, 그 외에는 미적용으로 표시한다. -->
                    <xsl:call-template name="info-row">
                        <xsl:with-param name="label" select="'draft-comment 삭제 옵션'"/>
                        <xsl:with-param name="value" select="if ($deleteDraft = 'Y') then '적용' else '미적용'"/>
                    </xsl:call-template>
                    <!-- 문장 치환 DB 적용 옵션이 Y이면 적용, 그 외에는 미적용으로 표시한다. -->
                    <xsl:call-template name="info-row">
                        <xsl:with-param name="label" select="'문장 DB 적용 옵션'"/>
                        <xsl:with-param name="value" select="if ($textDbApply = 'Y') then '적용' else '미적용'"/>
                    </xsl:call-template>
                    <!-- note type 변경 DB 적용 옵션이 Y이면 적용, 그 외에는 미적용으로 표시한다. -->
                    <xsl:call-template name="info-row">
                        <xsl:with-param name="label" select="'note type DB 적용 옵션'"/>
                        <xsl:with-param name="value" select="if ($noteDbApply = 'Y') then '적용' else '미적용'"/>
                    </xsl:call-template>
                    <Row ss:Height="30">
                        <Cell ss:StyleID="Header" ss:MergeAcross="2"><Data ss:Type="String">정제 결과</Data></Cell>
                    </Row>
                    <Row ss:Height="30">
                        <Cell ss:StyleID="Header"><Data ss:Type="String">항목</Data></Cell>
                        <Cell ss:StyleID="Header"><Data ss:Type="String">개수</Data></Cell>
                        <Cell ss:StyleID="Header"><Data ss:Type="String">처리 내용</Data></Cell>
                    </Row>
                    <!-- 정제 결과 행은 먼저 생성한 뒤 추가 → 수정 → 삭제 → 검출 순서로 정렬한다. -->
                    <xsl:variable name="result-rows">
                    <!-- modified 속성에 table-merge가 기록된 요소를 세어 tgroup 병합 대상 table 수를 표시한다. -->
                    <xsl:call-template name="modified-row">
                        <xsl:with-param name="final" select="$final"/>
                        <xsl:with-param name="label" select="'tgroup 병합 대상 table'"/>
                        <xsl:with-param name="key" select="'table-merge'"/>
                        <xsl:with-param name="change" select="'tgroup이 2개 이상인 table을 하나의 tgroup으로 병합'"/>
                    </xsl:call-template>
                    <!-- modified 속성에 image-href-renamed가 기록된 image를 세어 파일명 치환 수를 표시한다. -->
                    <xsl:call-template name="modified-row">
                        <xsl:with-param name="final" select="$final"/>
                        <xsl:with-param name="label" select="'image href 파일명 치환'"/>
                        <xsl:with-param name="key" select="'image-href-renamed'"/>
                        <xsl:with-param name="change" select="'dark_symbol DB 기준으로 image 파일명을 치환'"/>
                    </xsl:call-template>
                    <!-- image scale 설정 항목 Excel 리포트 출력 비활성화
                    아래 두 modified-row 호출은 각각 image-scale-inline과 image-scale-break 토큰을 집계한다.
                    활성화하면 placement=inline의 scale=15 설정 수와 placement=break의 scale=95 설정 수가 표시된다.
                    현재 0160 단계에서 scale 설정을 사용하지 않으므로 Excel 요약 시트에도 출력하지 않는다.
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
                    -->
                    <!-- modified 속성에 image-attr-removed가 기록된 image를 세어 불필요한 속성 삭제 수를 표시한다. -->
                    <xsl:call-template name="modified-row">
                        <xsl:with-param name="final" select="$final"/>
                        <xsl:with-param name="label" select="'image 속성 삭제'"/>
                        <xsl:with-param name="key" select="'image-attr-removed'"/>
                        <xsl:with-param name="change" select="'image의 width, height, xoffset, yoffset, id 및 inline image의 outputclass 삭제'"/>
                    </xsl:call-template>
                    <!-- 두 종류의 term translate=no 처리 토큰을 함께 세어 영문 유지 단어 처리 수를 표시한다. -->
                    <xsl:call-template name="modified-row">
                        <xsl:with-param name="final" select="$final"/>
                        <xsl:with-param name="label" select="'term translate=no 추가'"/>
                        <xsl:with-param name="keys" select="('refinement-term-translate-no', 'auto-term-translate-no')"/>
                        <xsl:with-param name="change" select="'영문 유지 단어 term translate=no 추가'"/>
                    </xsl:call-template>
                    <!-- 0120 단계에서 modified="Y"가 붙은 빈 토픽을 세어 내용 없는 DITA 파일 수를 표시한다. -->
                    <xsl:call-template name="modified-row">
                        <xsl:with-param name="final" select="$final"/>
                        <xsl:with-param name="label" select="'내용없는 dita 찾기'"/>
                        <xsl:with-param name="key" select="'Y'"/>
                        <xsl:with-param name="change" select="'타이틀만 존재하거나 하위에 내용없는 dita 파일'"/>
                    </xsl:call-template>
                    <!-- modified 속성에 empty가 기록된 요소를 세어 속성과 내용이 모두 없는 태그 수를 표시한다. -->
                    <xsl:call-template name="modified-row">
                        <xsl:with-param name="final" select="$final"/>
                        <xsl:with-param name="label" select="'빈 태그 찾기'"/>
                        <xsl:with-param name="key" select="'empty'"/>
                        <xsl:with-param name="change" select="'속성과 내용이 모두 없는 태그'"/>
                    </xsl:call-template>
                    <!-- modified 속성에 li-direct-text가 기록된 li를 세어 직접 텍스트 사용 수를 표시한다. -->
                    <xsl:call-template name="modified-row">
                        <xsl:with-param name="final" select="$final"/>
                        <xsl:with-param name="label" select="'li 직접 텍스트 찾기'"/>
                        <xsl:with-param name="key" select="'li-direct-text'"/>
                        <xsl:with-param name="change" select="'하위 태그로 감싸지 않고 li 바로 아래에 작성된 텍스트'"/>
                    </xsl:call-template>
                    <!-- modified 속성에 step-missing-cmd가 기록된 step을 세어 필수 cmd 누락 수를 표시한다. -->
                    <xsl:call-template name="modified-row">
                        <xsl:with-param name="final" select="$final"/>
                        <xsl:with-param name="label" select="'step cmd 누락 찾기'"/>
                        <xsl:with-param name="key" select="'step-missing-cmd'"/>
                        <xsl:with-param name="change" select="'task의 step 바로 아래에 필수 cmd 자식 요소가 없는 구조'"/>
                    </xsl:call-template>
                    <!-- modified 속성에 sentence-space-trimmed가 기록된 문장 요소를 세어 앞뒤 공백 제거 수를 표시한다. -->
                    <xsl:call-template name="modified-row">
                        <xsl:with-param name="final" select="$final"/>
                        <xsl:with-param name="label" select="'문장 앞뒤 공백 제거'"/>
                        <xsl:with-param name="key" select="'sentence-space-trimmed'"/>
                        <xsl:with-param name="change" select="'p, title, shortdesc, cmd 문장 맨 앞과 맨 뒤의 불필요한 공백 제거'"/>
                    </xsl:call-template>
                    <!-- modified 속성에 image-break-align-centered가 기록된 image를 세어 가운데 정렬 변경 수를 표시한다. -->
                    <xsl:call-template name="modified-row">
                        <xsl:with-param name="final" select="$final"/>
                        <xsl:with-param name="label" select="'break image 가운데 정렬'"/>
                        <xsl:with-param name="key" select="'image-break-align-centered'"/>
                        <xsl:with-param name="change" select="'placement=break이고 align이 left 또는 right인 image를 align=center로 변경'"/>
                    </xsl:call-template>
                    <!-- modified 속성에 image-server-href가 기록된 image를 세어 서버 주소 검출 수를 표시한다. -->
                    <xsl:call-template name="modified-row">
                        <xsl:with-param name="final" select="$final"/>
                        <xsl:with-param name="label" select="'image 서버 href 검출'"/>
                        <xsl:with-param name="key" select="'image-server-href'"/>
                        <xsl:with-param name="change" select="'http 또는 https 서버 주소가 사용된 image href 검출'"/>
                    </xsl:call-template>
                    <!-- 비 EPS image href 요약 행 임시 비활성화. 다시 사용할 때 xsl:if를 제거한다. -->
                    <xsl:if test="false()">
                        <xsl:call-template name="modified-row">
                            <xsl:with-param name="final" select="$final"/>
                            <xsl:with-param name="label" select="'image 비 EPS 확장자 검출'"/>
                            <xsl:with-param name="key" select="'image-href-non-eps'"/>
                            <xsl:with-param name="change" select="'href 확장자가 .eps가 아닌 image 검출(원본 href 유지)'"/>
                        </xsl:call-template>
                    </xsl:if>
                    <!-- .dita# 앞에 디렉터리 경로가 붙은 xref를 세어 경로 포함 오류 수를 표시한다. -->
                    <xsl:call-template name="modified-row">
                        <xsl:with-param name="final" select="$final"/>
                        <xsl:with-param name="label" select="'xref href 경로 포함 오류'"/>
                        <xsl:with-param name="key" select="'xref-href-path-invalid'"/>
                        <xsl:with-param name="change" select="'.dita# 앞에 디렉터리 경로가 포함된 xref 검출'"/>
                    </xsl:call-template>
                    <!-- .dita# 뒤 fragment에 /하위ID가 붙은 xref를 세어 element ID 포함 오류 수를 표시한다. -->
                    <xsl:call-template name="modified-row">
                        <xsl:with-param name="final" select="$final"/>
                        <xsl:with-param name="label" select="'xref href element ID 오류'"/>
                        <xsl:with-param name="key" select="'xref-href-element-id-invalid'"/>
                        <xsl:with-param name="change" select="'.dita# 뒤 fragment에 /하위ID가 포함된 xref 검출'"/>
                    </xsl:call-template>
                    <!-- 최종 문서 루트의 report-simple-operation-removed 값을 읽어 삭제된 Simple operation section 수를 표시한다. -->
                    <xsl:call-template name="summary-row">
                        <xsl:with-param name="label" select="'indexterm 삭제'"/>
                        <xsl:with-param name="count" select="number((($final/*/@report-indexterm-removed), 0)[1])"/>
                        <xsl:with-param name="change" select="'map title에 ko_KR과 Quick이 모두 포함된 경우 삭제한 indexterm 수'"/>
                    </xsl:call-template>
                    <xsl:call-template name="summary-row">
                        <xsl:with-param name="label" select="'Simple operation 삭제'"/>
                        <xsl:with-param name="count" select="number((($final/*/@report-simple-operation-removed), 0)[1])"/>
                        <xsl:with-param name="change" select="'section title=Simple operation 삭제한 수'"/>
                    </xsl:call-template>
                    <!-- 최종 문서 루트의 report-deliverytarget-removed 값을 읽어 삭제된 deliveryTarget 속성 수를 표시한다. -->
                    <xsl:call-template name="summary-row">
                        <xsl:with-param name="label" select="'deliveryTarget 속성 삭제'"/>
                        <xsl:with-param name="count" select="number((($final/*/@report-deliverytarget-removed), 0)[1])"/>
                        <xsl:with-param name="change" select="'deliveryTarget 삭제한 수'"/>
                    </xsl:call-template>
                    <!-- 최종 문서 루트의 report-draft-comment-removed 값을 읽어 삭제된 draft-comment 수를 표시한다. -->
                    <xsl:call-template name="summary-row">
                        <xsl:with-param name="label" select="'draft-comment 삭제'"/>
                        <xsl:with-param name="count" select="number((($final/*/@report-draft-comment-removed), 0)[1])"/>
                        <xsl:with-param name="change" select="'draft-comment 삭제한 수'"/>
                    </xsl:call-template>
                    <!-- 최종 문서 루트의 report-review-outputclass-cleaned 값을 읽어 제거된 review 토큰 수를 표시한다. -->
                    <xsl:call-template name="summary-row">
                        <xsl:with-param name="label" select="'outputclass=review 삭제'"/>
                        <xsl:with-param name="count" select="number((($final/*/@report-review-outputclass-cleaned), 0)[1])"/>
                        <xsl:with-param name="change" select="'outputclass의 review 삭제한 수'"/>
                    </xsl:call-template>
                    <!-- 최종 문서 루트의 report-legal-outputclass-cleaned 값을 읽어 제거된 legal 토큰 수를 표시한다. -->
                    <xsl:call-template name="summary-row">
                        <xsl:with-param name="label" select="'outputclass=legal 삭제'"/>
                        <xsl:with-param name="count" select="number((($final/*/@report-legal-outputclass-cleaned), 0)[1])"/>
                        <xsl:with-param name="change" select="'outputclass의 legal 삭제한 수'"/>
                    </xsl:call-template>
                    <!-- p, cmd, title, shortdesc 중 status=Textchanged인 요소를 세어 문장 DB 치환 수를 표시한다. -->
                    <xsl:call-template name="summary-row">
                        <xsl:with-param name="label" select="'text 문장 변경'"/>
                        <xsl:with-param name="count" select="count($final//*[self::p or self::cmd or self::title or self::shortdesc][@status = 'Textchanged'])"/>
                        <xsl:with-param name="change" select="'asis-tobe DB 기준으로 변경된 텍스트 수'"/>
                    </xsl:call-template>
                    <!-- status=changed인 note 요소를 세어 note DB에 의해 type이 변경된 수를 표시한다. -->
                    <xsl:call-template name="summary-row">
                        <xsl:with-param name="label" select="'note type 변경'"/>
                        <xsl:with-param name="count" select="count($final//note[@status = 'changed'])"/>
                        <xsl:with-param name="change" select="'note_db 기준으로 type이 변경된 note 수'"/>
                    </xsl:call-template>
                    </xsl:variable>
                    <xsl:variable name="add-labels" select="('term translate=no 추가')"/>
                    <xsl:variable name="delete-labels" select="(
                        'image 속성 삭제', 'indexterm 삭제', 'Simple operation 삭제', 'deliveryTarget 속성 삭제',
                        'draft-comment 삭제', 'outputclass=review 삭제', 'outputclass=legal 삭제')"/>
                    <xsl:variable name="detect-labels" select="(
                        '내용없는 dita 찾기', '빈 태그 찾기', 'li 직접 텍스트 찾기', 'step cmd 누락 찾기',
                        'image 서버 href 검출', 'image 비 EPS 확장자 검출', 'xref href 경로 포함 오류',
                        'xref href element ID 오류')"/>
                    <Row ss:Height="30"><Cell ss:StyleID="Header" ss:MergeAcross="2"><Data ss:Type="String">추가</Data></Cell></Row>
                    <xsl:sequence select="$result-rows/ss:Row[ss:Cell[1]/ss:Data = $add-labels]"/>
                    <Row ss:Height="30"><Cell ss:StyleID="Header" ss:MergeAcross="2"><Data ss:Type="String">수정</Data></Cell></Row>
                    <xsl:sequence select="$result-rows/ss:Row[not(ss:Cell[1]/ss:Data = ($add-labels, $delete-labels, $detect-labels))]"/>
                    <Row ss:Height="30"><Cell ss:StyleID="Header" ss:MergeAcross="2"><Data ss:Type="String">삭제</Data></Cell></Row>
                    <xsl:sequence select="$result-rows/ss:Row[ss:Cell[1]/ss:Data = $delete-labels]"/>
                    <Row ss:Height="30"><Cell ss:StyleID="Header" ss:MergeAcross="2"><Data ss:Type="String">검출</Data></Cell></Row>
                    <xsl:sequence select="$result-rows/ss:Row[ss:Cell[1]/ss:Data = $detect-labels]"/>
                </Table>
                <WorksheetOptions xmlns="urn:schemas-microsoft-com:office:excel">
                    <Zoom>85</Zoom>
                </WorksheetOptions>
            </Worksheet>
            <Worksheet ss:Name="내용없는 DITA 파일">
                <Table>
                    <Column ss:Width="250"/>
                    <Column ss:Width="420"/>
                    <Row ss:Height="30">
                        <Cell ss:StyleID="Header"><Data ss:Type="String">파일번호</Data></Cell>
                        <Cell ss:StyleID="Header"><Data ss:Type="String">타이틀</Data></Cell>
                    </Row>
                    <!-- 빈 토픽 상세 목록은 topicref href와 병합된 토픽 title만 별도 시트에 남긴다. -->
                    <xsl:for-each select="$final//*[local-name() = 'topicref'][some $token in tokenize(@modified, '\s+') satisfies $token = 'Y']">
                        <Row ss:Height="30">
                            <Cell ss:StyleID="Center">
                                <Data ss:Type="String"><xsl:value-of select="@href"/></Data>
                            </Cell>
                            <Cell ss:StyleID="Center">
                                <Data ss:Type="String">
                                    <xsl:value-of select="normalize-space((*[local-name() = ('topic', 'concept', 'task', 'reference')][1]/*[local-name() = 'title'][1]))"/>
                                </Data>
                            </Cell>
                        </Row>
                    </xsl:for-each>
                </Table>
                <WorksheetOptions xmlns="urn:schemas-microsoft-com:office:excel">
                    <Zoom>85</Zoom>
                </WorksheetOptions>
            </Worksheet>
            <Worksheet ss:Name="빈 태그 찾기">
                <Table>
                    <Column ss:Width="250"/>
                    <Column ss:Width="420"/>
                    <Row ss:Height="30">
                        <Cell ss:StyleID="Header"><Data ss:Type="String">파일명</Data></Cell>
                        <Cell ss:StyleID="Header"><Data ss:Type="String">내용</Data></Cell>
                    </Row>
                    <!-- empty 토큰이 붙은 요소별로 변경 후 topicref 파일명과 빈 태그 이름을 표시한다. -->
                    <xsl:for-each select="$final//*[@modified][some $token in tokenize(@modified, '\s+') satisfies $token = 'empty']">
                        <Row ss:Height="30">
                            <Cell ss:StyleID="Center">
                                <Data ss:Type="String"><xsl:value-of select="ancestor::*[local-name() = 'topicref'][1]/@href"/></Data>
                            </Cell>
                            <Cell ss:StyleID="Wrap">
                                <Data ss:Type="String"><xsl:value-of select="concat('&lt;', name(), '/&gt;')"/></Data>
                            </Cell>
                        </Row>
                    </xsl:for-each>
                </Table>
                <WorksheetOptions xmlns="urn:schemas-microsoft-com:office:excel">
                    <Zoom>85</Zoom>
                </WorksheetOptions>
            </Worksheet>
            <Worksheet ss:Name="li-step 구조 오류">
                <Table>
                    <Column ss:Width="250"/>
                    <Column ss:Width="130"/>
                    <Column ss:Width="1000"/>
                    <Row ss:Height="30">
                        <Cell ss:StyleID="Header"><Data ss:Type="String">파일명</Data></Cell>
                        <Cell ss:StyleID="Header"><Data ss:Type="String">오류 유형</Data></Cell>
                        <Cell ss:StyleID="Header"><Data ss:Type="String">내용</Data></Cell>
                    </Row>
                    <!-- li 직접 텍스트와 step cmd 누락을 한 시트에서 오류 유형별로 구분해 표시한다. -->
                    <xsl:for-each select="$final//*[(local-name() = 'li' or local-name() = 'step')][@modified][some $token in tokenize(@modified, '\s+') satisfies $token = ('li-direct-text', 'step-missing-cmd')]">
                        <xsl:variable name="report-content">
                            <xsl:apply-templates select="." mode="report-clean"/>
                        </xsl:variable>
                        <Row ss:Height="30">
                            <Cell ss:StyleID="Center">
                                <Data ss:Type="String"><xsl:value-of select="ancestor::*[local-name() = 'topicref'][1]/@href"/></Data>
                            </Cell>
                            <Cell ss:StyleID="Center">
                                <Data ss:Type="String"><xsl:value-of select="if (local-name() = 'li') then 'li 직접 텍스트' else 'step cmd 누락'"/></Data>
                            </Cell>
                            <Cell ss:StyleID="Wrap">
                                <Data ss:Type="String"><xsl:value-of select="serialize($report-content/*, map{'method': 'xml', 'omit-xml-declaration': true()})"/></Data>
                            </Cell>
                        </Row>
                    </xsl:for-each>
                </Table>
                <WorksheetOptions xmlns="urn:schemas-microsoft-com:office:excel">
                    <Zoom>85</Zoom>
                </WorksheetOptions>
            </Worksheet>
            <Worksheet ss:Name="image 서버 href 검출">
                <Table>
                    <Column ss:Width="250"/>
                    <Column ss:Width="1000"/>
                    <Row ss:Height="30">
                        <Cell ss:StyleID="Header"><Data ss:Type="String">파일명</Data></Cell>
                        <Cell ss:StyleID="Header"><Data ss:Type="String">이미지 정보</Data></Cell>
                    </Row>
                    <!-- 서버 href 검출 image별로 파일명과 image를 바로 감싸는 부모 태그 전체를 표시한다. -->
                    <xsl:for-each select="$final//*[local-name() = 'image'][@modified][some $token in tokenize(@modified, '\s+') satisfies $token = 'image-server-href']">
                        <xsl:variable name="report-content">
                            <xsl:apply-templates select="parent::*" mode="report-clean"/>
                        </xsl:variable>
                        <Row ss:Height="30">
                            <Cell ss:StyleID="Center">
                                <Data ss:Type="String"><xsl:value-of select="ancestor::*[local-name() = 'topicref'][1]/@href"/></Data>
                            </Cell>
                            <Cell ss:StyleID="Wrap">
                                <Data ss:Type="String"><xsl:value-of select="serialize($report-content/*, map{'method': 'xml', 'omit-xml-declaration': true()})"/></Data>
                            </Cell>
                        </Row>
                    </xsl:for-each>
                </Table>
                <WorksheetOptions xmlns="urn:schemas-microsoft-com:office:excel">
                    <Zoom>85</Zoom>
                </WorksheetOptions>
            </Worksheet>
            <!-- 비 EPS image href 상세 시트 임시 비활성화. 다시 사용할 때 xsl:if를 제거한다. -->
            <xsl:if test="false()">
            <Worksheet ss:Name="image 비 EPS 확장자">
                <Table>
                    <Column ss:Width="250"/>
                    <Column ss:Width="1000"/>
                    <Row ss:Height="30">
                        <Cell ss:StyleID="Header"><Data ss:Type="String">파일명</Data></Cell>
                        <Cell ss:StyleID="Header"><Data ss:Type="String">이미지 정보</Data></Cell>
                    </Row>
                    <!-- 비 EPS image별로 파일명과 image를 바로 감싸는 부모 태그 전체를 표시한다. -->
                    <xsl:for-each select="$final//*[local-name() = 'image'][@modified][some $token in tokenize(@modified, '\s+') satisfies $token = 'image-href-non-eps']">
                        <xsl:variable name="report-content">
                            <xsl:apply-templates select="parent::*" mode="report-clean"/>
                        </xsl:variable>
                        <Row ss:Height="30">
                            <Cell ss:StyleID="Center">
                                <Data ss:Type="String"><xsl:value-of select="ancestor::*[local-name() = 'topicref'][1]/@href"/></Data>
                            </Cell>
                            <Cell ss:StyleID="Wrap">
                                <Data ss:Type="String"><xsl:value-of select="serialize($report-content/*, map{'method': 'xml', 'omit-xml-declaration': true()})"/></Data>
                            </Cell>
                        </Row>
                    </xsl:for-each>
                </Table>
                <WorksheetOptions xmlns="urn:schemas-microsoft-com:office:excel">
                    <Zoom>85</Zoom>
                </WorksheetOptions>
            </Worksheet>
            </xsl:if>
            <Worksheet ss:Name="xref href 경로 포함 오류">
                <Table>
                    <Column ss:Width="250"/>
                    <Column ss:Width="1000"/>
                    <Row ss:Height="30">
                        <Cell ss:StyleID="Header"><Data ss:Type="String">파일명</Data></Cell>
                        <Cell ss:StyleID="Header"><Data ss:Type="String">xref 정보</Data></Cell>
                    </Row>
                    <!-- 경로 포함 오류 토큰이 붙은 xref별로 파일명과 xref를 바로 감싸는 부모 태그 전체를 표시한다. -->
                    <xsl:for-each select="$final//*[local-name() = 'xref'][@modified][some $token in tokenize(@modified, '\s+') satisfies $token = 'xref-href-path-invalid']">
                        <xsl:variable name="report-content">
                            <xsl:apply-templates select="parent::*" mode="report-clean"/>
                        </xsl:variable>
                        <Row ss:Height="30">
                            <Cell ss:StyleID="Center">
                                <Data ss:Type="String"><xsl:value-of select="ancestor::*[local-name() = 'topicref'][1]/@href"/></Data>
                            </Cell>
                            <Cell ss:StyleID="Wrap">
                                <Data ss:Type="String"><xsl:value-of select="serialize($report-content/*, map{'method': 'xml', 'omit-xml-declaration': true()})"/></Data>
                            </Cell>
                        </Row>
                    </xsl:for-each>
                </Table>
                <WorksheetOptions xmlns="urn:schemas-microsoft-com:office:excel">
                    <Zoom>85</Zoom>
                </WorksheetOptions>
            </Worksheet>
            <Worksheet ss:Name="xref element ID 오류">
                <Table>
                    <Column ss:Width="250"/>
                    <Column ss:Width="1000"/>
                    <Row ss:Height="30">
                        <Cell ss:StyleID="Header"><Data ss:Type="String">파일명</Data></Cell>
                        <Cell ss:StyleID="Header"><Data ss:Type="String">xref 정보</Data></Cell>
                    </Row>
                    <!-- element ID 오류 토큰이 붙은 xref별로 파일명과 xref를 바로 감싸는 부모 태그 전체를 표시한다. -->
                    <xsl:for-each select="$final//*[local-name() = 'xref'][@modified][some $token in tokenize(@modified, '\s+') satisfies $token = 'xref-href-element-id-invalid']">
                        <xsl:variable name="report-content">
                            <xsl:apply-templates select="parent::*" mode="report-clean"/>
                        </xsl:variable>
                        <Row ss:Height="30">
                            <Cell ss:StyleID="Center">
                                <Data ss:Type="String"><xsl:value-of select="ancestor::*[local-name() = 'topicref'][1]/@href"/></Data>
                            </Cell>
                            <Cell ss:StyleID="Wrap">
                                <Data ss:Type="String"><xsl:value-of select="serialize($report-content/*, map{'method': 'xml', 'omit-xml-declaration': true()})"/></Data>
                            </Cell>
                        </Row>
                    </xsl:for-each>
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
