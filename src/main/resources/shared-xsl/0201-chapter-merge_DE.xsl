<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="2.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

    <xsl:output method="xml" indent="no"/>
    <xsl:strip-space elements="*"/>

    <!-- 기본 복사 -->
    <xsl:template match="@* | node()">
        <xsl:copy>
            <xsl:apply-templates select="@* | node()"/>
        </xsl:copy>
    </xsl:template>

    <!-- Vorwort conbody 마지막에 section 추가 -->
    <xsl:template match="concept[title='Vorwort']/conbody">

        <xsl:copy>

            <!-- 기존 속성 -->
            <xsl:apply-templates select="@*"/>

            <!-- 기존 내용 먼저 유지 -->
            <xsl:apply-templates select="node()"/>

            <!-- 마지막에 추가 -->
            <section base="Top">
                <p>Ihr Kia-Fahrzeug ist mit einem SRS-Airbagsystem und Gurtstraffern ausgestattet. Bei vorhandenen Auslösekriterien (Frontalaufprall) werden Airbags und Gurtstraffer durch Gasgeneratoren ausgelöst. Im Fall eines seitlichen Aufpralls werden Seitenairbags aktiviert.</p>
                <p>Die Gasgeneratoren unterliegen gesetzlichen Bestimmungen, die unter anderem folgende Auflagen beinhalten.</p>
                <p><b>Die Demontage eines Gasgenerators aus dem Fahrzeug durch den Fahrzeughalter ist nicht zulässig. Der Fahrzeughalter muss eine Fachwerkstatt (Kia Vertragswerkstatt) mit der Instandsetzung oder Demontage beauftragen, wenn:</b></p>
                <ul>
                    <li>
                        <p><b>die Airbags/Gurtstraffer aktiviert wurden</b></p>
                    </li>
                    <li>
                        <p><b>Störungen am Airbagsystem oder den Gurtstraffern vorliegen oder</b></p>
                    </li>
                    <li>
                        <p><b>das System außer Funktion gesetzt werden soll (z.B. bei Instandsetzung der Fahrzeugelektrik).</b></p>
                    </li>
                </ul>
                <p>Weitere Informationen zum Airbagsystem und zu den Gurtstraffern entnehmen Sie bitte dieser Betriebsanleitung.</p>
            </section>

        </xsl:copy>

    </xsl:template>

</xsl:stylesheet>