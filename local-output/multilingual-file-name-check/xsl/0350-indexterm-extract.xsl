<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="2.0">

	<xsl:output method="xml" indent="no" omit-xml-declaration="yes"/>
    <xsl:strip-space elements="*"/>
    <xsl:param name="indexLevel" select="3"/>

    <xsl:template match="map">
    	<list>
			<xsl:for-each select=".//topicref[count(ancestor-or-self::topicref) &lt;= number($indexLevel)]">
				<xsl:variable name="level" select="count(ancestor-or-self::topicref)"/>
				<xsl:text>&#xA;&#x9;</xsl:text>
				<li file="{@href}" level="{$level}">
					<xsl:text>&#x20;</xsl:text>
					<xsl:for-each select="1 to $level - 1">
						<xsl:text>&#x20;&#x20;&#x20;&#x20;</xsl:text>
					</xsl:for-each>
					<xsl:value-of select="normalize-space(*/title)"/>
					<xsl:apply-templates select="* except topicref"/>
				</li>
			</xsl:for-each>
			<xsl:text>&#xA;</xsl:text>
		</list>
    </xsl:template>

    <xsl:template match="concept | task | reference">
		<xsl:apply-templates select=".//keywords"/>
    </xsl:template>

    <xsl:template match="keywords">
    	<index>
			<xsl:apply-templates/>
    	</index>
    </xsl:template>

    <xsl:template match="indexterm">
    	<xsl:choose>
    		<xsl:when test="parent::indexterm">
    			<xsl:text>###</xsl:text>
    			<xsl:apply-templates/>
    		</xsl:when>
    		<xsl:otherwise>
    			<xsl:if test="preceding-sibling::indexterm">
    				<xsl:text>%%%</xsl:text>
    			</xsl:if>
    			<xsl:apply-templates/>
    		</xsl:otherwise>
    	</xsl:choose>
    </xsl:template>

    <xsl:template match="text()">
    	<xsl:value-of select="normalize-space(.)"/>
    </xsl:template>

</xsl:stylesheet>
