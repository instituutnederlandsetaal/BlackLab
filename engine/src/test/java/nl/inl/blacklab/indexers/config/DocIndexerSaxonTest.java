package nl.inl.blacklab.indexers.config;

import java.io.File;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.util.BytesRef;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;
import org.xml.sax.InputSource;

import nl.inl.blacklab.index.DocumentFormats;
import nl.inl.blacklab.index.Indexer;
import nl.inl.blacklab.index.InputFormat;
import nl.inl.blacklab.exceptions.InvalidConfiguration;
import nl.inl.blacklab.forwardindex.FieldForwardIndex;
import nl.inl.blacklab.forwardindex.TestCollatorsZalgo;
import nl.inl.blacklab.index.annotated.AnnotationSensitivities;
import nl.inl.blacklab.exceptions.InvalidInputFormatConfig;
import nl.inl.blacklab.plugins.FileConverter;
import nl.inl.blacklab.search.BlackLab;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.BlackLabIndexWriter;
import nl.inl.blacklab.search.DocUtil;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldNameUtil;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.indexmetadata.FieldType;
import nl.inl.blacklab.search.indexmetadata.SourceRangeEncoding;
import nl.inl.blacklab.search.indexmetadata.SourceUnitCodec;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.BLSpanTermQuery;
import nl.inl.blacklab.search.lucene.RelationInfo;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.results.hitresults.HitResults;
import nl.inl.blacklab.search.results.hits.Hits;
import nl.inl.blacklab.search.textpattern.CompleteQuery;
import nl.inl.blacklab.search.textpattern.TextPatternTags;
import nl.inl.blacklab.search.textpattern.TextPatternFunctionCall;
import nl.inl.blacklab.search.textpattern.TextPatternValue;
import nl.inl.blacklab.search.textpattern.RelationOperatorInfo;
import nl.inl.blacklab.search.textpattern.RelationTarget;
import nl.inl.blacklab.search.textpattern.TextPatternAnyToken;
import nl.inl.blacklab.search.textpattern.TextPatternRelationMatch;
import nl.inl.util.UtilsForTesting;
import nl.inl.util.fileprocessor.FileReference;

public class DocIndexerSaxonTest {

    private UtilsForTesting.TestDir testDir;
    private File indexDir;

    @BeforeClass
    public static void beforeClass() {
        BlackLab.implicitInstance(); // init plugin system
    }

    @Before
    public void setUp() {
        testDir = UtilsForTesting.createBlackLabTestDir("DocIndexerSaxonTest");
        indexDir = testDir.file();
    }

    @After
    public void tearDown() {
        testDir.close();
    }

    /**
     * Test that indexing with saxon allow xpaths that result in strings at the basePath level.
     * This allows various optimizations when one string in the document results in multiple annotations.
     * E.g. by pre-splitting a string using basePath, then processing each split using valuePath
     */
    @Test
    public void testSaxonTokenizer() throws Exception {
        // 1. Create ConfigInputFormat
        ConfigInputFormat config = new ConfigInputFormat("saxon-test");
        config.setFileType(ConfigInputFormat.FileType.XML);
        config.addFileTypeOption("processor", "saxon");
        config.setDocumentPath("//doc");

        ConfigAnnotatedField contents = new ConfigAnnotatedField("contents");
        contents.setContainerPath(".");
        contents.setWordPath(".//w");

        // Add the main "word" annotation
        ConfigAnnotation word = new ConfigAnnotation();
        word.setName("word");
        word.setValuePath(".");
        contents.addAnnotation(word);

        ConfigAnnotation pos = new ConfigAnnotation();
        pos.setName("pos");
        pos.setBasePath("@pos");
        pos.setValuePath("tokenize(., '\\+')");

        ConfigAnnotation head = new ConfigAnnotation();
        head.setName("head");
        // Note: using ! for map operator in XPath 3.0+ (Saxon supports this)
        head.setValuePath("tokenize(., '\\+')!substring-before(., '(')");
        pos.addSubannotation(head);

        contents.addAnnotation(pos);
        config.addAnnotatedField(contents);

        DocumentFormats.add(config);

        // 2. Index a document
        try (BlackLabIndexWriter indexWriter = BlackLab.openForWriting(indexDir, true, "saxon-test")) {
            Indexer indexer = Indexer.create(indexWriter);
            String xml = "<doc><w pos='ADP(type=pre)+PD(type=d-p,subtype=art,position=prenom)'>word</w></doc>";
            FileReference fileRef = FileReference.fromBytes("doc1", xml.getBytes(), null);
            indexer.index(fileRef, null, FileConverter.ExtraConverters.NONE);
            indexer.close();
        }

        // 3. Verify
        try (BlackLabIndex index = BlackLab.open(indexDir)) {
            QueryInfo qi = QueryInfo.create(index);
            Assert.assertNull(index.annotatedField("contents").mainAnnotation().offsetsSensitivity());
            Assert.assertTrue(index.metadata().usesSourceRangeVectors());
            
            // Build Lucene field name for pos annotation (insensitive)
            String posField = AnnotatedFieldNameUtil.annotationField("contents", "pos", MatchSensitivity.INSENSITIVE.luceneFieldSuffix());
            // head is a subannotation of pos, so the field name includes the parent name
            String headField = AnnotatedFieldNameUtil.annotationField("contents", "pos" + AnnotatedFieldNameUtil.SUBANNOTATION_FIELD_PREFIX_SEPARATOR + "head", MatchSensitivity.INSENSITIVE.luceneFieldSuffix());

            // Search for the first part
            BLSpanQuery q = new BLSpanTermQuery(qi, new Term(posField, "adp(type=pre)"));
            HitResults hits = index.find(q, null);
            Assert.assertEquals("Should find the first part of the split pos", 1, hits.size());

            // Search for the second part
            q = new BLSpanTermQuery(qi, new Term(posField, "pd(type=d-p,subtype=art,position=prenom)"));
            hits = index.find(q, null);
            Assert.assertEquals("Should find the second part of the split pos", 1, hits.size());

            // Search for head
            q = new BLSpanTermQuery(qi, new Term(headField, "adp"));
            hits = index.find(q, null);
            Assert.assertEquals("Should find head ADP", 1, hits.size());

            q = new BLSpanTermQuery(qi, new Term(headField, "pd"));
            hits = index.find(q, null);
            Assert.assertEquals("Should find head PD", 1, hits.size());
        }
    }

    @Test
    public void testIndexingNonFirstMainAnnotation() throws Exception {
        String formatName = "saxon-non-first-main-test";
        ConfigInputFormat config = new ConfigInputFormat(formatName);
        config.setFileType(ConfigInputFormat.FileType.XML);
        config.setDocumentPath("//doc");

        ConfigAnnotatedField contents = new ConfigAnnotatedField("contents");
        contents.setContainerPath(".");
        contents.setWordPath(".//w");
        ConfigAnnotation lemma = new ConfigAnnotation();
        lemma.setName("lemma");
        lemma.setValuePath("@lemma");
        contents.addAnnotation(lemma);

        ConfigAnnotation word = new ConfigAnnotation();
        word.setName("word");
        word.setValuePath(".");
        contents.addAnnotation(word);
        
        contents.setMainAnnotation("word");
        config.addAnnotatedField(contents);
        DocumentFormats.add(config);

        try (BlackLabIndexWriter indexWriter = BlackLab.openForWriting(indexDir, true, formatName)) {
            Indexer indexer = Indexer.create(indexWriter);
            indexer.index(FileReference.fromBytes("doc1", "<doc><w lemma='L'>X</w></doc>".getBytes(), null), null,
                    FileConverter.ExtraConverters.NONE);
            indexer.close();
        }

        try (BlackLabIndex index = BlackLab.open(indexDir)) {
            Assert.assertEquals("word", index.annotatedField("contents").mainAnnotation().name());
            QueryInfo qi = QueryInfo.create(index);
            String lemmaField = AnnotatedFieldNameUtil.annotationField("contents", "lemma",
                    MatchSensitivity.INSENSITIVE.luceneFieldSuffix());
            String wordField = AnnotatedFieldNameUtil.annotationField("contents", "word",
                    MatchSensitivity.INSENSITIVE.luceneFieldSuffix());
            Assert.assertEquals(1, index.find(new BLSpanTermQuery(qi, new Term(lemmaField, "l")), null).size());
            Assert.assertEquals(1, index.find(new BLSpanTermQuery(qi, new Term(wordField, "x")), null).size());
        }
    }

