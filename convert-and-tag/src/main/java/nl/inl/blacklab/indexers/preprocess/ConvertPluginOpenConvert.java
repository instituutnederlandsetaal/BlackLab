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
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.jar.Manifest;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import nl.inl.blacklab.exceptions.PluginException;
import nl.inl.blacklab.index.Plugin;

public class ConvertPluginOpenConvert implements ConvertPlugin {

    static final Logger logger = LogManager.getLogger(ConvertPluginOpenConvert.class);

    private static final String PROP_JAR = "jarPath";

    private static final String VERSION = "0.2";

    private ClassLoader loader;

    private Class<?> clsOpenConvert;

    /** OpenConvert::getConverter() */
    private Method methodOpenConvertGetConverter;

    /** SimpleInputOutputProcess::handleStream() */
    private Method methodSimpleInputOutputProcessHandleStream;
    
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
            methodOpenConvertGetConverter = clsOpenConvert.getMethod("getConverter", String.class, String.class);

            Class<?> simpleInputOutputProcess = loader.loadClass("org.ivdnt.openconvert.filehandling.SimpleInputOutputProcess");
            methodSimpleInputOutputProcessHandleStream = simpleInputOutputProcess.getMethod("handleStream",
                    InputStream.class, Charset.class, OutputStream.class);
        } catch (ClassNotFoundException | NoSuchMethodException | SecurityException | MalformedURLException e) {
            throw new PluginException("Error loading the OpenConvert jar: " + e.getMessage(), e);
        }
    }

    @Override
    public void perform(InputStream is, Charset inputCharset, String inputFormat, OutputStream os)
            throws PluginException {
        // Set the ContextClassLoader to use the UrlClassLoader we pointed at the OpenConvert jar.
        // This is required because OpenConvert implicitly loads some dependencies through locators/providers (such as its xml transformers)
        // and these locators/providers sometimes prefer to use the ContextClassLoader, which may have been set by a servlet container or the like.
        // If those cases, the contextClassLoader does not have the jar we loaded on its classpath, and so it cannot find the correct classes.
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(loader);
        try {
            performImpl(is, inputCharset, inputFormat, os);
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    private void performImpl(InputStream in, Charset inputCharset, String inputFormat, OutputStream out)
            throws PluginException {
        try (PushbackInputStream pbIn = new PushbackInputStream(in, 251)) {
            // important to let openconvert know what we want to do
            inputFormat = getActualFormat(pbIn, inputFormat);
            if (!canConvert(pbIn, inputCharset, inputFormat))
                throw new PluginException("The OpenConvert plugin does not support conversion from '" + inputFormat
                        + "' to '" + getOutputFormat() + "'");

            Object openConvertInstance = clsOpenConvert.getConstructor().newInstance();
            Object simpleInputOutputProcessInstance = methodOpenConvertGetConverter.invoke(openConvertInstance,
                    getOutputFormat(), inputFormat);

            methodSimpleInputOutputProcessHandleStream.invoke(simpleInputOutputProcessInstance, pbIn, inputCharset, out);
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
    public Set<String> getInputFormats() {
        return Collections.unmodifiableSet(inputFormats);
    }

    @Override
    public String getOutputFormat() {
        return "tei";
    }

    @Override
    public boolean canConvert(PushbackInputStream is, Charset cs, String inputFormat) {
        return inputFormats.contains(getActualFormat(is, inputFormat));
    }

    private static String getActualFormat(PushbackInputStream is, String reportedFormat) {
        reportedFormat = reportedFormat.toLowerCase();
        if (reportedFormat.equals("xhtml"))
            return "html";
        if (reportedFormat.equals("xml") && isAlto(is)) {
            return "alto";
        }

        return reportedFormat;
    }

    private static boolean isAlto(PushbackInputStream i) {
        try {
            byte[] buffer = new byte[250];
            int bytesRead = i.read(buffer);
            String head = new String(buffer, StandardCharsets.US_ASCII).toLowerCase();
            i.unread(buffer, 0, bytesRead);
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
                logger.error("No Specification-Version found in referenced jarFile");
            else if (!version.equals(VERSION))
                throw new PluginException("Mismatched version! Expected " + VERSION + " but found " + version);
        } catch (IOException e) {
            throw new PluginException("Could not read manifest: " + e.getMessage(), e);
        }
    }
}
