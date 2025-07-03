package nl.inl.blacklab.index;

import java.io.IOException;
import java.io.StringReader;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import nl.inl.blacklab.exceptions.BlackLabException;
import nl.inl.blacklab.index.HookableSaxHandler.ElementHandler;

public class TestHookableSaxParser {

    private HookableSaxHandler hookableSaxHandler;

    private InputSource inputSource;

    private ElementHandler hookHandler;

    private SAXParser parser;

    @Before
    public void setUp() {
        String testXml = "<root>"
                + "<nochild>blabla</nochild>"
                + "<child><name>A</name><child att='123'><name>C</name></child></child>"
                + "<child><name>B</name><child att='456'><name>D</name></child></child>"
                + "</root>";
        inputSource = new InputSource(new StringReader(testXml));
        hookableSaxHandler = new HookableSaxHandler();

        SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(true);
        try {
            parser = factory.newSAXParser();
        } catch (ParserConfigurationException | SAXException e) {
            throw BlackLabException.wrapRuntime(e);
        }

        hookHandler = new StringConcatenateHandler();
    }

    static class StringConcatenateHandler extends ElementHandler {

        private final StringBuilder builder = new StringBuilder();

        public StringConcatenateHandler() {
            //
        }

        @Override
        public String toString() {
            return builder.toString();
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) {
            builder.append("<");
        }

        @Override
        public void endElement(String uri, String localName, String qName) {
            builder.append(">");
        }

        @Override
        public void characters(char[] ch, int start, int length) {
            builder.append("$");
        }

//		@Override
//		public void attribute(String name, String value) {
//			builder.append("@");
//		}

    }

    @Test
    public void testRootChild() throws SAXException, IOException {
        hookableSaxHandler.addHook("/root/child", hookHandler, false);
        parser.parse(inputSource, hookableSaxHandler);
        Assert.assertEquals("<><>", hookHandler.toString());
    }

    @Test
    public void testRootChildRelative() throws SAXException, IOException {
        hookableSaxHandler.addHook("root/child", hookHandler, false);
        parser.parse(inputSource, hookableSaxHandler);
        Assert.assertEquals("<><>", hookHandler.toString());
    }

    @Test
    public void testAnyChildName() throws SAXException, IOException {
        hookableSaxHandler.addHook("//child/name", hookHandler, false);
        parser.parse(inputSource, hookableSaxHandler);
        Assert.assertEquals("<$><$><$><$>", hookHandler.toString());
    }

    @Test
    public void testAllDescendants() throws SAXException, IOException {
        hookableSaxHandler.addHook("/root", hookHandler, true);
        parser.parse(inputSource, hookableSaxHandler);
        Assert.assertEquals("<<$><<$><<$>>><<$><<$>>>>", hookHandler.toString());
    }
}
