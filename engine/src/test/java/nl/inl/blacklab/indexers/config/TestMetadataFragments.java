package nl.inl.blacklab.indexers.config;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.TermQuery;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import nl.inl.blacklab.exceptions.ErrorIndexingFile;
import nl.inl.blacklab.exceptions.InvalidInputFormatConfig;
import nl.inl.blacklab.index.BLInputDocument;
import nl.inl.blacklab.index.DocumentFormats;
import nl.inl.blacklab.index.IndexListener;
import nl.inl.blacklab.index.Indexer;
import nl.inl.blacklab.plugins.FileConverter;
import nl.inl.blacklab.search.BlackLab;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.BlackLabIndexWriter;
import nl.inl.blacklab.search.results.hits.Hits;
import nl.inl.blacklab.search.textpattern.CompleteQuery;
import nl.inl.blacklab.search.textpattern.TextPatternAnyToken;
import nl.inl.util.UtilsForTesting;
import nl.inl.util.fileprocessor.FileReference;

public class TestMetadataFragments {

    private static final String PID_CONFIG = """
            corpusConfig:
              specialFields:
                pidField: id
            """;

    private static final String FORMAT = """
            documentPath: //doc
            annotatedFields:
              contents:
                containerPath: .
                wordPath: .//w
                annotations:
                  - name: word
                    valuePath: .
                inlineTags:
                  - path: .//frag
                    type: fragment
            metadata:
              fields:
                - name: id
                  valuePath: "@id"
                  type: untokenized
                  fragments: separate
                - name: author
                  valuePath: "@author"
                - name: title
                  valuePath: "@title"
                  fragments: docvalue

                - name: scope
                  valuePath: "@scope"
            """ + PID_CONFIG;

    private UtilsForTesting.TestDir testDir;
    private File indexDir;

    @BeforeClass
    public static void initializeBlackLab() {
        BlackLab.implicitInstance();
    }

    @Before
    public void setUp() {
        testDir = UtilsForTesting.createBlackLabTestDir("TestMetadataFragments");
        indexDir = testDir.file();
    }

    @After
    public void tearDown() {
        testDir.close();
    }

    @Test
    public void metadataFieldsUseConfiguredFragmentBehaviour() throws Exception {
        index("fragment-metadata-behaviour", """
                <doc id="document-id" author="document-author" title="document-title">
                  <frag id="fragment-id" author="fragment-author" title="ignored-fragment-title">
                    <w>A</w>
                  </frag>
                  <w>B</w>
                </doc>
                """);
        try (BlackLabIndex index = BlackLab.open(indexDir)) {
            int docId = index.getDocIdFromPid("document-id");
            Assert.assertTrue(docId >= 0);

            // separate: document and fragment IDs are independent. A document ID still filters the whole document;
            // a fragment ID filters only that fragment. Fragment matches are returned as their parent document.
            Assert.assertEquals("document-id", index.luceneDoc(docId).get("id"));
            assertNoFragmentHasMetadata(index, "id", "document-id");
            assertSomeFragmentHasMetadata(index, "id", "fragment-id");
            assertFilteredTokens(index, "id", "document-id", docId, 0, 1);
            assertFilteredTokens(index, "id", "fragment-id", docId, 0);
            Assert.assertEquals(1, index.queryDocuments(new TermQuery(new Term("id", "fragment-id"))).size());

            // default: a fragment value overrides the document's root metadata for its range. 
            // The document's root metadata value for the field is indexed not on the document itself, but instead on all implicit fragments. 
            // (all parts of the document that are not otherwise in fragments become implicit fragments).
            Assert.assertNull(index.luceneDoc(docId).get("author"));
            assertSomeFragmentHasMetadata(index, "author", "document-author");
            assertFilteredTokens(index, "author", "fragment-author", docId, 0);
            assertFilteredTokens(index, "author", "document-author", docId, 1);

            // docvalue: the fragment's own value is ignored and the document value applies everywhere.
            assertNoFragmentHasMetadata(index, "title", "ignored-fragment-title");
            assertSomeFragmentHasMetadata(index, "title", "document-title");
            assertFilteredTokens(index, "title", "document-title", docId, 0, 1);
        }
    }

    @Test
    public void emptyFragmentDoesNotApplyMetadataToFollowingTokens() throws Exception {
        index("fragment-empty", "<doc id='d'><w>A</w><frag scope='empty'/><w>B</w>" +
                "<frag scope='later'><w>C</w></frag></doc>");
        try (BlackLabIndex index = BlackLab.open(indexDir)) {
            int docId = index.getDocIdFromPid("d");
            assertFilteredTokens(index, "scope", "empty", docId);
            assertFilteredTokens(index, "scope", "later", docId, 2);
        }
    }

