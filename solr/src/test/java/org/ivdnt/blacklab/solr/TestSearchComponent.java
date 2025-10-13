/**
 * Embedded Solr test.
 *
 * Inspired by code from Mtas, https://github.com/meertensinstituut/mtas/
 */

package org.ivdnt.blacklab.solr;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.common.util.NamedList;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import nl.inl.blacklab.Constants;

@Ignore // FIXME adding a document to existing index fails with "cannot change field "pid" from doc values type=SORTED_SET to inconsistent doc values type=NONE"
public class TestSearchComponent {

    static final String CORE_NAME = "test";

    public static final String FIELD_TEST1 = "title";

    public static final String FIELD_TEST2 = "subtitle";

    @BeforeClass
    public static void prepareClass() throws Exception {
        final boolean USE_EXISTING_INDEX = true;

        Path resourcePath = Paths.get("src", "test", "resources", "solrDir");
        Path existingIndexPath = USE_EXISTING_INDEX ? Paths.get("src", "test", "resources", "existing-index") : null;

        // Create srver, core and add document
        SolrTestServer.createEmbeddedServer(CORE_NAME, resourcePath, existingIndexPath);
        SolrTestServer.setLogLevel("WARN"); // show log messages
        if (!USE_EXISTING_INDEX)
            SolrTestServer.createCore(CORE_NAME, resourcePath.resolve("conf"));
        SolrClient client = SolrTestServer.client();
        client.add(CORE_NAME, testDoc());
        client.commit(CORE_NAME);

        // (component is already added in solrconfig.xml, so this call is not needed,
        //  and the method unfortunately doesn't work yet anyway, see comment. we'll look at it later)
        //SolrTestServer.addSearchComponent(CORE_NAME, "apply-xslt", ApplyXsltComponent.class.getCanonicalName());
    }

    private static SolrInputDocument testDoc() {
        SolrInputDocument testDoc = new SolrInputDocument();
        testDoc.addField("pid", "THIS-IS-A-TEST-0002");
        testDoc.addField(FIELD_TEST1, "The quick brown fox");
        testDoc.addField(FIELD_TEST2, "woohoo");
        return testDoc;
    }

    @AfterClass
    public static void cleanUpClass() {
        SolrTestServer.close();
    }

    @Test
    public void testDisableBlackLab() throws SolrServerException, IOException {
        ModifiableSolrParams solrParams = new ModifiableSolrParams();
        solrParams.add(CommonParams.Q, "*:*");
        QueryResponse queryResponse = SolrTestServer.client().query(CORE_NAME, solrParams);
        Assert.assertNull(queryResponse.getResponse().get(Constants.SOLR_BLACKLAB_SECTION_NAME));
    }

    @Test
    public void testEnableBlackLabButNoOps() throws SolrServerException, IOException {
        ModifiableSolrParams solrParams = new ModifiableSolrParams();
        solrParams.add(CommonParams.Q, "*:*");
        solrParams.add("bl.op", "none"); // activate component and set operation
        QueryResponse queryResponse = SolrTestServer.client().query(CORE_NAME, solrParams);
        Assert.assertNull(queryResponse.getResponse().get(Constants.SOLR_BLACKLAB_SECTION_NAME));
    }

    @Test
    public void testSearch() throws SolrServerException, IOException {
        ModifiableSolrParams solrParams = new ModifiableSolrParams();
        solrParams.add(CommonParams.Q, "*:*");
        solrParams.add(CommonParams.FL, "fromInputFile,pid,title");
        solrParams.add("bl.op", "hits"); // activate component and set operation
        solrParams.add("bl.patt", "\"the\""); // activate our component
        solrParams.add("bl.number", "100"); // max. number of results to get

        //System.err.println(CORE_NAME);
        QueryResponse queryResponse = SolrTestServer.client().query(CORE_NAME, solrParams);

        NamedList<Object> blacklab = (NamedList<Object>)queryResponse.getResponse().get(Constants.SOLR_BLACKLAB_SECTION_NAME);
        List<NamedList<Object>> hits = (List<NamedList<Object>>) blacklab.get("hits");
        Assert.assertEquals(21, hits.size());
    }
}