    @Test
    public void testExplicitPunctuationPreservesValuesAndContainerGaps() throws Exception {
        String formatName = "saxon-explicit-punctuation-test";
        ConfigInputFormat config = new ConfigInputFormat(formatName);
        config.setFileType(ConfigInputFormat.FileType.XML);
        config.setDocumentPath("//doc");
        config.addAnnotatedField(punctuationField("contents",
                "concat(if (empty($previousWord)) then " +
                        "(if (count($container//w) = 2) then '  [' else '  {') else ' /', " +
                        "if (empty($nextWord)) then ']' else '+')",
                "if (empty($previousWord)) then '!!' else '??'"));
        config.addAnnotatedField(punctuationField("defaults", null, "''"));
        config.addAnnotatedField(punctuationField("sequences", "($previousWord/@p, @p)", "(@q, '!')"));
        ConfigAnnotatedField emptyLegacy = punctuationField("emptyLegacy", null, null);
        emptyLegacy.setContainerPath(".//empty");
        emptyLegacy.setPunctPath(".//text()");
        config.addAnnotatedField(emptyLegacy);
        ConfigAnnotatedField emptyExplicit = punctuationField("emptyExplicit", "''", null);
        emptyExplicit.setContainerPath(".//missing");
        config.addAnnotatedField(emptyExplicit);
        config.addAnnotatedField(punctuationField("afterEmpty", null, null));
        ConfigAnnotatedField legacy = punctuationField("legacy", null, null);
        legacy.setPunctPath(".//text()[not(parent::w)]");
        config.addAnnotatedField(legacy);
        DocumentFormats.add(config);

        try (BlackLabIndexWriter indexWriter = BlackLab.openForWriting(indexDir, true, formatName)) {
            indexWriter.metadata().setIndexFlag(SourceRangeEncoding.INDEX_FLAG, SourceRangeEncoding.VERSION);
            Indexer indexer = Indexer.create(indexWriter);
            String xml = "<doc><empty>LEAK</empty><s>~<w p='[' q=';'>A</w>,<w p=']' q='?'>B</w>?" +
                    "</s><s>!<w p='{' q=':'>C</w>#</s></doc>";
            indexer.index(FileReference.fromBytes("doc1", xml.getBytes(), null), null,
                    FileConverter.ExtraConverters.NONE);
            indexer.close();
        }

        try (BlackLabIndex index = BlackLab.open(indexDir)) {
            Assert.assertEquals(List.of("  [+", " /]", "??  {]", "!!"),
                    punctuationValues(index, "contents"));
            Assert.assertEquals(List.of(" ", " ", " ", ""), punctuationValues(index, "defaults"));
            Assert.assertEquals(List.of("[", "[]", "?!{", ":!"), punctuationValues(index, "sequences"));
            Assert.assertEquals(List.of("leak"), punctuationValues(index, "emptyLegacy"));
            Assert.assertEquals(List.of(" ", " ", " ", ""), punctuationValues(index, "afterEmpty"));
            Assert.assertEquals(List.of("~", ",", "?!", "#"), punctuationValues(index, "legacy"));
        }
    }

    @Test
    public void testExplicitPunctuationAppendsToNativeOffsetIndex() throws Exception {
        ConfigInputFormat legacy = new ConfigInputFormat("saxon-native-punctuation");
        legacy.setFileType(ConfigInputFormat.FileType.XML);
        legacy.setDocumentPath("//doc");
        legacy.addAnnotatedField(wordField("contents", null));
        DocumentFormats.add(legacy);
        ConfigInputFormat explicit = new ConfigInputFormat("saxon-explicit-native-punctuation");
        explicit.setFileType(ConfigInputFormat.FileType.XML);
        explicit.setDocumentPath("//doc");
        ConfigAnnotatedField contents = punctuationField("contents",
                "concat(if (empty($previousWord)) then '  ' else ' /', @p)",
                "if (empty($previousWord)) then '!!' else '??'");
        contents.setWordPath("reverse(.//w)");
        explicit.addAnnotatedField(contents);
        DocumentFormats.add(explicit);

        // Construct a native-offset fixture, then reopen and append using explicit punctuation.
        try (BlackLabIndexWriter writer = BlackLab.openForWriting(indexDir, true, legacy.getName())) {
            writer.metadata().setIndexFlag(SourceRangeEncoding.INDEX_FLAG, "");
            writer.metadata().save();
        }
        String xml = "<doc><s><w p='['>A</w><w p=']'>B</w></s><s><w p='{'>C</w></s></doc>";
        for (int append = 0; append < 2; append++) {
            try (BlackLabIndexWriter writer = BlackLab.openForWriting(indexDir, false)) {
                Assert.assertFalse(writer.metadata().usesSourceRangeVectors());
                Indexer indexer = Indexer.create(writer);
                try {
                    indexer.setFormatIdentifier(explicit.getName());
                    indexer.index(FileReference.fromBytes("source.xml", xml.getBytes(StandardCharsets.UTF_8), null),
                            null, FileConverter.ExtraConverters.NONE);
                } finally {
                    indexer.close();
                }
            }
        }
        try (BlackLabIndex index = BlackLab.open(indexDir)) {
            Assert.assertFalse(index.metadata().usesSourceRangeVectors());
            Assert.assertEquals(2, index.metadata().documentCount());
            Assert.assertEquals(6, index.metadata().tokenCount());
            var field = index.mainAnnotatedField();
            Hits hits = index.find(new BLSpanTermQuery(QueryInfo.create(index), new Term(
                    field.mainAnnotation().sensitivity(MatchSensitivity.INSENSITIVE).luceneField(), "a")), null).getHits();
            Assert.assertEquals(2, hits.size());
            for (var hit: hits) {
                Assert.assertEquals(0, hit.start());
                Assert.assertEquals(List.of("a", "b", "c", ""),
                        annotationValues(index, hit.doc(), "contents", "word"));
                Assert.assertEquals(List.of("  [", " /]", "??  {", "!!"),
                        annotationValues(index, hit.doc(), "contents", AnnotatedFieldNameUtil.PUNCTUATION_ANNOT_NAME));
                int[] starts = { 0, 1, 2 }, ends = { 0, 1, 2 };
                DocUtil.characterOffsets(index, hit.doc(), field, starts, ends, false);
                Assert.assertArrayEquals(new int[] { xml.indexOf("<w p='['>"), xml.indexOf("<w p=']'>"),
                        xml.indexOf("<w p='{'>") }, starts);
                Assert.assertArrayEquals(new int[] { xml.indexOf("A</w>") + 5, xml.indexOf("B</w>") + 5,
                        xml.indexOf("C</w>") + 5 }, ends);
            }
        }
    }

