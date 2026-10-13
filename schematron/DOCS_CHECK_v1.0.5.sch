<schema xmlns="http://purl.oclc.org/dsdl/schematron" queryBinding="xslt2">

<!--
V1.0.2 10.02
	정수부가 없는 소수 검사 추가
V1.0.3 10.03
	1. height, width, scale 속성이 모두 없는 경우 검출
	2. height, width, scale 속성이 모두 있는 경우 검출
	3. scale 속성이 없고, height와 width 중 하나만 있는 경우 검출
V1.0.5 10.12
	Severity Level 추가
-->

	<!-- 1 -->
	<!-- scale=15 이미지 placement inline 검사 -->
	<pattern id="inline-image-check">
		<rule context="image[@scale='15']">
			<assert role="none" test="@placement = 'inline'">
				@scale이 'inline'인 image의 @placement 속성값은 "inline" 이어야 합니다.
			</assert>
		</rule>
	</pattern>

	<!-- 2 -->
	<!-- image 크기 속성 조합 검사 10.03 -->
	<pattern id="image-size-attribute-check">
		<rule context="image">
			<!-- height, width, scale 속성이 모두 없는 경우 -->
			<assert role="none" test="@height or @width or @scale">
				image에 height, width, scale 속성이 모두 없습니다.
			</assert>

			<!-- height, width, scale 속성이 모두 있는 경우 -->
			<assert role="none" test="not(@height and @width and @scale)">
				image에 height, width, scale 속성을 모두 함께 사용할 수 없습니다.
			</assert>

			<!-- scale이 없고 height와 width 중 하나만 있는 경우 -->
			<assert role="none" test="not(not(@scale) and ((@height and not(@width)) or (@width and not(@height))))">
				image에 scale 속성이 없을 때는 height와 width 중 하나만 사용할 수 없습니다.
			</assert>
		</rule>
	</pattern>

	<!-- 3 -->
	<!-- figgroup 내부 image placement break 검사 -->
	<!--
	<pattern id="image-placement-check">
	  <rule context="fig/figgroup/image[not(ancestor::table and @scale='15')]">
	    <assert role="none" test="@placement = 'break'">
	      figgroup 내부의 image 요소는 @placement 속성값이 "break" 이어야 합니다.
	    </assert>
	  </rule>
	</pattern>
	-->

	<pattern id="image-placement-check">
		<rule context="fig/figgroup/image[not(ancestor::table and @scale='15') and not(contains(@href, 'WKIA'))]">
			<assert role="none" test="@placement = 'break'">
				figgroup 내부의 image 요소는 @placement 속성값이 "break" 이어야 합니다.
			</assert>
		</rule>
	</pattern>

	<!-- 4 -->
	<!-- image scale 허용값 검사 -->
	<pattern id="image-scale-check">
		<rule context="image[@scale]">
			<assert role="none" test="@scale='95' or @scale='70' or @scale='50' or @scale='30' or @scale='15'">
				image의 기본 scale 속성값은 95, 70, 50, 30, 15 이외 값으로 지정 되었습니다.
			</assert>
		</rule>
	</pattern>

	<!-- 5 -->
	<!-- table tgroup 1개만 허용 -->
	<pattern id="single-tgroup-check">
		<rule context="table">
			<assert role="none" test="count(tgroup) = 1">
				table은 반드시 하나의 tgroup만을 가져야 합니다.
			</assert>
		</rule>
	</pattern>

	<!-- 6 -->
	<!-- image data 경로 오류 검사 -->
	<pattern id="image-path-check">
		<rule context="data[@name='image']">
			<assert role="none" test="not(@format='svg' and @href = './oxygen-webhelp/template/images/')">
				DITAMAP의 SVG 잘못된 image 경로가 사용되었습니다.
			</assert>
		</rule>
	</pattern>

	<!-- 7 -->
	<!-- topicref에 topicmeta 필수 검사 -->
	<pattern id="topicref-topicmeta-check">
		<rule context="map/topicref[not(@type='subjectScheme')]">
			<assert role="none" test="topicmeta">
				DITAMAP의 1레벨 topicref에는 topicmeta가 있어야 합니다.(forword 제외)
			</assert>
		</rule>
	</pattern>

	<!-- 8 -->
	<!-- topicmeta에 navtitle 필수 검사 -->
	<pattern id="topicmeta-navtitle-check">
		<rule context="map/topicref/topicmeta">
			<assert role="none" test="navtitle">
				topicmeta에는 navtitle이 있어야 합니다.
			</assert>
		</rule>
	</pattern>

	<!-- 9 -->
	<!-- image href 경로 형식 검사 -->
	<pattern id="image-href-format-check">
		<rule context="image[@href]">
			<assert role="none" test=" starts-with(@href, '../../images/')">
				image의 href는 반드시 '../../images/' 경로를 사용해야 합니다.
			</assert>
		</rule>
	</pattern>

	<!-- 10 -->
	<!-- xref href 금지 문자 (%) 검사 -->
	<pattern id="xref-invalid-char-check">
		<rule context="xref[@href]">
			<assert role="none" test="not(contains(@href, '%'))">
				xref의 href에 '%' 문자가 포함되면 안됩니다.
			</assert>
		</rule>
	</pattern>

	<!-- 11 -->
	<!-- 빈 태그 금지 검사 (특정 태그 제외) -->
	<pattern id="empty-element-check">
		<rule context="*[not(ancestor-or-self::map) and not(self::xref) and not(self::image) and not(self::colspec) and not(self::entry) and not(self::tm)
							and not(self::related-links) and not(self::linkpool) and not(self::linkpool) and not(self::xref)
							and not(self::image) and not(self::indexterm)]">
			<assert role="none" test="normalize-space(.) != '' or *">내용이 없는 빈 태그 &lt;<name/>/&gt;는 사용할 수 없습니다.</assert>
		</rule>
	</pattern>

	<!-- 12 -->
	<!-- 요소 시작 공백 검사 -->
	<pattern id="trim-leading-whitespace-check">
		<rule context="p | ph | title | shortdesc">
			<assert role="none" test="not(text()[1][normalize-space(.) != '' and starts-with(., ' ') and not(preceding-sibling::*)])">
				문장 앞에 불필요한 공백이 있습니다.
			</assert>
		</rule>
	</pattern>

	<!-- 13 -->
	<!-- 문장 요소 끝 공백 검사 -->
	<pattern id="trim-trailing-whitespace-check">
		<rule context="p | title | shortdesc">

			<!-- 마지막 노드가 내용이 있는 텍스트이고 공백으로 끝나는 경우 -->
			<assert role="none" test="not(
				node()[last()]
					[self::text()]
					[normalize-space(.) != '']
					[ends-with(., ' ')]
			)">
				문장 끝에 불필요한 공백이 있습니다.
			</assert>

			<!-- 인라인 태그 뒤에 공백만 있는 텍스트가 남은 경우 -->
			<assert role="none" test="not(
				node()[last()]
					[self::text()]
					[normalize-space(.) = '']
					[preceding-sibling::*[1][
						self::b
						or self::i
						or self::u
						or self::tt
						or self::ph
						or self::xref
						or self::keyword
						or self::uicontrol
						or self::menucascade
						or contains(@class, ' hi-d/b ')
						or contains(@class, ' hi-d/i ')
						or contains(@class, ' hi-d/u ')
						or contains(@class, ' hi-d/tt ')
						or contains(@class, ' topic/ph ')
						or contains(@class, ' topic/xref ')
						or contains(@class, ' topic/keyword ')
						or contains(@class, ' ui-d/uicontrol ')
						or contains(@class, ' ui-d/menucascade ')
					]]
			)">
				문장 끝에 불필요한 공백이 있습니다.
			</assert>

		</rule>
	</pattern>

	<!-- 14 -->
	<!-- 인라인 태그 끝 공백 검사 -->
	<pattern id="inline-trailing-whitespace-check">
		<rule context="
			ph
			| b
			| i
			| u
			| tt
			| xref
			| keyword
			| uicontrol
			| menucascade
			| *[
				contains(@class, ' hi-d/b ')
				or contains(@class, ' hi-d/i ')
				or contains(@class, ' hi-d/u ')
				or contains(@class, ' hi-d/tt ')
				or contains(@class, ' topic/ph ')
				or contains(@class, ' topic/xref ')
				or contains(@class, ' topic/keyword ')
				or contains(@class, ' ui-d/uicontrol ')
			]
		">

			<!-- 마지막 노드가 내용이 있는 텍스트이고 공백으로 끝나는 경우 -->
			<assert role="none" test="not(
				node()[last()]
					[self::text()]
					[normalize-space(.) != '']
					[ends-with(., ' ')]
			)">
				인라인 태그 끝에 불필요한 공백이 있습니다.
			</assert>

			<!-- 인라인 태그 뒤에 공백만 있는 텍스트가 남은 경우 -->
			<assert role="none" test="not(
				node()[last()]
					[self::text()]
					[normalize-space(.) = '']
					[preceding-sibling::*[1][
						self::b
						or self::i
						or self::u
						or self::tt
						or self::ph
						or self::xref
						or self::keyword
						or self::uicontrol
						or self::menucascade
						or contains(@class, ' hi-d/b ')
						or contains(@class, ' hi-d/i ')
						or contains(@class, ' hi-d/u ')
						or contains(@class, ' hi-d/tt ')
						or contains(@class, ' topic/ph ')
						or contains(@class, ' topic/xref ')
						or contains(@class, ' topic/keyword ')
						or contains(@class, ' ui-d/uicontrol ')
						or contains(@class, ' ui-d/menucascade ')
					]]
			)">
				인라인 태그 끝에 불필요한 공백이 있습니다.
			</assert>

		</rule>
	</pattern>

	<!-- 15 -->
	<!-- ul아래 p 사용 금지 -->
	<pattern id="ul-direct-p-check">
		<rule context="ul">
			<assert role="none" test="not(p)">
				ul 바로 아래에 p 태그를 직접 사용할 수 없습니다.
				li를 사용해야 합니다.
			</assert>
		</rule>
	</pattern>

	<!-- 16 -->
	<!-- li 내부 직접 텍스트 사용 금지 -->
	<pattern id="li-direct-text-check">
		<rule context="li">
			<assert role="none" test="not(text()[normalize-space()])">
				li 안에 텍스트를 직접 작성할 수 없습니다.
				p 태그로 감싸야 합니다.
			</assert>
		</rule>
	</pattern>

	<!-- 17 -->
	<!-- step 내부 직접 텍스트 사용 금지 -->
	<pattern id="step-cmd-check">
		<rule context="step">
			<assert role="none" test="cmd">
				step에는 cmd가 반드시 있어야 합니다.
			</assert>
		</rule>
	</pattern>

	<!-- 18 -->
	<!-- 블록 요소 내부 직접 텍스트 금지 -->
	<pattern id="mixed-text-block-check">
		<rule context="conbody | taskbody | refbody | section">
			<assert role="none" test="not(text()[normalize-space()])">
				블록 요소 외에 불필요한 텍스트가 포함되어 있습니다.
			</assert>
		</rule>
	</pattern>

	<!-- 19 -->
	<!-- xref 형식 체크 -->
	<pattern id="xref-format-check">
		<rule context="xref[@href][not(@format='html')]">
			<!-- # 포함 필수 -->
			<assert role="none" test="contains(@href, '#')">
				xref는 반드시 '#id' 형식을 포함해야 합니다.
			</assert>
			<!-- # 이후 / 금지 -->
			<!-- <assert role="none" test="not(contains(substring-after(@href, '#'), '/'))">
				xref의 href는 topic ID까지만 사용할 수 있습니다.
				# 뒤에 element ID(/e00000 형태)는 사용할 수 없습니다.
			</assert> -->
		</rule>
	</pattern>

	<!-- 20 -->
	<!-- 허용된 DITA 태그 검사 (목록 외 태그 사용 시 오류) -->
	<pattern id="invalid-tag-check">
		<rule context="*[not(
				  self::concept or self::task or self::reference or
				  self::conbody or self::taskbody or self::refbody or
				  self::title or self::shortdesc or self::p or
				  self::ul or self::ol or self::li or
				  self::steps or self::step or self::cmd or self::substeps or self::substep or self::info or self::context or self::steps-unordered or
				  self::section or self::sectiondiv or self::div or
				  self::fig or self::figgroup or self::image or
				  self::xref or self::link or self::linktext or
				  self::note or self::fn or self::tm or self::itemgroup or
				  self::choices or self::choice or self::draft-comment or
				  self::table or self::desc or self::tgroup or self::thead or self::tbody or self::row or self::entry or self::colspec or
				  self::ph or self::b or self::i or self::u or
				  self::map or self::topicref or self::topicmeta or self::navtitle or
				  self::related-links or self::linkpool or
				  self::term or self::uicontrol or self::menucascade or
				  self::dl or self::dlentry or self::dt or self::dd or self::result or self::tasktroubleshooting or
				  self::postreq or self::stepresult or self::sub or self::sup or self::stepsection or self::steps-unordered or
				  self::metadata or self::data or self::prolog or self::keywords or self::indexterm
				)]">
			<assert role="none" test="false()">
				허용되지 않은 태그가 사용되었습니다.
			</assert>
		</rule>
	</pattern>

	<!-- 21 -->
	<!-- section 내부 indexterm 금지 -->
	<pattern id="section-indexterm-check">
		<rule context="section/indexterm">
			<assert role="none" test="false()">
				section 내부에는 indexterm을 사용할 수 없습니다.
			</assert>
		</rule>
	</pattern>

	<!-- 22 -->
	<!-- 빈 indexterm 금지 -->
	<pattern id="empty-indexterm-check">
		<rule context="indexterm">
			<assert role="none" test="normalize-space(.) != ''">
				비어있는 indexterm은 사용할 수 없습니다.
			</assert>
		</rule>
	</pattern>

	<!-- 23 -->
	<!-- 비영어(French/German/Spanish) 문장 혼입 검사 -->
	<pattern id="unicode-mark-check">

		<rule context=" title | p | shortdesc | entry[not(descendant::p)] | li[not(descendant::p)]">

	        <assert role="none" test="not(matches(normalize-unicode(., 'NFD'), '\p{M}'))">
	            영어가 아닌 다른 언어로 된 문장이 포함되어 있을 수 있습니다.
	        </assert>
	    </rule>
	</pattern>

	<!-- 24 -->
	<!-- 숫자와 단위 사이 공백 누락 검사 -->
	<pattern id="unit-space-check">
		<rule context="p | cmd | shortdesc | title">
			<!-- 숫자 + 단위 공백 누락 검사 -->
			<assert role="none" test="
				not(

				contains(., '0mph') or contains(., '1mph') or contains(., '2mph') or contains(., '3mph') or contains(., '4mph') or contains(., '5mph') or contains(., '6mph') or contains(., '7mph') or contains(., '8mph') or contains(., '9mph')

				or contains(., '0km/h') or contains(., '1km/h') or contains(., '2km/h') or contains(., '3km/h') or contains(., '4km/h') or contains(., '5km/h') or contains(., '6km/h') or contains(., '7km/h') or contains(., '8km/h') or contains(., '9km/h')

				or contains(., '0bar') or contains(., '1bar') or contains(., '2bar') or contains(., '3bar') or contains(., '4bar') or contains(., '5bar') or contains(., '6bar') or contains(., '7bar') or contains(., '8bar') or contains(., '9bar')

				or contains(., '0kPa') or contains(., '1kPa') or contains(., '2kPa') or contains(., '3kPa') or contains(., '4kPa') or contains(., '5kPa') or contains(., '6kPa') or contains(., '7kPa') or contains(., '8kPa') or contains(., '9kPa')

				or contains(., '0psi') or contains(., '1psi') or contains(., '2psi') or contains(., '3psi') or contains(., '4psi') or contains(., '5psi') or contains(., '6psi') or contains(., '7psi') or contains(., '8psi') or contains(., '9psi')

				or contains(., '0lbf·ft') or contains(., '1lbf·ft') or contains(., '2lbf·ft') or contains(., '3lbf·ft') or contains(., '4lbf·ft') or contains(., '5lbf·ft') or contains(., '6lbf·ft') or contains(., '7lbf·ft') or contains(., '8lbf·ft') or contains(., '9lbf·ft')

				or contains(., '0kgf·m') or contains(., '1kgf·m') or contains(., '2kgf·m') or contains(., '3kgf·m') or contains(., '4kgf·m') or contains(., '5kgf·m') or contains(., '6kgf·m') or contains(., '7kgf·m') or contains(., '8kgf·m') or contains(., '9kgf·m')

				or contains(., '0ft') or contains(., '1ft') or contains(., '2ft') or contains(., '3ft') or contains(., '4ft') or contains(., '5ft') or contains(., '6ft') or contains(., '7ft') or contains(., '8ft') or contains(., '9ft')

				or contains(., '0m') or contains(., '1m') or contains(., '2m') or contains(., '3m') or contains(., '4m') or contains(., '5m') or contains(., '6m') or contains(., '7m') or contains(., '8m') or contains(., '9m')

				or contains(., '0in') or contains(., '1in') or contains(., '2in') or contains(., '3in') or contains(., '4in') or contains(., '5in') or contains(., '6in') or contains(., '7in') or contains(., '8in') or contains(., '9in')

				or contains(., '0cm') or contains(., '1cm') or contains(., '2cm') or contains(., '3cm') or contains(., '4cm') or contains(., '5cm') or contains(., '6cm') or contains(., '7cm') or contains(., '8cm') or contains(., '9cm')

				or contains(., '0inches') or contains(., '1inches') or contains(., '2inches') or contains(., '3inches') or contains(., '4inches') or contains(., '5inches') or contains(., '6inches') or contains(., '7inches') or contains(., '8inches') or contains(., '9inches')

				or contains(., '0mm') or contains(., '1mm') or contains(., '2mm') or contains(., '3mm') or contains(., '4mm') or contains(., '5mm') or contains(., '6mm') or contains(., '7mm') or contains(., '8mm') or contains(., '9mm')

				or contains(., '0inche') or contains(., '1inche') or contains(., '2inche') or contains(., '3inche') or contains(., '4inche') or contains(., '5inche') or contains(., '6inche') or contains(., '7inche') or contains(., '8inche') or contains(., '9inche')

				or contains(., '0sm') or contains(., '1sm') or contains(., '2sm') or contains(., '3sm') or contains(., '4sm') or contains(., '5sm') or contains(., '6sm') or contains(., '7sm') or contains(., '8sm') or contains(., '9sm')

				or contains(., '0miles') or contains(., '1miles') or contains(., '2miles') or contains(., '3miles') or contains(., '4miles') or contains(., '5miles') or contains(., '6miles') or contains(., '7miles') or contains(., '8miles') or contains(., '9miles')

				or contains(., '0km') or contains(., '1km') or contains(., '2km') or contains(., '3km') or contains(., '4km') or contains(., '5km') or contains(., '6km') or contains(., '7km') or contains(., '8km') or contains(., '9km')

				or contains(., '0mi') or contains(., '1mi') or contains(., '2mi') or contains(., '3mi') or contains(., '4mi') or contains(., '5mi') or contains(., '6mi') or contains(., '7mi') or contains(., '8mi') or contains(., '9mi')

				or contains(., '0lb.') or contains(., '1lb.') or contains(., '2lb.') or contains(., '3lb.') or contains(., '4lb.') or contains(., '5lb.') or contains(., '6lb.') or contains(., '7lb.') or contains(., '8lb.') or contains(., '9lb.')

				or contains(., '0kg') or contains(., '1kg') or contains(., '2kg') or contains(., '3kg') or contains(., '4kg') or contains(., '5kg') or contains(., '6kg') or contains(., '7kg') or contains(., '8kg') or contains(., '9kg')

				or contains(., '0lbs.') or contains(., '1lbs.') or contains(., '2lbs.') or contains(., '3lbs.') or contains(., '4lbs.') or contains(., '5lbs.') or contains(., '6lbs.') or contains(., '7lbs.') or contains(., '8lbs.') or contains(., '9lbs.')

				or contains(., '0gallons') or contains(., '1gallons') or contains(., '2gallons') or contains(., '3gallons') or contains(., '4gallons') or contains(., '5gallons') or contains(., '6gallons') or contains(., '7gallons') or contains(., '8gallons') or contains(., '9gallons')

				or contains(., '0liters') or contains(., '1liters') or contains(., '2liters') or contains(., '3liters') or contains(., '4liters') or contains(., '5liters') or contains(., '6liters') or contains(., '7liters') or contains(., '8liters') or contains(., '9liters')

				or contains(., '0°F') or contains(., '1°F') or contains(., '2°F') or contains(., '3°F') or contains(., '4°F') or contains(., '5°F') or contains(., '6°F') or contains(., '7°F') or contains(., '8°F') or contains(., '9°F')

				or contains(., '0°C') or contains(., '1°C') or contains(., '2°C') or contains(., '3°C') or contains(., '4°C') or contains(., '5°C') or contains(., '6°C') or contains(., '7°C') or contains(., '8°C') or contains(., '9°C')

				)
			">
				숫자와 단위 사이 공백이 필요합니다.
			</assert>
		</rule>
	</pattern>

	<!-- 25 -->
	<!-- placement="break" 이미지 align 검사 -->
	<pattern id="image-break-align-check">
	    <rule context="image[@placement='break']">
	        <assert role="none" test="@align='center'">
	            placement="break" 이미지는 align="center" 정렬을 사용해야 합니다.
	        </assert>
	    </rule>
	</pattern>

	<!-- 26 -->
	<!-- WKIA_ 이미지 placement 검사 -->
	<pattern id="wkia-inline-placement-check">
	    <rule context="image[contains(@href, 'WKIA_')]">
	        <assert role="none" test="@placement='inline'">
	            WKIA_ 이미지는 placement="inline" 속성을 사용해야 합니다.
	        </assert>
	    </rule>
	</pattern>

	<pattern id="image-href-validation">
	    <rule context="image[@href]">

	        <!-- 공백 검사 -->
	        <assert role="none" test="not(matches(@href, '\s'))">
	            이미지명에 공백이 포함되어 있습니다:
	            <value-of select="@href"/>
	        </assert>

	        <!-- eps 확장자 검사 -->
	        <assert role="none" test="
	            matches(
	                normalize-space(@href),
	                '\.eps$',
	                'i'
	            )
	        ">
	            이미지 확장자가 .eps 가 아니거나 '.' 이 누락되었습니다:
	            <value-of select="@href"/>
	        </assert>

	    </rule>
	</pattern>


	<!-- 27 -->
	<!-- 정수부가 없는 소수 검사 -->
	<pattern id="decimal-leading-zero-check">
		<rule context="p | cmd | shortdesc | title">
			<assert role="none" test="not(matches(
				.,
				'(^|[^0-9])\.[0-9]+\s*(mph|km/h|bar|kPa|psi|lbf·ft|kgf·m|ft|m|in|cm|inches|mm|sm|miles|km|mi|lb\.|kg|lbs\.|gallons|liters|°F|°C)'
			))">
				소수점 앞에는 정수을 입력해야 합니다.
			</assert>
		</rule>
	</pattern>

	<!-- 여기서부터 재활용 확인 -->
	<!--
	<pattern id="uicontrol-usage-check">
		<rule context="uicontrol[not(ancestor::menucascade or ancestor::table)]">
			<assert role="none" test="false()">
				uicontrol은 menucascade 내부에서만 사용할 수 있습니다.
			</assert>
		</rule>
	</pattern>

	<pattern id="translate-no-check">
		<rule context="*[@translate='no' and not(self::term) and not(self::title) and not(self::tm)]">
			<assert role="none" test="false()">
				translate="no" 속성은 term 요소에서만 사용할 수 있습니다.
			</assert>
		</rule>
	</pattern>
	-->
	<!--
		<assert role="none" test="
				starts-with(@href, '../../images/') or
				starts-with(@href, '../../../images/') or
				starts-with(@href, '../../../2025/') or
				starts-with(@href, '../../../2026/')
				">
		<assert role="none" test=" starts-with(@href, '../../images/')">

	<pattern id="image-extension-check">
		<rule context="image[@href]">
			<assert role="none" test="substring(@href, string-length(@href) - 3) = '.eps'">
				image의 확장자는 소문자 .eps만 허용됩니다.
			</assert>
		</rule>
	</pattern>
