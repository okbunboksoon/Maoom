<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="2.0" 
	xmlns:xsl="http://www.w3.org/1999/XSL/Transform" 
	xmlns="urn:schemas-microsoft-com:office:spreadsheet" 
	xmlns:o="urn:schemas-microsoft-com:office:office"
	xmlns:x="urn:schemas-microsoft-com:office:excel" 
	xmlns:ss="urn:schemas-microsoft-com:office:spreadsheet" 
	xmlns:xs="http://www.w3.org/2001/XMLSchema" 
	xmlns:f="urn:func" 
	exclude-result-prefixes="xs o x f">
	
	<!-- ===== 파라미터: Before 파일 경로 ===== -->
	<xsl:param name="before" as="xs:string" select="'excel_before.xml'"/>
	
	<!-- ===== 출력 설정 ===== -->
	<xsl:output method="xml" encoding="UTF-8" indent="no" omit-xml-declaration="no"/>
	
	<!-- ===== 경로 -> 안전 URI ===== -->
	<xsl:function name="f:path-to-doc-uri" as="xs:anyURI">
		<xsl:param name="path" as="xs:string"/>
		<xsl:variable name="p1" select="replace($path, '\\\\', '/')"/>
		<xsl:variable name="p2" select="if (matches($p1, '^[A-Za-z]:/')) then concat('file:///', $p1) else $p1"/>
		<xsl:variable name="p3" select="if (matches($p2, '^[A-Za-z][A-Za-z0-9+.-]*:')) then $p2 else resolve-uri($p2, static-base-uri())"/>
		<xsl:sequence select="xs:anyURI(iri-to-uri($p3))"/>
	</xsl:function>
	
	<!-- ===== 셀 접근 (@ss:Index 반영) ===== -->
	<xsl:function name="f:cell" as="element(ss:Cell)?">
		<xsl:param name="row" as="element(ss:Row)?"/>
		<xsl:param name="n" as="xs:integer"/>
		<xsl:sequence select="f:cell-at($row/ss:Cell, 1, $n)"/>
	</xsl:function>
	<xsl:function name="f:cell-at" as="element(ss:Cell)?">
		<xsl:param name="cells" as="element(ss:Cell)*"/>
		<xsl:param name="col" as="xs:integer"/>
		<xsl:param name="n" as="xs:integer"/>
		<xsl:choose>
			<xsl:when test="empty($cells)">
				<xsl:sequence select="()"/>
			</xsl:when>
			<xsl:otherwise>
				<xsl:variable name="c" select="$cells[1]"/>
				<xsl:variable name="col2" select="if ($c/@ss:Index) then xs:integer($c/@ss:Index) else $col"/>
				<xsl:choose>
					<xsl:when test="$n = $col2">
						<xsl:sequence select="$c"/>
					</xsl:when>
					<xsl:otherwise>
						<xsl:sequence select="f:cell-at($cells[position() gt 1], $col2 + 1, $n)"/>
					</xsl:otherwise>
				</xsl:choose>
			</xsl:otherwise>
		</xsl:choose>
	</xsl:function>
	
	<!-- 마지막 실제 컬럼 인덱스 -->
	<xsl:function name="f:last-col" as="xs:integer">
		<xsl:param name="row" as="element(ss:Row)?"/>
		<xsl:sequence select="if (empty($row)) then 0 else f:last-col-iter($row/ss:Cell, 1)"/>
	</xsl:function>
	<xsl:function name="f:last-col-iter" as="xs:integer">
		<xsl:param name="cells" as="element(ss:Cell)*"/>
		<xsl:param name="col" as="xs:integer"/>
		<xsl:choose>
			<xsl:when test="empty($cells)">
				<xsl:sequence select="$col - 1"/>
			</xsl:when>
			<xsl:otherwise>
				<xsl:variable name="c" select="$cells[1]"/>
				<xsl:variable name="col2" select="if ($c/@ss:Index) then xs:integer($c/@ss:Index) else $col"/>
				<xsl:sequence select="f:last-col-iter($cells[position() gt 1], $col2 + 1)"/>
			</xsl:otherwise>
		</xsl:choose>
	</xsl:function>
	
	<!-- 텍스트 추출 -->
	<xsl:function name="f:text" as="xs:string">
		<xsl:param name="row" as="element(ss:Row)?"/>
		<xsl:param name="n" as="xs:integer"/>
		<xsl:variable name="c" select="if ($row) then f:cell($row,$n) else ()"/>
		<xsl:sequence select="normalize-space(string(($c/ss:Data)[1]))"/>
	</xsl:function>
	
	<!-- ===== 색상 판정: 글자 빨강 / 배경 노랑 ===== -->
	<xsl:function name="f:is-red-cell" as="xs:boolean">
		<xsl:param name="doc" as="node()"/>
		<xsl:param name="row" as="element(ss:Row)?"/>
		<xsl:param name="n" as="xs:integer"/>
		<xsl:variable name="c" select="f:cell($row,$n)"/>
		<xsl:variable name="sid" select="string($c/@ss:StyleID)"/>
		<xsl:variable name="color" select="lower-case(string($doc/*/ss:Styles/ss:Style[@ss:ID=$sid]/ss:Font/@ss:Color))"/>
		<xsl:sequence select="exists($c) and ($color = '#ff0000' or contains($color, 'ff0000'))"/>
	</xsl:function>
	
	<!-- 노란 배경: #FFFF00, #FFFF66, #FFFF99, #FFFFCC … 모두 허용 -->
	<xsl:function name="f:is-yellow-cell" as="xs:boolean">
		<xsl:param name="doc" as="node()"/>
		<xsl:param name="row" as="element(ss:Row)?"/>
		<xsl:param name="n" as="xs:integer"/>
		<xsl:variable name="c" select="f:cell($row,$n)"/>
		<xsl:variable name="sid" select="string($c/@ss:StyleID)"/>
		<xsl:variable name="bg" select="lower-case(string($doc/*/ss:Styles/ss:Style[@ss:ID=$sid]/ss:Interior/@ss:Color))"/>
		<xsl:sequence select="exists($c) and starts-with($bg,'#ffff')"/>
	</xsl:function>
	
	<!-- ===== 시퀀스 유틸 ===== -->
	<xsl:function name="f:remove-first" as="xs:string*">
		<xsl:param name="seq" as="xs:string*"/>
		<xsl:param name="k" as="xs:string"/>
		<xsl:variable name="p" select="(index-of($seq,$k))[1]"/>
		<xsl:sequence select=" if (exists($p)) then (subsequence($seq, 1, $p - 1), subsequence($seq, $p + 1)) else $seq "/>
	</xsl:function>
	<xsl:function name="f:merge-keys" as="xs:string*">
		<xsl:param name="A" as="xs:string*"/>
		<xsl:param name="B" as="xs:string*"/>
		<xsl:sequence select=" if (empty($A)) then $B else if (empty($B)) then $A else ( let $a := $A[1], $b := $B[1] return if ($a = $b) then ($a, f:merge-keys(f:remove-first($A,$a), f:remove-first($B,$a))) else ( let $bInA := (index-of($A,$b))[1], $aInB := (index-of($B,$a))[1] return if (empty($bInA) and empty($aInB)) then ($a, f:merge-keys(subsequence($A,2), $B)) else if (empty($bInA)) then ($b, f:merge-keys($A, subsequence($B,2))) else if (empty($aInB)) then ($a, f:merge-keys(subsequence($A,2), $B)) else ($b, f:merge-keys($A, subsequence($B,2))) ) ) "/>
	</xsl:function>
	
	<!-- ===== 입력 문서 ===== -->
	<xsl:variable name="after-doc" select="/"/>
	<xsl:variable name="before-doc" select="doc(f:path-to-doc-uri($before))"/>
	
	<!-- 첫 Worksheet 기준 -->
	<xsl:variable name="A-rows" select="$after-doc/*/ss:Worksheet[1]/ss:Table/ss:Row"/>
	<xsl:variable name="B-rows" select="$before-doc/*/ss:Worksheet[1]/ss:Table/ss:Row"/>
	<xsl:variable name="A-header" select="$A-rows[1]"/>
	<xsl:variable name="B-header" select="$B-rows[1]"/>
	<xsl:variable name="A-body" select="$A-rows[position() gt 1]"/>
	<xsl:variable name="B-body" select="$B-rows[position() gt 1]"/>
	<!-- 키(ITEM NO) = 1열 -->
	<xsl:key name="kA" match="ss:Row" use="f:text(.,1)"/>
	<xsl:key name="kB" match="ss:Row" use="f:text(.,1)"/>
	<!-- ===== 좌측 열 여부 / 스타일 결정 / 셀 출력 공통 ===== -->
	<xsl:function name="f:is-left-text-col" as="xs:boolean">
		<xsl:param name="col" as="xs:integer"/>
		<xsl:sequence select="$col = 1 or $col = 2"/>
	</xsl:function>
	<xsl:function name="f:style-for" as="xs:string?">
		<xsl:param name="isLeft" as="xs:boolean"/>
		<xsl:param name="isRed" as="xs:boolean"/>
		<xsl:param name="isYel" as="xs:boolean"/>
		<xsl:sequence select=" if ($isLeft) then if ($isRed and $isYel) then 'LeftTextRedBgYellow' else if ($isRed) then 'LeftTextRed' else if ($isYel) then 'LeftTextBgYellow' else 'LeftText' else if ($isRed and $isYel) then 'RedBgYellow' else if ($isRed) then 'Red' else if ($isYel) then 'BgYellow' else () "/>
	</xsl:function>
	<xsl:template name="emit-cell">
		<xsl:param name="val" as="xs:string"/>
		<xsl:param name="style" as="xs:string?"/>

		<Cell>
			<xsl:if test="exists($style)">
				<xsl:attribute name="ss:StyleID" select="$style"/>
			</xsl:if>

			<Data ss:Type="String">
				<xsl:value-of select="$val"/>
			</Data>
		</Cell>
	</xsl:template>

	<!-- ===== 메인 출력 ===== -->
	<xsl:template match="/"> 
		<?mso-application progid="Excel.Sheet"?>
		<Workbook 
			xmlns="urn:schemas-microsoft-com:office:spreadsheet" 
			xmlns:ss="urn:schemas-microsoft-com:office:spreadsheet">
			<DocumentProperties>
				<Author>auto</Author>
				<LastAuthor>auto</LastAuthor>
				<Created>
					<xsl:value-of select="current-dateTime()"/>
				</Created>
				<Version>16.00</Version>
			</DocumentProperties>
			<ExcelWorkbook>
				<ProtectStructure>False</ProtectStructure>
				<ProtectWindows>False</ProtectWindows>
			</ExcelWorkbook> 
			<Styles>
				<!-- 기본 스타일: 가운데 정렬 + 맑은 고딕 -->
				<Style ss:ID="Default" ss:Name="Normal">
					<Alignment ss:Horizontal="Center" ss:Vertical="Center"/>
					<Font ss:FontName="맑은 고딕"/>
				</Style>
				<!-- 왼쪽 정렬 + 줄바꿈 + 맑은 고딕 -->
				<Style ss:ID="LeftText">
					<Alignment ss:Horizontal="Left" ss:Vertical="Center" ss:WrapText="1"/>
					<Borders>
						<Border ss:Position="Left" ss:LineStyle="Continuous" ss:Weight="1"/>
						<Border ss:Position="Right" ss:LineStyle="Continuous" ss:Weight="1"/>
						<Border ss:Position="Top" ss:LineStyle="Continuous" ss:Weight="1"/>
						<Border ss:Position="Bottom" ss:LineStyle="Continuous" ss:Weight="1"/>
					</Borders>
					<Font ss:FontName="맑은 고딕"/>
				</Style>
				<!-- 빨간 글씨 -->
				<Style ss:ID="Red">
					<Font ss:FontName="Arial" ss:Color="#FF0000" ss:Bold="1"/>
					<Borders>
						<Border ss:Position="Left" ss:LineStyle="Continuous" ss:Weight="1"/>
						<Border ss:Position="Right" ss:LineStyle="Continuous" ss:Weight="1"/>
						<Border ss:Position="Top" ss:LineStyle="Continuous" ss:Weight="1"/>
						<Border ss:Position="Bottom" ss:LineStyle="Continuous" ss:Weight="1"/>
					</Borders>
				</Style>
				<!-- 노란 배경만 적용 -->
				<Style ss:ID="BgYellow">
					<Interior ss:Color="#FFFF00" ss:Pattern="Solid"/>
					<Borders>
						<Border ss:Position="Left" ss:LineStyle="Continuous" ss:Weight="1"/>
						<Border ss:Position="Right" ss:LineStyle="Continuous" ss:Weight="1"/>
						<Border ss:Position="Top" ss:LineStyle="Continuous" ss:Weight="1"/>
						<Border ss:Position="Bottom" ss:LineStyle="Continuous" ss:Weight="1"/>
					</Borders>
				</Style>
				<!-- 빨간 글씨 + 노란 배경 -->
				<Style ss:ID="RedBgYellow">
					<Font ss:FontName="Arial" ss:Color="#FF0000" ss:Bold="1"/>
					<Interior ss:Color="#FFFF00" ss:Pattern="Solid"/>
					<Borders>
						<Border ss:Position="Left" ss:LineStyle="Continuous" ss:Weight="1"/>
						<Border ss:Position="Right" ss:LineStyle="Continuous" ss:Weight="1"/>
						<Border ss:Position="Top" ss:LineStyle="Continuous" ss:Weight="1"/>
						<Border ss:Position="Bottom" ss:LineStyle="Continuous" ss:Weight="1"/>
					</Borders>
				</Style>
				<!-- 왼쪽 정렬 + 노란 배경 -->
				<Style ss:ID="LeftTextBgYellow">
					<Alignment ss:Horizontal="Left" ss:Vertical="Center" ss:WrapText="1"/>
					<Interior ss:Color="#FFFF00" ss:Pattern="Solid"/>
					<Borders>
						<Border ss:Position="Left" ss:LineStyle="Continuous" ss:Weight="1"/>
						<Border ss:Position="Right" ss:LineStyle="Continuous" ss:Weight="1"/>
						<Border ss:Position="Top" ss:LineStyle="Continuous" ss:Weight="1"/>
						<Border ss:Position="Bottom" ss:LineStyle="Continuous" ss:Weight="1"/>
					</Borders>
				</Style>
				<!-- 추가 : 왼쪽 정렬 + 분홍 배경 -->
				<Style ss:ID="NewItem">
					<Alignment ss:Horizontal="Left" ss:Vertical="Center"/>
					<Interior ss:Color="#FFCCE5" ss:Pattern="Solid"/>
					<Borders>
						<Border ss:Position="Left" ss:LineStyle="Continuous" ss:Weight="1"/>
						<Border ss:Position="Right" ss:LineStyle="Continuous" ss:Weight="1"/>
						<Border ss:Position="Top" ss:LineStyle="Continuous" ss:Weight="1"/>
						<Border ss:Position="Bottom" ss:LineStyle="Continuous" ss:Weight="1"/>
					</Borders>
				</Style>
				<!-- 추가 : 분홍 배경 -->
				<Style ss:ID="NewK">
					<Interior ss:Color="#FFCCE5" ss:Pattern="Solid"/>
					<Borders>
						<Border ss:Position="Left" ss:LineStyle="Continuous" ss:Weight="1"/>
						<Border ss:Position="Right" ss:LineStyle="Continuous" ss:Weight="1"/>
						<Border ss:Position="Top" ss:LineStyle="Continuous" ss:Weight="1"/>
						<Border ss:Position="Bottom" ss:LineStyle="Continuous" ss:Weight="1"/>
					</Borders>
				</Style>
				<!-- 삭제 : 왼쪽 정렬 + 회색 배경 -->
				<Style ss:ID="DelItem">
					<Alignment ss:Horizontal="Left" ss:Vertical="Center"/>
					<Interior ss:Color="#E0E0E0" ss:Pattern="Solid"/>
					<Borders>
						<Border ss:Position="Left" ss:LineStyle="Continuous" ss:Weight="1"/>
						<Border ss:Position="Right" ss:LineStyle="Continuous" ss:Weight="1"/>
						<Border ss:Position="Top" ss:LineStyle="Continuous" ss:Weight="1"/>
						<Border ss:Position="Bottom" ss:LineStyle="Continuous" ss:Weight="1"/>
					</Borders>
				</Style>
				<!-- 삭제 : 회색 배경 -->
				<Style ss:ID="DelK">
					<Interior ss:Color="#E0E0E0" ss:Pattern="Solid"/>
					<Borders>
						<Border ss:Position="Left" ss:LineStyle="Continuous" ss:Weight="1"/>
						<Border ss:Position="Right" ss:LineStyle="Continuous" ss:Weight="1"/>
						<Border ss:Position="Top" ss:LineStyle="Continuous" ss:Weight="1"/>
						<Border ss:Position="Bottom" ss:LineStyle="Continuous" ss:Weight="1"/>
					</Borders>
				</Style>
				<!-- 변경 : 연두 배경 -->
				<Style ss:ID="DiffK">
					<Interior ss:Color="#CCFFCC" ss:Pattern="Solid"/>
					<Borders>
						<Border ss:Position="Left" ss:LineStyle="Continuous" ss:Weight="1"/>
						<Border ss:Position="Right" ss:LineStyle="Continuous" ss:Weight="1"/>
						<Border ss:Position="Top" ss:LineStyle="Continuous" ss:Weight="1"/>
						<Border ss:Position="Bottom" ss:LineStyle="Continuous" ss:Weight="1"/>
					</Borders>
				</Style>
				<!-- 상태 : 빨간 글씨 -->
				<Style ss:ID="Status">
					<Font ss:Color="#FF0000" ss:Bold="1"/>
				</Style>
				<Style ss:ID="Bordered">
					<Borders>
						<Border ss:Position="Left" ss:LineStyle="Continuous" ss:Weight="1"/>
						<Border ss:Position="Right" ss:LineStyle="Continuous" ss:Weight="1"/>
						<Border ss:Position="Top" ss:LineStyle="Continuous" ss:Weight="1"/>
						<Border ss:Position="Bottom" ss:LineStyle="Continuous" ss:Weight="1"/>
					</Borders>
				</Style>
				<!-- 신규 추가된열에 정의할 스타일임 -->
				<!-- BgYellow 계열 -->
				<Style ss:ID="BgYellow_First" ss:Parent="BgYellow">
				    <Borders>
				        <Border ss:Position="Left"   ss:LineStyle="Continuous" ss:Weight="2" ss:Color="#FF0000"/>
				        <Border ss:Position="Right"  ss:LineStyle="Continuous" ss:Weight="2" ss:Color="#FF0000"/>
				        <Border ss:Position="Top"    ss:LineStyle="Continuous" ss:Weight="2" ss:Color="#FF0000"/>
				        <Border ss:Position="Bottom" ss:LineStyle="Continuous" ss:Weight="1" ss:Color="#000000"/>
				    </Borders>
				</Style>

				<Style ss:ID="BgYellow_Middle" ss:Parent="BgYellow">
				    <Borders>
				        <Border ss:Position="Left"   ss:LineStyle="Continuous" ss:Weight="2" ss:Color="#FF0000"/>
				        <Border ss:Position="Right"  ss:LineStyle="Continuous" ss:Weight="2" ss:Color="#FF0000"/>
				        <Border ss:Position="Top"    ss:LineStyle="Continuous" ss:Weight="1" ss:Color="#000000"/>
				        <Border ss:Position="Bottom" ss:LineStyle="Continuous" ss:Weight="1" ss:Color="#000000"/>
				    </Borders>
				</Style>

				<Style ss:ID="BgYellow_Last" ss:Parent="BgYellow">
				    <Borders>
				        <Border ss:Position="Left"   ss:LineStyle="Continuous" ss:Weight="2" ss:Color="#FF0000"/>
				        <Border ss:Position="Right"  ss:LineStyle="Continuous" ss:Weight="2" ss:Color="#FF0000"/>
				        <Border ss:Position="Top"    ss:LineStyle="Continuous" ss:Weight="1" ss:Color="#000000"/>
				        <Border ss:Position="Bottom" ss:LineStyle="Continuous" ss:Weight="2" ss:Color="#FF0000"/>
				    </Borders>
				</Style>

				<!-- NewK 계열 (Parent 반드시 NewK) -->
				<Style ss:ID="NewK_First" ss:Parent="NewK">
				    <Borders>
				        <Border ss:Position="Left"   ss:LineStyle="Continuous" ss:Weight="2" ss:Color="#FF0000"/>
				        <Border ss:Position="Right"  ss:LineStyle="Continuous" ss:Weight="2" ss:Color="#FF0000"/>
				        <Border ss:Position="Top"    ss:LineStyle="Continuous" ss:Weight="2" ss:Color="#FF0000"/>
				        <Border ss:Position="Bottom" ss:LineStyle="Continuous" ss:Weight="1" ss:Color="#000000"/>
				    </Borders>
				</Style>

				<Style ss:ID="NewK_Middle" ss:Parent="NewK">
				    <Borders>
				        <Border ss:Position="Left"   ss:LineStyle="Continuous" ss:Weight="2" ss:Color="#FF0000"/>
				        <Border ss:Position="Right"  ss:LineStyle="Continuous" ss:Weight="2" ss:Color="#FF0000"/>
				        <Border ss:Position="Top"    ss:LineStyle="Continuous" ss:Weight="1" ss:Color="#000000"/>
				        <Border ss:Position="Bottom" ss:LineStyle="Continuous" ss:Weight="1" ss:Color="#000000"/>
				    </Borders>
				</Style>

				<Style ss:ID="NewK_Last" ss:Parent="NewK">
				    <Borders>
				        <Border ss:Position="Left"   ss:LineStyle="Continuous" ss:Weight="2" ss:Color="#FF0000"/>
				        <Border ss:Position="Right"  ss:LineStyle="Continuous" ss:Weight="2" ss:Color="#FF0000"/>
				        <Border ss:Position="Top"    ss:LineStyle="Continuous" ss:Weight="1" ss:Color="#000000"/>
				        <Border ss:Position="Bottom" ss:LineStyle="Continuous" ss:Weight="2" ss:Color="#FF0000"/>
				    </Borders>
				</Style>

				<!-- DiffK 계열 (Parent 반드시 DiffK) -->
				<Style ss:ID="DiffK_First" ss:Parent="DiffK">
				    <Borders>
				        <Border ss:Position="Left"   ss:LineStyle="Continuous" ss:Weight="2" ss:Color="#FF0000"/>
				        <Border ss:Position="Right"  ss:LineStyle="Continuous" ss:Weight="2" ss:Color="#FF0000"/>
				        <Border ss:Position="Top"    ss:LineStyle="Continuous" ss:Weight="2" ss:Color="#FF0000"/>
				        <Border ss:Position="Bottom" ss:LineStyle="Continuous" ss:Weight="1" ss:Color="#000000"/>
				    </Borders>
				</Style>

				<Style ss:ID="DiffK_Middle" ss:Parent="DiffK">
				    <Borders>
				        <Border ss:Position="Left"   ss:LineStyle="Continuous" ss:Weight="2" ss:Color="#FF0000"/>
				        <Border ss:Position="Right"  ss:LineStyle="Continuous" ss:Weight="2" ss:Color="#FF0000"/>
				        <Border ss:Position="Top"    ss:LineStyle="Continuous" ss:Weight="1" ss:Color="#000000"/>
				        <Border ss:Position="Bottom" ss:LineStyle="Continuous" ss:Weight="1" ss:Color="#000000"/>
				    </Borders>
				</Style>

				<Style ss:ID="DiffK_Last" ss:Parent="DiffK">
				    <Borders>
				        <Border ss:Position="Left"   ss:LineStyle="Continuous" ss:Weight="2" ss:Color="#FF0000"/>
				        <Border ss:Position="Right"  ss:LineStyle="Continuous" ss:Weight="2" ss:Color="#FF0000"/>
				        <Border ss:Position="Top"    ss:LineStyle="Continuous" ss:Weight="1" ss:Color="#000000"/>
				        <Border ss:Position="Bottom" ss:LineStyle="Continuous" ss:Weight="2" ss:Color="#FF0000"/>
				    </Borders>
				</Style>
				
				<!-- Bordered 계열 -->
				<Style ss:ID="Bordered_First" ss:Parent="Bordered">
				    <Borders>
				        <!-- 좌우 빨강 -->
				        <Border ss:Position="Left"   ss:LineStyle="Continuous" ss:Weight="2" ss:Color="#FF0000"/>
				        <Border ss:Position="Right"  ss:LineStyle="Continuous" ss:Weight="2" ss:Color="#FF0000"/>

				        <!-- 위 빨강 -->
				        <Border ss:Position="Top"    ss:LineStyle="Continuous" ss:Weight="2" ss:Color="#FF0000"/>

				        <!-- 아래 검정 -->
				        <Border ss:Position="Bottom" ss:LineStyle="Continuous" ss:Weight="1" ss:Color="#000000"/>
				    </Borders>
				</Style>

				<Style ss:ID="Bordered_Middle" ss:Parent="Bordered">
				    <Borders>
				        <!-- 좌우 빨강 -->
				        <Border ss:Position="Left"   ss:LineStyle="Continuous" ss:Weight="2" ss:Color="#FF0000"/>
				        <Border ss:Position="Right"  ss:LineStyle="Continuous" ss:Weight="2" ss:Color="#FF0000"/>

				        <!-- 위아래 검정 -->
				        <Border ss:Position="Top"    ss:LineStyle="Continuous" ss:Weight="1" ss:Color="#000000"/>
				        <Border ss:Position="Bottom" ss:LineStyle="Continuous" ss:Weight="1" ss:Color="#000000"/>
				    </Borders>
				</Style>

				<Style ss:ID="Bordered_Last" ss:Parent="Bordered">
				    <Borders>
				        <!-- 좌우 빨강 -->
				        <Border ss:Position="Left"   ss:LineStyle="Continuous" ss:Weight="2" ss:Color="#FF0000"/>
				        <Border ss:Position="Right"  ss:LineStyle="Continuous" ss:Weight="2" ss:Color="#FF0000"/>

				        <!-- 위 검정 -->
				        <Border ss:Position="Top"    ss:LineStyle="Continuous" ss:Weight="1" ss:Color="#000000"/>

				        <!-- 아래 빨강 -->
				        <Border ss:Position="Bottom" ss:LineStyle="Continuous" ss:Weight="2" ss:Color="#FF0000"/>
				    </Borders>
				</Style>
				
				<!-- 스타일아이디 없을때 -->
				<Style ss:ID="Default_First">
				    <Borders>
				        <Border ss:Position="Left"   ss:LineStyle="Continuous" ss:Weight="2" ss:Color="#FF0000"/>
				        <Border ss:Position="Right"  ss:LineStyle="Continuous" ss:Weight="2" ss:Color="#FF0000"/>
				        <Border ss:Position="Top"    ss:LineStyle="Continuous" ss:Weight="2" ss:Color="#FF0000"/>
				        <Border ss:Position="Bottom" ss:LineStyle="Continuous" ss:Weight="1" ss:Color="#000000"/>
				    </Borders>
				</Style>

				<Style ss:ID="Default_Middle">
				    <Borders>
				        <Border ss:Position="Left"   ss:LineStyle="Continuous" ss:Weight="2" ss:Color="#FF0000"/>
				        <Border ss:Position="Right"  ss:LineStyle="Continuous" ss:Weight="2" ss:Color="#FF0000"/>
				        <Border ss:Position="Top"    ss:LineStyle="Continuous" ss:Weight="1" ss:Color="#000000"/>
				        <Border ss:Position="Bottom" ss:LineStyle="Continuous" ss:Weight="1" ss:Color="#000000"/>
				    </Borders>
				</Style>

				<Style ss:ID="Default_Last">
				    <Borders>
				        <Border ss:Position="Left"   ss:LineStyle="Continuous" ss:Weight="2" ss:Color="#FF0000"/>
				        <Border ss:Position="Right"  ss:LineStyle="Continuous" ss:Weight="2" ss:Color="#FF0000"/>
				        <Border ss:Position="Top"    ss:LineStyle="Continuous" ss:Weight="1" ss:Color="#000000"/>
				        <Border ss:Position="Bottom" ss:LineStyle="Continuous" ss:Weight="2" ss:Color="#FF0000"/>
				    </Borders>
				</Style>
			</Styles>
			<Worksheet ss:Name="Sheet1">
				<!-- 열 폭 계산 -->
				<xsl:variable name="An" select="f:last-col($A-header)"/>
				<xsl:variable name="Bn" select="f:last-col($B-header)"/>
				<!-- 전체 열 수: 왼쪽 An + (빈칸 2) + 오른쪽 Bn -->
				<Table ss:ExpandedColumnCount="{ $An + 2 + $Bn }">
					<!-- 열 너비 고정 -->
					<Column ss:Index="1" ss:Width="99.75"/>
					<Column ss:Index="2" ss:Width="200.25"/>
					<Column ss:Index="{ $An + 3 }" ss:Width="99.75"/>
					<Column ss:Index="{ $An + 4 }" ss:Width="200.25"/>
					<!-- ===== 헤더 출력 ===== -->
					<Row>
						<!-- 왼쪽 헤더 -->
						<xsl:for-each select="1 to $An">
							<xsl:variable name="col" select="."/>
							<xsl:variable name="isRed" select="f:is-red-cell($after-doc,$A-header,$col)"/>
							<xsl:variable name="isYel" select="f:is-yellow-cell($after-doc,$A-header,$col)"/>
							<xsl:variable name="style" select="f:style-for(f:is-left-text-col($col), $isRed, $isYel)"/>
							<xsl:call-template name="emit-cell">
								<xsl:with-param name="val" select="f:text($A-header,$col)"/>
								<xsl:with-param name="style" select="$style"/>
							</xsl:call-template>
						</xsl:for-each>
						<!-- 공백 2칸 -->
						<Cell>
							<Data ss:Type="String"/>
						</Cell>
						<Cell>
							<Data ss:Type="String"/>
						</Cell>
						<!-- 오른쪽 헤더 -->
						<xsl:for-each select="1 to $Bn">
							<xsl:variable name="col" select="."/>
							<xsl:variable name="isRed" select="f:is-red-cell($before-doc,$B-header,$col)"/>
							<xsl:variable name="isYel" select="f:is-yellow-cell($before-doc,$B-header,$col)"/>
							<xsl:variable name="style" select="f:style-for(f:is-left-text-col($col), $isRed, $isYel)"/>
							<xsl:call-template name="emit-cell">
								<xsl:with-param name="val" select="f:text($B-header,$col)"/>
								<xsl:with-param name="style" select="$style"/>
							</xsl:call-template>
						</xsl:for-each>
					</Row>
					<!-- ===== 키 병합 ===== -->
					<xsl:variable name="keysA" select="for $r in $A-body return f:text($r,1)"/>
					<xsl:variable name="keysB" select="for $r in $B-body return f:text($r,1)"/>
					<xsl:variable name="keysMerged" select="f:merge-keys($keysA,$keysB)"/>
					<!-- ===== 본문 출력 ===== -->
					<xsl:for-each select="$keysMerged">
						<xsl:variable name="k" select="."/>
						<xsl:variable name="rowA" select="key('kA',$k,$after-doc)[1]"/>
						<xsl:variable name="rowB" select="key('kB',$k,$before-doc)[1]"/>
						<Row>
							<!-- 좌: After -->
							<xsl:for-each select="1 to $An">
								<xsl:variable name="col" select="."/>
								<xsl:variable name="isRed" select="f:is-red-cell($after-doc,$rowA,$col)"/>
								<xsl:variable name="isYel" select="f:is-yellow-cell($after-doc,$rowA,$col)"/>
								<xsl:variable name="style" select="f:style-for(f:is-left-text-col($col), $isRed, $isYel)"/>
								<xsl:call-template name="emit-cell">
									<xsl:with-param name="val" select="f:text($rowA,$col)"/>
									<xsl:with-param name="style" select="$style"/>
								</xsl:call-template>
							</xsl:for-each>
							<!-- 공백 2칸 -->
							<Cell>
								<Data ss:Type="String"/>
							</Cell>
							<Cell>
								<Data ss:Type="String"/>
							</Cell>
							<!-- 우: Before -->
							<xsl:for-each select="1 to $Bn">
								<xsl:variable name="col" select="."/>
								<xsl:variable name="isRed" select="f:is-red-cell($before-doc,$rowB,$col)"/>
								<xsl:variable name="isYel" select="f:is-yellow-cell($before-doc,$rowB,$col)"/>
								<xsl:variable name="style" select="f:style-for(f:is-left-text-col($col), $isRed, $isYel)"/>
								<xsl:call-template name="emit-cell">
									<xsl:with-param name="val" select="f:text($rowB,$col)"/>
									<xsl:with-param name="style" select="$style"/>
								</xsl:call-template>
							</xsl:for-each>
						</Row>
					</xsl:for-each>
				</Table>
				 <!-- 첫 행 틀 고정 -->
			    <WorksheetOptions xmlns="urn:schemas-microsoft-com:office:excel">
			        <FreezePanes/>
			        <FrozenNoSplit/>
			        <SplitHorizontal>1</SplitHorizontal>
			        <TopRowBottomPane>1</TopRowBottomPane>
			        <ActivePane>2</ActivePane>
			    </WorksheetOptions>
			</Worksheet>
		</Workbook>
	</xsl:template>
</xsl:stylesheet>