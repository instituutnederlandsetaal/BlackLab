package nl.inl.blacklab.server.lib.results;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.xml.transform.stream.StreamSource;

import org.junit.Test;

import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.XdmDestination;
import net.sf.saxon.s9api.XdmNode;
import nl.inl.blacklab.indexers.config.ConfigAnnotatedField;
import nl.inl.blacklab.indexers.config.ConfigAnnotation;
import nl.inl.blacklab.indexers.config.ConfigInlineTag;
import nl.inl.blacklab.indexers.config.ConfigInputFormat;
import nl.inl.blacklab.server.exceptions.NotFound;

public class TestXslGenerator {
    private final Processor processor = new Processor(false);

    private XdmNode transform(ConfigInputFormat config, String source) throws Exception {
        var compiler = processor.newXsltCompiler();
        // Duplicate templates can compile with warnings but silently lose a field's rendering.
        compiler.setErrorReporter(error -> { throw new AssertionError(error.getMessage()); });
        var transformer = compiler.compile(new StreamSource(new StringReader(
                XslGenerator.generateXsltFromConfig(config)))).load();
        var output = new XdmDestination();
        transformer.setSource(new StreamSource(new StringReader(source)));
        transformer.setDestination(output);
        transformer.transform();
        return output.getXdmNode();
    }

    private String xpath(XdmNode result, String expression) throws Exception {
        return processor.newXPathCompiler().evaluateSingle(expression, result).getStringValue();
    }

    private String words(XdmNode result) throws Exception {
        return xpath(result, "string-join(//span[@class='word'], ' ')");
    }

    private ConfigAnnotatedField field(String name, String container, String wordPath) {
        var field = new ConfigInputFormat().getOrCreateAnnotatedField(name);
        field.setContainerPath(container);
        field.setWordPath(wordPath);
        field.addAnnotation(new ConfigAnnotation("word", "@word", null));
        field.addAnnotation(new ConfigAnnotation("lemma", "@lemma", null));
        return field;
    }

    private ConfigInputFormat config(ConfigAnnotatedField... fields) {
        var config = new ConfigInputFormat();
        config.setDocumentPath("//doc");
        for (var field : fields)
            config.addAnnotatedField(field);
        return config;
    }

    @Test
    public void alpinoUsesYieldOrderAndRetainsHighlights() throws Exception {
        var config = ConfigInputFormat.read(new File("../contrib/input-formats/alpino.blf.yaml"), "alpino");
        String source = Files.readString(Path.of("../engine/src/test/resources/alpino-2231.xml"));
        String expected = "Santerra en Dax in \" the adventures \" .";
        XdmNode raw = transform(config, source);
        assertEquals(expected, words(raw));
        assertEquals(expected, raw.getStringValue());
        assertEquals("0", xpath(raw, "count(//span[@class='hl'])"));

        source = source.replaceAll("(<node\\b[^>]*\\bword=\"(?:Santerra|en|Dax)\"[^>]*/>)",
                "<hl index=\"7\" start=\"0\" end=\"3\">$1</hl>");
        XdmNode highlighted = transform(config, source);
        assertEquals(expected, words(highlighted));
        assertEquals("3", xpath(highlighted, "count(//span[@class='hl'][@index='7'][@start='0'][@end='3'])"));
        assertEquals("Santerra en Dax", xpath(highlighted,
                "string-join(//span[@class='hl']/span[@class='word'], ' ')"));
        assertEquals("Santerra", xpath(highlighted, "string((//span[@class='word'])[1]/@data-lemma)"));
    }

    @Test
    public void orderedWordsKeepAncestorContextAndNestedHighlights() throws Exception {
        var field = field("contents", ".", "reverse(.//w)");
        field.getAnnotation("lemma").setBasePath("ancestor::doc");
        field.getAnnotation("lemma").setValuePath("concat(@lemma, ' | ', @lemma)");
        var result = transform(config(field), """
                <doc lemma="context"><hl index="2"><hl index="1">
                  <w word="first"/><w word="second"/>
                </hl></hl></doc>
                """);
        assertEquals("second first", words(result));
        assertEquals("2", xpath(result, "count(/span[@index='2']/span[@index='1']/span[@class='word'])"));
        assertEquals("context | context", xpath(result, "string((//span[@class='word'])[1]/@data-lemma)"));
    }

    @Test
    public void punctuationHasTheIndexersContainerAndNeighbourContexts() throws Exception {
        var field = field("contents", "s", "w");
        field.setPunctBeforePath("if (empty($previousWord) and $nextWord/@word = 'b') then $container/@open else '|'");
        field.setPunctAfterLastWordPath("concat(';', $previousWord/@word, $container/@close, string($nextWord))");
        assertEquals("[a|b;a]", transform(config(field),
                "<doc><s open='[' close=']'><w word='a'/><w word='b'/></s></doc>").getStringValue());
    }

    @Test
    public void defaultSpacingSeparatesContainersAndFields() throws Exception {
        var result = transform(config(field("first", "a", "w"), field("second", "b", "w")),
                "<doc><a><w word='one'/></a><a><w word='two'/></a><b><w word='three'/></b></doc>");
        assertEquals("one two three ", result.getStringValue());
    }

    @Test
    public void expressionsAreXmlEscapedAndNeverResubstituted() throws Exception {
        var field = field("contents", ".", ".//w[@n < 2 and @word = \"a\"]");
        field.getAnnotation("word").setValuePath("concat(@word, ' & < \" ${id} $${wordValue} ', \"'\")");
        field.getAnnotation("lemma").setValuePath("'${documentPath} & < > \"'");
        var result = transform(config(field), "<doc><w n='1' word='a'/><w n='2' word='b'/></doc>");
        assertEquals("a & < \" ${id} $${wordValue} '", words(result));
        assertEquals("${documentPath} & < > \"", xpath(result, "string(//span/@data-lemma)"));
    }

