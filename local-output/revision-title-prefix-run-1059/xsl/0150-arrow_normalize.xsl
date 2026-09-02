<xsl:stylesheet version="2.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:xs="http://www.w3.org/2001/XMLSchema"
                exclude-result-prefixes="xs">

	<xsl:output method="xml" indent="no"/>

	<!-- 공백 정리 -->
	<xsl:strip-space elements="*"/>
	<xsl:preserve-space elements=""/>

	<!-- 기본 copy -->
	<xsl:template match="@* | node()">
		<xsl:copy>
			<xsl:apply-templates select="@* | node()"/>
		</xsl:copy>
	</xsl:template>

	<!-- 0. 전역 텍스트 정제 : 「 」만 제거 -->
	<xsl:template match="text()" priority="5">
		<xsl:value-of select="replace(replace(., '「', ' '), '」', '')"/>
	</xsl:template>

	<!-- 1. p 안에서 term + b + uicontrol + image + → 메뉴 -->
	<xsl:template match="p[(term or b or uicontrol or image) and contains(., '→')]" priority="50">
		<p>
			<xsl:apply-templates select="@*"/>

			<xsl:for-each-group
					select="node()"
					group-adjacent="
					if (
						(
							self::term
							or self::b
							or self::uicontrol
							or self::image
						)
						and not(self::b[contains(normalize-space(.), '→')])
						and not(self::uicontrol[contains(normalize-space(.), '→')])
						or (self::text() and contains(., '→'))
						or (
							self::text()
							and matches(., '^\s*/\s*$')
							and (
								preceding-sibling::node()[1][self::term]
								or following-sibling::node()[1][self::term]
							)
						)
					)
					then 'menu'
					else generate-id()
				">

				<xsl:choose>

					<!-- 메뉴 그룹 -->
					<xsl:when test="count(current-group()[self::term or self::b or self::uicontrol or self::image]) &gt; 1">

						<menucascade modified="menucascade-added">

							<xsl:for-each select="
								current-group()[
									self::term
									or self::b
									or self::uicontrol
									or self::image
								]
							">

								<xsl:choose>

									<!-- image -->
									<xsl:when test="self::image">

										<uicontrol>
											<xsl:copy-of select="."/>
										</uicontrol>

									</xsl:when>

									<!-- slash 옵션 묶음의 두 번째 이후 term은 첫 term에서 합쳐 출력 -->
									<xsl:when test="self::term and preceding-sibling::node()[1][self::text()[matches(., '^\s*/\s*$')]]"/>

									<!-- slash 옵션 묶음: Extended/Normal/Off -->
									<xsl:when test="self::term and following-sibling::node()[1][self::text()[matches(., '^\s*/\s*$')]]">
										<xsl:variable name="option-end" select="
											following-sibling::node()[
												not(
													self::text()[matches(., '^\s*/\s*$')]
													or (
														self::term
														and preceding-sibling::node()[1][self::text()[matches(., '^\s*/\s*$')]]
													)
												)
											][1]"/>
										<uicontrol>
											<xsl:value-of select="
												string-join(
													(
														normalize-space(.),
														for $n in following-sibling::node()[
															(not($option-end) or . &lt;&lt; $option-end)
															and (
																self::text()[matches(., '^\s*/\s*$')]
																or self::term
															)
														]
														return normalize-space($n)
													),
													''
												)
											"/>
										</uicontrol>
									</xsl:when>

									<!-- 일반 -->
									<xsl:otherwise>

										<uicontrol>
											<xsl:value-of select="normalize-space(.)"/>
										</uicontrol>

									</xsl:otherwise>

								</xsl:choose>

							</xsl:for-each>

						</menucascade>

					</xsl:when>

					<xsl:otherwise>
						<xsl:apply-templates select="current-group()"/>
					</xsl:otherwise>

				</xsl:choose>

			</xsl:for-each-group>

		</p>
	</xsl:template>

	<!-- 2. b 안에 메뉴 경로가 있는 경우 -->
	<xsl:template match="b[contains(., '→')]" priority="40">
		<xsl:variable name="clean" select="translate(normalize-space(.), '()[]「」', '')"/>
		<menucascade modified="menucascade-added">
			<xsl:for-each select="tokenize($clean, '→')">
				<uicontrol>
					<xsl:value-of select="normalize-space(.)"/>
				</uicontrol>
			</xsl:for-each>
		</menucascade>
	</xsl:template>

	<!-- 3. uicontrol 안에 메뉴 경로가 있는 경우 -->
	<xsl:template match="uicontrol[contains(., '→')]" priority="40">
		<xsl:variable name="clean" select="translate(normalize-space(.), '()[]「」', '')"/>
		<menucascade modified="menucascade-added">
			<xsl:for-each select="tokenize($clean, '→')">
				<uicontrol>
					<xsl:value-of select="normalize-space(.)"/>
				</uicontrol>
			</xsl:for-each>
		</menucascade>
	</xsl:template>

	<!-- 4. 순수 텍스트 메뉴 -->
	<xsl:template match="text()[contains(., '→')
	              and not(parent::uicontrol)
	              and not(parent::b)
	              and not(parent::term)]"
	              priority="20">
		<xsl:variable name="clean" select="translate(., '[]「」', '')"/>
		<xsl:choose>
			<xsl:when test="matches($clean, '^\s*→\s*$')">
				<xsl:value-of select="$clean"/>
			</xsl:when>
			<xsl:otherwise>
				<xsl:analyze-string
						select="$clean"
						regex="(\([^()]+→[^()]+\))|('?[^']+→[^']+'?)">
					<xsl:matching-substring>
						<xsl:choose>
							<!--  ( ) 있는 경우 -->
							<xsl:when test="starts-with(regex-group(0), '(')">
								<xsl:value-of select="'('"/>
								<menucascade modified="menucascade-added">
									<xsl:for-each
											select="tokenize(substring(regex-group(0), 2, string-length(regex-group(0)) - 2), '→')">
										<uicontrol>
											<xsl:value-of select="normalize-space(.)"/>
										</uicontrol>
									</xsl:for-each>
								</menucascade>
								<xsl:value-of select="')'"/>
							</xsl:when>
							<!-- 괄호 없는 경우 → 기존 로직 -->
							<xsl:otherwise>
								<menucascade modified="menucascade-added">
									<xsl:for-each select="tokenize(translate(regex-group(0), '''', ''), '→')">
										<uicontrol>
											<xsl:value-of select="normalize-space(.)"/>
										</uicontrol>
									</xsl:for-each>
								</menucascade>
							</xsl:otherwise>
						</xsl:choose>
					</xsl:matching-substring>
					<xsl:non-matching-substring>
						<xsl:value-of select="."/>
					</xsl:non-matching-substring>
				</xsl:analyze-string>
			</xsl:otherwise>
		</xsl:choose>
	</xsl:template>
</xsl:stylesheet>
