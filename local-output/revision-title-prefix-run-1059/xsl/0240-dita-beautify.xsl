<xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

	<xsl:import href="indentation1.xsl"/>
	
	<xsl:output method="xml" indent="no" omit-xml-declaration="no"/>
	<xsl:strip-space elements="*" />
	<xsl:preserve-space elements="p"/>

	<xsl:variable name="files" select="collection('../topics/?select=*.dita')"/>

	<xsl:template match="map">
		<dummy/>
		<xsl:for-each select="$files">
			<xsl:result-document href="{concat('../topics/', tokenize(base-uri(.), '/')[last()])}">
				<xsl:apply-templates select="."/>
			</xsl:result-document>
		</xsl:for-each>
	</xsl:template>

	<xsl:template match="concept | task | reference">
		<xsl:text>&#x0A;</xsl:text>
		<xsl:choose>
			<xsl:when test="name() = 'concept'">
				<xsl:text disable-output-escaping="yes">&lt;!DOCTYPE concept PUBLIC &quot;-//OASIS//DTD DITA 1.3 Concept//EN&quot; &quot;concept.dtd&quot;&gt;</xsl:text>
			</xsl:when>
			<xsl:when test="name() = 'task'">
				<xsl:text disable-output-escaping="yes">&lt;!DOCTYPE task PUBLIC &quot;-//OASIS//DTD DITA 1.3 Task//EN&quot; &quot;task.dtd&quot;&gt;</xsl:text>
			</xsl:when>
			<xsl:when test="name() = 'reference'">
				<xsl:text disable-output-escaping="yes">&lt;!DOCTYPE reference PUBLIC &quot;-//OASIS//DTD DITA 1.3 Reference//EN&quot; &quot;reference.dtd&quot;&gt;</xsl:text>
			</xsl:when>
		</xsl:choose>
		<xsl:text>&#x0A;</xsl:text>
		<xsl:copy>
			<xsl:apply-templates select="@* | node()"/>
			<xsl:text>&#x0A;</xsl:text>
		</xsl:copy>
	</xsl:template>

</xsl:stylesheet>