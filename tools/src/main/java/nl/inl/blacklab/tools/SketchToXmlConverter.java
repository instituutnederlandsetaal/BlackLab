package nl.inl.blacklab.tools;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;

import org.apache.commons.text.StringEscapeUtils;

import nl.inl.blacklab.index.Indexer;
import nl.inl.util.FileUtil;

/**
 * Convert the ANW Corpus Sketch format files to almost-identical XML files.
 * Using XML is useful because it allows us to later easily display it using
 * XSLT.
 */
public class SketchToXmlConverter {
    public static void main(String[] args) throws IOException {
        // Read property file
        Properties properties = getPropertiesFromResource("anwcorpus.properties");

        File inDir = getFileProp(properties, "sketchDir", null);
        File listFile = new File(inDir, "lijst.txt");

        File outDir = getFileProp(properties, "inputDir", "input", null);

        SketchToXmlConverter.convertList(listFile, inDir, outDir);
    }

    /**
     * Read Properties from a resource
     *
     * @param resourcePath file path, relative to the classpath, where the
     *            properties file is
     * @return the Properties read
     */
    public static Properties getPropertiesFromResource(String resourcePath) throws IOException {
        Properties properties;
        try (InputStream isProps = SketchToXmlConverter.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (isProps == null) {
                throw new IllegalArgumentException("Properties file not found: " + resourcePath
                        + " (must be accessible from the classpath)");
            }
            properties = new Properties();
            properties.load(isProps);
            return properties;
        }
    }

    /**
     * Get a File property from a Properties object.
     *
     * This may be an absolute file path (starts with / or \ or a Windows drive
     * letter spec), or a path relative to basePath
     *
     * @param properties where to read the value from
     * @param name the value's name
     * @param basePath base path the file path may be relative to
     * @return the file, or null if not found
     */
    public static File getFileProp(Properties properties, String name, File basePath) {
        return getFileProp(properties, name, null, basePath);
    }

    /**
     * Get a File property from a Properties object.
     *
     * This may be an absolute file path (starts with / or \ or a Windows drive
     * letter spec), or a path relative to basePath
     *
     * @param properties where to read the value from
     * @param name the value's name
     * @param defaultValue default value if the property was not specified
     * @param basePath base path the file path may be relative to
     * @return the file, or null if not found
     */
    public static File getFileProp(Properties properties, String name, String defaultValue, File basePath) {
        Object prop = properties.get(name);
        if (prop == null)
            prop = defaultValue;
        if (prop == null)
            return null;
        File filePath = new File(prop.toString());

        // Is it an absolute path, or no base path given?
        if (basePath == null || filePath.isAbsolute()) {
            // Yes; ignore our base directory
            return filePath;
        }
        // Relative path; concatenate with base directory
        return new File(basePath, filePath.getPath());
    }

    private static final int LINES_PER_CHUNK_FILE = 30_000;

    boolean inSentence = false;

    boolean inDoc = false;

    private int linesDone;

    private boolean docWasEmpty = true;

    private String lastDocLine = "(no docs processed yet)";

