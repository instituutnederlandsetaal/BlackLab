package nl.inl.blacklab.indexers.preprocess;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PushbackInputStream;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.jar.Manifest;

import org.apache.commons.io.FilenameUtils;

import nl.inl.blacklab.exceptions.PluginException;
import nl.inl.blacklab.index.Plugin;

public class ConvertPluginOpenConvert implements ConvertPlugin {
    private static final String PROP_JAR = "jarPath";

    private static final String VERSION = "0.2";

    private ClassLoader loader;

    private Class<?> clsOpenConvert;
    private Method methodOpenConvert_GetConverter;

    private Method SimpleInputOutputProcess_handleStream;
    
    @Override
    public boolean needsConfig() {
        return true;
    }

    @Override
    public void init(Map<String, String> config) throws PluginException {
        if (config == null)
            throw new PluginException("This plugin requires a configuration.");
        initJar(new File(Plugin.configStr(config, PROP_JAR)));
    }

    private void initJar(File jar) throws PluginException {
        if (!jar.exists())
            throw new PluginException("Could not find the openConvert jar at location " + jar);
        if (!jar.canRead())
            throw new PluginException("Could not read the openConvert jar at location " + jar);

        try {
            URL jarUrl = jar.toURI().toURL();
            loader = new URLClassLoader(new URL[] { jarUrl }, null);
            assertVersion(loader);

            clsOpenConvert = loader.loadClass("org.ivdnt.openconvert.converters.OpenConvert");
            methodOpenConvert_GetConverter = clsOpenConvert.getMethod("getConverter", String.class, String.class);

            Class<?> simpleInputOutputProcess = loader.loadClass("org.ivdnt.openconvert.filehandling.SimpleInputOutputProcess");
            SimpleInputOutputProcess_handleStream = simpleInputOutputProcess.getMethod("handleStream",
                    InputStream.class, Charset.class, OutputStream.class);
        } catch (ClassNotFoundException | NoSuchMethodException | SecurityException | MalformedURLException e) {
            throw new PluginException("Error loading the OpenConvert jar: " + e.getMessage(), e);
        }
    }

    @Override
    public void perform(InputStream is, Charset inputCharset, String fileName, OutputStream os)
            throws PluginException {
        // Set the ContextClassLoader to use the UrlClassLoader we pointed at the OpenConvert jar.
        // This is required because OpenConvert implicitly loads some dependencies through locators/providers (such as its xml transformers)
        // and these locators/providers sometimes prefer to use the ContextClassLoader, which may have been set by a servlet container or the like.
        // If those cases, the contextClassLoader does not have the jar we loaded on its classpath, and so it cannot find the correct classes.
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(loader);
        try {
            performImpl(is, inputCharset, fileName, os);
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    private void performImpl(InputStream in, Charset inputCharset, String fileName, OutputStream out)
            throws PluginException {
        try (PushbackInputStream pbIn = new PushbackInputStream(in, 251)) {
            // important to let openconvert know what we want to do
            if (!canConvert(pbIn, inputCharset, fileName))
                throw new PluginException("The OpenConvert plugin does not support conversion from '" + fileName
                        + "' to tei");

            Object openConvertInstance = clsOpenConvert.getConstructor().newInstance();
            Object simpleInputOutputProcessInstance = methodOpenConvert_GetConverter.invoke(openConvertInstance,
                    "tei", fileName);

            SimpleInputOutputProcess_handleStream.invoke(simpleInputOutputProcessInstance, pbIn, inputCharset, out);
        } catch (ReflectiveOperationException | IllegalArgumentException | IOException | SecurityException e) {
            throw new PluginException("Exception while running OpenConvert: " + e.getMessage(), e);
        }
    }

    @Override
    public String getId() {
        return "OpenConvert";
    }

    @Override
    public String getDisplayName() {
        return "OpenConvert";
    }

    @Override
    public String getDescription() {
        return "File converter using the OpenConvert library";
    }

    private static final Set<String> inputFormats = new HashSet<>(
            Arrays.asList("doc", "docx", "txt", "epub", "html", "alto", "rtf", "odt")); // TODO (not supported in openconvert yet): pdf

    @Override
    public String getOutputFileName(String inputFileName) {
        return FilenameUtils.removeExtension(inputFileName).concat(".tei.xml");
    }

    public Charset getOutputCharset() {
        // openconvert always outputs in utf8
        return StandardCharsets.UTF_8;
    }

    @Override
    public boolean canConvert(InputStream is, Charset cs, String fileName) {
        return inputFormats.contains(getActualFormat(is, cs, fileName));
    }

    private static String getActualFormat(InputStream is, Charset cs, String fileName) {
        String extension = FilenameUtils.getExtension(fileName).toLowerCase();
        if (extension.equals("xhtml"))
            return "html";
        if (extension.equals("xml") && isAlto(is, cs)) {
            return "alto";
        }

        return extension;
    }

    private static boolean isAlto(InputStream i, Charset cs) {
        try {
            byte[] buffer = new byte[250];
            int bytesRead = i.read(buffer);
            String head = new String(buffer, 0, bytesRead, cs != null ? cs : StandardCharsets.UTF_8).toLowerCase();
            return head.contains("<alto");
        } catch (IOException e) {
            return false;
        }
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
