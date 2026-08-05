<xsl:stylesheet version="3.0"
	xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
	xmlns="urn:schemas-microsoft-com:office:spreadsheet"
	xmlns:o="urn:schemas-microsoft-com:office:office"
	xmlns:x="urn:schemas-microsoft-com:office:excel"
	xmlns:ss="urn:schemas-microsoft-com:office:spreadsheet"
	xmlns:html="http://www.w3.org/TR/REC-html40">

	<xsl:character-map name="a">
		<xsl:output-character character="&#10;" string="&amp;#10;" />
	</xsl:character-map>

	<xsl:output method="xml" indent="yes" omit-xml-declaration="no" use-character-maps="a"/>

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
					<Alignment ss:Horizontal="Center" ss:Vertical="Center"/>
				</Style>
				<Style ss:ID="s63">
					<Alignment ss:Vertical="Center" ss:WrapText="1"/>
				</Style>
				<Style ss:ID="s66">
					<Alignment ss:Horizontal="Center" ss:Vertical="Center"/>
					<Font ss:FontName="맑은 고딕" x:CharSet="129" x:Family="Modern" ss:Size="11" ss:Color="#000000" ss:Bold="1"/>
					<Interior ss:Color="#BFBFBF" ss:Pattern="Solid"/>
				</Style>
				<Style ss:ID="s67">
					<Alignment ss:Horizontal="Center" ss:Vertical="Center"/>
					<Interior ss:Color="#FFFF00" ss:Pattern="Solid"/>
				</Style>
				<Style ss:ID="s68">
					<Alignment ss:Vertical="Center"/>
					<Interior ss:Color="#FFFF00" ss:Pattern="Solid"/>
				</Style>
				<Style ss:ID="s69">
					<Alignment ss:Vertical="Center" ss:WrapText="1"/>
				</Style>
			</Styles>
            <Worksheet ss:Name="Index Review">
                <Table ss:ExpandedColumnCount="5" ss:ExpandedRowCount="1070" x:FullColumns="1" x:FullRows="1" ss:DefaultColumnWidth="54" ss:DefaultRowHeight="16.5">
				   <Column ss:StyleID="s62" ss:AutoFitWidth="0" ss:Width="88.5"/>
				   <Column ss:StyleID="s62" ss:AutoFitWidth="0" ss:Width="85.5"/>
				   <Column ss:AutoFitWidth="0" ss:Width="702.75"/>
				   <Column ss:AutoFitWidth="0" ss:Width="540"/>
				   <Column ss:AutoFitWidth="0" ss:Width="306.75"/>
				   <Row>
					    <Cell ss:StyleID="s66"><Data ss:Type="String">파일명</Data></Cell>
					    <Cell ss:StyleID="s66"><Data ss:Type="String">레벨</Data></Cell>
					    <Cell ss:StyleID="s66"><Data ss:Type="String">타이틀</Data></Cell>
					    <Cell ss:StyleID="s66"><Data ss:Type="String">인덱스</Data></Cell>
				   </Row>
                   <xsl:apply-templates/>
                </Table>
				<WorksheetOptions xmlns="urn:schemas-microsoft-com:office:excel">
					<Zoom>130</Zoom>
					<Selected/>
					<ProtectObjects>False</ProtectObjects>
					<ProtectScenarios>False</ProtectScenarios>
				</WorksheetOptions>
            </Worksheet>
        </Workbook>
    </xsl:template>

    <xsl:template match="list">
    	<xsl:for-each select="li">
    		<xsl:variable name="current" select="."/>
    		<Row>
    			<xsl:choose>
    				<xsl:when test="contains(index, '###')">
						<xsl:attribute name="ss:Height">33</xsl:attribute>
    				</xsl:when>
    				<xsl:otherwise>
						<xsl:attribute name="ss:AutoFitHeight">0</xsl:attribute>
    				</xsl:otherwise>
    			</xsl:choose>
		    	<xsl:for-each select="@*">
		    		<xsl:choose>
		    			<xsl:when test="name()='level'">
				    		<Cell>
				    			<xsl:if test=". = 1">
				    				<xsl:attribute name="ss:StyleID">s67</xsl:attribute>
				    			</xsl:if>
					            <Data ss:Type="Number">
					                <xsl:value-of select="."/>
					            </Data>
				         	</Cell>
		    			</xsl:when>
		    			<xsl:otherwise>
				    		<Cell>
				    			<xsl:if test="parent::li/@level = 1">
				    				<xsl:attribute name="ss:StyleID">s67</xsl:attribute>
				    			</xsl:if>
					            <Data ss:Type="String">
					                <xsl:value-of select="."/>
					            </Data>
				         	</Cell>
		    			</xsl:otherwise>
		    		</xsl:choose>
		    	</xsl:for-each>
				<xsl:choose>
					<xsl:when test="index">
			    		<Cell>
			    			<xsl:if test="@level = 1">
			    				<xsl:attribute name="ss:StyleID">s68</xsl:attribute>
			    			</xsl:if>
				            <Data ss:Type="String">
				                <xsl:value-of select="text()"/>
				            </Data>
			         	</Cell>
			    		<Cell>
			    			<xsl:choose>
				    			<xsl:when test="@level = 1">
				    				<xsl:attribute name="ss:StyleID">s68</xsl:attribute>
				    			</xsl:when>
				    			<xsl:when test="contains(index, '###')">
				    				<xsl:attribute name="ss:StyleID">s69</xsl:attribute>
				    			</xsl:when>
			    			</xsl:choose>
				            <Data ss:Type="String">
				                <xsl:value-of select="replace(replace(index, '%%%', '&#10;'), '###', '&#10;    ')"/>
				            </Data>
			         	</Cell>
					</xsl:when>
					<xsl:otherwise>
			    		<Cell>
			    			<xsl:if test="@level = 1">
			    				<xsl:attribute name="ss:StyleID">s68</xsl:attribute>
			    			</xsl:if>
				            <Data ss:Type="String">
				                <xsl:value-of select="."/>
				            </Data>
			         	</Cell>
			    		<Cell>
			    			<xsl:if test="@level = 1">
			    				<xsl:attribute name="ss:StyleID">s68</xsl:attribute>
			    			</xsl:if>
			         	</Cell>
					</xsl:otherwise>
				</xsl:choose>
         	</Row>
    	</xsl:for-each>
    </xsl:template>

</xsl:stylesheet>
