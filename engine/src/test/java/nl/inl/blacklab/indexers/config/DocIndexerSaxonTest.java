package nl.inl.blacklab.indexers.config;

import java.io.File;

import org.apache.lucene.index.Term;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import nl.inl.blacklab.index.DocumentFormats;
import nl.inl.blacklab.index.Indexer;
import nl.inl.blacklab.plugins.FileConverter;
import nl.inl.blacklab.search.BlackLab;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.BlackLabIndexWriter;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldNameUtil;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.BLSpanTermQuery;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.results.hitresults.HitResults;
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
}