    @Test
    public void testDirectInputFormatRequiresSourceRangeCapability() throws Exception {
        InputFormat plugin = Mockito.mock(InputFormat.class, Mockito.CALLS_REAL_METHODS);
        DocumentFormats.add("direct-legacy-plugin", plugin);
        Assert.assertFalse(plugin.supportsSourceRangeVectors());
        var wrapped = new InputFormatTypeWithConverters().createInputFormat(plugin,
                List.of(Mockito.mock(FileConverter.Parameterized.class)));
        Assert.assertFalse(wrapped.supportsSourceRangeVectors());
        DocumentFormats.add("wrapped-legacy-plugin", wrapped);
        try (BlackLabIndexWriter writer = BlackLab.openForWriting(indexDir, true)) {
            for (String name: List.of("direct-legacy-plugin", "wrapped-legacy-plugin")) {
                InvalidInputFormatConfig error = Assert.assertThrows(InvalidInputFormatConfig.class,
                        () -> Indexer.create(writer, name));
                Assert.assertTrue(error.getMessage().contains("does not support source-range storage"));
            }
            writer.metadata().setIndexFlag(SourceRangeEncoding.INDEX_FLAG, "");
            Indexer legacy = Indexer.create(writer, "direct-legacy-plugin");
            Assert.assertSame(plugin, legacy.getDocIndexer());
            legacy.close();
        }
        Mockito.verify(plugin, Mockito.never()).index(Mockito.any(), Mockito.any());
    }

    @Test
    public void testCreationAndAppendKeepTheirCodecAndTokenOrder() throws Exception {
        ConfigInputFormat config = new ConfigInputFormat("saxon-codec-lifecycle");
        config.setFileType(ConfigInputFormat.FileType.XML);
        config.setDocumentPath("//doc");
        ConfigAnnotatedField contents = wordField("contents", null);
        contents.setWordPath("reverse(.//w)");
        config.addAnnotatedField(contents);
        DocumentFormats.add(config);
        String xml = "<doc><w>A</w><w>B</w></doc>";
        for (boolean legacy: List.of(false, true)) {
            File directory = new File(indexDir, legacy ? "legacy" : "source");
            // Exercise CREATE_OR_APPEND on a missing index, then the usual explicit metadata save.
            try (BlackLabIndexWriter writer = BlackLab.openForWriting(directory, false, config)) {
                Assert.assertTrue(writer.metadata().usesSourceRangeVectors());
                Assert.assertNull(writer.mainAnnotatedField().mainAnnotation().offsetsSensitivity());
                if (legacy)
                    writer.metadata().setIndexFlag(SourceRangeEncoding.INDEX_FLAG, "");
                writer.metadata().save();
            }
            for (int append = 0; append < 2; append++) {
                try (BlackLabIndexWriter writer = BlackLab.openForWriting(directory, false)) {
                    Assert.assertEquals(!legacy, writer.metadata().usesSourceRangeVectors());
                    Indexer indexer = Indexer.create(writer);
                    try {
                        indexer.index(FileReference.fromBytes("source.xml", xml.getBytes(StandardCharsets.UTF_8), null),
                                null, FileConverter.ExtraConverters.NONE);
                    } finally {
                        indexer.close();
                    }
                }
            }
            try (BlackLabIndex index = BlackLab.open(directory)) {
                Assert.assertEquals(!legacy, index.metadata().usesSourceRangeVectors());
                Assert.assertEquals(2, index.metadata().documentCount());
                Assert.assertEquals(4, index.metadata().tokenCount());
                var field = index.mainAnnotatedField();
                Hits hits = index.find(new BLSpanTermQuery(QueryInfo.create(index), new Term(
                        field.mainAnnotation().sensitivity(MatchSensitivity.INSENSITIVE).luceneField(), "a")), null).getHits();
                Assert.assertEquals(2, hits.size());
                for (var hit: hits) {
                    Assert.assertEquals(legacy ? 0 : 1, hit.start());
                    int[] starts = {0, 1}, ends = {0, 1};
                    DocUtil.characterOffsets(index, hit.doc(), field, starts, ends, false);
                    Assert.assertArrayEquals(legacy ? new int[] {5, 13} : new int[] {13, 5}, starts);
                    Assert.assertArrayEquals(legacy ? new int[] {13, 21} : new int[] {21, 13}, ends);
                }
            }
        }
    }

    @Test
    public void testEmptyIndexWithoutFormatKeepsNewCodec() {
        try (BlackLabIndexWriter writer = BlackLab.openForWriting(indexDir, true)) {
            Assert.assertTrue(writer.metadata().usesSourceRangeVectors());
            writer.metadata().save();
        }
        try (BlackLabIndexWriter writer = BlackLab.openForWriting(indexDir, false)) {
            Assert.assertTrue(writer.metadata().usesSourceRangeVectors());
        }
    }

    @Test
    public void testSourceRangePreflightResolvesLinkedFormats() throws Exception {
        ConfigInputFormat config = new ConfigInputFormat("saxon-linked-resolution-preflight");
        config.setFileType(ConfigInputFormat.FileType.XML);
        config.setDocumentPath("//doc");
        config.addAnnotatedField(wordField("contents", null));
        DocumentFormats.add(config);
        try (BlackLabIndexWriter writer = BlackLab.openForWriting(indexDir, true, config.getName())) {
            writer.metadata().setIndexFlag(SourceRangeEncoding.INDEX_FLAG, SourceRangeEncoding.VERSION);
            Indexer indexer = Indexer.create(writer);
            try {
                config.getOrCreateLinkedDocument("external").setInputFormat("nonexistent-linked-preflight");
                InvalidInputFormatConfig error = Assert.assertThrows(InvalidInputFormatConfig.class,
                        () -> Indexer.create(writer));
                Assert.assertTrue(error.getMessage().contains("nonexistent-linked-preflight"));
                Assert.assertThrows(InvalidInputFormatConfig.class,
                        () -> indexer.setFormatIdentifier(config.getName()));
                Assert.assertEquals(0, writer.writer().getNumberOfDocs());
            } finally {
                indexer.close();
            }
        }
    }

    @Test
    public void testVectorLegacyPunctuationPreservesContainerGaps() throws Exception {
        String formatName = "saxon-vector-legacy-punctuation-test";
        ConfigInputFormat config = new ConfigInputFormat(formatName);
        config.setFileType(ConfigInputFormat.FileType.XML);
        config.setDocumentPath("//doc");
        ConfigAnnotatedField contents = punctuationField("contents", null, null);
        contents.setWordPath("reverse(.//w)");
        contents.setPunctPath(".//text()[not(parent::w)]");
        config.addAnnotatedField(contents);
        ConfigAnnotatedField empty = punctuationField("empty", null, null);
        empty.setPunctPath(".//missing/text()");
        config.addAnnotatedField(empty);
        DocumentFormats.add(config);

        try (BlackLabIndexWriter indexWriter = BlackLab.openForWriting(indexDir, true, formatName)) {
            indexWriter.metadata().setIndexFlag(SourceRangeEncoding.INDEX_FLAG, SourceRangeEncoding.VERSION);
            Indexer indexer = Indexer.create(indexWriter);
            String xml = "<doc><s>~<w>A</w>,<w>B</w>!</s><s>?<w>C</w>#</s></doc>";
            indexer.index(FileReference.fromBytes("doc1", xml.getBytes(), null), null,
                    FileConverter.ExtraConverters.NONE);
            indexer.close();
        }

        try (BlackLabIndex index = BlackLab.open(indexDir)) {
            Assert.assertEquals(List.of(",", "~", "!?", "#"), punctuationValues(index, "contents"));
            Assert.assertEquals(List.of("b", "a", "c", ""), annotationValues(index, "contents", "word"));
            Assert.assertEquals(List.of("", "", "", ""), punctuationValues(index, "empty"));
        }
    }

