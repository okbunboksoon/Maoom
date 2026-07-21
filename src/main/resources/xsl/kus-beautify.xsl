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

	<xsl:template match="comment()" priority="20">
		<xsl:variable name="depth" select="count(ancestor::*)"/>
		<xsl:call-template name="indentation">
			<xsl:with-param name="depth" select="$depth"/>
		</xsl:call-template>
		<xsl:copy/>
	</xsl:template>

	<xsl:template match="p">
		<xsl:variable name="depth" select="count(ancestor::*)"/>
		<xsl:call-template name="indentation">
			<xsl:with-param name="depth" select="$depth"/>
		</xsl:call-template>
		<xsl:copy>
			<xsl:apply-templates select="@* | node()"/>
			<xsl:if test="draft-comment">
				<xsl:call-template name="indentation">
					<xsl:with-param name="depth" select="$depth"/>
				</xsl:call-template>
			</xsl:if>
		</xsl:copy>
	</xsl:template>

	<xsl:template match="title | titlealts | colspec | toc | dt | fn | shortdesc | navtitle | linktext | cmd | info | choices | choice | data[@name='attr-width'] | data[@name='attr-height'] | old | new | li">
		<xsl:variable name="depth" select="count(ancestor::*)"/>
		<xsl:call-template name="indentation">
			<xsl:with-param name="depth" select="$depth"/>
		</xsl:call-template>
		<xsl:copy>
			<xsl:apply-templates select="@* | node()"/>
			<xsl:if test="(name()='choices' or name()='choice' or name()='new' or name()='old' or name()='title' or name()='shortdesc' or name()='cmd' or name()='info' or name()='li' or name()='fn') and (*[name()!='uicontrol'][name()!='xref'][name()!='image'][name()!='menucascade'][name()!='tm'][name()!='b'][name()!='term'])">
				<xsl:call-template name="indentation">
					<xsl:with-param name="depth" select="$depth"/>
				</xsl:call-template>
			</xsl:if>
		</xsl:copy>
	</xsl:template>

	<xsl:template match="entry/p[*[name()!='uicontrol'][name()!='xref'][name()!='image'][name()!='menucascade'][name()!='tm'][name()!='b'][name()!='term']]">
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

	<xsl:template match="chapter | topicref | concept | task | reference | desc | related-links | linkpool | link | fig | steps-unordered |
						 conbody | taskbody | refbody | sectiondiv |section | prolog | metadata | steps | substeps | step | substep | 
						 figgroup | ul | ol | note | table | tgroup | thead | tbody | row | pair | stepsection | result | postreq |
						 stepresult | dl | dlentry | dd | context | topicmeta | data[@name='wh-tile'] | data[@name='image']">
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

	<xsl:template name="indentation">
		<xsl:param name="depth"/>
		<xsl:text>&#x0A;</xsl:text>
		<xsl:for-each select="1 to $depth">
			<xsl:text>&#x09;</xsl:text>
		</xsl:for-each>
	</xsl:template>

	<xsl:template match="ph">
		<xsl:variable name="depth" select="count(ancestor::*)"/>
		<xsl:call-template name="indentation">
			<xsl:with-param name="depth" select="$depth"/>
		</xsl:call-template>
		<xsl:copy>
			<xsl:apply-templates select="@* | node()"/>
		</xsl:copy>
	</xsl:template>

	<xsl:template match="draft-comment">
		<xsl:variable name="depth" select="count(ancestor::*)"/>
		<xsl:call-template name="indentation">
			<xsl:with-param name="depth" select="$depth"/>
		</xsl:call-template>
		<xsl:copy>
			<xsl:apply-templates select="@* | node()"/>
			<xsl:if test="p or ul or ol or li or shortdesc">
				<xsl:call-template name="indentation">
					<xsl:with-param name="depth" select="$depth"/>
				</xsl:call-template>
			</xsl:if>
		</xsl:copy>
	</xsl:template>

</xsl:stylesheet>