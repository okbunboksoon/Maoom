<xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns="urn:schemas-microsoft-com:office:spreadsheet" xmlns:o="urn:schemas-microsoft-com:office:office" xmlns:x="urn:schemas-microsoft-com:office:excel" xmlns:ss="urn:schemas-microsoft-com:office:spreadsheet" xmlns:html="http://www.w3.org/TR/REC-html40">
	<xsl:output method="xml" indent="no" omit-xml-declaration="no"/>
	<xsl:template match="/">
		<xsl:processing-instruction name="mso-application">
			<xsl:text>progid="Excel.Sheet"</xsl:text>
		</xsl:processing-instruction>
		<Workbook>
			<DocumentProperties xmlns="urn:schemas-microsoft-com:office:office">
				<Version>16.00</Version>
			</DocumentProperties>
			<OfficeDocumentSettings xmlns="urn:schemas-microsoft-com:office:office">
				<AllowPNG/>
			</OfficeDocumentSettings>
			<ExcelWorkbook xmlns="urn:schemas-microsoft-com:office:excel">
				<WindowHeight>17325</WindowHeight>
				<WindowWidth>32767</WindowWidth>
				<WindowTopX>32767</WindowTopX>
				<WindowTopY>32767</WindowTopY>
				<ProtectStructure>False</ProtectStructure>
				<ProtectWindows>False</ProtectWindows>
			</ExcelWorkbook>
			<Styles>
				<Style ss:ID="Default" ss:Name="Normal">
					<Alignment ss:Vertical="Center"/>
					<Borders/>
					<Font ss:FontName="맑은 고딕" x:CharSet="129" x:Family="Modern" ss:Size="11" ss:Color="#000000"/>
					<Interior/>
					<NumberFormat/>
					<Protection/>
				</Style>
				<Style ss:ID="s62">
					<Alignment ss:Horizontal="Left" ss:Vertical="Center"/>
				</Style>
				<Style ss:ID="s63">
					<Alignment ss:Horizontal="Left" ss:Vertical="Center"/>
					<Interior ss:Color="#92D050" ss:Pattern="Solid"/>
				</Style>
				<Style ss:ID="s64">
					<Alignment ss:Vertical="Center" ss:WrapText="1"/>
				</Style>
				<Style ss:ID="s65">
					<Interior ss:Color="#FFFF00" ss:Pattern="Solid"/>
				</Style>
			</Styles>
			<Worksheet ss:Name="Multi Cases">
				<Table ss:ExpandedColumnCount="5" ss:ExpandedRowCount="65535" x:FullColumns="1" x:FullRows="1" ss:DefaultColumnWidth="54" ss:DefaultRowHeight="16.5">
					<Column ss:StyleID="s62" ss:AutoFitWidth="0" ss:Width="58.5"/>
					<Column ss:StyleID="s62" ss:AutoFitWidth="0" ss:Width="90.75"/>
					<Column ss:StyleID="s62" ss:AutoFitWidth="0" ss:Width="348.75"/>
					<Column ss:StyleID="s62" ss:AutoFitWidth="0" ss:Width="348.75"/>
					<Column ss:StyleID="s62" ss:AutoFitWidth="0" ss:Width="769.5"/>
					<Row>
						<Cell ss:StyleID="s63">
							<Data ss:Type="String">Line No.</Data>
						</Cell>
						<Cell ss:StyleID="s63">
							<Data ss:Type="String">Filename</Data>
						</Cell>
						<Cell ss:StyleID="s63">
							<Data ss:Type="String">Location</Data>
						</Cell>
						<Cell ss:StyleID="s63">
							<Data ss:Type="String">Content</Data>
						</Cell>
						<Cell ss:StyleID="s63">
							<Data ss:Type="String">Sentence</Data>
						</Cell>
					</Row>
					<xsl:for-each select="fileset/file">
						<xsl:apply-templates select="."/>
					</xsl:for-each>
				</Table>
				<WorksheetOptions xmlns="urn:schemas-microsoft-com:office:excel">
					<PageSetup>
						<Header x:Margin="0.3"/>
						<Footer x:Margin="0.3"/>
						<PageMargins x:Bottom="0.75" x:Left="0.7" x:Right="0.7" x:Top="0.75"/>
					</PageSetup>
					<Unsynced/>
					<Zoom>145</Zoom>
					<Selected/>
					<Panes>
						<Pane>
							<Number>3</Number>
							<ActiveRow>1</ActiveRow>
							<ActiveCol>1</ActiveCol>
						</Pane>
					</Panes>
					<ProtectObjects>False</ProtectObjects>
					<ProtectScenarios>False</ProtectScenarios>
				</WorksheetOptions>
			</Worksheet>
		</Workbook>
	</xsl:template>
	<xsl:template match="file">
		<xsl:variable name="filename" select="@filename"/>
		<xsl:for-each select="detect">
			<Row ss:AutoFitHeight="0">
				<!-- Line No -->
				<Cell ss:StyleID="s62"><Data ss:Type="Number"><xsl:value-of select="count(preceding::detect) + 1"/></Data></Cell>
				<!-- Filename -->
				<Cell ss:StyleID="s62">
					<Data ss:Type="String">
						<xsl:if test="position() = 1">
							<xsl:value-of select="$filename"/>
						</xsl:if>
					</Data>
				</Cell>
				<!-- Location -->
				<Cell ss:StyleID="s62"><Data ss:Type="String"><xsl:value-of select="@location"/></Data></Cell>
				<!-- Content -->
				<Cell ss:StyleID="s62">
					<Data ss:Type="String">
						<xsl:value-of select="substring-before(., '###')"/>
					</Data>
				</Cell>
				<!-- Sentence -->
				<Cell ss:StyleID="s62">
					<Data ss:Type="String">
						<xsl:apply-templates select="node()" mode="sentence"/>
					</Data>
				</Cell>
	 		</Row>
		</xsl:for-each>
	</xsl:template>
	<xsl:template match="text()" mode="sentence">
		<xsl:choose>
			<xsl:when test="contains(., '###')">
				<xsl:value-of select="substring-after(., '###')"/>
			</xsl:when>
			<xsl:when test="preceding-sibling::node()[contains(string(.), '###')]">
				<xsl:value-of select="."/>
			</xsl:when>
		</xsl:choose>
	</xsl:template>
	<xsl:template match="span" mode="sentence">
		<Font xmlns="http://www.w3.org/TR/REC-html40" html:Color="#FF0000">
			<xsl:apply-templates select="node()" mode="sentence"/>
		</Font>
	</xsl:template>
</xsl:stylesheet>