    @Test
    public void testSourceRangeOrderPreservesValidInlineSpan() throws Exception {
        String formatName = "saxon-source-range-inline-order-test";
        ConfigInputFormat config = new ConfigInputFormat(formatName);
        config.setFileType(ConfigInputFormat.FileType.XML);
        config.setDocumentPath("//doc");
        ConfigAnnotatedField contents = wordField("contents", ".//body");
        contents.setWordPath("sort(.//w, (), function($w) { xs:integer($w/@n) })");
        contents.addInlineTag(new ConfigInlineTag(".//span", ""));
        contents.addInlineTag(new ConfigInlineTag(".//anchor", ""));
        config.addAnnotatedField(contents);
        DocumentFormats.add(config);

        String xml = "<doc><body><anchor/><span><w n='2'>A</w><w n='1'>B</w></span>" +
                "<w n='3'>C</w></body></doc>";
        try (BlackLabIndexWriter indexWriter = BlackLab.openForWriting(indexDir, true, formatName)) {
            indexWriter.metadata().setIndexFlag(SourceRangeEncoding.INDEX_FLAG, SourceRangeEncoding.VERSION);
            Indexer indexer = Indexer.create(indexWriter);
            indexer.index(FileReference.fromBytes("doc1", xml.getBytes(), null), null,
                    FileConverter.ExtraConverters.NONE);
            indexer.close();
        }

        try (BlackLabIndex index = BlackLab.open(indexDir)) {
            Assert.assertEquals(List.of("b", "a", "c", ""), annotationValues(index, "contents", "word"));
            int docId = index.metadata().metadataDocId() == 0 ? 1 : 0;
            int[] starts = { 0, 1, 2 };
            int[] ends = { 0, 1, 2 };
            DocUtil.characterOffsets(index, docId, index.annotatedField("contents"), starts, ends, false);
            Assert.assertArrayEquals(new int[] {
                    xml.indexOf("<w n='1'>B</w>"),
                    xml.indexOf("<w n='2'>A</w>"),
                    xml.indexOf("<w n='3'>C</w>") }, starts);
            Assert.assertArrayEquals(new int[] {
                    xml.indexOf("<w n='1'>B</w>") + "<w n='1'>B</w>".length(),
                    xml.indexOf("<w n='2'>A</w>") + "<w n='2'>A</w>".length(),
                    xml.indexOf("<w n='3'>C</w>") + "<w n='3'>C</w>".length() }, ends);
            HitResults tags = index.search().find(new CompleteQuery(new TextPatternTags("span", null))).execute();
            Assert.assertEquals(1, tags.size());
            Assert.assertEquals(0, tags.getHits().get(0).start());
            Assert.assertEquals(2, tags.getHits().get(0).end());
            HitResults anchors = index.search().find(new CompleteQuery(new TextPatternTags("anchor", null))).execute();
            Assert.assertEquals(1, anchors.size());
            Assert.assertEquals(0, anchors.getHits().get(0).start());
            Assert.assertEquals(0, anchors.getHits().get(0).end());
        }
    }

    @Test
    public void testMetadataFragmentsUseTokenOrderAndTrailingAnchors() throws Exception {
        for (boolean sourceRanges: new boolean[] { false, true }) {
            indexDir = new File(testDir.file(), sourceRanges ? "source-ranges" : "native-offsets");
            ConfigInputFormat config = fragmentFormat("saxon-fragment-order-" + sourceRanges);
            ConfigAnnotatedField contents = config.getAnnotatedFields().get("contents");
            contents.setWordPath("sort(.//w, (), function($w) { xs:integer($w/@n) })");
            contents.setTokenIdPath("@id");
            // One XML element can be both a searchable span and a metadata fragment.
            contents.addInlineTag(new ConfigInlineTag(".//span", ""));
            ConfigInlineTag anchor = new ConfigInlineTag(".//anchor", "");
            anchor.setTokenIdPath("@id");
            contents.addInlineTag(anchor);
            ConfigStandoffAnnotations standoff = new ConfigStandoffAnnotations(".//mark", "@start");
            standoff.setType(AnnotationType.FRAGMENT);
            standoff.setSpanEndPath("@end");
            standoff.setSpanEndIsInclusive(false);
            contents.addStandoffAnnotation(standoff);
            DocumentFormats.add(config);

            String xml = "<doc pid='d'><span inside='inner'><w id='a' n='2'>A</w>" +
                    "<w id='b' n='1'>B</w></span><w id='c' n='3'>C</w><anchor id='end'/>" +
                    "<mark start='a' end='end' outside='outer'/></doc>";
            try (BlackLabIndexWriter writer = BlackLab.openForWriting(indexDir, true, config.getName())) {
                writer.metadata().setIndexFlag(SourceRangeEncoding.INDEX_FLAG,
                        sourceRanges ? SourceRangeEncoding.VERSION : "");
                Indexer indexer = Indexer.create(writer);
                try {
                    indexer.index(FileReference.fromBytes("source.xml", xml.getBytes(StandardCharsets.UTF_8), null),
                            null, FileConverter.ExtraConverters.NONE);
                } finally {
                    indexer.close();
                }
            }
            try (BlackLabIndex index = BlackLab.open(indexDir)) {
                int docId = index.getDocIdFromPid("d");
                Assert.assertEquals(sourceRanges ? List.of("b", "a", "c", "") : List.of("a", "b", "c", ""),
                        annotationValues(index, docId, "contents", "word"));
                assertFragmentHits(index, "inside", "inner", docId, 0, 1);
                assertFragmentHits(index, "outside", "outer", docId,
                        sourceRanges ? new int[] { 1, 2 } : new int[] { 0, 1, 2 });
                Hits spans = index.search().find(new CompleteQuery(new TextPatternTags("span", null)))
                        .execute().getHits();
                Assert.assertEquals(1, spans.size());
                Assert.assertEquals(0, spans.get(0).start());
                Assert.assertEquals(2, spans.get(0).end());
                if (sourceRanges) {
                    int[] starts = { 0, 1 }, ends = { 0, 1 };
                    DocUtil.characterOffsets(index, docId, index.mainAnnotatedField(), starts, ends, false);
                    Assert.assertArrayEquals(new int[] { xml.indexOf("<w id='b'"), xml.indexOf("<w id='a'") }, starts);
                    Assert.assertEquals(xml, DocUtil.xmlContents(index, docId, index.mainAnnotatedField(), 0, 2, null));
                }
            }
        }
    }

    private static ConfigInputFormat fragmentFormat(String name) {
        ConfigInputFormat config = new ConfigInputFormat(name);
        config.setFileType(ConfigInputFormat.FileType.XML);
        config.setDocumentPath("//doc");
        ConfigAnnotatedField contents = wordField("contents", ".");
        ConfigInlineTag fragment = new ConfigInlineTag(".//span", "");
        fragment.setType(AnnotationType.FRAGMENT);
        contents.addInlineTag(fragment);
        config.addAnnotatedField(contents);
        config.getCorpusConfig().specialFields.put("pidField", "pid");
        ConfigMetadataBlock metadata = config.createMetadataBlock();
        ConfigMetadataField pid = new ConfigMetadataField("pid", "@pid");
        pid.setType(FieldType.UNTOKENIZED);
        pid.setFragments(FragmentBehaviour.SEPARATE);
        metadata.addMetadataField(pid);
        metadata.addMetadataField(new ConfigMetadataField("inside", "@inside"));
        metadata.addMetadataField(new ConfigMetadataField("outside", "@outside"));
        return config;
    }

    private static void assertFragmentHits(BlackLabIndex index, String field, String value, int docId,
            int... positions) {
        Hits hits = index.search().find(new CompleteQuery(new TextPatternAnyToken(1),
                new TermQuery(new Term(field, value)))).execute().getHits();
        Assert.assertEquals(positions.length, hits.size());
        for (int i = 0; i < positions.length; i++) {
            Assert.assertEquals(docId, hits.get(i).doc());
            Assert.assertEquals(positions[i], hits.get(i).start());
            Assert.assertEquals(positions[i] + 1, hits.get(i).end());
        }
    }

