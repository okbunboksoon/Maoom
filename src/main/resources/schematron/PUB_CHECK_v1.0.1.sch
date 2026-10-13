<schema xmlns="http://purl.oclc.org/dsdl/schematron" queryBinding="xslt2">

<!--
V1.0.1 05.27
	독일어
	   - 전용 서문 확인
	캐나다 불어
	   - 캐나다 불어 TC 확인
	러시아
	   - Introduction챕터 러시아 ERA-GLONASS 무단 개조 경고 관련 내용 확인
	   - Emergency 챕터 ERA-GLONASS 내용 중 맵 램프와 차량 형상이 해당 차종 형상 확인
	   - 러시아 전용 Abbreviation 확인
	멕시코 스페인
	   - 에어백 관련 경고 문구 노트 확인
	   - 에어백 라벨 관련 경고 문구 수정 확인
	   - 연료 주입구 관련 노트 문구 변경 확인
	   - Foreword의 BER문구 변경 확인
	   - Introduction 챕터의 Fuel Additives 내용 ber문구 변경
	세르비아
	   - 세르비아어 조합형 문자사용 확인
-->

	<!-- 독일어 특이사항 -->
	<pattern id="de-vorwort-check">
		<rule context="map[@xml:lang='de-DE' or contains(title, 'de_DE')]">
			<assert role="none" test="some $t in topicref/@href
	            satisfies
	            exists(
	                document(resolve-uri($t, base-uri(.)))
	                //concept[
	                    upper-case(normalize-space(title))='VORWORT'
	                    and
	                    contains(
	                        normalize-space(string(.)),
	                        'Ihr Kia-Fahrzeug ist mit einem SRS-Airbagsystem und Gurtstraffern ausgestattet.'
	                    )
	                ]
	            )
	        ">
	            독일어 전용 서문이 없습니다.
	        </assert>
		</rule>
	</pattern>



	<!-- 캐나다 불어 특이사항 -->
	<pattern id="fr-tc-check">
		<rule context="map[contains(title,'fr_CA')]">
			<assert role="none" test="some $t in .//topicref/@href satisfies exists(document(resolve-uri($t, base-uri(.)))//concept[normalize-space(title)='Signalement des défauts de sécurité' and contains(normalize-space(string(.)),'Si vous croyez que votre véhicule présente un défaut qui pourrait provoquer un accident ou causer des')])">
	            캐나다 불어 TC가 없습니다.
	        </assert>
		</rule>
	</pattern>



	<!-- 러시아 특이사항 -->
	<pattern id="ru-era-glonass-check">
		<rule context="map[@xml:lang='ru-RU' or contains(title,'ru_RU')]">
			<assert role="none" test="exists(.//topicref[@href='RU_Intro.dita'])">
	            러시아어 Introduction챕터 러시아 ERA-GLONASS 무단 개조 경고 관련 내용의 DITA파일이 없습니다.
	        </assert>
		</rule>
	</pattern>
	<pattern id="ru-era-glonass-image-check">
		<rule context="map[@xml:lang='ru-RU' or contains(title,'ru_RU')]">
			<report role="none" test="true()">
	            Emergency 챕터 ERA-GLONASS 내용 중 맵 램프와 차량 형상이 해당 차종 형상이 맞는지 확인 필요합니다.
	        </report>
		</rule>
	</pattern>
	<pattern id="ru-appendix-check">
		<rule context="map[@xml:lang='ru-RU' or contains(title,'ru_RU')]">
			<assert role="none" test="exists(.//topicref[@href='OM_14_3_Appendix_RU.dita'])">
	            러시아 전용 Abbreviation이 없습니다.
	        </assert>
			<assert role="none" test="exists(.//topicref[@href='OM_14_4_Appendix_RU.dita'])">
	            러시아 전용 Abbreviation이 없습니다.
	        </assert>
			<assert role="none" test="exists(.//topicref[@href='OM_14_5_Appendix_RU.dita'])">
	            러시아 전용 Abbreviation이 없습니다.
	        </assert>
		</rule>
	</pattern>



	<!-- 멕시코 스페인어 특이사항 -->
	<pattern id="es-mx-warning-check">
		<rule context="map[contains(title,'es_MX')]">
			<assert role="none" test="some $t in .//topicref/@href satisfies exists(document(resolve-uri($t, base-uri(.)))//concept[normalize-space(string(title))='Vista general' and .//note[@type='warning']/p[contains(normalize-space(string(.)),'No abrir, retirar ni colocar en otro vehículo.')]])">
	           에어백 관련 경고 문구 노트가 없습니다.
	        </assert>
		</rule>
	</pattern>
	<pattern id="es-mx-airbag-warning-check">
		<rule context="map[contains(title,'es_MX')]">
			<assert role="none" test="some $t in .//topicref/@href satisfies exists(document(resolve-uri($t, base-uri(.)))//*[contains(normalize-space(string(.)),'Las etiquetas de advertencia del airbag están adheridas para alertar a los pasajeros del riesgo potencial del sistema de airbags.')])">
	            에어백 라벨 관련 경고 문구 수정해야합니다.
	        </assert>
		</rule>
	</pattern>
	<pattern id="es-mx-retire-lentamente-check">
		<rule context="map[contains(title,'es_MX')]">
			<assert role="none" test="some $t in .//topicref/@href satisfies exists(document(resolve-uri($t, base-uri(.)))//p[normalize-space(string(.))='Retire lentamente.'])">
	            연료 주입구 관련 노트 문구 변경해야합니다.
	        </assert>
		</rule>
	</pattern>
	<pattern id="es-mx-brica-lentamente-check">
		<rule context="map[contains(title,'es_MX')]">
			<report role="none" test="some $t in .//topicref/@href satisfies exists(document(resolve-uri($t, base-uri(.)))//p[contains(translate(normalize-space(string(.)),'&#160;',' '),'técnicos capacitados en fábrica')])">
				Foreword의 BER문구 변경해야합니다.
			</report>
		</rule>
	</pattern>
	<pattern id="es-mx-fuel-additive-check">
		<rule context="map[contains(title,'es_MX')]">
			<assert role="none" test="some $t in .//topicref/@href satisfies exists(document(resolve-uri($t, base-uri(.)))//section[normalize-space(string(title))='Aditivos para combustible' and .//p[contains(normalize-space(string(.)),'Los aditivos están disponibles en uno de sus concesionarios autorizados Kia autorizado por Kia')]])">
				Introduction 챕터의 Fuel Additives 내용 (스페인어:Aditivos del combustible) ber문구 변경해야합니다.
			</assert>
		</rule>
	</pattern>



	<!-- 세르비아 특이사항 -->
	<pattern id="sr-latn-precomposed-c-check">
	    <rule context="p[contains(., '&#x0301;')]">
	        <report role="none" test="true()">
	            세르비아어 조합형 문자(c + U+0301) 사용됨 다시 배치돌려야합니다.
	        </report>
	    </rule>
	</pattern>



	<!-- 하이픈(Non-Breaking Hyphen) 특이사항 -->
	<pattern id="non-breaking-hyphen-check">
		<rule context="p[contains(., '&#x2011;')]">
			<report role="none" test="true()">
				Non-Breaking Hyphen(U+2011) 사용됨 다시 배치돌려야합니다.
			</report>
		</rule>
	</pattern>

</schema>