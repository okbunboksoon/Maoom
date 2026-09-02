<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

  <xsl:output method="xml" indent="no" omit-xml-declaration="yes"/>
  <xsl:strip-space elements="*"/>

  <!-- 예외 조건 목록 -->
  <xsl:variable name="exceptions" as="element()*">
	<entry error="두 문장이 붙어 있습니다." key="다라는"/>
	<entry error="콜론 앞에는 공백이 없고 콜론 뒤에는 공백이 있어야 합니다." key="시:분:초"/>
	<entry error="콜론 앞에는 공백이 없고 콜론 뒤에는 공백이 있어야 합니다." key="https://"/>
	<entry error="콜론 앞에는 공백이 없고 콜론 뒤에는 공백이 있어야 합니다." key="http://"/>
	<entry error="의성어 앞뒤에 큰따옴표가 있어야 합니다." key="삐-삐-삐-"/>
	<entry error="의성어 앞뒤에 큰따옴표가 있어야 합니다." key="삐---삐---"/>
	<entry error="두 문장이 공백 없이 붙어 있습니다." key="소프트웨어 버전은 X.XX입니다."/>
	<entry error="두 문장이 공백 없이 붙어 있습니다." key="Address: Molex B.V., Ascent 1 Aerospace Boulevard, Farnborough, GU14 6XW, UK"/>
	<entry error="4자리 이상 수는 쉼표를 넣어야 합니다." key="Tel: +886 2 2258 2986 EXT.265"/>
	<entry error="4자리 이상 수는 쉼표를 넣어야 합니다." key="22241"/>
	<entry error="4자리 이상 수는 쉼표를 넣어야 합니다." key="Current model vehicle:"/>
	<entry error="4자리 이상 수는 쉼표를 넣어야 합니다." key="New model vehicle:"/>
	<entry error="4자리 이상 수는 쉼표를 넣어야 합니다." key="Address:"/>
	<entry error="4자리 이상 수는 쉼표를 넣어야 합니다." key="DOT XXXX XXXX"/>   
	<entry error="4자리 이상 수는 쉼표를 넣어야 합니다." key="DOT XXXX XXXX 1625 represents that the tire was produced in the 16th week of 2025."/>
	<entry error="4자리 이상 수는 쉼표를 넣어야 합니다." key="Address: 1000 Great West Road, Brentford, TW8 9DW, UK"/>
	<entry error="4자리 이상 수는 쉼표를 넣어야 합니다." key="5: Directive 95/46/EC is repealed by Regulation (EU) 2016/679 of the European Parliament and of the Council of 27 April 2016 on the protection of natural persons with regard to the processing of personal data and on the free movement of such data (General Data Protection Regulation) (OJ L 119, 4.5.2016, p. 1). The Regulation applies from 25 May 2018."/>
	<entry error="4자리 이상 수는 쉼표를 넣어야 합니다." key="5: Directive 95/46/EC is repealed by Regulation (EU) 2016/679 of the European Parliament and of the Council of 27 April 2016 on the protection of natural persons with regard to the processing of personal data and on the free movement of such data (General Data Protection Regulation) (OJ L 119, 4.5.2016, p. 1). The Regulation applies from 25 May 2018."/>
	<entry error="4자리 이상 수는 쉼표를 넣어야 합니다." key="4: Directive 2002/58/EC of the European Parliament and of the Council of 12 July 2002 concerning the processing of personal data and the protection of privacy in the electronic communications sector (Directive on privacy and electronic communications) (OJ L 201, 31.7.2002, p. 37)."/>
	<entry error="4자리 이상 수는 쉼표를 넣어야 합니다." key="4: Directive 2002/58/EC of the European Parliament and of the Council of 12 July 2002 concerning the processing of personal data and the protection of privacy in the electronic communications sector (Directive on privacy and electronic communications) (OJ L 201, 31.7.2002, p. 37)."/>
	<entry error="4자리 이상 수는 쉼표를 넣어야 합니다." key="3: Directive 95/46/EC of the European Parliament and of the Council of 24 October 1995 on the protection of individuals with regard to the processing of personal data and on the free movement of such data (OJ L 281, 23.11.1995, p. 31)."/>
	<entry error="4자리 이상 수는 쉼표를 넣어야 합니다." key="3: Directive 95/46/EC of the European Parliament and of the Council of 24 October 1995 on the protection of individuals with regard to the processing of personal data and on the free movement of such data (OJ L 281, 23.11.1995, p. 31)."/>
	<entry error="4자리 이상 수는 쉼표를 넣어야 합니다." key="© "/>
	<entry error="두 문장이 공백 없이 붙어 있습니다." key="소프트웨어 버전은 X.XX 입니다."/>
	<entry error="두 문장이 공백 없이 붙어 있습니다." key="(H.K.SHELL)"/>
	<entry error="두 문장이 공백 없이 붙어 있습니다." key="P.O. Box 52410"/>
	<entry error="두 문장이 공백 없이 붙어 있습니다." key="Printed in U.S.A."/>
	<entry error="두 문장이 공백 없이 붙어 있습니다." key="Reporting Safety Defects (U.S. only)"/>
	<entry error="두 문장이 공백 없이 붙어 있습니다." key="To contact NHTSA, you may call the Vehicle Safety Hotline toll-free at 1-888-327-4236 (TTY: 1-888-275-9171); go to http://www.NHTSA.gov; download the SaferCar mobile application; or write to: Administrator, NHTSA, 1200 New Jersey Ave. SE., West Building, Washington, DC 20590."/>
	<entry error="두 문장이 공백 없이 붙어 있습니다." key="The following publications are available on www.KiaTechinfo.com."/>
  </xsl:variable>
  <!-- fileset 루트 처리 -->
  <xsl:template match="fileset">
    <fileset date="{@date}">
      <xsl:apply-templates select="file"/>
    </fileset>
  </xsl:template>

  <!-- file 처리 -->
	<xsl:template match="file">
	  <xsl:variable name="validDetects">
		<xsl:apply-templates select="detect"/>
	  </xsl:variable>
	  <xsl:if test="normalize-space($validDetects) != ''">
		<file filename="{@filename}">
		  <xsl:copy-of select="$validDetects"/>
		</file>
	  </xsl:if>
	</xsl:template>

  <!-- detect: 예외 조건만 제외 -->
  <xsl:template match="detect">
    <xsl:variable name="errorType" select="normalize-space(substring-before(., '###'))"/>
    <xsl:variable name="sentence" select="normalize-space(substring-after(., '###'))"/>

    <!-- 예외 조건에 해당하는지 판단 -->
    <xsl:variable name="isException"
      select="some $e in $exceptions satisfies 
              (contains($errorType, string($e/@error)) and contains($sentence, string($e/@key)))"/>

    <!-- 예외가 아닌 경우에만 출력 -->
    <xsl:if test="not($isException)">
      <detect location="{@location}">
        <xsl:value-of select="."/>
      </detect>
    </xsl:if>
  </xsl:template>

</xsl:stylesheet>