    @Test
    public void testZalgoSourceRangesRetainOriginalUtf16() throws Exception {
        ConfigInputFormat config = new ConfigInputFormat("saxon-zalgo-source-test");
        config.setFileType(ConfigInputFormat.FileType.XML);
        config.setDocumentPath("//doc");
        ConfigAnnotatedField contents = wordField("contents", null);
        contents.getAnnotation("word").setSensitivity(AnnotationSensitivities.CASE_AND_DIACRITICS_SEPARATE);
        config.addAnnotatedField(contents);
        List<String> zalgo = TestCollatorsZalgo.examples();
        String first = "<w>😀" + zalgo.get(0) + "</w>";
        String second = "<w>" + zalgo.get(1) + "</w>";
        String xml = "<doc>\r\n" + first + "\r\n" + second + "</doc>";
        indexSourceXml(config, xml);
        try (BlackLabIndex index = BlackLab.open(indexDir)) {
            var field = index.mainAnnotatedField();
            int docId = index.metadata().metadataDocId() == 0 ? 1 : 0;
            // Lexical storage normalizes Unicode; source ranges must still address the original spelling.
            Assert.assertEquals(List.of(Normalizer.normalize("😀" + zalgo.get(0), Normalizer.Form.NFC),
                            Normalizer.normalize(zalgo.get(1), Normalizer.Form.NFC), ""),
                    annotationValues(index, "contents", "word"));
            int[] starts = { 0, 1 };
            int[] ends = { 0, 1 };
            DocUtil.characterOffsets(index, docId, field, starts, ends, false);
            Assert.assertArrayEquals(new int[] { xml.indexOf(first), xml.indexOf(second) }, starts);
            Assert.assertArrayEquals(new int[] { xml.indexOf(first) + first.length(),
                    xml.indexOf(second) + second.length() }, ends);
            Assert.assertEquals(xml, DocUtil.xmlContents(index, docId, field, 1, 2, null));
            Hits hit = Hits.single(new Hits.HitsContext(field), docId, 0, 1);
            Assert.assertEquals(xml.replace(first, "<hl index=\"0\" start=\"0\" end=\"1\">" + first + "</hl>"),
                    DocUtil.xmlContents(index, docId, field, -1, -1, hit));
            Assert.assertEquals(xml.replace(first, "<hl index=\"0\" start=\"0\" end=\"1\">" + first + "</hl>"),
                    DocUtil.highlightContent(index, docId, hit, 0, 1));
            Assert.assertEquals(xml.replace(first, "<hl index=\"0\" start=\"0\" end=\"1\">" + first + "</hl>"),
                    DocUtil.highlightDocument(index, field, docId, hit));
        }
    }

    @Test
    public void testAlpinoSentence2231EndToEnd() throws Exception {
        ConfigInputFormat config = ConfigInputFormat.read(new File("../contrib/input-formats/alpino.blf.yaml"),
                "alpino-source-test");
        String xml;
        try (var input = getClass().getResourceAsStream("/alpino-2231.xml")) {
            xml = new String(input.readAllBytes(), StandardCharsets.UTF_8).stripTrailing();
        }
        indexSourceXml(config, xml);
        try (BlackLabIndex index = BlackLab.open(indexDir)) {
            int docId = index.getDocIdFromPid("2231");
            Assert.assertTrue(docId >= 0);
            Assert.assertEquals(List.of("Santerra", "en", "Dax", "in", "\"", "the", "adventures", "\"", ".", ""),
                    annotationValues(index, "contents", "word"));
            Assert.assertEquals(List.of("", " ", " ", " ", " ", " ", " ", " ", " ", ""),
                    punctuationValues(index, "contents"));
            Hits spans = index.search().find(new CompleteQuery(new TextPatternTags("node", null))).execute().getHits();
            Assert.assertEquals(1, spans.size());
            Assert.assertEquals(0, spans.get(0).start());
            Assert.assertEquals(9, spans.get(0).end());
            assertAlpinoRelation(index, "mod", 0, 3);
            assertAlpinoRelation(index, "obj1", 3, 5);
            assertAlpinoRelation(index, "top", null, 0);

            var field = index.mainAnnotatedField();
            Assert.assertEquals(xml, DocUtil.contents(index, field, docId, null));
            Hits hit = Hits.single(new Hits.HitsContext(field), docId, 0, 3);
            String content = DocUtil.xmlContents(index, docId, field, 0, 3, hit);
            Assert.assertEquals(content, DocUtil.highlightContent(index, docId, hit, 0, 3));
            Assert.assertEquals(content, DocUtil.highlightDocument(index, field, docId, hit));
            org.w3c.dom.Document parsed = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(
                    new InputSource(new StringReader(content)));
            var highlights = parsed.getElementsByTagName("hl");
            Assert.assertEquals(3, highlights.getLength());
            List<String> sourceOrder = List.of("en", "Santerra", "Dax");
            for (int i = 0; i < highlights.getLength(); i++) {
                var highlight = (org.w3c.dom.Element) highlights.item(i);
                int position = i == 0 ? 1 : i == 1 ? 0 : 2;
                Assert.assertEquals(Integer.toString(position), highlight.getAttribute("start"));
                Assert.assertEquals(Integer.toString(position + 1), highlight.getAttribute("end"));
                var token = (org.w3c.dom.Element) highlight.getFirstChild();
                Assert.assertEquals(sourceOrder.get(i), token.getAttribute("word"));
            }
            Assert.assertEquals(xml, content.replaceAll("<hl[^>]*>", "").replace("</hl>", ""));
        }
    }

    @Test
    public void testAlpinoHeadAfterNonHeadTerminal() throws Exception {
        ConfigInputFormat config = ConfigInputFormat.read(new File("../contrib/input-formats/alpino.blf.yaml"),
                "alpino-head-test");
        indexSourceXml(config, "<alpino_ds id='heads'><node begin='0' end='2' id='0' rel='top'>" +
                "<node begin='0' end='1' id='1' rel='det' word='de'/>" +
                "<node begin='1' end='2' id='2' rel='hd' word='hond'/>" +
                "</node></alpino_ds>");
        try (BlackLabIndex index = BlackLab.open(indexDir)) {
            assertAlpinoRelation(index, "det", 1, 0);
            assertAlpinoRelation(index, "top", null, 1);
        }
    }

    private void indexSourceXml(ConfigInputFormat config, String xml) throws Exception {
        DocumentFormats.add(config);
        try (BlackLabIndexWriter writer = BlackLab.openForWriting(indexDir, true, config.getName())) {
            writer.metadata().setIndexFlag(SourceRangeEncoding.INDEX_FLAG, SourceRangeEncoding.VERSION);
            Indexer indexer = Indexer.create(writer);
            try {
                indexer.index(FileReference.fromBytes("source.xml", xml.getBytes(StandardCharsets.UTF_8), null),
                        null, FileConverter.ExtraConverters.NONE);
            } finally {
                indexer.close();
            }
        }
    }

    private static void assertAlpinoRelation(BlackLabIndex index, String type, Integer source, int target) {
        Hits hits = index.search().find(new CompleteQuery(new TextPatternFunctionCall("rel",
                List.of(TextPatternValue.fromObject("dep::" + type), TextPatternAnyToken.anyNGram(),
                        TextPatternValue.fromObject(source == null ? "target" : "source"),
                        TextPatternValue.fromObject("dependency"),
                        TextPatternValue.fromObject(source == null ? "root" : "both"))))).execute().getHits();
        Assert.assertEquals(1, hits.size());
        RelationInfo relation = Arrays.stream(hits.get(0).matchInfos())
                .filter(RelationInfo.class::isInstance).map(RelationInfo.class::cast).findFirst().orElseThrow();
        if (source == null) {
            Assert.assertTrue(relation.isRoot());
        } else {
            Assert.assertEquals((int) source, relation.getSourceStart());
            Assert.assertEquals(source + 1, relation.getSourceEnd());
        }
        Assert.assertEquals(target, relation.getTargetStart());
        Assert.assertEquals(target + 1, relation.getTargetEnd());
    }