    @Test
    public void fragmentMetadataDoesNotLeakBetweenDocumentsInOneFile() throws Exception {
        index("fragment-multiple-documents", "<docs><doc id='first'><frag scope='fragment'><w>A</w></frag></doc>" +
                "<doc id='second' scope='document'><w>B</w></doc></docs>");
        try (BlackLabIndex index = BlackLab.open(indexDir)) {
            assertFilteredTokens(index, "scope", "fragment", index.getDocIdFromPid("first"), 0);
            int secondDoc = index.getDocIdFromPid("second");
            assertFilteredTokens(index, "scope", "document", secondDoc, 0);
            // Its predecessor's fragment settings must not suppress this document-level value.
            Assert.assertEquals("document", index.luceneDoc(secondDoc).get("scope"));
        }
    }

    @Test
    public void fragmentsRequirePidFieldConfiguration() throws Exception {
        Throwable error = indexExpectingError("fragment-without-pid-field", formatWithoutPidConfig(),
                "<doc id='d'><frag><w>A</w></frag></doc>");
        assertErrorCause(error, InvalidInputFormatConfig.class);
    }

    @Test
    public void fragmentsRequireDocumentPidValue() throws Exception {
        Throwable error = indexExpectingError("fragment-without-pid", FORMAT,
                "<doc><frag><w>A</w></frag></doc>");
        assertErrorCause(error, ErrorIndexingFile.class);
    }

    private void index(String formatName, String xml) throws Exception {
        Throwable error = index(formatName, FORMAT, xml);
        if (error instanceof Exception exc) {
            throw exc;
        }
        Assert.assertNull("Expected indexing to succeed, got: " + error, error);
    }

    private Throwable index(String formatName, String formatContents, String xml) throws Exception {
        AtomicReference<Throwable> error = new AtomicReference<>();
        ConfigInputFormat config = ConfigInputFormat.read(formatContents, false, formatName, null);
        DocumentFormats.add(config);
        try (BlackLabIndexWriter writer = BlackLab.openForWriting(indexDir, true, config.getName())) {
            Indexer indexer = Indexer.create(writer);
            indexer.setListener(new IndexListener() {
                @Override
                public boolean errorOccurred(Throwable e, String path, File file) {
                    error.compareAndSet(null, e);
                    return false;
                }
            });
            try {
                indexer.index(FileReference.fromBytes("source.xml", xml.getBytes(StandardCharsets.UTF_8), null),
                        null, FileConverter.ExtraConverters.NONE);
            } finally {
                indexer.close();
            }
        }
        return error.get();
    }

    private Throwable indexExpectingError(String formatName, String formatContents, String xml) throws Exception {
        Throwable error = index(formatName, formatContents, xml);
        Assert.assertNotNull("Expected indexing to fail", error);
        return error;
    }

    private static String formatWithoutPidConfig() {
        Assert.assertEquals("Expected PID config to occur once, at the end of the format",
                FORMAT.length() - PID_CONFIG.length(), FORMAT.indexOf(PID_CONFIG));
        return FORMAT.replace(PID_CONFIG, "");
    }

    private static void assertErrorCause(Throwable error, Class<? extends Throwable> expected) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (expected.isInstance(cause))
                return;
        }
        Assert.fail("Expected " + expected.getSimpleName() + " in error chain, got: " + error);
    }

    private static void assertNoFragmentHasMetadata(BlackLabIndex index, String field, String value) throws Exception {
        BooleanQuery fragmentsWithValue = new BooleanQuery.Builder()
                .add(new TermQuery(new Term(field, value)), BooleanClause.Occur.MUST)
                .add(BLInputDocument.docTypeQuery(BLInputDocument.DocType.FRAGMENT), BooleanClause.Occur.FILTER)
                .build();
        Assert.assertEquals(0, index.searcher().count(fragmentsWithValue));
    }

    private static void assertSomeFragmentHasMetadata(BlackLabIndex index, String field, String value) throws Exception {
        BooleanQuery fragmentsWithValue = new BooleanQuery.Builder()
                .add(new TermQuery(new Term(field, value)), BooleanClause.Occur.MUST)
                .add(BLInputDocument.docTypeQuery(BLInputDocument.DocType.FRAGMENT), BooleanClause.Occur.FILTER)
                .build();
        Assert.assertTrue(index.searcher().count(fragmentsWithValue) > 0);
    }

    private static void assertFilteredTokens(BlackLabIndex index, String field, String value, int docId,
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
}
