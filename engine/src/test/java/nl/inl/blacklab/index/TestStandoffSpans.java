package nl.inl.blacklab.index;

import java.io.File;
import java.io.IOException;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import nl.inl.blacklab.exceptions.BlackLabException;
import nl.inl.blacklab.exceptions.DocumentFormatNotFound;
import nl.inl.blacklab.exceptions.ErrorOpeningIndex;
import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.BlackLab;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.BlackLabIndexWriter;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldNameUtil;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.AnnotationSensitivity;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.results.Hits;
import nl.inl.blacklab.searches.SearchEmpty;
import nl.inl.util.UtilsForTesting;

public class TestStandoffSpans {

    public static final String TEST_FORMAT_NAME = "tei-standoff-spans";

    public UtilsForTesting.TestDir testDir;
    public BlackLabIndex testIndex;

    @BeforeClass
    public static void beforeClass() throws IOException {
        InputFormatWithConfig inputFormat = new InputFormatWithConfig(TEST_FORMAT_NAME,
                getFile("standoff/tei-standoff-spans.blf.yaml"));
        DocumentFormats.add(inputFormat);
    }

    @Before
    public void before() {
        testDir = UtilsForTesting.createBlackLabTestDir("TestStandoffSpans");
        // Instantiate the BlackLab indexer, supplying our DocIndexer class
        try {
            BlackLabIndexWriter indexWriter = BlackLab.openForWriting(testDir.file(), true,
                    TEST_FORMAT_NAME);
            Indexer indexer = Indexer.create(indexWriter);
            indexer.setListener(new IndexListener() {
                @Override
                public boolean errorOccurred(Throwable e, String path, File f) {
                    System.err.println("Error while indexing. path=" + path + ", file=" + f);
                    e.printStackTrace();
                    return false; // don't continue
                }
            });
            try {
                indexer.index(getFile("standoff/test.xml"));
            } finally {
                // Finalize and close the index.
                indexer.close();
            }

            // Create the BlackLab index object
            testIndex = BlackLab.open(testDir.file());
        } catch (DocumentFormatNotFound | ErrorOpeningIndex e) {
            throw BlackLabException.wrapRuntime(e);
        }
    }

    private static File getFile(String path) {
        ClassLoader classLoader = TestStandoffSpans.class.getClassLoader();
        return new File(classLoader.getResource(path).getFile());
    }

    @Test
    public void testStandoffSpans() throws IOException, InvalidQuery {
        SearchEmpty s = testIndex.search();

        AnnotatedField field = testIndex.mainAnnotatedField();
        String relAnnotName = AnnotatedFieldNameUtil.RELATIONS_ANNOT_NAME;
        Annotation relAnnotation = field.annotation(relAnnotName);
        AnnotationSensitivity annotationSensitivity = relAnnotation.hasSensitivity(MatchSensitivity.SENSITIVE) ?
                relAnnotation.sensitivity(MatchSensitivity.SENSITIVE) :
                relAnnotation.sensitivity(MatchSensitivity.INSENSITIVE);
        BLSpanQuery query = testIndex.tagQuery(s.queryInfo(), annotationSensitivity, "character", null, null);
        Hits results = s.find(query).execute();
        Assert.assertEquals(2, results.size());
        Assert.assertEquals(0, results.get(0).start());
        Assert.assertEquals(2, results.get(0).end()); // FAILS, actually 3, but that's wrong
        Assert.assertEquals(3, results.get(1).start());
        Assert.assertEquals(5, results.get(1).end());
    }

    @After
    public void teardown() {
        if (testIndex != null)
            testIndex.close();
        if (testDir != null)
            testDir.close();
    }
}