-->
	<!-- 빈 태그 금지 검사 (특정 태그 제외) -->
<!--
	<pattern id="empty-element-check">
		<rule context="*[not(ancestor-or-self::map) and not(self::xref) and not(self::image) and not(self::colspec) and not(self::entry) and not(self::tm)
							    and not(self::related-links) and not(self::linkpool) and not(self::linkpool) and not(self::xref)
							    and not(self::image) and not(self::indexterm)]">
			<assert role="none" test="normalize-space(.) != '' or *">
				내용이 없는 빈 태그는 사용할 수 없습니다.
			</assert>
		</rule>
	</pattern>
-->
<!-- p 태그 시작 공백 검사 -->
<!--
	<pattern id="trim-whitespace-check">
		<rule context="p">

			p 시작 공백
			<assert role="none" test="not(text()[1][starts-with(.,' ') and not(preceding-sibling::*)])">
				문장 앞에 불필요한 공백이 있습니다.
			</assert>

			인라인 내부 마지막 문자 공백
			<assert role="none" test="not(*[
				(contains(@class,' hi-d/b ') or contains(@class,' hi-d/i ') or contains(@class,' hi-d/u ') or contains(@class,' hi-d/tt ') or contains(@class,' topic/ph ') or contains(@class,' topic/xref ') or contains(@class,' topic/keyword ') or contains(@class,' ui-d/uicontrol '))
				and substring(string(.),string-length(string(.)),1)=' '
			])">
				인라인 태그 끝에 불필요한 공백이 있습니다.
			</assert>

		</rule>
	</pattern>
-->
	<!-- p 태그 끝 공백 검사 (인라인 태그 뒤 포함) -->
<!--
	<pattern id="trim-trailing-whitespace-check">
		<rule context="p">

			마지막 노드가 text인 경우
			<assert role="none" test="not(node()[last()][self::text()][normalize-space(.)!='' and substring(.,string-length(.),1)=' '])">
				문장 끝에 불필요한 공백이 있습니다.
			</assert>

			마지막 노드 앞에 인라인 태그가 있고 마지막 text가 공백인 경우
			<assert role="none" test="not(node()[last()][self::text()][normalize-space(.)='' and preceding-sibling::*])">
				문장 끝에 불필요한 공백이 있습니다.
			</assert>

		</rule>
	</pattern>
	-->
	<!-- @height @width 검사 -->
	<!-- 여긴 다시 사용할지 확인
	<pattern id="image-size-attribute-check">
		<rule context="image[@width or @height]">
			<assert role="none" test="false()">
				image에 @width 또는 @height를 사용할 수 없습니다. @scale 속성을 사용해야 합니다.
			</assert>
		</rule>
	</pattern>
-->
</schema>
