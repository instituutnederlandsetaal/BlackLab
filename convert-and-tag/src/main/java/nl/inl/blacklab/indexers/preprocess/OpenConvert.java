package nl.inl.blacklab.indexers.preprocess;

import java.io.ByteArrayOutputStream;
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
import java.util.Set;
import java.util.jar.Manifest;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import nl.inl.blacklab.exceptions.PluginException;
import nl.inl.blacklab.plugins.FileConverter;
import nl.inl.blacklab.plugins.param.PluginParams;
import nl.inl.util.fileprocessor.FileReference;

public class OpenConvert extends FileConverter {

    static final Logger logger = LogManager.getLogger(OpenConvert.class);

    private static final String PROP_JAR = "jarPath";

    private static final String VERSION = "0.2";

    private ClassLoader loader;

    private Class<?> clsOpenConvert;

    /** OpenConvert::getConverter() */
    private Method methodOpenConvertGetConverter;

    /** SimpleInputOutputProcess::handleStream() */
    private Method methodSimpleInputOutputProcessHandleStream;

    @Override
    public void initialize() throws PluginException {
        initJar();
    }

    private void initJar() throws PluginException {
        File jarFile = cfgFile(PROP_JAR, "OpenConvert.jar");
        try {
            URL jarUrl = jarFile.toURI().toURL();
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
    public FileReference perform(FileReference file, String inputFormat, PluginParams params) throws PluginException {
        // Set the ContextClassLoader to use the UrlClassLoader we pointed at the OpenConvert jar.
        // This is required because OpenConvert implicitly loads some dependencies through locators/providers (such as its xml transformers)
        // and these locators/providers sometimes prefer to use the ContextClassLoader, which may have been set by a servlet container or the like.
        // If those cases, the contextClassLoader does not have the jar we loaded on its classpath, and so it cannot find the correct classes.
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(loader);
        try (InputStream is = file.getSinglePassInputStream()) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            if (!performImpl(is, file.getCharSet(), inputFormat, output))
                throw new PluginException("Cannot convert input format " + inputFormat + " to " + getOutputFormat());
            return FileReference.fromBytesOverrideCharset(file.getPath(), output.toByteArray(),
                    file.getAssociatedFile(), file.getCharSet());
        } catch (IOException e) {
            throw new PluginException("Error converting file " + file.getPath() + ": " + e.getMessage(), e);
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    private boolean performImpl(InputStream in, Charset inputCharset, String inputFormat, OutputStream out)
            throws PluginException {
        try (PushbackInputStream pbIn = in instanceof PushbackInputStream ?
                (PushbackInputStream) in : new PushbackInputStream(in, 251)) {
            // important to let openconvert know what we want to do
            inputFormat = getActualFormat(pbIn, inputFormat);
            if (!canConvert(pbIn, inputCharset, inputFormat))
                return false;

            Object openConvertInstance = clsOpenConvert.getConstructor().newInstance();
            Object simpleInputOutputProcessInstance = methodOpenConvertGetConverter.invoke(openConvertInstance,
                    getOutputFormat(), inputFormat);

            methodSimpleInputOutputProcessHandleStream.invoke(simpleInputOutputProcessInstance, pbIn, inputCharset, out);
        } catch (ReflectiveOperationException | IllegalArgumentException | IOException | SecurityException e) {
            throw new PluginException("Exception while running OpenConvert: " + e.getMessage(), e);
        }
        return true;
    }

    private static final Set<String> inputFormats = new HashSet<>(
            Arrays.asList("doc", "docx", "txt", "epub", "html", "alto", "rtf", "odt")); // TODO (not supported in openconvert yet): pdf

    public String getOutputFormat() {
        return "tei";
    }


    /**
     * Can this converter convert this file?
     *
     * @param is stream containing a pushback buffer of at least 251 characters
     * @param cs (optional) charset of the inputstream, if this is a text
     *            (non-binary) file type
     * @return true if this file can be converted into this plugin's outputFormat
     */
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

    @Override
    public boolean isWebSafe() {
        return true;
    }
}
