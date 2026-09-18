package nl.inl.blacklab.indexers.config;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import org.apache.lucene.index.Term;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import nl.inl.blacklab.forwardindex.FieldForwardIndex;
import nl.inl.blacklab.index.DocumentFormats;
import nl.inl.blacklab.index.Indexer;
import nl.inl.blacklab.plugins.FileConverter;
import nl.inl.blacklab.search.BlackLab;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.BlackLabIndexWriter;
import nl.inl.blacklab.search.DocUtil;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldNameUtil;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.BLSpanTermQuery;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.results.hitresults.HitResults;
import nl.inl.blacklab.search.results.hits.Hits;
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

        // Create a native-offset index, then reopen and append using explicit punctuation.
        try (BlackLabIndexWriter writer = BlackLab.openForWriting(indexDir, true, legacy.getName())) {
            writer.metadata().save();
        }
        String xml = "<doc><s><w p='['>A</w><w p=']'>B</w></s><s><w p='{'>C</w></s></doc>";
        for (int append = 0; append < 2; append++) {
            try (BlackLabIndexWriter writer = BlackLab.openForWriting(indexDir, false)) {
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
