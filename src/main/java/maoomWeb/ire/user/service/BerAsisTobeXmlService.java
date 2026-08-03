package maoomWeb.ire.user.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import maoomWeb.ire.user.dto.BerAsisTobePair;
import maoomWeb.ire.user.mapper.BerAsisTobePairMapper;

/** DB에 저장된 BER asis-tobe 데이터를 기존 XSL이 읽는 XML 파일로 만든다. */
@Service
public class BerAsisTobeXmlService {

    private static final String REGION_EU = "EU";
    private static final String REGION_US = "US";

    private final BerAsisTobePairMapper pairMapper;

    public BerAsisTobeXmlService(BerAsisTobePairMapper pairMapper) {
        this.pairMapper = pairMapper;
    }

    public void writeRegionXmlFiles(Path xslDirectory) throws IOException {
        Files.createDirectories(xslDirectory);
        writeRegionXml(REGION_EU, xslDirectory.resolve("asis-tobe_eu.xml"));
        writeRegionXml(REGION_US, xslDirectory.resolve("asis-tobe_us.xml"));
    }

    private void writeRegionXml(String region, Path outputFile)
            throws IOException {

        List<BerAsisTobePair> pairs = pairMapper.findByRegion(region);
        if(pairs.isEmpty()){
            return;
        }

        try{
            Document document = BerXmlFragments.newDocument();
            Element root = document.createElement("pairs");
            document.appendChild(root);

            for(BerAsisTobePair pair : pairs){
                validatePair(region, pair);
                Element pairElement = document.createElement("pair");
                pairElement.setAttribute("hash", pair.getHash());
                appendTextElement(
                        document,
                        pairElement,
                        "old",
                        pair.getOldText());
                appendTextElement(
                        document,
                        pairElement,
                        "new",
                        pair.getNewText());
                root.appendChild(pairElement);
            }

            var transformer = TransformerFactory
                    .newInstance()
                    .newTransformer();
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.setOutputProperty(OutputKeys.INDENT, "no");
            transformer.transform(
                    new DOMSource(document),
                    new StreamResult(outputFile.toFile()));
        }catch(Exception e){
            throw new IOException(
                    "BER asis-tobe XML 생성 중 오류가 발생했습니다: "
                    + outputFile,
                    e);
        }
    }

    private void appendTextElement(
            Document document,
            Element pairElement,
            String elementName,
            String value) {

        Element element = document.createElement(elementName);
        BerXmlFragments.appendFragment(document, element, value);
        pairElement.appendChild(element);
    }

    private void validatePair(String region, BerAsisTobePair pair) {
        if(pair.getHash() == null || pair.getHash().isBlank()){
            throw new IllegalArgumentException(
                    "BER " + region + " DB에 hash가 비어 있는 행이 있습니다.");
        }
        if(pair.getNewText() == null || pair.getNewText().isBlank()){
            throw new IllegalArgumentException(
                    "BER " + region + " DB에 new_text가 비어 있습니다. hash="
                    + pair.getHash());
        }
    }
}
