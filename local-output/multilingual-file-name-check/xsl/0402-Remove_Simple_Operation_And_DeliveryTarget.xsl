<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="2.0"
	xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

	<xsl:output method="xml" encoding="UTF-8"/>
	<xsl:param name="removeSimpleOperation" select="'N'"/>
	<xsl:param name="removeDeliveryTarget" select="'N'"/>

	<xsl:template match="@*|node()">
		<xsl:copy>
			<xsl:apply-templates select="@*|node()"/>
		</xsl:copy>
	</xsl:template>

	<xsl:template match="/*">
		<xsl:copy>
			<xsl:apply-templates select="@*"/>
			<xsl:attribute name="report-simple-operation-removed" select="if (upper-case($removeSimpleOperation) = 'Y') then count(.//section[title='Simple operation']) else 0"/>
			<xsl:attribute name="report-deliverytarget-removed" select="if (upper-case($removeDeliveryTarget) = 'Y') then count(.//@deliveryTarget) else 0"/>
			<xsl:apply-templates select="node()"/>
		</xsl:copy>
	</xsl:template>

	<xsl:template match="@deliveryTarget">
		<xsl:if test="upper-case($removeDeliveryTarget) != 'Y'">
			<xsl:copy/>
		</xsl:if>
	</xsl:template>

	<xsl:template match="section[title='Simple operation']">
		<xsl:if test="upper-case($removeSimpleOperation) != 'Y'">
			<xsl:copy>
				<xsl:apply-templates select="@*|node()"/>
			</xsl:copy>
		</xsl:if>
	</xsl:template>

</xsl:stylesheet>