    @Test
    public void testSourceRangeOrderRejectsReturningBehindClosedInline() throws Exception {
        ConfigInputFormat config = new ConfigInputFormat("saxon-source-range-inline-barrier-test");
        config.setFileType(ConfigInputFormat.FileType.XML);
        config.setDocumentPath("//doc");
        ConfigAnnotatedField contents = wordField("contents", ".//body");
        contents.setWordPath("(.//w[. = 'A'], .//w[. = 'C'], .//w[. = 'B'])");
        contents.addInlineTag(new ConfigInlineTag(".//span", ""));
        config.addAnnotatedField(contents);

        assertInvalidSourceUnits(config,
                "<doc><body><span><w>A</w><w>B</w></span><w>C</w></body></doc>",
                "moves behind closed inline span");
    }

    @Test
    public void testSourceRangesRejectDuplicateWords() throws Exception {
        ConfigInputFormat config = new ConfigInputFormat("saxon-source-ranges-duplicate-word-test");
        config.setFileType(ConfigInputFormat.FileType.XML);
        config.setDocumentPath("//doc");
        ConfigAnnotatedField contents = wordField("contents", ".");
        contents.setWordPath("(.//w, .//w)");
        config.addAnnotatedField(contents);

        assertInvalidSourceUnits(config, "<doc><w>A</w></doc>", "same element more than once");
    }

    @Test
    public void testRepeatedContainerCannotDuplicateTokens() throws Exception {
        ConfigInputFormat config = new ConfigInputFormat("saxon-repeated-container-test");
        config.setFileType(ConfigInputFormat.FileType.XML);
        config.setDocumentPath("//doc");
        config.addAnnotatedField(wordField("contents", "(.//s, .//s)"));

        assertInvalidSourceUnits(config, "<doc><s><w>A</w></s></doc>", "same element more than once");
    }

    @Test
    public void testRepeatedEmptyContainerDoesNotAffectIndexedOutput() throws Exception {
        ConfigInputFormat config = new ConfigInputFormat("saxon-repeated-empty-container-test");
        config.setFileType(ConfigInputFormat.FileType.XML);
        config.setDocumentPath("//doc");
        config.addAnnotatedField(wordField("contents", "(.//s[1], .//s[1], .//s[2])"));
        indexSourceXml(config, "<doc><s/><s><w>A</w></s></doc>");

        try (BlackLabIndex index = BlackLab.open(indexDir)) {
            Assert.assertEquals(List.of("a", ""), annotationValues(index, "contents", "word"));
            Assert.assertEquals(List.of(" ", ""), punctuationValues(index, "contents"));
            int docId = index.metadata().metadataDocId() == 0 ? 1 : 0;
            Assert.assertEquals("<s><w>A</w></s>",
                    DocUtil.xmlContents(index, docId, index.mainAnnotatedField(), 0, 1, null));
        }
    }

    @Test
    public void testSourceRangesRejectDuplicateTokenIds() throws Exception {
        ConfigInputFormat config = new ConfigInputFormat("saxon-source-ranges-duplicate-token-id-test");
        config.setFileType(ConfigInputFormat.FileType.XML);
        config.setDocumentPath("//doc");
        ConfigAnnotatedField contents = wordField("contents", ".");
        contents.setTokenIdPath("@id");
        config.addAnnotatedField(contents);

        assertInvalidSourceUnits(config, "<doc><w id='same'>A</w><w id='same'>B</w></doc>",
                "Duplicate token ID 'same'");
    }

    @Test
    public void testSourceRangesDoNotResolveEmptyTokenIds() throws Exception {
        String formatName = "saxon-source-ranges-empty-token-id-test";
        ConfigInputFormat config = new ConfigInputFormat(formatName);
        config.setFileType(ConfigInputFormat.FileType.XML);
        config.setDocumentPath("//doc");
        ConfigAnnotatedField contents = wordField("contents", ".//body");
        contents.setTokenIdPath("@id");
        ConfigStandoffAnnotations ghost = new ConfigStandoffAnnotations(".//mark", "@start");
        ghost.setSpanEndPath("@end");
        ghost.setValue("ghost");
        contents.addStandoffAnnotation(ghost);
        config.addAnnotatedField(contents);
        DocumentFormats.add(config);

        String xml = "<doc><body><w id='start'>A</w><w>B</w>" +
                "<mark start='start' end=''/></body></doc>";
        try (BlackLabIndexWriter indexWriter = BlackLab.openForWriting(indexDir, true, formatName)) {
            indexWriter.metadata().setIndexFlag(SourceRangeEncoding.INDEX_FLAG, SourceRangeEncoding.VERSION);
            Indexer indexer = Indexer.create(indexWriter);
            indexer.index(FileReference.fromBytes("doc1", xml.getBytes(), null), null,
                    FileConverter.ExtraConverters.NONE);
            indexer.close();
        }

        try (BlackLabIndex index = BlackLab.open(indexDir)) {
            HitResults ghosts = index.search().find(new CompleteQuery(new TextPatternTags("ghost", null))).execute();
            Assert.assertEquals(0, ghosts.size());
        }
    }

    @Test
    public void testSourceRangesRejectAtomicWordResults() throws Exception {
        ConfigInputFormat config = new ConfigInputFormat("saxon-source-ranges-atomic-word-test");
        config.setFileType(ConfigInputFormat.FileType.XML);
        config.setDocumentPath("//doc");
        ConfigAnnotatedField contents = wordField("contents", ".");
        contents.setWordPath("(.//w, 1)");
        config.addAnnotatedField(contents);

        assertInvalidSourceUnits(config, "<doc><w>A</w></doc>", "must return nodes");
    }

    @Test
    public void testSourceRangesRejectAtomicContainerResults() throws Exception {
        ConfigInputFormat config = new ConfigInputFormat("saxon-source-ranges-atomic-container-test");
        config.setFileType(ConfigInputFormat.FileType.XML);
        config.setDocumentPath("//doc");
        config.addAnnotatedField(wordField("contents", "(.//s, 1)"));

        assertInvalidSourceUnits(config, "<doc><s><w>A</w></s></doc>", "must return nodes");
    }

    @Test
    public void testDynamicMetadataCannotUseSourceBookkeepingName() throws Exception {
        String formatName = "saxon-reserved-metadata-test";
        ConfigInputFormat config = new ConfigInputFormat(formatName);
        config.setFileType(ConfigInputFormat.FileType.XML);
        config.setDocumentPath("//doc");
        config.addAnnotatedField(punctuationField("contents", null, null));
        ConfigMetadataField dynamicField = new ConfigMetadataField();
        dynamicField.setForEachPath(".//meta");
        dynamicField.setNamePath("@name");
        dynamicField.setValuePath(".");
        ConfigMetadataBlock metadata = new ConfigMetadataBlock();
        metadata.addMetadataField(dynamicField);
        config.addMetadataBlock(metadata);
        DocumentFormats.add(config);

        try (BlackLabIndexWriter indexWriter = BlackLab.openForWriting(indexDir, true, formatName)) {
            Indexer indexer = Indexer.create(indexWriter);
            try {
                String xml = "<doc><meta name='contents#source_ranges'>value</meta><s><w>A</w></s></doc>";
                indexer.getDocIndexer().index(indexer, FileReference.fromBytes("doc1", xml.getBytes(), null));
                Assert.fail("Expected reserved dynamic metadata name to be rejected");
            } catch (InvalidInputFormatConfig e) {
                Assert.assertTrue(e.getMessage().contains("Metadata field name is reserved by BlackLab"));
            } finally {
                indexer.close();
            }
        }
    }

