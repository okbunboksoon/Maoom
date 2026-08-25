<xsl:stylesheet version="3.0" 
	xmlns:xsl="http://www.w3.org/1999/XSL/Transform" 
	xmlns="urn:schemas-microsoft-com:office:spreadsheet" 
	xmlns:ss="urn:schemas-microsoft-com:office:spreadsheet">

	<xsl:output method="xml" indent="yes"/>

	<!-- map title 추출 -->
	<xsl:variable name="mapTitle" select="normalize-space(string((/map/title)[1]))"/>

	<!-- 지역 판별 (US 여부) -->
	<xsl:variable name="isNA" select="contains(upper-case($mapTitle), 'US') or contains(upper-case($mapTitle), 'CA') or contains(upper-case($mapTitle), 'MX')"/>
	<xsl:variable name="isEuRg" select="contains(upper-case($mapTitle), 'RG') or contains(upper-case($mapTitle), 'IN')"/>
	<xsl:variable name="dbPath" select="if ($isNA) then 'asis-tobe_us.xml' else if ($isEuRg) then 'asis-tobe_eu_rg.xml' else 'asis-tobe_eu.xml'"/>
	<xsl:variable name="db" select="document($dbPath)"/>

	<xsl:template match="/">

		<!-- Excel로 열기 위한 PI -->
		<xsl:processing-instruction name="mso-application">
			<xsl:text>progid="Excel.Sheet"</xsl:text>
		</xsl:processing-instruction>

		<!-- 
			대상 문장 1회 수집
			- p, shortdesc, cmd 중에서
			- 지역별 dealer 문장 조건에 맞는 노드만 추출
			- 텍스트 normalize 및 공백 정규화 1회만 수행
		-->

		<xsl:variable name="targets" as="element()*">
			<xsl:for-each select="map//*[self::p or self::shortdesc or self::cmd]">
				
				<xsl:variable name="txt" 
					select="replace(normalize-space(string(.)), '\s+', ' ')"/>

				<xsl:if test="contains($txt, 'an authorized Kia dealer') or contains($txt, 'an authorised Kia dealer')">
					<xsl:sequence select="."/>
				</xsl:if>

			</xsl:for-each>
		</xsl:variable>

		<!-- 전체 dealer 문장 수 -->
		<xsl:variable name="dealer-count" select="count($targets)"/>

		<!-- 
			changed 개수
			- 대상 문장 중에서
			- 자신 또는 상위 노드에 status='changed'가 있는 경우
		-->
		<xsl:variable name="changed-count"
			select="count($targets[ancestor-or-self::*[starts-with(@status,'changed')]])"/>

		<!-- 이미 BER 반영된 결과를 다시 돌린 경우: 현재 문장이 DB의 new 문장과 같으면 db 없음에서 제외 -->
		<xsl:variable name="already-applied-targets" as="element()*">
			<xsl:for-each select="$targets[not(ancestor-or-self::*[starts-with(@status,'changed')])]">
				<xsl:variable name="targetText" select="replace(normalize-space(string(.)), '\s+', ' ')"/>
				<xsl:if test="some $newText in $db/pairs/pair/new satisfies $targetText = replace(normalize-space(string($newText)), '\s+', ' ')">
					<xsl:sequence select="."/>
				</xsl:if>
			</xsl:for-each>
		</xsl:variable>
		<xsl:variable name="already-applied-count" select="count($already-applied-targets)"/>

		<!-- unchanged 개수 -->
		<xsl:variable name="unchanged-count"
			select="$dealer-count - $changed-count - $already-applied-count"/>

		<!-- 변경 비율 계산 -->
		<xsl:variable name="ratio"
			select="if ($dealer-count != 0) then ($changed-count div $dealer-count) * 100 else 0"/>

		<Workbook>

			<!-- 스타일 정의 -->
			<Styles>
				<Style ss:ID="Center">
					<Alignment ss:Horizontal="Center" ss:Vertical="Center"/>
				</Style>
				<Style ss:ID="HeaderCenter">
					<Alignment ss:Horizontal="Center" ss:Vertical="Center"/>
					<Font ss:Bold="1"/>
				</Style>
				<Style ss:ID="wrap">
					<Alignment ss:Vertical="Top" ss:WrapText="1"/>
				</Style>
			</Styles>

			<!-- 결과 시트 -->
			<Worksheet ss:Name="Changed">
				<Table>

					<Column ss:Width="120"/>

					<!-- Total -->
					<Row>
						<Cell ss:StyleID="Center">
							<Data ss:Type="String">Total</Data>
						</Cell>
						<Cell>
							<Data ss:Type="String">an authorized Kia dealer 또는 an authorised Kia dealer가 포함된 전체 문장</Data>
						</Cell>
					</Row>
					<Row>
						<Cell ss:StyleID="Center">
							<Data ss:Type="Number">
								<xsl:value-of select="$dealer-count"/>
							</Data>
						</Cell>
					</Row>

					<!-- Changed -->
					<Row>
						<Cell ss:StyleID="Center">
							<Data ss:Type="String">Changed</Data>
						</Cell>
						<Cell>
							<Data ss:Type="String">db 등록된 문장, 바뀐문장의 수</Data>
						</Cell>
					</Row>
					<Row>
						<Cell ss:StyleID="Center">
							<Data ss:Type="Number">
								<xsl:value-of select="$changed-count"/>
							</Data>
						</Cell>
					</Row>

					<!-- Already applied -->
					<Row>
						<Cell ss:StyleID="Center">
							<Data ss:Type="String">Already applied</Data>
						</Cell>
						<Cell>
							<Data ss:Type="String">이미 DB의 new 문장과 일치해서 db 없는 문장에서 제외한 수</Data>
						</Cell>
					</Row>
					<Row>
						<Cell ss:StyleID="Center">
							<Data ss:Type="Number">
								<xsl:value-of select="$already-applied-count"/>
							</Data>
						</Cell>
					</Row>

					<!-- Unchanged -->
					<Row>
						<Cell ss:StyleID="Center">
							<Data ss:Type="String">Unchanged</Data>
						</Cell>
						<Cell>
							<Data ss:Type="String">db 없는 문장의 수</Data>
						</Cell>
					</Row>
					<Row>
						<Cell ss:StyleID="Center">
							<Data ss:Type="Number">
								<xsl:value-of select="$unchanged-count"/>
							</Data>
						</Cell>
					</Row>

					<!-- Ratio -->
					<Row>
						<Cell ss:StyleID="Center">
							<Data ss:Type="String">Ratio (%)</Data>
						</Cell>
					</Row>
					<Row>
						<Cell ss:StyleID="Center">
							<Data ss:Type="Number">
								<xsl:value-of select="format-number($ratio,'0.00')"/>
							</Data>
						</Cell>
					</Row>

				</Table>
			</Worksheet>
			<Worksheet ss:Name="SentenceList(db 없는 문장)">
				<Table>
					<Column ss:Width="80"/>
					<Column ss:Width="500"/>
					<Row>
						<Cell ss:StyleID="HeaderCenter">
							<Data ss:Type="String">no</Data>
						</Cell>
						<Cell ss:StyleID="HeaderCenter">
							<Data ss:Type="String">sentence</Data>
						</Cell>
					</Row>
					<!-- <xsl:for-each select="$targets[not(ancestor-or-self::*[starts-with(@status,'changed')])]"> -->
					<xsl:for-each select="$targets[not(ancestor-or-self::*[starts-with(@status,'changed')]) and not(. intersect $already-applied-targets) and not(ancestor-or-self::*[contains(@outputclass,'exclude')])]">
						<Row>
							<Cell ss:StyleID="Center">
								<Data ss:Type="Number">
									<xsl:value-of select="position()"/>
								</Data>
							</Cell>
							<Cell>
								<Data ss:Type="String">
									<xsl:value-of select="serialize(node(), map{'method':'xml'})"/>
								</Data>
							</Cell>
						</Row>
					</xsl:for-each>
				</Table>
			</Worksheet>
			<Worksheet ss:Name="ExcludeSentance(특이문장)">
			    <Table>

			        <Column ss:Width="150"/>
			        <Column ss:Width="500"/>

			        <!-- 헤더 -->
			        <Row>
			            <Cell ss:StyleID="HeaderCenter">
			                <Data ss:Type="String">href</Data>
			            </Cell>
			            <Cell ss:StyleID="HeaderCenter">
			                <Data ss:Type="String">sentence = 문단(p, cmd 등)이 아닌, 문장 단위로 분리된 특이 문장들</Data>
			            </Cell>
			        </Row>

			        <!-- exclude 문장 -->
			        <xsl:for-each select="map//*[self::p or self::cmd or self::shortdesc or self::title][contains(@outputclass,'exclude')]">

			            <!-- 바로 위 topicref 찾기 -->
			            <xsl:variable name="href"
			                select="ancestor::topicref[1]/@href"/>

			            <Row>
			                <!-- A열 -->
			                <Cell ss:StyleID="Center">
			                    <Data ss:Type="String">
			                        <xsl:value-of select="$href"/>
			                    </Data>
			                </Cell>

			                <!-- B열 -->
			                <Cell>
			                    <Data ss:Type="String">
			                        <xsl:value-of select="normalize-space(string(.))"/>
			                    </Data>
			                </Cell>
			            </Row>

			        </xsl:for-each>

			    </Table>
			</Worksheet>
		</Workbook>

	</xsl:template>
</xsl:stylesheet>
