<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="2.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:o="urn:schemas-microsoft-com:office:office"
    xmlns:x="urn:schemas-microsoft-com:office:excel"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xmlns:html="http://www.w3.org/TR/REC-html40"
    xmlns="urn:schemas-microsoft-com:office:spreadsheet"
    xmlns:ss="urn:schemas-microsoft-com:office:spreadsheet"
    xpath-default-namespace="urn:schemas-microsoft-com:office:spreadsheet"
    exclude-result-prefixes="xs o x ss html">

    <!-- 첫 행 Cell 텍스트 배열 -->
    <xsl:variable name="cells" select="/ss:Workbook/ss:Worksheet/ss:Table/ss:Row[1]/ss:Cell/ss:Data"/>

    <!-- 좌측, 우측 사양군 시작과 끝 -->
    <xsl:variable name="left-start" select="index-of($cells, 'DESCRIPTION')[1] + 1" as="xs:integer"/>
    <xsl:variable name="left-end" select="index-of($cells, '중요항목')[1] - 1" as="xs:integer"/>
    <xsl:variable name="right-start" select="index-of($cells, 'DESCRIPTION')[2] + 1" as="xs:integer"/>
    <xsl:variable name="right-end" select="index-of($cells, '중요항목')[2] - 1" as="xs:integer"/>

    <xsl:variable name="left-headers" select="$cells[position() ge $left-start and position() le $left-end]"/>
    <xsl:variable name="right-headers" select="$cells[position() ge $right-start and position() le $right-end]"/>

	<xsl:variable name="common-specs">
		<!-- 공통 사양군 매핑 -->
		<common>
			<xsl:for-each select="$left-headers">
				<xsl:variable name="l-text" select="."/>
				<xsl:variable name="li" select="$left-start + position() - 1"/>
				<xsl:for-each select="$right-headers">
					<xsl:variable name="r-text" select="."/>
					<xsl:variable name="ri" select="$right-start + position() - 1"/>
					<xsl:if test="$l-text = $r-text">
						<specs>
							<left><xsl:value-of select="$li"/></left>
							<right><xsl:value-of select="$ri"/></right>
						</specs>
					</xsl:if>
				</xsl:for-each>
			</xsl:for-each>
		</common>
	</xsl:variable>

    <xsl:template match="@* | node()">
        <xsl:copy>
            <xsl:apply-templates select="@* | node()"/>
        </xsl:copy>
    </xsl:template>

    <xsl:template match="ss:Row">
        <xsl:choose>
            <!-- 첫 번째 Row(헤더)는 그대로 출력 -->
            <xsl:when test="not(preceding-sibling::ss:Row)">
                <Row>
                    <xsl:apply-templates select="@* | node()"/>
                </Row>
            </xsl:when>

            <!-- 이후 행은 비교 템플릿으로 -->
            <xsl:otherwise>
                <xsl:call-template name="compare_cells"/>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>

    <!-- ================================================
         3) 실제 비교 로직(compare_cells)
         - AFTER / BEFORE 값 추출
         - 삭제 / 신규 판단
         - 변경 여부 판단
         ================================================= -->

    <xsl:template name="compare_cells">
        <!-- 현재 행 -->
        <xsl:variable name="currentRow" select="."/>

        <xsl:variable name="tempCells">
            <xsl:choose>
                <!-- 삭제 -->
                <xsl:when test="$currentRow/ss:Cell[$left-start - 2]/ss:Data = ''">
                    <xsl:for-each select="$currentRow/ss:Cell">
                        <xsl:variable name="pos" select="position()"/>
                        <xsl:choose>
                            <!-- BEFORE의 핵심값 영역: DelItem 스타일 -->
                            <xsl:when test="$pos &gt;= $right-start - 2 and $pos &lt;= $right-start - 1">
                                <Cell ss:StyleID="DelItem">
                                    <xsl:apply-templates select="@* except @ss:StyleID"/>
                                    <xsl:apply-templates select="node()"/>
                                </Cell>
                            </xsl:when>

                            <!-- BEFORE의 사양군 영역: DelK 스타일 -->
                            <xsl:when test="$pos &gt;= $right-start and $pos &lt;= $right-end">
                                <Cell ss:StyleID="DelK">
                                    <xsl:apply-templates select="node()"/>
                                </Cell>
                            </xsl:when>

                            <!-- 상태열: 삭제 표시 -->
                            <xsl:when test="$pos = $right-end + 3">
                                <Cell ss:StyleID="Status">
                                	<xsl:apply-templates select="@* except @ss:StyleID"/>
                                    <Data ss:Type="String">삭제</Data>
                                </Cell>
                            </xsl:when>

                            <!-- 그 외 영역 그대로 -->
                            <xsl:otherwise>
                                <Cell><xsl:apply-templates select="@* | node()"/></Cell>
                            </xsl:otherwise>
                        </xsl:choose>
                    </xsl:for-each>
                </xsl:when>
                <!--  신규  -->
                <xsl:when test="$currentRow/ss:Cell[$right-start - 2]/ss:Data = ''">
                    <xsl:for-each select="$currentRow/ss:Cell">
                        <xsl:variable name="pos" select="position()"/>

                        <xsl:choose>
                            <!-- AFTER 핵심값: NewItem -->
                            <xsl:when test="$pos &gt;= $left-start - 2 and $pos &lt;= $left-start - 1">
                                <Cell ss:StyleID="NewItem">
                                	<xsl:apply-templates select="@* except @ss:StyleID"/>
                                    <xsl:apply-templates select="node()"/>
                                </Cell>
                            </xsl:when>

                            <!-- AFTER 사양군: NewK -->
                            <xsl:when test="$pos &gt;= $left-start and $pos &lt;= $left-end">
                                <Cell ss:StyleID="NewK">
                                    <xsl:apply-templates select="node()"/>
                                </Cell>
                            </xsl:when>

                            <!-- 상태열: 신규 -->
                            <xsl:when test="$pos = $left-end + 3">
                                <Cell ss:StyleID="Status">
                                	<xsl:apply-templates select="@* except @ss:StyleID"/>
                                    <Data ss:Type="String">신규</Data>
                                </Cell>
                            </xsl:when>

                            <!-- 기타 -->
                            <xsl:otherwise>
                                <Cell><xsl:apply-templates select="@* | node()"/></Cell>
                            </xsl:otherwise>
                        </xsl:choose>
                    </xsl:for-each>
                </xsl:when>
                <!--  변경 또는 동일  -->
                <xsl:otherwise>
                    <xsl:for-each select="$currentRow/ss:Cell">
                		<Cell><xsl:apply-templates select="@* | node()"/></Cell>
                	</xsl:for-each>
                </xsl:otherwise>
            </xsl:choose>
        </xsl:variable>

        <xsl:variable name="tempCells2">
        	<xsl:for-each select="$tempCells/ss:Cell">
        	 	<xsl:variable name="pos" select="position()"/>
        		<xsl:choose>
        			<xsl:when test="$pos = ($common-specs/common/specs/left)">
        				<xsl:variable name="rIndex" select="$common-specs/common/specs/left[.=$pos]/parent::*/right/text()" as="xs:integer"/>
        				<xsl:variable name="lValue" select="string(ss:Data/text())"/>
        				<xsl:variable name="rValue" select="string($tempCells/ss:Cell[$rIndex]/ss:Data/text())"/>
        				<xsl:choose>
        					<xsl:when test="string($tempCells/ss:Cell[$left-end + 3]/ss:Data/text()) = '신규'">
        						<xsl:copy-of select="."/>
        					</xsl:when>
        					<xsl:when test="string($tempCells/ss:Cell[$right-end + 3]/ss:Data/text()) = '삭제'">
        						<xsl:copy-of select="."/>
        					</xsl:when>
        					<xsl:when test="$lValue = $rValue">
        						<xsl:copy-of select="."/>
        					</xsl:when>
        					<!-- <xsl:otherwise>
		                        <Cell ss:StyleID="DiffK">
		                        	<xsl:apply-templates select="@* except @ss:StyleID"/>
		                            <xsl:apply-templates select="node()"/>
		                        </Cell>
        					</xsl:otherwise> -->
        					<xsl:otherwise>
						    <ss:Cell ss:StyleID="DiffK">
						        <xsl:apply-templates select="@* except @ss:StyleID"/>
						        <xsl:apply-templates select="node()"/>
						    </ss:Cell>
						</xsl:otherwise>
        				</xsl:choose>
        			</xsl:when>
			<xsl:otherwise>
			    <ss:Cell>
			        <xsl:apply-templates select="@*"/>
			        <xsl:apply-templates select="node()"/>
			    </ss:Cell>
			</xsl:otherwise>
        		</xsl:choose>
        	</xsl:for-each>
        </xsl:variable>

        <Row>
            <xsl:for-each select="$tempCells2/Cell">
                <xsl:variable name="pos" select="position()"/>
                <xsl:choose>
                    <!-- 변경이 발생한 줄이면 Status 열에 '변경' 표시 -->
                    <xsl:when test="($pos = $left-end + 3) and parent::node()/Cell[@ss:StyleID = 'DiffK']">
                        <Cell ss:StyleID="Status">
                            <Data ss:Type="String">변경</Data>
                        </Cell>
                    </xsl:when>
                    <!-- 기본 -->
                    <xsl:otherwise>
                        <xsl:copy-of select="."/>
                    </xsl:otherwise>
                </xsl:choose>

            </xsl:for-each>
        </Row>

    </xsl:template>

</xsl:stylesheet>
