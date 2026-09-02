<schema xmlns="http://purl.oclc.org/dsdl/schematron">
	<title>Check Structure</title>
	<pattern>
		<!--
		<rule context="note">
			<assert test="*">note 하위에 아무것도 없습니다.###<value-of select="."/></assert>
			<assert test="exists(@type)">note에 type 속성을 지정하지 않았습니다.###<value-of select="."/></assert>
		</rule>
		<rule context="li">
			<assert test="p">li 하위에 p가 없습니다.###<value-of select="."/></assert>
		</rule>
		<rule context="image">
			<report test="@width">image에 width 속성이 있습니다.</report>
			<report test="@height">image에 height 속성이 있습니다.</report>
		</rule>
		<rule context="entry">
			<assert test="@align">entry에 align 속성이 없습니다.###<value-of select="."/></assert>
			<assert test="@valign">entry에 valign 속성이 없습니다.###<value-of select="."/></assert>
		</rule>
		<rule context="xref">
			<assert test="contains(@href, '#')">href 속성에 #topicid가 없습니다.###<value-of select="."/></assert>
		</rule>
		-->
		<rule context="//p[text()]">
			<assert test="not(matches(., '((니다)|(시오))([.])?[가-힣]'))">두 문장이 붙어 있습니다.###<value-of select="."/></assert>
			<assert test="not(matches(., '\w\.\p{Lu}'))">두 문장이 공백 없이 붙어 있습니다.###<value-of select="."/></assert>
			<assert test="not(matches(., '\s+:')) and not(matches(., ':[^\s]'))">콜론 앞에는 공백이 없고 콜론 뒤에는 공백이 있어야 합니다.###<value-of select="."/></assert>
			<assert test="not(matches(., '\s+~\s+'))">물결표 앞과 뒤에 공백이 없어야 합니다.###<value-of select="."/></assert>
			<assert test="not(matches(., '\s+–\s+'))">엔대시 앞과 뒤에 공백이 없어야 합니다.###<value-of select="."/></assert>
			<assert test="not(matches(., '\d+(km/h|kPa|kgf·m|m|cm|mm|km|kg|liters|cc|mph|bar|psi|lbf·ft|ft|in|inches|miles|mi|lb\.|lbs\.|gallons)'))">이 단위 앞에 공백이 있어야 합니다.###<value-of select="."/></assert>
			<assert test="not(matches(., '\d+\s+(%|°|\$|℃|℉)'))">이 단위 앞에 공백이 없어야 합니다.###<value-of select="."/></assert>
			<assert test="not(matches(., '\d+\s?(ℓ)'))">부피 단위는 ℓ이 아니라 대문자 L이어야 합니다.###<value-of select="."/></assert>
			<assert test="not(matches(., '\s\d{4,4}(\d{1,3})*\s'))">4자리 이상 수는 쉼표를 넣어야 합니다.###<value-of select="."/></assert>
			<assert test="not(matches(., '[^&#x0022;](딸깍|찰칵|삐|삐삐)[^&#x0022;]'))">의성어 앞뒤에 큰따옴표가 있어야 합니다.###<value-of select="."/></assert>
		</rule>
		<rule context="//cmd[text()]">
			<assert test="not(matches(., '((니다)|(시오))([.])?[가-힣]'))">두 문장이 붙어 있습니다.###<value-of select="."/></assert>
			<assert test="not(matches(., '\w\.\p{Lu}'))">두 문장이 공백 없이 붙어 있습니다.###<value-of select="."/></assert>
			<assert test="not(matches(., '\s+:')) and not(matches(., ':[^\s]'))">콜론 앞에는 공백이 없고 콜론 뒤에는 공백이 있어야 합니다.###<value-of select="."/></assert>
			<assert test="not(matches(., '\s+~\s+'))">물결표 앞과 뒤에 공백이 없어야 합니다.###<value-of select="."/></assert>
			<assert test="not(matches(., '\s+–\s+'))">엔대시 앞과 뒤에 공백이 없어야 합니다.###<value-of select="."/></assert>
			<assert test="not(matches(., '\d+(km/h|kPa|kgf·m|m|cm|mm|km|kg|liters|cc|mph|bar|psi|lbf·ft|ft|in|inches|miles|mi|lb\.|lbs\.|gallons)'))">이 단위 앞에 공백이 있어야 합니다.###<value-of select="."/></assert>
			<assert test="not(matches(., '\d+\s+(%|°|\$|℃|℉)'))">이 단위 앞에 공백이 없어야 합니다.###<value-of select="."/></assert>
			<assert test="not(matches(., '\d+\s?(ℓ)'))">부피 단위는 ℓ이 아니라 대문자 L이어야 합니다.###<value-of select="."/></assert>
			<assert test="not(matches(., '\s\d{4,4}(\d{1,3})*\s'))">4자리 이상 수는 쉼표를 넣어야 합니다.###<value-of select="."/></assert>
			<assert test="not(matches(., '[^&#x0022;](딸깍|찰칵|삐|삐삐)[^&#x0022;]'))">의성어 앞뒤에 큰따옴표가 있어야 합니다.###<value-of select="."/></assert>
		</rule>
		<rule context="//title[text()]">
			<assert test="not(matches(., '((니다)|(시오))([.])?[가-힣]'))">두 문장이 붙어 있습니다.###<value-of select="."/></assert>
			<assert test="not(matches(., '\w\.\p{Lu}'))">두 문장이 공백 없이 붙어 있습니다.###<value-of select="."/></assert>
			<assert test="not(matches(., '\s+:')) and not(matches(., ':[^\s]'))">콜론 앞에는 공백이 없고 콜론 뒤에는 공백이 있어야 합니다.###<value-of select="."/></assert>
			<assert test="not(matches(., '\s+~\s+'))">물결표 앞과 뒤에 공백이 없어야 합니다.###<value-of select="."/></assert>
			<assert test="not(matches(., '\s+–\s+'))">엔대시 앞과 뒤에 공백이 없어야 합니다.###<value-of select="."/></assert>
			<assert test="not(matches(., '\d+(km/h|kPa|kgf·m|m|cm|mm|km|kg|liters|cc|mph|bar|psi|lbf·ft|ft|in|inches|miles|mi|lb\.|lbs\.|gallons)'))">이 단위 앞에 공백이 있어야 합니다.###<value-of select="."/></assert>
			<assert test="not(matches(., '\d+\s+(%|°|\$|℃|℉)'))">이 단위 앞에 공백이 없어야 합니다.###<value-of select="."/></assert>
			<assert test="not(matches(., '\d+\s?(ℓ)'))">부피 단위는 ℓ이 아니라 대문자 L이어야 합니다.###<value-of select="."/></assert>
			<assert test="not(matches(., '\s\d{4,4}(\d{1,3})*\s'))">4자리 이상 수는 쉼표를 넣어야 합니다.###<value-of select="."/></assert>
			<assert test="not(matches(., '[^&#x0022;](딸깍|찰칵|삐|삐삐)[^&#x0022;]'))">의성어 앞뒤에 큰따옴표가 있어야 합니다.###<value-of select="."/></assert>
		</rule>
		<rule context="//shortdesc[text()]">
			<assert test="not(matches(., '((니다)|(시오))([.])?[가-힣]'))">두 문장이 붙어 있습니다.###<value-of select="."/></assert>
			<assert test="not(matches(., '\w\.\p{Lu}'))">두 문장이 공백 없이 붙어 있습니다.###<value-of select="."/></assert>오후 3:28 2025-05-13
			<assert test="not(matches(., '\s+:')) and not(matches(., ':[^\s]'))">콜론 앞에는 공백이 없고 콜론 뒤에는 공백이 있어야 합니다.###<value-of select="."/></assert>
			<assert test="not(matches(., '\s+~\s+'))">물결표 앞과 뒤에 공백이 없어야 합니다.###<value-of select="."/></assert>
			<assert test="not(matches(., '\d+(km/h|kPa|kgf·m|m|cm|mm|km|kg|liters|cc|mph|bar|psi|lbf·ft|ft|in|inches|miles|mi|lb\.|lbs\.|gallons)'))">이 단위 앞에 공백이 있어야 합니다.###<value-of select="."/></assert>
			<assert test="not(matches(., '\d+\s+(%|°|\$|℃|℉)'))">이 단위 앞에 공백이 없어야 합니다.###<value-of select="."/></assert>
			<assert test="not(matches(., '\d+\s?(ℓ)'))">부피 단위는 ℓ이 아니라 대문자 L이어야 합니다.###<value-of select="."/></assert>
			<assert test="not(matches(., '\s\d{4,4}(\d{1,3})*\s'))">4자리 이상 수는 쉼표를 넣어야 합니다.###<value-of select="."/></assert>
			<assert test="not(matches(., '[^&#x0022;](딸깍|찰칵|삐|삐삐)[^&#x0022;]'))">의성어 앞뒤에 큰따옴표가 있어야 합니다.###<value-of select="."/></assert>
		</rule>
	</pattern>
</schema>