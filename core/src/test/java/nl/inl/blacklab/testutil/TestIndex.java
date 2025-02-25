package nl.inl.blacklab.testutil;

import java.io.File;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.exceptions.DocumentFormatNotFound;
import nl.inl.blacklab.exceptions.ErrorOpeningIndex;
import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.index.IndexListener;
import nl.inl.blacklab.index.Indexer;
import nl.inl.blacklab.queryParser.corpusql.CorpusQueryLanguageParser;
import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.resultproperty.PropertyValue;
import nl.inl.blacklab.search.BlackLab;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.BlackLabIndex.IndexType;
import nl.inl.blacklab.search.BlackLabIndexWriter;
import nl.inl.blacklab.search.Kwic;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.results.ContextSize;
import nl.inl.blacklab.search.results.DocResults;
import nl.inl.blacklab.search.results.Hit;
import nl.inl.blacklab.search.results.Hits;
import nl.inl.blacklab.search.results.Kwics;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.util.UtilsForTesting;

public class TestIndex {

    /** Classic external index format */
    private static TestIndex testIndexExternal;

    /** Integrated index format */
    private static TestIndex testIndexIntegrated;

    /** External, pre-indexed (to test that we don't accidentally break file compatibility). */
    private static TestIndex testIndexExternalPre;

    public static TestIndex get(IndexType indexType) {
        return new TestIndex(false, indexType);
    }

    private synchronized static TestIndex getPreindexed(IndexType indexType) {
        if (indexType == IndexType.INTEGRATED)
            throw new UnsupportedOperationException("Integrated index still in development, no preindexed version!");
        if (testIndexExternalPre == null) {
            String strType = indexType == IndexType.EXTERNAL_FILES ? "external" : "integrated";
            try {
                File indexDir = new File(TestIndex.class.getResource("/test-index-" + strType).toURI());
                testIndexExternalPre = new TestIndex(indexDir);
            } catch (URISyntaxException e) {
                throw new RuntimeException(e);
            }
        }
        return testIndexExternalPre;
    }

