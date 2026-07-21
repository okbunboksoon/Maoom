<xsl:stylesheet version="2.0" 
	xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
	xmlns:xs="http://www.w3.org/2001/XMLSchema"
	exclude-result-prefixes="xs">

	<xsl:template match="@* | node()"> 
		<xsl:copy>
			<xsl:apply-templates select="@* | node()"/>
		</xsl:copy>
	</xsl:template>

	<xsl:template match="indexterm[not(ancestor::p)]">
		<xsl:variable name="depth" select="count(ancestor::*)"/>
		<xsl:call-template name="indentation">
			<xsl:with-param name="depth" select="$depth"/>
		</xsl:call-template>
		<xsl:copy>
			<xsl:apply-templates select="@* | node()"/>
			<xsl:if test="not(parent::indexterm) and child::indexterm">
				<xsl:call-template name="indentation">
					<xsl:with-param name="depth" select="$depth"/>
				</xsl:call-template>
			</xsl:if>
		</xsl:copy>
	</xsl:template>

	<xsl:template match="image">
		<xsl:variable name="depth" select="count(ancestor::*)"/>
		<xsl:if test="parent::figgroup or parent::fig">
			<xsl:call-template name="indentation">
				<xsl:with-param name="depth" select="$depth"/>
			</xsl:call-template>
		</xsl:if>
		<xsl:copy>
			<xsl:apply-templates select="@* | node()"/>
		</xsl:copy>
	</xsl:template>

	<xsl:template match="title | titlealts | colspec | toc | dt | shortdesc | navtitle | linktext | cmd |
						 data[@name='attr-width'] | data[@name='attr-height']">
		<xsl:variable name="depth" select="count(ancestor::*)"/>
		<xsl:call-template name="indentation">
			<xsl:with-param name="depth" select="$depth"/>
		</xsl:call-template>
		<xsl:copy>
			<xsl:apply-templates select="@* | node()"/>
		</xsl:copy>
	</xsl:template>

	<xsl:template match="p">
		<xsl:variable name="depth" select="count(ancestor::*)"/>
		<xsl:if test="not(parent::p) and not(parent::title)">
			<xsl:call-template name="indentation">
				<xsl:with-param name="depth" select="$depth"/>
			</xsl:call-template>
		</xsl:if>
		<xsl:copy>
			<xsl:apply-templates select="@* | node()"/>
			<xsl:if test="image[@placement='break'] or note or ul or ol or fig or figgroup">
				<xsl:call-template name="indentation">
					<xsl:with-param name="depth" select="$depth"/>
				</xsl:call-template>
			</xsl:if>
		</xsl:copy>
	</xsl:template>

	<xsl:template match="fn/p">
		<xsl:copy>
			<xsl:apply-templates select="@* | node()"/>
		</xsl:copy>
	</xsl:template>

	<xsl:template match="chapter | topicref | concept | task | reference | desc | related-links | linkpool | link | result | postreq | tasktroubleshooting |
						 conbody | taskbody | refbody | sectiondiv |section | prolog | metadata | choices | choice | steps | step | stepsection |
						 fig | figgroup | ul | ol | note | table | tgroup | thead | tbody | row | substeps | substep | stepresult | steps-unordered |
						 dl | dlentry | dd | context | topicmeta | data[@name='wh-tile'] | data[@name='image']">
		<xsl:variable name="depth" select="count(ancestor::*)"/>
		<xsl:call-template name="indentation">
			<xsl:with-param name="depth" select="$depth"/>
		</xsl:call-template>
		<xsl:copy>
			<xsl:apply-templates select="@* | node()"/>
			<xsl:call-template name="indentation">
				<xsl:with-param name="depth" select="$depth"/>
			</xsl:call-template>
		</xsl:copy>
	</xsl:template>

	<xsl:template match="li | info">
		<xsl:variable name="depth" select="count(ancestor::*)"/>
		<xsl:call-template name="indentation">
			<xsl:with-param name="depth" select="$depth"/>
		</xsl:call-template>
		<xsl:copy>
			<xsl:apply-templates select="@* | node()"/>
			<xsl:if test="p or image[@placement='break'] or note or ul or ol or fig or figgroup">
				<xsl:call-template name="indentation">
					<xsl:with-param name="depth" select="$depth"/>
				</xsl:call-template>
			</xsl:if>
		</xsl:copy>
	</xsl:template>

	<xsl:template match="entry | keywords">
		<xsl:variable name="depth" select="count(ancestor::*)"/>
		<xsl:call-template name="indentation">
			<xsl:with-param name="depth" select="$depth"/>
		</xsl:call-template>
		<xsl:copy>
			<xsl:apply-templates select="@* | node()"/>
			<xsl:if test="*">
				<xsl:call-template name="indentation">
					<xsl:with-param name="depth" select="$depth"/>
				</xsl:call-template>
			</xsl:if>
		</xsl:copy>
	</xsl:template>

	<xsl:template match="text()" priority="20"> 
		<xsl:value-of select="replace(., '\s+', '&#x20;')"/>
	</xsl:template>

	<xsl:template name="indentation">
		<xsl:param name="depth"/>
		<xsl:text>&#x0A;</xsl:text>
		<xsl:for-each select="1 to $depth">
			<xsl:text>&#x09;</xsl:text>
		</xsl:for-each>
	</xsl:template>

</xsl:stylesheet>