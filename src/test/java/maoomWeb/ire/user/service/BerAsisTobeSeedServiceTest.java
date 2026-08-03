package maoomWeb.ire.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.w3c.dom.Element;

import maoomWeb.ire.user.dto.BerAsisTobePair;

class BerAsisTobeSeedServiceTest {

    @Test
    void readsExistingEuAndUsXmlAsDatabaseRows() {
        BerAsisTobeSeedService service =
                new BerAsisTobeSeedService(null);

        assertThat(service.readPairs("EU", "xsl/asis-tobe_eu.xml"))
                .hasSizeGreaterThan(1000)
                .allSatisfy(this::hasRequiredFields);
        assertThat(service.readPairs("US", "xsl/asis-tobe_us.xml"))
                .hasSizeGreaterThan(100)
                .allSatisfy(this::hasRequiredFields);
    }

    @Test
    void storesLastNewWhenPairHasMultipleNewValues() throws Exception {
        BerAsisTobeSeedService service =
                new BerAsisTobeSeedService(null);
        Element sourcePair = firstPairWithMultipleNew(
                "xsl/asis-tobe_us.xml");

        BerAsisTobePair imported = service
                .readPairs("US", "xsl/asis-tobe_us.xml")
                .stream()
                .filter(pair -> pair.getHash().equals(
                        sourcePair.getAttribute("hash")))
                .findFirst()
                .orElseThrow();

        var newNodes = sourcePair.getElementsByTagName("new");
        String expectedLastNew = BerXmlFragments.innerXml(
                (Element) newNodes.item(newNodes.getLength() - 1));

        assertThat(imported.getNewText()).isEqualTo(expectedLastNew);
    }

    private void hasRequiredFields(BerAsisTobePair pair) {
        assertThat(pair.getRegion()).isIn("EU", "US");
        assertThat(pair.getHash()).isNotBlank();
        assertThat(pair.getNewText()).isNotBlank();
    }

    private Element firstPairWithMultipleNew(String resourcePath)
            throws Exception {

        try(InputStream input = new ClassPathResource(resourcePath)
                .getInputStream()){
            var document = javax.xml.parsers.DocumentBuilderFactory
                    .newInstance()
                    .newDocumentBuilder()
                    .parse(input);
            var pairs = document
                    .getDocumentElement()
                    .getElementsByTagName("pair");

            for(int index = 0; index < pairs.getLength(); index++){
                Element pair = (Element) pairs.item(index);

                if(pair.getElementsByTagName("new").getLength() > 1){
                    return pair;
                }
            }

            throw new AssertionError("multiple new pair not found");
        }
    }
}