    public synchronized static TestIndex getReusable(IndexType indexType) {
        if (testIndexExternal == null) {
            // Instantiate reusable testindexes
            testIndexExternal = new TestIndex(false, IndexType.EXTERNAL_FILES);
            testIndexIntegrated = new TestIndex(false, IndexType.INTEGRATED);
            // Make sure files are cleaned up at the end
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                testIndexExternal.close();
                testIndexIntegrated.close();
            }));
        }
        if (indexType == null)
            indexType = BlackLab.implicitInstance().getDefaultIndexType();
        return indexType == IndexType.EXTERNAL_FILES ? testIndexExternal : testIndexIntegrated;
    }

    public static TestIndex getWithTestDelete(IndexType indexType) {
        return new TestIndex(true, indexType);
    }

    public static Collection<TestIndex> typesForTests() {
        return List.of(
                getPreindexed(BlackLabIndex.IndexType.EXTERNAL_FILES),
                getReusable(BlackLabIndex.IndexType.EXTERNAL_FILES),
                getReusable(BlackLabIndex.IndexType.INTEGRATED)
        );
    }

    private static final class IndexListenerAbortOnError extends IndexListener {
        @Override
        public boolean errorOccurred(Throwable e, String path, File f) {
            // FileProcessor doesn't like when we re-throw the exception.
            //throw new BlackLabRuntimeException("Error in indexer, path=" + path + ", file=" + f, e);
            System.err.println("Error while indexing. path=" + path + ", file=" + f);
            e.printStackTrace();
            return false; // don't continue
        }
    }

    /**
     * Some test XML data to index.
     */
    public static final String[] TEST_DATA = {
            // Note that "The|DOH|ZZZ" will be indexed as multiple values at the same token position.
            // All values will be searchable in the reverse index, but only the first will be stored in the
            // forward index.
            "<doc pid='0' title='Pangram'><s test='1'><entity>"
                + "<w l='the'   p='art'>The|DOH|ZZZ</w> "
                + "<w l='quick' p='adj'>quick</w> "
                + "<w l='brown' p='adj'>brown</w> "
                + "<w l='fox'   p='nou'>fox</w></entity> "
                + "<w l='jump'  p='vrb' >jumps</w> "
                + "<w l='over'  p='pre' >over</w> "
                + "<entity><w l='the'   p='art' >the</w> "
                + "<w l='lazy'  p='adj'>lazy</w> "
                + "<w l='dog'   p='nou'>dog</w></entity>" + ".</s></doc>",

            // This doc contains no p annotations.
            // This is intentional, to test this case.
            // It is not the last doc, because we need to make
            // sure that doesn't mess up docs indexed after this one.
            "<doc pid='1' title='Learning words'> <w l='noot'>noot</w> "
                    + "<w l='mier'>mier</w> "
                    + "<w l='aap'>aap</w> "
                    + "<w l='mier'>mier</w> "
                    + "<w l='mier'>mier</w> "
                    + "<w l='mier'>mier</w> "
                    + "<w l='noot'>noot</w> "
                    + "<w l='noot'>noot</w> "
                    + "<w l='aap'>aap</w> "
                    + "<w l='aap'>aap</w> "
                    + "<w l='aap'>aap</w> "
                    + "<w l='aap'>aap</w> "
                    + "</doc>",

            "<doc pid='2' title='Star Wars'> <s test='2'><w l='may' p='vrb'>May</w> "
                    + "<entity><w l='the' p='art'>the</w> "
                    + "<w l='force' p='nou'>Force</w></entity> "
                    + "<w l='be' p='vrb'>be</w> "
                    + "<w l='with' p='pre'>with</w> "
                    + "<w l='you' p='pro'>you</w>" + ".</s></doc>",

            "<doc pid='3' title='Bastardized Shakespeare'> <s><w l='to' p='pre'>To</w> "
                    + "<w l='find' p='vrb'>find</w> "
                    + "(<w l='or' p='con'>or</w> "
                    + "<w l='be' p='adv'>not</w> "
                    + "<w l='to' p='pre'>to</w> "
                    + "<w l='find' p='vrb'>find</w>).</s>"
                    + "<s><w l='that' p='pro'>That</w> "
                    + "<w l='be' p='vrb'>is</w> "
                    + "<w l='the' p='art'>the</w> "
                    + "<w l='question' p='nou'>question</w>."
                    + "</s></doc>",
    };

    public static final int[] DOC_LENGTHS_TOKENS = { 9, 12, 6, 10 };

    final static String TEST_FORMAT_NAME = "testformat";

    /**
     * The BlackLab index object.
     */
    BlackLabIndex index;

    // Either indexDir is set (when directory supplied externally), or dir is set (when we create the dir ourselves)
    private final File indexDir;
    private final UtilsForTesting.TestDir dir;

    private final Annotation word;

    /** Open the index in this directory, does not delete the directory when closed */
    private TestIndex(File indexDir) {
        this.indexDir = indexDir;
        this.dir = null;
        try {
            index = BlackLab.open(indexDir);
            word = index.mainAnnotatedField().annotation("word");
        } catch (ErrorOpeningIndex e) {
            throw new RuntimeException(e);
        }
    }

    /** Create a temporary index, delete the directory when finished */
    private TestIndex(boolean testDelete, IndexType indexType) {
        // Get a temporary directory for our test index
        dir = UtilsForTesting.createBlackLabTestDir("TestIndex");
        indexDir = dir.file();

        // Instantiate the BlackLab indexer, supplying our DocIndexer class
        try {
            BlackLabIndexWriter indexWriter = BlackLab.openForWriting(indexDir, true, TEST_FORMAT_NAME, null, indexType);
            Indexer indexer = Indexer.create(indexWriter);
            indexer.setListener(new IndexListenerAbortOnError()); // throw on error
            try {
                // Index each of our test "documents".
                for (int i = 0; i < TEST_DATA.length; i++) {
                    indexer.index("test" + (i + 1), TEST_DATA[i].getBytes());
                }
                if (testDelete) {
                    // Delete the first doc, to test deletion.
                    // (close and re-open to be sure the document was written to disk first)
                    indexer.close();
                    indexWriter = BlackLab.openForWriting(indexDir, false, null, null, indexType);
                    indexer = Indexer.create(indexWriter);
                    String luceneField = indexer.indexWriter().metadata().annotatedField("contents").annotation("word").sensitivity(MatchSensitivity.INSENSITIVE).luceneField();
                    indexer.indexWriter().delete(new TermQuery(new Term(luceneField, "dog")));
                }
            } finally {
                // Finalize and close the index.
                indexer.close();
            }

            // Create the BlackLab index object
            index = BlackLab.open(indexDir);
            word = index.mainAnnotatedField().annotation("word");
        } catch (DocumentFormatNotFound | ErrorOpeningIndex e) {
            throw BlackLabRuntimeException.wrap(e);
        }
    }

    public IndexType getIndexType() {
        return index.getType();
    }

    public boolean isPreindexed() {
        return dir == null;
    }

    @Override
    public String toString() {
        return (dir != null ? "" : "PREINDEXED ") + getIndexType().toString();
    }

    public BlackLabIndex index() {
        return index;
    }

    public void close() {
        if (index != null)
            index.close();
        if (dir != null)
            dir.close();
    }

    /**
     * For a given document number (input docs), return the Lucene doc id.
     * <p>
     * May not be the same because of the document containing index metadata.
     *
     * @param docNumber document number.
     * @return Lucene doc id.
     */
    public int getDocIdForDocNumber(int docNumber) {
        DocResults r = index.queryDocuments(new TermQuery(new Term("pid", String.valueOf(docNumber))));
        return r.get(0).docId();
    }

    /**
     * Find concordances from a Corpus Query Language query.
     *
     * @param query the query to parse
     * @return the resulting BlackLab text pattern
     */
    public List<String> findConc(String query) {
        Hits hits = find(query, null);
        return getConcordances(hits, word);
    }

    /**
     * Find concordances from a Corpus Query Language query.
     *
     * @param query the query to parse
     * @param sortBy property to sort by
     * @return the resulting BlackLab text pattern
     */
    public List<String> findConc(String query, HitProperty sortBy) {
        Hits hits = find(query, null).sort(sortBy);
        return getConcordances(hits, word);
    }
    
    public List<String> findConc(String query, HitProperty prop, PropertyValue value) {
        Hits hits = find(query, null).filter(prop, value);
        return getConcordances(hits, word);
    }

    /**
     * Find concordances from a Corpus Query Language query.
     *
     * @param pattern CorpusQL pattern to find
     * @param filter how to filter the query
     * @return the resulting BlackLab text pattern
     */
    public List<String> findConc(String pattern, Query filter) {
        return getConcordances(find(pattern, filter), word);
    }

    /**
     * Find hits from a Corpus Query Language query.
     *
     * @param pattern CorpusQL pattern to find
     * @param filter how to filter the query
     * @return the resulting BlackLab text pattern
     */
    public Hits find(String pattern, Query filter) {
        try {
            return index.find(CorpusQueryLanguageParser.parse(pattern).toQuery(QueryInfo.create(index), filter), null);
        } catch (InvalidQuery e) {
            throw BlackLabRuntimeException.wrap(e);
        }
    }

    /**
     * Find hits from a Corpus Query Language query.
     *
     * @param pattern CorpusQL pattern to find
     * @return the resulting BlackLab text pattern
     */
    public Hits find(String pattern) {
        return find(pattern, null);
    }

    /**
     * Find hits from a Corpus Query Language query.
     *
     * @param query what to find
     * @return the resulting BlackLab text pattern
     */
    public List<String> findConc(BLSpanQuery query) {
        return getConcordances(index.find(query, null), word);
    }

    /**
     * Return a list of concordance strings.
     *
     * @param hits the hits to display
     * @return the left, match and right values for the "word" annotation
     */
    static List<String> getConcordances(Hits hits, Annotation word) {
        List<String> results = new ArrayList<>();
        Kwics kwics = hits.kwics(ContextSize.get(1, Integer.MAX_VALUE));
        for (Hit hit : hits) {
            Kwic kwic = kwics.get(hit);
            String left = StringUtils.join(kwic.before(word), " ");
            String match = StringUtils.join(kwic.match(word), " ");
            String right = StringUtils.join(kwic.after(word), " ");
            String conc = left + " [" + match + "] " + right;
            results.add(conc.trim());
        }
        return results;
    }

}
