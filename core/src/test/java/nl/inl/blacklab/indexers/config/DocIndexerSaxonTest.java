package nl.inl.blacklab.indexers.config;

import java.io.File;
import java.io.Reader;
import java.util.Map;

import net.sf.saxon.s9api.XdmValue;
import nl.inl.util.FileReference;
import nl.inl.util.UtilsForTesting;
import nl.inl.blacklab.search.BlackLab;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.BlackLabIndexWriter;
import nl.inl.blacklab.index.DocWriter;
import nl.inl.blacklab.index.DocumentFormats;
import nl.inl.blacklab.index.Indexer;
import nl.inl.blacklab.queryParser.corpusql.CorpusQueryLanguageParser;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.results.Hits;
import nl.inl.util.TextContent;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class DocIndexerSaxonTest {

    private UtilsForTesting.TestDir testDir;
    private File indexDir;

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
	 * Test that indexing with saxon allow xpaths that result in strings at the for-each and containerPath level.
	 * This allows various optimizations when one string in the document results in multiple annotations. 
	 * E.g. by pre-splitting a string using containerPath, then processing each split using forEachPath
	 */
    @Test
    public void testSaxonTokenizer() throws Exception {
        // 1. Create ConfigInputFormat
        ConfigInputFormat config = new ConfigInputFormat("saxon-test");
        config.setFileType(ConfigInputFormat.FileType.XML);
        config.addFileTypeOption("processor", "saxon");
        config.setDocumentPath("//doc");

        ConfigAnnotatedField contents = new ConfigAnnotatedField("contents");
        contents.setWordPath(".//w");
        
        ConfigAnnotation pos = new ConfigAnnotation();
        pos.setName("pos");
        pos.setBasePath("@pos");
        pos.setValuePath("tokenize(., '\\+')");
        
        ConfigAnnotation head = new ConfigAnnotation();
        head.setName("head");
        // Note: using ! for map operator in XPath 3.0+ (Saxon supports this)
        head.setValuePath("tokenize(., '\\+')!substring-before(., '(')"); 
        pos.addSubAnnotation(head);
        
        contents.addAnnotation(pos);
        config.addAnnotatedField(contents);
        
        DocumentFormats.add(config);

        // 2. Index a document
        try (BlackLabIndexWriter indexWriter = BlackLab.openForWriting(indexDir, true, "saxon-test", null, BlackLabIndex.IndexType.INTEGRATED)) {
            Indexer indexer = Indexer.create(indexWriter);
            String xml = "<doc><w pos='ADP(type=pre)+PD(type=d-p,subtype=art,position=prenom)'>word</w></doc>";
            indexer.index("doc1", xml.getBytes());
            indexer.close();
        }

        // 3. Verify
        try (BlackLabIndex index = BlackLab.open(indexDir)) {
            QueryInfo qi = QueryInfo.create(index);
            
            // Search for the first part
            BLSpanQuery q = CorpusQueryLanguageParser.parse("[pos='ADP\\(type=pre\\)']").toQuery(qi);
            Hits hits = index.find(q, null);
            Assert.assertEquals("Should find the first part of the split pos", 1, hits.size());
            
            // Search for the second part
            q = CorpusQueryLanguageParser.parse("[pos='PD\\(type=d-p,subtype=art,position=prenom\\)']").toQuery(qi);
            hits = index.find(q, null);
            Assert.assertEquals("Should find the second part of the split pos", 1, hits.size());
            
            // Search for head
            q = CorpusQueryLanguageParser.parse("[head='ADP']").toQuery(qi);
            hits = index.find(q, null);
            Assert.assertEquals("Should find head ADP", 1, hits.size());
            
            q = CorpusQueryLanguageParser.parse("[head='PD']").toQuery(qi);
            hits = index.find(q, null);
            Assert.assertEquals("Should find head PD", 1, hits.size());
        }
    }
}