    public boolean processLine(String line, Writer out) throws IOException {
        boolean shouldContinue = true;
        if (line.length() >= 1 && line.charAt(0) == '<' && line.endsWith(">")) { // tag?
            if (line.charAt(1) == '/') {
                // end tag
                if (line.equals("</s>")) {
                    if (!inSentence)
                        System.err.println("Sentence close without open!");
                    inSentence = false;
                    docWasEmpty = false;
                } else if (line.equals("</doc>")) {
                    if (!inDoc)
                        System.err.println("Doc close without open! After: " + lastDocLine);
                    if (docWasEmpty)
                        System.err.println("Empty document");
                    if (inSentence)
                        out.append("</s>\n"); // close last sentence tag!
                    inSentence = false;
                    inDoc = false;
                    if (linesDone >= LINES_PER_CHUNK_FILE) {
                        shouldContinue = false;
                    }
                } else
                    System.err.println("Unknown end tag: " + line);
            } else {
                if (line.equals("<s>")) {
                    if (inSentence)
                        System.err.println("Nested sentence!");
                    docWasEmpty = false;
                    inSentence = true;
                } else if (line.equals("<g/>")) {
                    // do nothing
                } else if (line.startsWith("<doc")) {
                    lastDocLine = line;
                    if (inDoc) {
                        out.append("</doc>\n"); // close unclosed doc
                        System.err.println("--- Unclosed " + (docWasEmpty ? "empty " : "")
                                + "document before: " + line);
                        System.err.println("    Fixed: (added close tag)");
                    }

                    // Fix o.a. subcorpus="Domeinen" id="7637"
                    // [v2]
                    // line = line.replaceAll("\\s+jaar=\"(\\d+)\"\"\\s+", " jaar=\"$1\" ");

                    // Quotes om jaar
                    // [v2]
                    // line = line.replaceAll("\\s+jaar=(\\d+)\\s+", " jaar=\"$1\" ");

                    // Properly escape &'s
                    line = line.replaceAll("&", "&amp;");

                    // Spaties normaliseren
                    line = line.replaceAll("\\s\\s+", " ");

                    Pattern pattDocTag = Pattern.compile("<doc(\\s+\\w+\\s*=\\s*\"[^<>\"]*\")*+\\s*>\\s*");
                    if (!pattDocTag.matcher(line).matches()) {
                        System.err.println("--- Illegal doc line: " + line);

                        // Fix subcorpus="Pluscorpus" id="68795"
                        line = line.replaceAll("<URL >", "url=\"");
                        line = line.replaceAll("/URL</URL>", "\"");

                        // Fix subcorpus="CLT" id="69591"
                        line = line
                                .replaceAll("<CLTSTRATUM>EON</CLTSTRATUM>", "cltstratum=\"EON\"");

                        // Fix subcorpus="Domeinen" id="2312"
                        line = line.replaceAll("auteur=\"\" ", "auteur=\"");

                        // [v2]
                        line = line.replaceAll("auteurwebtekst=\"\" ", "auteurwebtekst=\"");

                        // Fix subcorpus="Domeinen" id="69831"
                        line = line.replaceAll("<FILEDESC\\s+>DOMEIN\\s+</FILEDESC>",
                                "filedesc=\"DOMEIN\"");

                        // Fix subcorpus="CLT" id="478"
                        line = line.replaceAll("<TEKST>", "tekst=\"");
                        line = line.replaceAll("</TEKST>", "\"");

                        // Check that that fixed it
                        if (!pattDocTag.matcher(line).matches()) {
                            System.err.println("!!! Failed: " + line);
                        } else {
                            System.err.println("    Fixed: " + line);
                        }
                    }

                    // Keep track of where we are
                    inDoc = true;
                    docWasEmpty = true;
                } else
                    System.err.println("Unknown tag: " + line);
            }
            out.append(line);
        } else if (line.indexOf('\t') < 0) {
            // no tabs; punctuation
            out.append("<pu>").append(StringEscapeUtils.escapeXml10(line)).append("</pu>");
            docWasEmpty = false;
        } else {
            line = StringEscapeUtils.escapeXml10(line);
            String[] parts = line.split("\t", 3);
            out.append("<w p=\"").append(parts[1]).append("\" l=\"").append(parts[2]).append("\">")
                    .append(parts[0]).append("</w>");
            docWasEmpty = false;
        }
        out.append('\n');
        linesDone++;
        return shouldContinue;
    }

    public void convert(Reader in, File outDir, String outFn) throws IOException {
        int chunksDone = 0;
        Writer out = openOutFile(outDir, outFn, chunksDone);
        try {
            BufferedReader br;
            if (!(in instanceof BufferedReader))
                in = new BufferedReader(in);
            br = (BufferedReader) in;
            while (true) {
                String line = br.readLine();
                if (line == null)
                    break;
                line = line.trim();
                boolean shouldContinueChunk = processLine(line, out);
                if (!shouldContinueChunk) {
                    chunksDone++;
                    closeOutFile(out);
                    out = openOutFile(outDir, outFn, chunksDone);
                }
            }
        } finally {
            closeOutFile(out);
        }
    }

    private void closeOutFile(Writer out) throws IOException {
        if (inDoc) {
            out.append("</doc>\n"); // close unclosed doc
            System.err.println("Unclosed document at end of chunk");
        }
        out.append("</docs>\n");
        out.close();
    }

    private Writer openOutFile(File outDir, String outFn, int chunksDone) throws IOException {
        linesDone = 0;
        if (chunksDone > 0) {
            int punt = outFn.lastIndexOf('.');
            outFn = outFn.substring(0, punt) + " (" + (chunksDone + 1) + ")"
                    + outFn.substring(punt);
        }
        File outFile = new File(outDir, outFn);
        Writer out = new OutputStreamWriter(
                new BufferedOutputStream(new FileOutputStream(outFile)), Indexer.DEFAULT_INPUT_ENCODING);
        String encName = Indexer.DEFAULT_INPUT_ENCODING.name();
        out.append("<?xml version=\"1.0\" encoding=\"" + encName + "\" ?>\n")
                .append("<?xml-stylesheet type=\"text/xsl\" href=\"xsl/corpus.xsl\" ?>\n")
                .append("<docs file=\"" + outFn + "\">\n");
        return out;
    }

    /**
     * Convert a list of files.
     *
     * @param listFile list of files to index (assumed to reside in or under
     *            basedir)
     * @param inDir basedir for the files to index
     * @param outDir where to write output files
     */
    private static void convertList(File listFile, File inDir, File outDir) throws FileNotFoundException, IOException {
        SketchToXmlConverter converter = new SketchToXmlConverter();
        List<String> filesToRead = FileUtil.readLines(listFile);
        // Contains a list of files to index
        for (String filePath : filesToRead) {
            File file = new File(inDir, filePath);
            convertFile(converter, file, outDir);
        }
    }

    private static void convertFile(SketchToXmlConverter converter, File inFile, File outDir)
            throws IOException {
        try (Reader in = new InputStreamReader(new FileInputStream(inFile), Indexer.DEFAULT_INPUT_ENCODING)) {
            String fn = inFile.getName();
            String outFn = fn.substring(0, fn.lastIndexOf('.')) + ".xml";
            converter.convert(in, outDir, outFn);
        }
    }

}
