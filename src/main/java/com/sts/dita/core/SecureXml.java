package com.sts.dita.core;

import java.io.StringReader;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

/** DITA/Schematron XML 파서에 동일한 로컬 전용 보안 정책을 적용한다. */
public final class SecureXml {
    private static final String LOAD_EXTERNAL_DTD =
            "http://apache.org/xml/features/nonvalidating/load-external-dtd";
    private static final String EXTERNAL_GENERAL_ENTITIES =
            "http://xml.org/sax/features/external-general-entities";
    private static final String EXTERNAL_PARAMETER_ENTITIES =
            "http://xml.org/sax/features/external-parameter-entities";

    private SecureXml() { }

    public static DocumentBuilder newDocumentBuilder() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature(LOAD_EXTERNAL_DTD, false);
        factory.setFeature(EXTERNAL_GENERAL_ENTITIES, false);
        factory.setFeature(EXTERNAL_PARAMETER_ENTITIES, false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        DocumentBuilder builder = factory.newDocumentBuilder();
        builder.setEntityResolver((publicId, systemId) -> emptyInput(publicId, systemId));
        return builder;
    }

    public static XMLReader newXmlReader() throws Exception {
        SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setXIncludeAware(false);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature(LOAD_EXTERNAL_DTD, false);
        factory.setFeature(EXTERNAL_GENERAL_ENTITIES, false);
        factory.setFeature(EXTERNAL_PARAMETER_ENTITIES, false);
        XMLReader reader = factory.newSAXParser().getXMLReader();
        reader.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        reader.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        reader.setEntityResolver((publicId, systemId) -> emptyInput(publicId, systemId));
        return reader;
    }

    private static InputSource emptyInput(String publicId, String systemId) {
        InputSource input = new InputSource(new StringReader(""));
        input.setPublicId(publicId);
        input.setSystemId(systemId);
        return input;
    }
}
