package nl.inl.blacklab.indexers.config.saxon;

import java.io.Reader;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.Source;
import javax.xml.transform.sax.SAXSource;
import javax.xml.xpath.XPathFactory;

import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import net.sf.saxon.Configuration;
import net.sf.saxon.om.TreeInfo;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.xpath.XPathFactoryImpl;

/**
 * A helper for indexing using Saxon.
 */
public class SaxonHelper {

    private SaxonHelper() {}

    /** Maintain one XPath factory per thread. */
    public static final ThreadLocal<XPathFactory> XPATH_FACTORY = new InheritableThreadLocal<>() {
        @Override
        protected XPathFactory initialValue() {
            return new XPathFactoryImpl();
        }
    };

    public static XPathFactory getXPathFactory() {
        return XPATH_FACTORY.get();
    }

    /** Parse the document, using the given content handler.
     *
     * @param reader document to parse
     * @param handler content handler to use
     * @return parsed document
     */
    public static TreeInfo parseDocument(Reader reader, ContentHandler handler) throws ParserConfigurationException,
            SAXException, XPathException {
        // make sure our content handler doesn't get overwritten by saxon
        SAXParserFactory parserFactory = SAXParserFactory.newInstance();
        parserFactory.setXIncludeAware(true);
        SAXParser parser = parserFactory.newSAXParser();
        XMLReader xmlReader = parser.getXMLReader();
        xmlReader.setContentHandler(handler);
        XMLReader trackingReader = new CharPosTrackingXMLReader(xmlReader);
        // regular parsing with line numbering enabled
        InputSource inputSrc = new InputSource(reader);
        Source source = new SAXSource(trackingReader, inputSrc);
        Configuration config = ((XPathFactoryImpl) XPATH_FACTORY.get()).getConfiguration();
        config.setLineNumbering(true);
        return config.buildDocumentTree(source);
    }
}