    @Test
    public void supportsAbsentDefaultAndPrefixedNamespaces() throws Exception {
        String source = "<doc xmlns='urn:test?a&amp;b'><w word='a'/></doc>";
        assertEquals("a", words(transform(config(field("contents", ".", ".//w")), source)));
        var defaults = config(field("contents", ".", ".//w"));
        defaults.getNamespaces().put("", "urn:test?a&b");
        assertEquals("a", words(transform(defaults, source)));
        var prefixed = config(field("contents", ".", ".//t:w"));
        prefixed.setDocumentPath("//t:doc");
        prefixed.getNamespaces().put("t", "urn:test?a&b");
        assertEquals("a", words(transform(prefixed, source)));
    }

    @Test
    public void structuralFormattingAndHighlightingRemainAvailable() throws Exception {
        var field = field("contents", ".", ".//w");
        String cssClass = "sentence{literal} & \"${id}\"";
        field.addInlineTag(new ConfigInlineTag(".//s", cssClass));
        var result = transform(config(field), """
                <doc><s><w word="one"/><hl index="3"><w word="two"/></hl></s></doc>
                """);
        assertEquals("one two", words(result));
        assertEquals(cssClass, xpath(result, "string(/span/@class)"));
        assertEquals("two", xpath(result, "string(/span/span[@class='hl'][@index='3']/span[@class='word'])"));
    }

    @Test
    public void multipleFieldsHaveIndependentTemplatesAndOneEntryPoint() throws Exception {
        var first = field("first", "a", ".//w");
        first.addInlineTag(new ConfigInlineTag(".//s", "sentence"));
        var second = field("second", "b", "reverse(.//w)");
        var result = transform(config(first, second), """
                <doc><a><s><w word="one"/></s></a><b><w word="three"/><w word="two"/></b></doc>
                """);
        assertEquals("one two three", words(result));
        assertEquals("1", xpath(result, "count(/span[@class='sentence'])"));
    }

    @Test
    public void structuralFieldsOnlyRenderHighlightsForTheirOwnWords() throws Exception {
        var first = field("first", ".", ".//w[@version='a']");
        var second = field("second", ".", ".//w[@version='b']");
        first.addInlineTag(new ConfigInlineTag(".//s", "sentence"));
        second.addInlineTag(new ConfigInlineTag(".//s", "sentence"));
        var result = transform(config(first, second), """
                <doc><s><hl index="0"><w version="a" word="one"/></hl>
                  <hl index="1"><w version="b" word="two"/></hl></s></doc>
                """);
        assertEquals("one two", words(result));
        assertEquals("2", xpath(result, "count(//span[@class='hl'])"));
        assertEquals("0", xpath(result, "count(//span[@class='hl'][not(span[@class='word'])])"));
    }

    @Test
    public void builtinTeiAndFoliaKeepTheirStructuralViews() throws Exception {
        var tei = ConfigInputFormat.read(new File("../contrib/input-formats/tei-p5.blf.yaml"), "tei-p5");
        var teiResult = transform(tei, """
                <TEI xmlns="http://www.tei-c.org/ns/1.0"><text><p><s>
                  <w lemma="first">one</w><hl index="4"><w lemma="second">two</w></hl>
                </s></p></text></TEI>
                """);
        assertEquals("one two", words(teiResult));
        assertEquals("two", xpath(teiResult, "string(/span[@class='p']/span[@class='s']/span[@index='4']/span)"));
        var folia = ConfigInputFormat.read(new File("../contrib/input-formats/folia.blf.yaml"), "folia");
        var foliaResult = transform(folia, """
                <FoLiA xmlns="http://ilk.uvt.nl/folia"><text><p><s>
                  <hl xmlns="" index="5"><w xmlns="http://ilk.uvt.nl/folia"><t>one</t><lemma class="first"/></w></hl>
                </s></p></text></FoLiA>
                """);
        assertEquals("one", words(foliaResult));
        assertEquals("first", xpath(foliaResult, "string(/span[@class='p']/span[@class='s']/span[@index='5']/span/@data-lemma)"));
    }

    @Test
    public void firstAnnotationIsTheFallbackAndEmptyFieldsAreSkipped() throws Exception {
        var field = new ConfigInputFormat().getOrCreateAnnotatedField("contents");
        field.setWordPath(".//w");
        field.addAnnotation(new ConfigAnnotation("surface", ".", null));
        var empty = new ConfigInputFormat().getOrCreateAnnotatedField("empty");
        var result = transform(config(empty, field), "<doc><w>one</w></doc>");
        assertEquals("one", words(result));
        assertEquals("0", xpath(result, "count(//span/@data-lemma)"));
    }

    @Test
    public void missingWordsProduceOneWarning() throws Exception {
        assertTrue(transform(config(field("contents", ".", ".//w")), "<doc/>")
                .getStringValue().startsWith("No words have been found"));
        assertTrue(transform(config(), "<doc/>").getStringValue().startsWith("No words have been found"));
    }

    @Test(expected = NotFound.class)
    public void nonXmlFormatsAreStillRejected() {
        var config = config();
        config.setFileType(ConfigInputFormat.FileType.TABULAR);
        XslGenerator.generateXsltFromConfig(config);
    }
}
