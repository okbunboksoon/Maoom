<xsl:stylesheet version="2.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:f="urn:func"
    exclude-result-prefixes="f">

    <xsl:output method="xml" indent="no"/>
    <xsl:strip-space elements="*"/>

    <!-- 치환 규칙 -->
    <xsl:variable name="rules">
        <r from="ć" to="ć"/>
        <r from="Ć" to="Ć"/>
        <r from="ć" to="ć"/>
        <r from="Ć" to="Ć"/>
        <r from="č" to="č"/>
        <r from="Č" to="Č"/>
    </xsl:variable>	
    <!--
    <xsl:variable name="rules"> 
    		<r from="ć" to="ć"/> 
    		<r from="ć" to="ć"/> 
    		<r from="ć" to="ć"/> 
    		<r from="ć" to="ć"/> 
    		<r from="ć" to="ć"/> 
    		<r from="ć" to="ć"/> 
    		<r from="ć" to="ć"/> 
    		<r from="ć" to="ć"/> 
    		<r from="ć" to="ć"/> 
    </xsl:variable>
    -->
    <!-- 재귀 replace -->
    <xsl:function name="f:replace-all">
        <xsl:param name="text"/>
        <xsl:param name="rules"/>

        <xsl:choose>
            <xsl:when test="empty($rules)">
                <xsl:sequence select="$text"/>
            </xsl:when>
            <xsl:otherwise>
                <xsl:variable name="r" select="$rules[1]"/>
                <xsl:variable name="new"
                    select="replace($text, $r/@from, $r/@to)"/>
                <xsl:sequence select="f:replace-all($new, $rules[position()>1])"/>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:function>

	<xsl:template match="text()" priority="2">
	    <xsl:value-of select="f:replace-all(., $rules/r)"/>
	</xsl:template>

	<xsl:template match="@* | node()">
	    <xsl:copy>
	        <xsl:apply-templates select="@* | node()"/>
	    </xsl:copy>
	</xsl:template>

</xsl:stylesheet>