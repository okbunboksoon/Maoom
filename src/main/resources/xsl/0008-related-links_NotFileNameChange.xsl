<xsl:stylesheet version="2.0" 
	xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
	xmlns:xs="http://www.w3.org/2001/XMLSchema"
	exclude-result-prefixes="xs">

	<xsl:output method="xml" indent="no" omit-xml-declaration="yes"/>

	<xsl:strip-space elements="*" />
	<!--
	<xsl:preserve-space elements="p" />
	-->

	<xsl:template match="@* | node()">
		<xsl:copy>
			<xsl:apply-templates select="@* | node()"/>
		</xsl:copy>
	</xsl:template>

	<xsl:template match="topicref">
		<xsl:copy>
			<xsl:apply-templates select="@*"/>
			<!--
			<xsl:attribute name="href" select="concat(*[1]/@id, '.dita')"/>
			-->
			<xsl:apply-templates select="node()"/>
		</xsl:copy>
	</xsl:template>

	<xsl:template match="concept | task | reference">
		<xsl:copy>
			<xsl:apply-templates select="@* | node()"/>
			<xsl:choose>
				<xsl:when test="count(ancestor::topicref) = 1">
					<related-links>
						<linkpool>
							<linkpool>
								<xsl:for-each select="following-sibling::topicref">
									<link format="dita" href="{concat(concat(*[1]/@id, '.dita#'), *[1]/@id)}" role="child" scope="local" type="{*[1]/name()}">
										<linktext>
											<xsl:value-of select="*[1]/title"/>
										</linktext>
									</link>
								</xsl:for-each>
							</linkpool>
						</linkpool>
					</related-links>
				</xsl:when>
				<xsl:when test="count(ancestor::topicref) &gt; 1">
					<related-links>
						<linkpool>
							<xsl:for-each select="ancestor::topicref[2]">
								<link format="dita" href="{concat(concat(*[1]/@id, '.dita#'), *[1]/@id)}" role="parent" scope="local" type="{*[1]/name()}">
									<linktext>
										<xsl:value-of select="*[1]/title"/>
									</linktext>
								</link>
							</xsl:for-each>
						</linkpool>
					</related-links>
				</xsl:when>
			</xsl:choose>
		</xsl:copy>
	</xsl:template>

	<xsl:template match="indexterm/text()">
		<xsl:value-of select="replace(replace(., '\s+', '&#x20;'), '\s$', '')"/>
	</xsl:template>

</xsl:stylesheet>