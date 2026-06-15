<xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
	<xsl:import href="indentation1.xsl"/>
	<xsl:output method="xml" indent="no" omit-xml-declaration="no"/>
	<xsl:strip-space elements="*"/>
	<xsl:preserve-space elements="p"/>
	<xsl:template match="map">
		<xsl:text>&#x0A;</xsl:text>
		<xsl:copy>
			<xsl:apply-templates select="@* | node()"/>
			<xsl:text>&#x0A;</xsl:text>
		</xsl:copy>
	</xsl:template>
	<xsl:template match="topicmeta">
	</xsl:template>
	<xsl:template match="topicref">
		<xsl:variable name="depth" select="count(ancestor::*)"/>
		<xsl:call-template name="indentation">
			<xsl:with-param name="depth" select="$depth"/>
		</xsl:call-template>
		<xsl:copy>
			<xsl:apply-templates select="@* except (@href, @outputclass)"/>
			<xsl:attribute name="href" select="if (contains(@href, '#')) then substring-before(@href, '#') else @href"/>
			<xsl:if test="parent::map">
				<xsl:variable name="navtitle" select="normalize-space(lower-case(topicmeta/navtitle))"/>
				<xsl:variable name="wh_tile_image" select="tokenize(topicmeta/data[1]/data/@href, '/')[last()]"/>
				<xsl:variable name="chapnum" select="format-number(count(preceding-sibling::topicref), '00')"/>
				<xsl:variable name="filename">
					<xsl:choose>
						<xsl:when test="$navtitle = 'opening and closing'">opening_and_closing.xml</xsl:when>
						<xsl:when test="$navtitle = 'driver adjustments'">driver_adjustments.xml</xsl:when>
						<xsl:when test="$navtitle = 'controls and features'">controls_and_features.xml</xsl:when>
						<xsl:when test="not(topicmeta/data)">Foreword.xml</xsl:when>
						<xsl:when test="$wh_tile_image = 'electric_vehicle_guide.svg'">EV.xml</xsl:when>
						<xsl:when test="$wh_tile_image = 'introduction.svg'">Intro.xml</xsl:when>
						<xsl:when test="$wh_tile_image = 'hev.svg'">hev.xml</xsl:when>
						<xsl:when test="$wh_tile_image = 'your_vehicle_at_a_glance.svg'">Glance.xml</xsl:when>
						<xsl:when test="$wh_tile_image = 'vehicle_safety_controls.svg'">Safety.xml</xsl:when>
						<xsl:when test="$wh_tile_image = 'Vehicle_safety_controls.svg'">Safety.xml</xsl:when>
						<xsl:when test="$wh_tile_image = 'Infotainment.svg'">Infotainment.xml</xsl:when>
						<xsl:when test="$wh_tile_image = 'vehicle_controls.svg'">Features.xml</xsl:when>
						<xsl:when test="$wh_tile_image = 'vehicle_driving_controls.svg'">Driving.xml</xsl:when>
						<xsl:when test="$wh_tile_image = 'driver_assistance_guide.svg'">ADAS.xml</xsl:when>
						<xsl:when test="$wh_tile_image = 'what_to_do_in_an_emergency.svg'">Emergency.xml</xsl:when>
						<xsl:when test="$wh_tile_image = 'maintenance.svg'">Maintenance.xml</xsl:when>
						<xsl:when test="$wh_tile_image = 'specifications_consumer_information.svg'">Spec.xml</xsl:when>
						<xsl:when test="$wh_tile_image = 'abbreviation.svg'">Abbreviation.xml</xsl:when>
						<xsl:when test="$wh_tile_image = 'appendix.svg'">appendix.xml</xsl:when>
						<xsl:when test="$wh_tile_image = 'overview.svg'">Overview.xml</xsl:when>
						<xsl:when test="$wh_tile_image = 'specifications.svg'">Specifications.xml</xsl:when>
						<xsl:when test="$wh_tile_image = 'opening_and_closing.svg'">Opening_and_Closing.xml</xsl:when>
						<xsl:when test="$wh_tile_image = 'driver_adjustments.svg'">Driver_Adjustments.xml</xsl:when>
						<xsl:when test="$wh_tile_image = 'safety_restraints.svg'">Seating_and_Safety_Restraints.xml</xsl:when>
						<xsl:when test="$wh_tile_image = 'controls_and_features.svg'">Controls_and_Features.xml</xsl:when>
						<xsl:when test="$wh_tile_image = 'Seating and safety restraints.svg'">Seating_and_safety_restraints.xml</xsl:when>
						<xsl:when test="$wh_tile_image = 'consumer_info.svg'">Consumer_info.xml</xsl:when>
					</xsl:choose>
				</xsl:variable>
				<xsl:attribute name="filename" select="if ($chapnum = '00') then $filename else concat($chapnum, '_', $filename)"/>
			</xsl:if>
			<xsl:apply-templates select="node()"/>
			<xsl:if test="topicref">
				<xsl:call-template name="indentation">
					<xsl:with-param name="depth" select="$depth"/>
				</xsl:call-template>
			</xsl:if>
		</xsl:copy>
	</xsl:template>
</xsl:stylesheet>