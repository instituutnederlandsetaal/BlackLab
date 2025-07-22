/**
 * Embedded Solr test.
 *
 * Inspired by code from Mtas, https://github.com/meertensinstituut/mtas/
 */

package org.ivdnt.blacklab.solr;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.util.Strings;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.util.BytesRef;
import org.apache.solr.client.solrj.SolrRequest;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.request.GenericSolrRequest;
import org.apache.solr.client.solrj.request.RequestWriter;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.common.util.NamedList;
import org.eclipse.collections.impl.list.mutable.primitive.IntArrayList;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import nl.inl.blacklab.analysis.DesensitizeFilter;
import nl.inl.blacklab.index.annotated.TokenStreamFromList;

public class TestIndexComponent {

    static final String CORE_NAME = "test";

    public static final String DOCUMENT_FORMAT = "voice-tei";
    public static final String[] INPUT_FILE_PATH = new String[] {"..", "test", "data", "input", "PBsve430.xml"};

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
    }


    @AfterClass
    public static void cleanUpClass() {
        SolrTestServer.close();
    }

    @Test
    public void testAddData() throws SolrServerException, IOException {
        File file = Paths.get("..", "test", "data", DOCUMENT_FORMAT + ".blf.yaml").toFile();
        String configFileContents = FileUtils.readFileToString(file, StandardCharsets.UTF_8);
        
        ModifiableSolrParams solrParams = new ModifiableSolrParams();
        solrParams.add(CommonParams.Q, "*:*");
        solrParams.add("bl", "true"); // activate our component
        solrParams.add("bl.format", configFileContents);
        solrParams.add("bl.filename", Strings.join(Arrays.asList(INPUT_FILE_PATH), File.separatorChar));

        String urlPath = "/update";
        GenericSolrRequest r = new GenericSolrRequest(SolrRequest.METHOD.POST, urlPath, solrParams);

        Path path = Paths.get("..", "test", "data", "input", "PBsve430.xml");
        path = path.toAbsolutePath();
        String content = Files.readString(path);
        r.setContentWriter(new RequestWriter.StringPayloadContentWriter(content, "application/xml"));

        NamedList<Object> response = SolrTestServer.client().request(r);
        System.err.println("Add file response\n" + response.toString());
        NamedList<Object> responseHeader = (NamedList<Object>) response.get("responseHeader");
        Assert.assertTrue("Response should contain 'status' field", responseHeader.get("status") != null);
    }

    /** A test of a weird case that somehow sometime broke the code during development */
    public void testPreAnalyzedField() throws IOException {
        List<BytesRef> payloads = Collections.nCopies(336, null);
        var values = List.of(" ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", "");
        var increments = new IntArrayList();
        for (int i : List.of(1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1)) {
            increments.add(i);
        }
        DesensitizeFilter tokenstream = new DesensitizeFilter(new TokenStreamFromList(values, increments, payloads), true, true);

        BLSolrPreAnalyzedFieldParser p = new BLSolrPreAnalyzedFieldParser();
        FieldType type = new FieldType();
        type.setTokenized(true);
        type.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS);


        Field field = new Field("", tokenstream, type); // fieldtype doesn't matter for this test I think

        String serialized = p.toFormattedString(field);
        Reader r = new StringReader(serialized);
        p.parse(r, new TokenStreamFromList(new ArrayList<>(), new IntArrayList(), new ArrayList<>()));
    }
}