    @Test
    public void testParallelProjectionUsesTargetSourceCoordinates() throws Exception {
        ConfigInputFormat config = new ConfigInputFormat("saxon-parallel-source-projection-test");
        config.setFileType(ConfigInputFormat.FileType.XML);
        config.setDocumentPath("//doc");
        ConfigAnnotatedField source = wordField("contents__nl", ".//nl");
        ConfigAnnotatedField target = wordField("contents__de", ".//de");
        source.setTokenIdPath("@id");
        target.setTokenIdPath("@id");
        ConfigStandoffAnnotations alignment = new ConfigStandoffAnnotations(".//link", "@from");
        alignment.setTargetPath("@to");
        alignment.setTargetVersionPath("'de'");
        alignment.setRelationClass("al");
        alignment.setValue("word");
        source.addStandoffAnnotation(alignment);
        config.addAnnotatedField(source);
        config.addAnnotatedField(target);
        String xml = "<doc><nl><w id='n'>A</w><link from='n' to='d2'/></nl>" +
                "<de><w id='d1'>B</w><w id='d2'>C</w></de></doc>";
        indexSourceXml(config, xml);
        try (BlackLabIndex index = BlackLab.open(indexDir)) {
            var sourceField = index.annotatedField(source.getName());
            var targetField = index.annotatedField(target.getName());
            var aligned = new TextPatternRelationMatch(new TextPatternAnyToken(1, 1), List.of(
                    new RelationTarget(RelationOperatorInfo.fromOperator("=al::word=>de"),
                            new TextPatternAnyToken(1, 1), RelationInfo.SpanMode.SOURCE, "aligned")));
            Assert.assertEquals("Unprojected alignment", 1,
                    index.search(sourceField, false).find(new CompleteQuery(aligned)).execute().size());
            var projected = new TextPatternFunctionCall("rfield", List.of(aligned, TextPatternValue.fromObject("de")));
            HitResults results = index.search(sourceField, false).find(new CompleteQuery(projected)).execute();
            Assert.assertSame(targetField, results.field());
            Hits hits = results.getHits();
            Assert.assertSame(targetField, hits.field());
            Assert.assertEquals(1, hits.size());
            Assert.assertEquals(1, hits.get(0).start());
            Assert.assertEquals(2, hits.get(0).end());
            String content = DocUtil.xmlContents(index, hits.get(0).doc(), targetField, -1, -1, hits);
            Assert.assertEquals("<de><w id='d1'>B</w><hl index=\"0\" start=\"1\" end=\"2\"><w id='d2'>C</w></hl></de>", content);
        }
    }

    @Test
    public void testSourceStatusSharedAcrossParallelVersions() throws Exception {
        String formatName = "saxon-source-status-test";
        ConfigInputFormat config = new ConfigInputFormat(formatName);
        config.setFileType(ConfigInputFormat.FileType.XML);
        config.setDocumentPath("//doc");
        config.addAnnotatedField(punctuationField("contents__nl", null, null));
        config.addAnnotatedField(punctuationField("contents__de", null, null));
        DocumentFormats.add(config);

        String xml = "<doc><s><w>A</w></s></doc>";
        try (BlackLabIndexWriter indexWriter = BlackLab.openForWriting(indexDir, true, formatName)) {
            indexWriter.metadata().setIndexFlag(SourceRangeEncoding.INDEX_FLAG, SourceRangeEncoding.VERSION);
            Indexer indexer = Indexer.create(indexWriter);
            indexer.index(FileReference.fromBytes("doc1", xml.getBytes(), null), null,
                    FileConverter.ExtraConverters.NONE);
            indexer.close();
        }

        try (BlackLabIndex index = BlackLab.open(indexDir)) {
            int docId = index.metadata().metadataDocId() == 0 ? 1 : 0;
            String statusField = AnnotatedFieldNameUtil.sourceStatusField("contents__nl");
            int flags = index.luceneDoc(docId).getField(statusField).numericValue().intValue();
            Assert.assertEquals("contents#source_status", statusField);
            Assert.assertEquals(SourceRangeEncoding.DOC_FLAG_XML |
                    SourceRangeEncoding.DOC_FLAG_SOURCE_RANGE_VECTORS, flags);
            Assert.assertEquals(1, index.luceneDoc(docId).getFields(statusField).length);
            Assert.assertNull(index.luceneDoc(docId).getField("contents__nl#source_status"));
            Assert.assertNull(index.annotatedField("contents__nl").mainAnnotation().offsetsSensitivity());
            int sourceLength = "<s><w>A</w></s>".length();
            assertUnit(sourceUnits(index, docId, "contents__nl"), 0, 0, sourceLength, 1);
            assertUnit(sourceUnits(index, docId, "contents__de"), 0, 0, sourceLength, 1);
            int[] starts = { 0 };
            int[] ends = { 0 };
            DocUtil.characterOffsets(index, docId, index.annotatedField("contents__nl"), starts, ends, false);
            Assert.assertArrayEquals(new int[] { 3 }, starts);
            Assert.assertArrayEquals(new int[] { 11 }, ends);
        }
    }

    @Test
    public void testExplicitContainersWriteSourceUnits() throws Exception {
        String formatName = "saxon-source-units-test";
        ConfigInputFormat config = new ConfigInputFormat(formatName);
        config.setFileType(ConfigInputFormat.FileType.XML);
        config.setDocumentPath("//doc");
        config.addAnnotatedField(punctuationField("containers", null, null));
        config.addAnnotatedField(wordField("whole", "."));
        config.addAnnotatedField(wordField("words", null));
        config.addAnnotatedField(wordField("whole__nl", "."));
        config.addAnnotatedField(wordField("whole__de", null));
        DocumentFormats.add(config);

        String xml = "<doc><s/><s><w>A</w><w>B</w></s></doc>";
        try (BlackLabIndexWriter indexWriter = BlackLab.openForWriting(indexDir, true, formatName)) {
            indexWriter.metadata().setIndexFlag(SourceRangeEncoding.INDEX_FLAG, SourceRangeEncoding.VERSION);
            Indexer indexer = Indexer.create(indexWriter);
            indexer.index(FileReference.fromBytes("doc1", xml.getBytes(), null), null,
                    FileConverter.ExtraConverters.NONE);
            indexer.close();
        }

        try (BlackLabIndex index = BlackLab.open(indexDir)) {
            int docId = index.metadata().metadataDocId() == 0 ? 1 : 0;
            Assert.assertNull(index.luceneDoc(docId).getBinaryValue(
                    AnnotatedFieldNameUtil.sourceUnitsField("containers")));
            BytesRef units = sourceUnits(index, docId, "containers");
            Assert.assertNotNull(index.luceneDoc(docId, true).getBinaryValue(
                    AnnotatedFieldNameUtil.sourceUnitsField("containers")));
            assertUnit(units, 0, xml.indexOf("<s><w"), xml.indexOf("</s>") + 4, 2);
            Assert.assertEquals(1, SourceUnitCodec.unitCount(units));
            Assert.assertNull(sourceUnits(index, docId, "whole"));
            Assert.assertNull(sourceUnits(index, docId, "words"));
            Assert.assertNull(sourceUnits(index, docId, "whole__nl"));
            Assert.assertNull(sourceUnits(index, docId, "whole__de"));
            int[] endsOnly = { 0 };
            DocUtil.characterOffsets(index, docId, index.annotatedField("whole"), new int[0], endsOnly, false);
            Assert.assertArrayEquals(new int[] { xml.indexOf("</w>") + 4 }, endsOnly);
        }
    }

    @Test
    public void testSourceUnitsRejectContainersOutsideDocument() throws Exception {
        ConfigInputFormat config = new ConfigInputFormat("saxon-source-units-document-scope-test");
        config.setFileType(ConfigInputFormat.FileType.XML);
        config.setDocumentPath("//doc");
        config.addAnnotatedField(wordField("contents", "//s"));

        assertInvalidSourceUnits(config,
                "<root><doc><s><w>A</w></s></doc><doc><s><w>B</w></s></doc></root>",
                "outside document");
    }

