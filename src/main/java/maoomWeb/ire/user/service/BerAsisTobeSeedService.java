package maoomWeb.ire.user.service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import maoomWeb.ire.user.dto.BerAsisTobePair;
import maoomWeb.ire.user.mapper.BerAsisTobePairMapper;

/** 기존 classpath XML을 BER DB 최초 데이터로 적재한다. */
@Service
public class BerAsisTobeSeedService {

    private final BerAsisTobePairMapper pairMapper;

    public BerAsisTobeSeedService(BerAsisTobePairMapper pairMapper) {
        this.pairMapper = pairMapper;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seedIfEmpty() {
        seedRegionIfEmpty("EU", "xsl/asis-tobe_eu.xml");
        seedRegionIfEmpty("US", "xsl/asis-tobe_us.xml");
    }

    private void seedRegionIfEmpty(String region, String resourcePath) {
        if(pairMapper.countByRegion(region) > 0){
            return;
        }

        for(BerAsisTobePair pair : readPairs(region, resourcePath)){
            pairMapper.upsert(pair);
        }
    }

    List<BerAsisTobePair> readPairs(
            String region,
            String resourcePath) {

        try(InputStream input = new ClassPathResource(resourcePath)
                .getInputStream()){
            return readPairs(region, input);
        }catch(Exception e){
            throw new IllegalStateException(
                    "BER asis-tobe XML을 DB 초기 데이터로 읽지 못했습니다: "
                    + resourcePath,
                    e);
        }
    }

    List<BerAsisTobePair> readPairs(
            String region,
            InputStream input) {

        try{
            DocumentBuilderFactory factory =
                    DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            var document = factory.newDocumentBuilder().parse(input);
            NodeList nodes =
                    document.getDocumentElement().getElementsByTagName("pair");
            List<BerAsisTobePair> pairs = new ArrayList<>();

            for(int index = 0; index < nodes.getLength(); index++){
                Element pairElement = (Element) nodes.item(index);
                BerAsisTobePair pair = new BerAsisTobePair();
                pair.setRegion(region);
                pair.setHash(pairElement.getAttribute("hash"));
                pair.setOldText(innerXmlOfFirst(pairElement, "old"));
                pair.setNewText(innerXmlOfLast(pairElement, "new"));

                if(pair.getHash() != null
                        && !pair.getHash().isBlank()
                        && pair.getNewText() != null
                        && !pair.getNewText().isBlank()){
                    pairs.add(pair);
                }
            }

            return pairs;
        }catch(Exception e){
            throw new IllegalStateException(
                    "BER asis-tobe XML을 읽지 못했습니다.",
                    e);
        }
    }

    private String innerXmlOfFirst(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        if(nodes.getLength() == 0){
            return "";
        }

        return BerXmlFragments.innerXml((Element) nodes.item(0));
    }

    private String innerXmlOfLast(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        if(nodes.getLength() == 0){
            return "";
        }

        Node node = nodes.item(nodes.getLength() - 1);
        return BerXmlFragments.innerXml((Element) node);
    }
}
