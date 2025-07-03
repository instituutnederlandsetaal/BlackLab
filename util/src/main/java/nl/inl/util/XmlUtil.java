package nl.inl.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSOutput;
import org.w3c.dom.ls.LSSerializer;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import nl.inl.blacklab.exceptions.BlackLabException;

/**
 * Utilities for working with XML.
 */
public class XmlUtil {
    private static boolean namespaceAware = false;

    public static boolean isNamespaceAware() {
        return namespaceAware;
    }

    public static void setNamespaceAware(boolean namespaceAware) {
        XmlUtil.namespaceAware = namespaceAware;
    }

    /**
     * Parse XML from reader to a DOM Document
     *
     * @param reader where to read the XML document from
     * @return the DOM representation of the document
     */
    public static Document parseXml(Reader reader) throws SAXException {
        try {
            DocumentBuilderFactory domFactory = DocumentBuilderFactory.newInstance();
            domFactory.setNamespaceAware(namespaceAware);
            DocumentBuilder domBuilder = domFactory.newDocumentBuilder();
            // Avoid errors written to stderr
            domBuilder.setErrorHandler(new SimpleErrorHandler());
            return domBuilder.parse(new InputSource(reader));
        } catch (ParserConfigurationException | IOException e) {
            throw BlackLabException.wrapRuntime(e);
        }
    }

    /**
     * Takes an XML input string and... - removes tags - replaces entities with
     * characters - normalizes whitespace
     *
     * @param conc the input XML string
     * @return the plain text output string
     */
    public static String xmlToPlainText(String conc) {
        return xmlToPlainText(conc, false);
    }

    private static final class SimpleErrorHandler implements ErrorHandler {
        public SimpleErrorHandler() {
            // NOP
        }

        @Override
        public void warning(SAXParseException e) {
            //
        }

        @Override
        public void fatalError(SAXParseException e) throws SAXException {
            throw e;
        }

        @Override
        public void error(SAXParseException e) throws SAXException {
            throw e;
        }
    }

    /**
     * States of the xmlToPlainText() state machine
     */
    private enum XmlToPlainTextState {

        /** Copy these characters to the destination */
        COPY,

        /** We're inside a tag; don't copy */
        IN_TAG,

        /** We're inside an entity; don't copy, but add appropriate character at end */
        IN_ENTITY,
    }

    /**
     * Takes an XML input string and... * removes tags * replaces entities with
     * characters * normalizes whitespace * (optionally) replaces spaces with
     * non-breaking spaces
     *
     * @param conc the input XML string
     * @param makeNonBreaking if true, the output string only contains non-breaking
     *            spaces
     * @return the plain text output string
     */
    public static String xmlToPlainText(String conc, boolean makeNonBreaking) {
        // Allocate buffer.
        int inputLength = conc.length();
        char[] src = new char[inputLength];

        // Get character array
        conc.getChars(0, inputLength, src, 0);

        // Loop through character array
        int dstIndex = 0;
        XmlToPlainTextState state = XmlToPlainTextState.COPY;
        int entityStart = -1;
        char space = ' ';
        if (makeNonBreaking)
            space = StringUtil.CHAR_NON_BREAKING_SPACE; // Non-breaking space (codepoint 160)
        boolean lastCopiedASpace = false; // To normalize whitespace
        for (int srcIndex = 0; srcIndex < inputLength; srcIndex++) {
            char c = src[srcIndex];
            switch (c) {
            case '<':
                // Entering tag
                state = XmlToPlainTextState.IN_TAG;
                break;

            case '>':
                // Leaving tag, back to copy
                state = XmlToPlainTextState.COPY;
                break;

            case '&':
                // Entering entity (NOTE: ignore entities if we're inside a tag)
                if (state != XmlToPlainTextState.IN_TAG) {
                    // Go to entity state
                    state = XmlToPlainTextState.IN_ENTITY;
                    entityStart = srcIndex + 1;
                }
                break;

            case ';':
                if (state == XmlToPlainTextState.IN_ENTITY) {
                    // Leaving entity
                    char whichEntity;
                    String entityName = conc.substring(entityStart, srcIndex);
                    if (entityName.equals("lt"))
                        whichEntity = '<';
                    else if (entityName.equals("gt"))
                        whichEntity = '>';
                    else if (entityName.equals("amp"))
                        whichEntity = '&';
                    else if (entityName.equals("quot"))
                        whichEntity = '"';
                    else if (entityName.startsWith("#x")) {
                        // Hex entity
                        whichEntity = (char) Integer.parseInt(entityName.substring(2), 16);
                    } else if (entityName.length() > 0 && entityName.charAt(0) == '#') {
                        // Decimal entity
                        whichEntity = (char) Integer.parseInt(entityName.substring(1), 10);
                    } else {
                        // Unknown entity!
                        whichEntity = '?';
                    }

                    // Put character in destination buffer
                    src[dstIndex] = whichEntity;
                    dstIndex++;
                    lastCopiedASpace = false; // should be: whichEntity == ' ' || ...

                    // Back to copy state
                    state = XmlToPlainTextState.COPY;
                } else if (state == XmlToPlainTextState.COPY) {
                    // Not in entity or tag, just copy character
                    src[dstIndex] = c;
                    dstIndex++;
                    lastCopiedASpace = false;
                }
                // else: inside tag, ignore all characters until end of tag
                break;

            case ' ':
            case '\t':
            case '\n':
            case '\r':
            case '\u00A0':
                if (state == XmlToPlainTextState.COPY && !lastCopiedASpace) {
                    // First space in a run; copy it
                    src[dstIndex] = space;
                    dstIndex++;
                    lastCopiedASpace = true;
                }
                break;

            default:
                if (state == XmlToPlainTextState.COPY) {
                    // Copy character
                    src[dstIndex] = c;
                    dstIndex++;
                    lastCopiedASpace = false;
                }
                break;
            }
        }

        return new String(src, 0, dstIndex);
    }

    public static String readXmlAndResolveReferences(BufferedReader reader) {
        Document document;
        try {
            DocumentBuilderFactory domFactory = DocumentBuilderFactory.newInstance();
            domFactory.setNamespaceAware(true);
            DocumentBuilder domBuilder = domFactory.newDocumentBuilder();
            domBuilder.setErrorHandler(new SimpleErrorHandler());
            document = domBuilder.parse(new InputSource(reader));
        } catch (ParserConfigurationException | IOException | SAXException e) {
            throw BlackLabException.wrapRuntime(e);
        }
        DOMImplementationLS domImplementation = (DOMImplementationLS) document.getImplementation();
        LSSerializer lsSerializer = domImplementation.createLSSerializer();
        LSOutput out = domImplementation.createLSOutput();
        StringWriter sw = new StringWriter();
        out.setCharacterStream(sw);
        out.setEncoding("UTF-8");
        lsSerializer.write(document, out);
        return sw.toString();
    }

}