    @Test
    public void testSourceUnitsRejectMultipleParallelContainers() throws Exception {
        ConfigInputFormat config = new ConfigInputFormat("saxon-source-units-parallel-container-test");
        config.setFileType(ConfigInputFormat.FileType.XML);
        config.setDocumentPath("//doc");
        config.addAnnotatedField(wordField("contents__nl", ".//s"));

        assertInvalidSourceUnits(config, "<doc><s><w>A</w></s><s><w>B</w></s></doc>",
                "must select exactly one container");
    }

    @Test
    public void testSourceUnitsRejectMissingParallelContainer() throws Exception {
        ConfigInputFormat config = new ConfigInputFormat("saxon-source-units-missing-parallel-container-test");
        config.setFileType(ConfigInputFormat.FileType.XML);
        config.setDocumentPath("//doc");
        config.addAnnotatedField(wordField("contents__nl", ".//missing"));

        assertInvalidSourceUnits(config, "<doc><s><w>A</w></s></doc>",
                "must select exactly one container");
    }

    @Test
    public void testSourceUnitsAllowOneEmptyParallelContainer() throws Exception {
        String formatName = "saxon-source-units-empty-parallel-container-test";
        ConfigInputFormat config = new ConfigInputFormat(formatName);
        config.setFileType(ConfigInputFormat.FileType.XML);
        config.setDocumentPath("//doc");
        config.addAnnotatedField(wordField("contents__nl", ".//s"));
        config.addAnnotatedField(wordField("words", null));
        DocumentFormats.add(config);

        try (BlackLabIndexWriter indexWriter = BlackLab.openForWriting(indexDir, true, formatName)) {
            indexWriter.metadata().setIndexFlag(SourceRangeEncoding.INDEX_FLAG, SourceRangeEncoding.VERSION);
            Indexer indexer = Indexer.create(indexWriter);
            indexer.index(FileReference.fromBytes("doc1", "<doc><s/></doc>".getBytes(), null), null,
                    FileConverter.ExtraConverters.NONE);
            indexer.close();
        }

        try (BlackLabIndex index = BlackLab.open(indexDir)) {
            int docId = index.metadata().metadataDocId() == 0 ? 1 : 0;
            BytesRef units = sourceUnits(index, docId, "contents__nl");
            Assert.assertNotNull(units);
            Assert.assertEquals(0, SourceUnitCodec.unitCount(units));
            Assert.assertNull(sourceUnits(index, docId, "words"));
        }
    }

    @Test
    public void testSourceUnitsRejectWordsOutsideContainer() throws Exception {
        ConfigInputFormat config = new ConfigInputFormat("saxon-source-units-word-scope-test");
        config.setFileType(ConfigInputFormat.FileType.XML);
        config.setDocumentPath("//doc");
        ConfigAnnotatedField contents = wordField("contents", ".//s[1]");
        contents.setWordPath("//w");
        config.addAnnotatedField(contents);

        assertInvalidSourceUnits(config, "<doc><s><w>A</w></s><s><w>B</w></s></doc>",
                "outside container");
    }

    @Test
    public void testSourceRangesRejectWordsOutsideDefaultDocumentContainer() throws Exception {
        ConfigInputFormat config = new ConfigInputFormat("saxon-source-ranges-default-scope-test");
        config.setFileType(ConfigInputFormat.FileType.XML);
        config.setDocumentPath("//doc");
        ConfigAnnotatedField contents = wordField("contents", null);
        contents.setWordPath("//w");
        config.addAnnotatedField(contents);

        assertInvalidSourceUnits(config,
                "<root><doc><w>A</w></doc><doc><w>B</w></doc></root>",
                "outside container");
    }

    @Test
    public void testLegacyAllowsEmptyEntityExpandedContainer() throws Exception {
        String formatName = "saxon-legacy-entity-container-test";
        ConfigInputFormat config = new ConfigInputFormat(formatName);
        config.setFileType(ConfigInputFormat.FileType.XML);
        config.setDocumentPath("//doc");
        config.addAnnotatedField(wordField("contents", ".//s"));
        DocumentFormats.add(config);

        String xml = "<!DOCTYPE doc [<!ENTITY e '<s/>'>]><doc>&e;</doc>";
        try (BlackLabIndexWriter indexWriter = BlackLab.openForWriting(indexDir, true, formatName)) {
            indexWriter.metadata().setIndexFlag(SourceRangeEncoding.INDEX_FLAG, "");
            Indexer indexer = Indexer.create(indexWriter);
            indexer.index(FileReference.fromBytes("doc1", xml.getBytes(), null), null,
                    FileConverter.ExtraConverters.NONE);
            indexer.close();
        }

        try (BlackLabIndex index = BlackLab.open(indexDir)) {
            Assert.assertEquals(2, index.reader().numDocs());
        }
    }

    private void assertInvalidSourceUnits(ConfigInputFormat config, String xml, String message) throws Exception {
        DocumentFormats.add(config);
        try (BlackLabIndexWriter indexWriter = BlackLab.openForWriting(indexDir, true, config.getName())) {
            indexWriter.metadata().setIndexFlag(SourceRangeEncoding.INDEX_FLAG, SourceRangeEncoding.VERSION);
            Indexer indexer = Indexer.create(indexWriter);
            try {
                indexer.getDocIndexer().index(indexer, FileReference.fromBytes("doc1", xml.getBytes(), null));
                Assert.fail("Expected invalid source-unit container selection");
            } catch (InvalidConfiguration e) {
                Assert.assertTrue(e.getMessage(), e.getMessage().contains(message));
            } finally {
                indexer.close();
            }
        }
    }

    private static void assertUnit(BytesRef units, int unit, int sourceStart, int sourceEnd, int tokenEnd) {
        int[] expected = { sourceStart, sourceEnd, tokenEnd };
        for (int component = 0; component < expected.length; component++)
            Assert.assertEquals(expected[component], SourceUnitCodec.value(units, unit, component));
    }

    private static BytesRef sourceUnits(BlackLabIndex index, int docId, String fieldName) throws Exception {
        String unitField = AnnotatedFieldNameUtil.sourceUnitsField(fieldName);
        return index.reader().storedFields().document(docId, Set.of(unitField)).getBinaryValue(unitField);
    }

    private static ConfigAnnotatedField wordField(String name, String containerPath) {
        ConfigAnnotatedField field = new ConfigAnnotatedField(name);
        if (containerPath != null)
            field.setContainerPath(containerPath);
        field.setWordPath(".//w");
        ConfigAnnotation word = new ConfigAnnotation();
        word.setName("word");
        word.setValuePath(".");
        field.addAnnotation(word);
        return field;
    }

    private static ConfigAnnotatedField punctuationField(String name, String before, String after) {
        ConfigAnnotatedField field = new ConfigAnnotatedField(name);
        field.setContainerPath(".//s");
        field.setWordPath(".//w");
        field.setPunctBeforePath(before);
        field.setPunctAfterLastWordPath(after);
        ConfigAnnotation word = new ConfigAnnotation();
        word.setName("word");
        word.setValuePath(".");
        field.addAnnotation(word);
        return field;
    }

    private static List<String> punctuationValues(BlackLabIndex index, String fieldName) {
        return annotationValues(index, fieldName, AnnotatedFieldNameUtil.PUNCTUATION_ANNOT_NAME);
    }

    private static List<String> annotationValues(BlackLabIndex index, String fieldName, String annotationName) {
        return annotationValues(index, 0, fieldName, annotationName);
    }

    private static List<String> annotationValues(BlackLabIndex index, int docId, String fieldName, String annotationName) {
        String luceneField = index.annotatedField(fieldName)
                .annotation(annotationName)
                .forwardIndexSensitivity().luceneField();
        var leaf = index.getLeafReaderContext(docId);
        var forwardIndex = FieldForwardIndex.get(leaf, luceneField);
        int localDocId = docId - leaf.docBase;
        int[] termIds = forwardIndex.retrievePart(localDocId, 0, (int) forwardIndex.docLength(localDocId));
        return Arrays.stream(termIds).mapToObj(forwardIndex.terms()::get).toList();
    }
}
