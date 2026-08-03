package maoomWeb.ire.user.service;

import java.io.StringReader;
import java.io.StringWriter;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXParseException;

final class BerXmlFragments {

    private BerXmlFragments() {
    }

    static Document newDocument() {
        try{
            DocumentBuilderFactory factory =
                    DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            return factory.newDocumentBuilder().newDocument();
        }catch(Exception e){
            throw new IllegalStateException(
                    "XML 문서를 생성하지 못했습니다.",
                    e);
        }
    }

    static void appendFragment(
            Document targetDocument,
            Element targetElement,
            String fragment) {

        if(fragment == null || fragment.isEmpty()){
            return;
        }

        try{
            DocumentBuilderFactory factory =
                    DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            var builder = factory.newDocumentBuilder();
            builder.setErrorHandler(new org.xml.sax.ErrorHandler() {
                @Override
                public void warning(SAXParseException exception) {
                }

                @Override
                public void error(SAXParseException exception) {
                }

                @Override
                public void fatalError(SAXParseException exception)
                        throws SAXParseException {
                    throw exception;
                }
            });
            Document fragmentDocument = builder.parse(new InputSource(
                    new StringReader("<root>" + fragment + "</root>")));
            NodeList children =
                    fragmentDocument.getDocumentElement().getChildNodes();

            for(int index = 0; index < children.getLength(); index++){
                Node imported = targetDocument.importNode(
                        children.item(index),
                        true);
                targetElement.appendChild(imported);
            }
        }catch(Exception e){
            targetElement.appendChild(
                    targetDocument.createTextNode(fragment));
        }
    }

    static String innerXml(Element element) {
        try{
            NodeList children = element.getChildNodes();
            StringBuilder builder = new StringBuilder();

            for(int index = 0; index < children.getLength(); index++){
                builder.append(toXml(children.item(index)));
            }

            return builder.toString();
        }catch(Exception e){
            throw new IllegalArgumentException(
                    "BER XML 조각을 문자열로 변환하지 못했습니다.",
                    e);
        }
    }

    static String toXml(Node node) throws Exception {
        var transformer = TransformerFactory
                .newInstance()
                .newTransformer();
        transformer.setOutputProperty(
                OutputKeys.OMIT_XML_DECLARATION,
                "yes");
        transformer.setOutputProperty(OutputKeys.INDENT, "no");

        StringWriter writer = new StringWriter();
        transformer.transform(
                new DOMSource(node),
                new StreamResult(writer));
        return writer.toString();
    }
}
