<xsl:stylesheet version="2.0" 
	xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
	xmlns:xs="http://www.w3.org/2001/XMLSchema"
	exclude-result-prefixes="xs">

	<xsl:output method="xml" indent="no" omit-xml-declaration="no"/>
	<xsl:strip-space elements="*" />
	<xsl:preserve-space elements="p"/>

	<xsl:template match="@* | node()"> 
		<xsl:copy>
			<xsl:apply-templates select="@* | node()"/>
		</xsl:copy>
	</xsl:template>

	<xsl:template match="map">
		<xsl:copy>
			<xsl:apply-templates select="@* | node()"/>
		</xsl:copy>
	</xsl:template>

	<xsl:template match="chapter">
		<xsl:apply-templates />
	</xsl:template>

	<xsl:template match="topicref">
		<xsl:copy>
			<xsl:apply-templates select="@*"/>
			<topicmeta>
				<navtitle>
					<xsl:choose>
						<xsl:when test="@href = 'OM_14_3_Appendix_RU.dita'">Сокращение</xsl:when>
						<xsl:otherwise>
							<xsl:value-of select="if ( string-length(*[1]/title) &gt; 59 ) then concat(substring(*[1]/title, 1, 59), '...') else substring(*[1]/title, 1, 60)"/>
						</xsl:otherwise>
					</xsl:choose>
				</navtitle>
				<linktext>
					<xsl:choose>
						<xsl:when test="@href = 'OM_14_3_Appendix_RU.dita'">Сокращение</xsl:when>
						<xsl:otherwise>
							<xsl:value-of select="*[1]/title"/>
						</xsl:otherwise>
					</xsl:choose>
				</linktext>
				
				<xsl:if test="parent::chapter and count(parent::chapter/preceding-sibling::chapter) &gt;= 1">
					<xsl:variable name="filename" select="lower-case(parent::chapter/@filename)"/>
					<xsl:variable name="wh_tile_image">
						<xsl:choose>
							<!--<xsl:when test="contains($filename, '_hev') or contains($filename, '_ev')">electric_vehicle_guide.svg</xsl:when>-->
							<xsl:when test="contains($filename, 'hev')">hev.svg</xsl:when>
							<xsl:when test="contains($filename, 'EV')">electric_vehicle_guide.svg</xsl:when>
							<xsl:when test="contains($filename, 'intro')">introduction.svg</xsl:when>
							<xsl:when test="contains($filename, 'glance')">your_vehicle_at_a_glance.svg</xsl:when>
							<xsl:when test="contains($filename, 'safety')">vehicle_safety_controls.svg</xsl:when>
							<xsl:when test="contains($filename, 'features')">vehicle_controls.svg</xsl:when>
							<xsl:when test="contains($filename, 'infotainment')">Infotainment.svg</xsl:when>
							<xsl:when test="contains($filename, 'driving')">vehicle_driving_controls.svg</xsl:when>
							<xsl:when test="contains($filename, 'adas')">driver_assistance_guide.svg</xsl:when>
							<xsl:when test="contains($filename, 'emergency')">what_to_do_in_an_emergency.svg</xsl:when>
							<xsl:when test="contains($filename, 'maintenance')">maintenance.svg</xsl:when>
							<xsl:when test="contains($filename, 'spec')">specifications_consumer_information.svg</xsl:when>
							<xsl:when test="contains($filename, 'abbreviation')">abbreviation.svg</xsl:when>
							<xsl:when test="contains($filename, 'appendix')">appendix.svg</xsl:when>
							<xsl:when test="contains($filename, 'manual')">electric_vehicle_guide.svg</xsl:when>
							<xsl:when test="contains($filename, 'overview')">your_vehicle_at_a_glance.svg</xsl:when>
							<xsl:when test="contains($filename, 'specifications')">specifications.svg</xsl:when>
							<xsl:when test="contains($filename, 'opening_and_closing')">vehicle_controls.svg</xsl:when>
							<xsl:when test="contains($filename, 'driver_adjustments')">vehicle_controls.svg</xsl:when>
							<xsl:when test="contains($filename, 'seating_and_safety_restraints')">safety_restraints.svg</xsl:when>
							<xsl:when test="contains($filename, 'controls_and_features')">vehicle_controls.svg</xsl:when>
							<xsl:when test="contains($filename, 'consumer_info')">specifications_consumer_information.svg</xsl:when>	
							<xsl:when test="contains($filename, 'Seating_and_Safety_Restraints')">safety_restraints.svg</xsl:when>							
						</xsl:choose>
					</xsl:variable>
					<data name="wh-tile">
						<data name="image" format="svg">
							<xsl:call-template name="add-href">
								<xsl:with-param name="href" select="concat('./oxygen-webhelp/template/images/', $wh_tile_image)"/>
							</xsl:call-template>
							<data name="attr-width" value="64"/>
							<data name="attr-height" value="64"/>
						</data>
					</data>
				</xsl:if>

			</topicmeta>
			<xsl:apply-templates select="topicref"/>
		</xsl:copy>
	</xsl:template>

	<xsl:template name="add-href">
		<xsl:param name="href"/>
		<xsl:attribute name="href" select="$href"/>
	</xsl:template>

</xsl:stylesheet>