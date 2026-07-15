<xsl:stylesheet version="3.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

    <xsl:output method="xml" indent="yes"/>

    <!-- 루트 -->
    <xsl:template match="/">

        <!--report 먼저 생성 -->
		<xsl:result-document href="table_report.xml">
		    <report>
		        <xsl:for-each select="//table[count(tgroup) >= 2]">
		            <item>
		                <!-- 여기 변경 -->
		                <file>
		                    <xsl:value-of select="ancestor::*[local-name()='topicref'][1]/@href"/>
		                </file>

		                <tgroup-count>
		                    <xsl:value-of select="count(tgroup)"/>
		                </tgroup-count>
		            </item>
		        </xsl:for-each>
		    </report>
		</xsl:result-document>

        <!--메인 결과 -->
        <xsl:apply-templates/>
    </xsl:template>


    <!-- 기본 복사 -->
    <xsl:template match="@* | node()">
        <xsl:copy>
            <xsl:apply-templates select="@* | node()"/>
        </xsl:copy>
    </xsl:template>

    <!-- tgroup 2개 이상이면 하나로 합침 -->
    <xsl:template match="table[count(tgroup) >= 2]">
        <xsl:copy>
            <xsl:apply-templates select="@*"/>
            <!-- 첫 번째 tgroup 기준 -->
            <xsl:variable name="first" select="tgroup[1]"/>
            <tgroup>
                <xsl:copy-of select="$first/@*"/>
                <xsl:copy-of select="$first/colspec"/>
                <tbody>
                    <!-- 모든 tgroup의 row 합침 -->
                    <xsl:for-each select="tgroup/tbody/row">
                        <xsl:copy-of select="."/>
                    </xsl:for-each>
                </tbody>
            </tgroup>

        </xsl:copy>

    </xsl:template>

</xsl:stylesheet>