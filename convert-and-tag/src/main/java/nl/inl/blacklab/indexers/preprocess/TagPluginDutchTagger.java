package nl.inl.blacklab.indexers.preprocess;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import java.util.jar.Manifest;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;

import nl.inl.blacklab.exceptions.PluginException;
import nl.inl.blacklab.index.Plugin;

public class TagPluginDutchTagger implements TagPlugin {
    private static final String PROP_JAR = "jarPath";
    private static final String PROP_VECTORS = "vectorFile";
    private static final String PROP_MODEL = "modelFile";
    private static final String PROP_LEXICON = "lexiconFile";

    private static final String VERSION = "0.2";
    private ClassLoader loader;

    /** The object doing the actual conversion */
    private Object converter = null;

    private Method handleFile;
    
    @Override
    public boolean needsConfig() {
        return true;
    }

    @Override
    public void init(Map<String, String> config) throws PluginException {
        if (config == null)
            throw new PluginException("This plugin requires a configuration.");

        Properties converterProps = new Properties();
        converterProps.setProperty("word2vecFile", Plugin.configStr(config, PROP_VECTORS));
        converterProps.setProperty("taggingModel", Plugin.configStr(config, PROP_MODEL));
        converterProps.setProperty("lexiconPath", Plugin.configStr(config, PROP_LEXICON));
        converterProps.setProperty("tokenize", "true");

        File jar = new File(Plugin.configStr(config, PROP_JAR));
        initJar(jar, converterProps);
    }

    private void initJar(File jar, Properties converterProps)
            throws PluginException {
        try {
            if (!jar.exists())
                throw new PluginException("Could not find the dutchTagger jar at location " + jar);
            if (!jar.canRead())
            throw new PluginException("Could not read the dutchTagger jar at location " + jar);
            URL jarUrl = jar.toURI().toURL();
            loader = new URLClassLoader(new URL[] { jarUrl }, null);
            assertVersion(loader);


            Class<?> converterClass = loader.loadClass("nl.namescape.tagging.ImpactTaggerLemmatizerClient");
            Method setProperties = converterClass.getMethod("setProperties", Properties.class);
            handleFile = converterClass.getMethod("handleFile", String.class, String.class);

            converter = converterClass.getConstructor().newInstance();
            setProperties.invoke(converter, converterProps);
        } catch (MalformedURLException | ClassNotFoundException | NoSuchMethodException | SecurityException
                | InstantiationException | IllegalAccessException | IllegalArgumentException
                | InvocationTargetException e) {
            throw new PluginException("Error initializing DutchTaggerLemmatizer plugin", e);
        }
    }

    @Override
    public void perform(InputStream is, Charset cs, String fileName, OutputStream os) throws PluginException {
        // Set the ContextClassLoader to use the UrlClassLoader we pointed at the OpenConvert jar.
        // This is required because OpenConvert implicitly loads some dependencies through locators/providers (such as its xml transformers)
        // and these locators/providers sometimes prefer to use the ContextClassLoader, which may have been set by a servlet container or the like.
        // If those cases, the contextClassLoader does not have the jar we loaded on its classpath, and so it cannot find the correct classes.
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(loader);
        try {
            performImpl(is, cs, os);
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    private synchronized void performImpl(InputStream is, Charset cs, OutputStream os) throws PluginException {
        Path tmpInput = null;
        Path tmpOutput = null;
        try {
            tmpInput = Files.createTempFile("", ".xml");
            tmpOutput = Files.createTempFile("", ".xml");

            // Use this, as the tagger is a little dumb and doesn't allow us to specify a charset
            try (FileOutputStream fos = new FileOutputStream(tmpInput.toFile())) {
                IOUtils.copy(is, fos);
            }

            handleFile.invoke(converter, tmpInput.toString(), tmpOutput.toString());

            try (FileInputStream fis = new FileInputStream(tmpOutput.toFile())) {
                IOUtils.copy(fis, os);
            }
        } catch (Exception e) {
            throw new PluginException("Could not tag file: " + e.getMessage(), e);
        } finally {
            if (tmpInput != null)
                FileUtils.deleteQuietly(tmpInput.toFile());
            if (tmpOutput != null)
                FileUtils.deleteQuietly(tmpOutput.toFile());
        }
    }

    @Override
    public String getOutputFileName(String inputFileName) {
        return FilenameUtils.removeExtension(inputFileName).concat(".xml");
    }

    public Charset getOutputCharset() {
        // tagger always outputs in utf8
        return StandardCharsets.UTF_8;
    }

    @Override
    public String getId() {
        return "DutchTagger";
    }

    @Override
    public String getDisplayName() {
        return "DutchTagger";
    }

    @Override
    public String getDescription() {
        return "Tags dutch text in the TEI format";
    }

    public boolean canConvert(InputStream is, Charset cs, String filename) {
        if (filename.toLowerCase().endsWith(".xml")) {
            try {
                byte[] buffer = new byte[250];
                int bytesRead = is.read(buffer);
                String content = new String(buffer, 0, bytesRead, cs != null ? cs : StandardCharsets.UTF_8);
                return content.contains("<tei");
            } catch (IOException e) {
                return false;
            }
        }
        return false;
    }

    /**
     * Ensure that the maven artifact version matches VERSION
     *
     */
    private static void assertVersion(ClassLoader loader) throws PluginException {
        try (InputStream is = loader.getResourceAsStream("META-INF/MANIFEST.MF")) {
            Manifest manifest = new Manifest(is);
            String version = manifest.getMainAttributes().getValue("Specification-Version");
            if (version == null)
                System.err.println ("No Specification-Version found in referenced jarFile");
            else if (!version.equals(VERSION))
                throw new PluginException("Mismatched version! Expected " + VERSION + " but found " + version);
        } catch (IOException e) {
            throw new PluginException("Could not read manifest: " + e.getMessage(), e);
        }
    }
}
