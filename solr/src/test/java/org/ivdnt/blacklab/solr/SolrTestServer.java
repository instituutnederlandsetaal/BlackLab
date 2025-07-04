package org.ivdnt.blacklab.solr;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrRequest;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.embedded.EmbeddedSolrServer;
import org.apache.solr.client.solrj.request.CoreAdminRequest;
import org.apache.solr.client.solrj.request.GenericSolrRequest;
import org.apache.solr.client.solrj.request.RequestWriter;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.core.CoreContainer;
import org.apache.solr.core.NodeConfig;
import org.apache.solr.logging.LogWatcherConfig;
import org.apache.solr.logging.log4j2.Log4j2Watcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import nl.inl.blacklab.exceptions.InvalidIndex;

public class SolrTestServer {
    private static final String SOLR_DIR_NAME = "blacklab-test-solr";

    private static final Logger LOGGER = LoggerFactory.getLogger(SolrTestServer.class);

    private static EmbeddedSolrServer server;

    private static Path solrPath;

    private SolrTestServer() {
    }

    private static void copy(Path sourcePath, Path targetPath, String fileName) {
        copy(sourcePath, targetPath, fileName, fileName);
    }

    /**
     * Copy a file or directory tree.
     *
     * @param sourcePath where to find fileName
     * @param targetPath where to copy fileName
     * @param fileName file or dir to copy
     */
    private static void copy(Path sourcePath, Path targetPath, String fileName, String targetFileName) {
        Path srcFilePath = sourcePath.resolve(fileName);
        Path targetFilePath = targetPath.resolve(targetFileName);
        if (srcFilePath.toFile().isDirectory()) {
            // Directory; recursively copy it
            File srcDir = srcFilePath.toFile();
            File targetDir = targetFilePath.toFile();
            if (!targetDir.mkdir())
                throw new InvalidIndex("Cannot create dir: " + targetFilePath);
            File[] files = srcDir.listFiles();
            if (files != null) {
                for (File f: files) {
                    copy(srcFilePath, targetFilePath, f.getName());
                }
            }
        } else {
            try {
                // Regular file; copy it
                Files.copy(srcFilePath, targetFilePath);
            } catch (IOException e) {
                throw new InvalidIndex(e);
            }
        }
    }

    static boolean deleteDirectoryTree(File dir) {
        File[] allContents = dir.listFiles();
        if (allContents != null) {
            for (File file: allContents) {
                if (!deleteDirectoryTree(file))
                    return false;
            }
        }
        return dir.delete();
    }

    static void createEmbeddedServer(String defaultCoreName, Path resourcePath, Path existingIndexPath) {
        try {
            solrPath = Files.createTempDirectory(SOLR_DIR_NAME);
            copy(resourcePath, solrPath, "solr.xml");

            if (existingIndexPath != null)
                copy(existingIndexPath.getParent(), solrPath, existingIndexPath.toFile().getName(), defaultCoreName);

        NodeConfig config = new NodeConfig.NodeConfigBuilder("testNode", solrPath)
                .setLogWatcherConfig(new LogWatcherConfig(true,null,null,0))
                .build();
        CoreContainer container = new CoreContainer(config);
        container.load();

            server = new EmbeddedSolrServer(container, defaultCoreName);
        } catch (IOException e) {
            throw new InvalidIndex(e);
        }
    }

    /**
     * Add a search component via de API.
     *
     * ERROR: unknown handler: /test/config
     *
     * @param coreName core to add component to
     * @param compName component name
     * @param fqcn fully qualified class name for component
     */
    public static void addSearchComponent(String coreName, String compName, String fqcn) {
        try {
            String json =
                "{\n" +
                "  \"add-searchcomponent\": {\n" +
                "    \"name\": \""+ compName +"\",\n" +
                "    \"class\": \"" + fqcn + "\",\n" +
                "    \"xsltFile\": \"test.xslt\",\n" +
                "    \"inputField\": \"test1\",\n" +
                "  }\n" +
                "}\n";
            String path = "/" + coreName + "/config";
            GenericSolrRequest reqAddSearchComponent = new GenericSolrRequest(SolrRequest.METHOD.POST,
                    path, new ModifiableSolrParams());
            RequestWriter.ContentWriter contentWriter =
                    new RequestWriter.StringPayloadContentWriter(json, "application/json");
            reqAddSearchComponent.setContentWriter(contentWriter);
            // ERROR: unknown handler: /test/config
            // (but in JUnit we can just put the config in solrconfig.xml, so not needed right now)
            NamedList<Object> response = server.request(reqAddSearchComponent);
            //System.err.println("Add search component response\n" + response.toString());
        } catch (Exception e) {
            throw new InvalidIndex(e);
        }
    }

    static void setLogLevel(String level) throws SolrServerException, IOException {
//        ModifiableSolrParams params = new ModifiableSolrParams();
//        params.set("set", "root:" + level);
//        GenericSolrRequest reqSetLogLevel = new GenericSolrRequest(SolrRequest.METHOD.POST,
//                "/admin/info/logging", params);
//        NamedList<Object> response = server.request(reqSetLogLevel);
        //System.err.println("Log level response\n" + response.toString());
    }

    /**
     * Create core via the CoreAdmin API.
     *
     * @param coreName name for new core
     * @param confTemplatePath where template files can be found
     */
    static void createCore(String coreName, Path confTemplatePath) {
        String pathToSolrConfigXml = confTemplatePath.resolve("solrconfig.xml").toAbsolutePath().toString();
        String pathToSchemaXml = confTemplatePath.resolve("managed-schema").toAbsolutePath().toString();

        CoreAdminRequest.Create request = new CoreAdminRequest.Create();
        request.setCoreName(coreName);
        request.setConfigName(pathToSolrConfigXml);
        request.setSchemaName(pathToSchemaXml);

        try {
            System.setProperty("solr.allow.unsafe.resourceloading", "true"); // allow loading files from outside dirs
            server.request(request);
        } catch (Exception e) {
            throw new InvalidIndex("Error creating core " + coreName, e);
        }

        // Copy XSLT file used by our SearchComponent.
        // (we should probably put stuff like this in a configset instead, or use the API to add it later)
        //Path coreConfPath = server.getCoreContainer().getCore(coreName).getInstancePath().resolve("conf");
        //copy(confTemplatePath, coreConfPath, "test.xslt");
    }

    public static void close() {
        try {
            if (server != null)
                server.close();
            File f = SolrTestServer.solrPath.toFile();
            if (f.isDirectory() && f.getName().equals(SolrTestServer.SOLR_DIR_NAME) && f.canWrite()) {
                SolrTestServer.deleteDirectoryTree(f);
            }
        } catch (IOException e) {
            throw new InvalidIndex(e);
        }
    }

    static SolrClient client() {
        return server;
    }

}
