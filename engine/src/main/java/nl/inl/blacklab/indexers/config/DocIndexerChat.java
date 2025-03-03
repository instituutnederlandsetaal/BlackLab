package nl.inl.blacklab.indexers.config;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.nio.charset.Charset;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.exceptions.InvalidInputFormatConfig;
import nl.inl.blacklab.exceptions.PluginException;
import nl.inl.util.CollUtil;
import nl.inl.util.FileReference;
import nl.inl.util.StringUtil;

/**
 * Class to read files in (CHILDES) CHAT format.
 *
 * Ported from Python code by Jan Odijk, see https://github.com/UUDigitalHumanitieslab/chamd
 */
public class DocIndexerChat extends DocIndexerConfig {

    private BufferedReader reader;

    private StringBuilder fullText;

    /** Where to write log messages, or null for no logging */
    private PrintWriter log = null;

    /** The locale to use for date parsing (by default, use system locale) */
    private Locale locale = null;

    /** Fallback locale in case we can't parse the date */
    private final Locale usLocale = new Locale("en", "US");

    private ConfigAnnotatedField currentAnnotatedField;

    @Override
    public void indexSpecificDocument(String documentExpr) {
        // documentExpr is ignored because CHAT files always contain 1 document
        try {
            index();
        } catch (Exception e) {
            throw BlackLabRuntimeException.wrap(e);
        }
    }

    @Override
    protected void storeDocument() {
        storeWholeDocument(fullText.toString());
    }

    @Override
    protected int getCharacterPosition() {
        return fullText.length();
    }

    @Override
    public void setConfigInputFormat(ConfigInputFormat config) {
        if (config.getAnnotatedFields().size() > 1)
            throw new InvalidInputFormatConfig("CHAT input type can only have 1 annotated field");
        super.setConfigInputFormat(config);
    }

    @Override
    public void setDocument(FileReference file) {
        super.setDocument(file);
        file = file.withCreateReader(); // we need two readers, one extra to read char encoding line
        String charEncodingLine;
        try (BufferedReader reader = file.createReader()) {
            charEncodingLine = reader.readLine();
        } catch (IOException e) {
            throw BlackLabRuntimeException.wrap(e);
        }
        Charset charEncoding = charEncodingLine == null ? null : getCharEncoding(charEncodingLine);
        if (charEncoding == null) {
            log("No character encoding encountered in " + file.getPath() + "; using utf-8");
            charEncoding = file.getCharSet();
        }
        setDocumentName(file.getPath());
        setDocument(file.createReader(charEncoding));
    }

    public void setDocument(Reader reader) {
        this.reader = reader instanceof BufferedReader ? (BufferedReader) reader : new BufferedReader(reader);
    }

    @Override
    public void close() throws BlackLabRuntimeException {
        try {
            if (reader != null)
                reader.close();
        } catch (IOException e) {
            throw BlackLabRuntimeException.wrap(e);
        }
    }

    @Override
    public void index() throws IOException, PluginException {
        super.index();

        startDocument();

        fullText = new StringBuilder();

        // For the configured annotated field...
        for (ConfigAnnotatedField annotatedField : config.getAnnotatedFields().values()) {
            currentAnnotatedField = annotatedField;
            setCurrentAnnotatedFieldName(annotatedField.getName());

            log("processing " + documentName + "...");

            metadata = new HashMap<>();
            currentFileBaseName = new File(documentName).getName().replaceAll("\\.[^.]+$", "");

            int lineNumber = 0;
            int uttId = 0;
            counter = new HashMap<>();
            for (String el : SIMPLE_COUNTER_HEADERS)
                counter.put(el, 0);
            boolean headerModified = false;
            String lineToProcess = "";
            while (true) {
                String line = reader.readLine();
                if (line == null)
                    break;
                if (isStoreDocuments()) {
                    fullText.append(line);
                }
                lineNumber++;
                char startChar = line.charAt(0);
                if (startChar == '\t')
                    lineToProcess = combineLines(lineToProcess, line);
                else if (START_CHARS_TO_CHECK.contains(startChar)) {
                    if (!lineToProcess.isEmpty()) {
                        Pair<Integer, Boolean> result = processLine(lineNumber, lineToProcess, uttId, headerModified);
                        uttId = result.getLeft();
                        headerModified = result.getRight();
                    }
                    lineToProcess = line;
                }
                // print(metadata, file = logfile)
                // print(input("Continue?"), file = logfile)
            }
            if (inBlock)
                endBlock();
            addDocumentMetadata(metadata); // "header metadata" is document metadata (?)
            // deal with the last line
            Pair<Integer, Boolean> result2 = processLine(lineNumber, lineToProcess, uttId, headerModified);
            uttId = result2.getLeft();
            headerModified = result2.getRight();
        }

        endDocument();

    }

    private void log(String msg) {
        if (log != null)
            log.println("LOG: " + msg);
    }

    private void printToCleanfile(String msg) {
        if (log != null)
            log.println("CLN: " + msg);
    }

    private static String toIsoFormat(Date d) {
        TimeZone tz = TimeZone.getTimeZone("UTC");
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
        df.setTimeZone(tz);
        return df.format(d);
    }

    /**
     * Slice a string like Python.
     *
     * Negative indices are counted from the end of the string. Indices out of range
     * result in an empty string, not an exception.
     *
     * @param str string to slice
     * @param start starting point
     * @param end end point
     * @return slice
     */
    private static String slice(String str, int start, int end) {
        if (start < 0) {
            start += str.length();
            if (start < 0)
                start = 0;
        }
        if (end < 0) {
            end += str.length();
            if (end < 0)
                end = 0;
        }
        if (start > str.length())
            start = str.length();
        if (end > str.length())
            end = str.length();
        return str.substring(start, end);
    }

    /**
     * Slice a string like Python.
     *
     * Negative indices are counted from the end of the string. Indices out of range
     * result in an empty string, not an exception.
     *
     * @param str string to slice
     * @param start starting point
     * @return slice
     */
    private static String slice(String str, int start) {
        return slice(str, start, str.length());
    }

    /**
     * Set the locale to use for date parsing.
     * 
     * @param locale locale to use
     */
    public void setLocale(Locale locale) {
        this.locale = locale;
    }

    /**
     * Set where to write log messages.
     * 
     * @param log where to write messages, or null to disable logging.
     */
    public void setLog(PrintWriter log) {
        this.log = log;
    }

    //-----------------

    // Combine continued input lines into one longer line.
    private static String combineLines(String str1, String str2) {
        if (str1.isEmpty())
            return str2;
        return slice(str1, 0, -1) + " " + str2;
    }

    // Trim string and replace internal whitespace with underscore.
    // Used for outputting metadata names
    private static String despaceMetadataName(String name) {
        // remvove leading and trailing spaces
        // replace other sequences of spaces by underscore
        return name.trim().replaceAll(" +", "_");
    }

    // Get the encoding from a possible encoding line, or null if not an encoding line
    private static Charset getCharEncoding(String encodingLine) {
        //  if str[1:] in legalcharencodings) {
        //     result = str[1:]
        //  else:
        //     result = None
        if (encodingLine.charAt(0) == MD_CHAR)
            return Charset.forName(encodingLine.substring(1)); // (fixed tov origineel)
        return null; // (fixed tov origineel)
    }

    // Convert date string to month number
    private int getMonths(String age) {
        // input format is 3;6.14 (y;m.d)
        // also accept y.m.d and y;m;d and y.m;d with a warning or any separators for that matter
        String cleanAge = age.trim();
        boolean errorFound = false;
        boolean warningNeeded = false;
        String monthStr = "";
        String yearStr = "";
        String[] parts = cleanAge.split(SEPS, -1);
        // print(age, thelist, file = logfile)
        // print(input("continue?"), file = logfile)
        if (parts.length >= 1) {
            yearStr = parts[0];
            if (!yearStr.matches("[0-9]+"))
                errorFound = true;
        }
        if (parts.length >= 2) {
            monthStr = parts[1];
            if (!monthStr.matches("[0-9]{1,2}"))
                errorFound = true;
        }
        if (parts.length < 1 || parts.length > 3)
            errorFound = true;
        int result;
        if (!errorFound) {
            if (!cleanAge.matches(AGE_REGEX))
                warningNeeded = true;
            int year = Integer.parseInt(yearStr);
            int month = monthStr.isEmpty() ? 0 : Integer.parseInt(monthStr);
            if (month < 0 || month > 11)
                log("Warning: Illegal month value in age(0< = m< = 11): " + cleanAge);
            result = 12 * year + month;
        } else {
            result = 0;
            log("Error: uninterpretable age value: " + cleanAge + ". No months attribute computed");
        }
        if (warningNeeded)
            log("Warning: Illegal age syntax for " + cleanAge + ". Syntax must be y;m.d");
        return result;
    }

    private void addMetaDate(String el, Map<String, Object> metadata) {
        Date d = (Date) metadata.get(el);
        String normalizeddate = toIsoFormat(d);
        String uel = despaceMetadataName(el);
        normalizeddate = processMetadataValue(uel, normalizeddate);
        addMetadataField(uel, normalizeddate);
    }

    private void addMetaInt(String el, Map<String, Object> metadata) {
        String uel = despaceMetadataName(el);
        String value = processMetadataValue(uel, metadata.get(el).toString());
        addMetadataField(uel, value);
    }

    private void addMetaTxt(String el, Map<String, Object> metadata) {
        String uel = despaceMetadataName(el);
        String value = processMetadataValue(uel, metadata.get(el).toString());
        addMetadataField(uel, value);
    }

    private Date normalizeDate(String str) {
        Date date;
        try {
            date = DateUtils.parseDate(str, locale, "d-M-Y", "dd-MMM-yyyy");
        } catch (ParseException e) {
            try {
                date = DateUtils.parseDate(str, usLocale, "d-M-Y", "dd-MMM-yyyy");
            } catch (ParseException e1) {
                log("Date " + str + " cannot be interpreted");
                throw BlackLabRuntimeException.wrap(e1);
            }
        }
        return date;
    }

    private void addDocumentMetadata(Map<String, Object> metadata) {
        for (Entry<String, Object> entry : metadata.entrySet()) {
            String el = entry.getKey();
            if (DO_NOT_PRINT_IN_HEADERS.contains(el)) {
                // (pass)
            } else if (ALL_HEADERS.contains(el)) {
                Object curval = metadata.get(el);
                if (curval instanceof String) {
                    addMetaTxt(el, metadata);
                } else if (curval instanceof Date) {
                    addMetaDate(el, metadata);
                } else if (curval instanceof Integer) {
                    addMetaInt(el, metadata);
                }
                if (!PRINT_IN_HEADERS.contains(el))
                    log("unknown metadata element encountered: " + el);
            }
        }
    }

    /**
     * Are we inside a "block"? Blocks have their own set of metadata, and are
     * indexed just like inline XML tags.
     */
    final boolean inBlock = false;

    final String blockTagName = "block";

    @SuppressWarnings("unchecked")
    private void startBlock() {
        Map<String, String> blockMetadata = new HashMap<>();
        for (Entry<String, Object> entry : metadata.entrySet()) {
            String el = entry.getKey();
            if (DO_NOT_PRINT_IN_HEADERS.contains(el)) {
                // (pass)
            } else if (ALL_HEADERS.contains(el)) {
                Object curval = metadata.get(el);
                if (curval instanceof Date) {
                    blockMetadata.put(despaceMetadataName(el), toIsoFormat((Date) curval));
                } else if (curval instanceof String || curval instanceof Integer) {
                    blockMetadata.put(despaceMetadataName(el), curval.toString());
                } else {
                    log("startBlock: unknown type for " + el + " = " + curval);
                }
                if (!PRINT_IN_HEADERS.contains(el))
                    log("unknown metadata element encountered: " + el);
            }
        }
        if (metadata.containsKey("uttid"))
            blockMetadata.put("uttid", metadata.get("uttid").toString());
        if (metadata.containsKey("origutt"))
            blockMetadata.put("origutt", metadata.get("origutt").toString());
        String curcode = "";
        if (metadata.containsKey("speaker")) {
            curcode = metadata.get("speaker").toString();
            blockMetadata.put("speaker", curcode);
            Map<String, Object> participants = (Map<String, Object>) metadata.get("participants");
            if (participants != null && participants.containsKey(curcode)) {
                Map<String, Object> codeMap = (Map<String, Object>) participants.get(curcode);
                for (Entry<String, Object> entry : codeMap.entrySet()) {
                    String el = entry.getKey();
                    blockMetadata.put(despaceMetadataName(el), codeMap.get(el).toString());
                }
            }
        }
        if (metadata.containsKey("id")) {
            Map<String, Object> mdid = (Map<String, Object>) metadata.get("id");
            if (mdid != null) {
                Map<String, Object> curcodeMap = (Map<String, Object>) mdid.get(curcode);
                if (curcodeMap != null) {
                    for (Entry<String, Object> entry : curcodeMap.entrySet()) {
                        String el = entry.getKey();
                        Object curval = curcodeMap.get(el);
                        if (curval instanceof Date) {
                            blockMetadata.put(despaceMetadataName(el), toIsoFormat((Date) curcodeMap.get(el)));
                        } else if (curval instanceof String || curval instanceof Integer) {
                            blockMetadata.put(despaceMetadataName(el), curcodeMap.get(el).toString());
                        } else {
                            log("startBlock: unknown type for " + el + " = " + curval);
                        }
                    }
                }
            }
        }

        Map<String, List<String>> atts = CollUtil.toMapOfLists(blockMetadata);
        inlineTag(blockTagName, true, atts);
    }

    private void endBlock() {
        inlineTag(blockTagName, false, null);
    }

    private void addWords(String line) {
        String[] words = line.trim().split(StringUtil.REGEX_WHITESPACE);
        for (String word : words) {
            beginWord();
            for (ConfigAnnotation annot : currentAnnotatedField.getAnnotationsFlattened().values()) {
                String processed = processString(word, annot.getProcess(), null);
                annotationValueAppend(annot.getName(), processed, 1);
            }
            endWord();
        }
    }

    private Pair<Integer, Boolean> processLine(int lineNumber, String line, int uttId, boolean headerModified) {
        char startChar = line.charAt(0);
        if (startChar == MD_CHAR) {
            // to implement
            treatMetadataLine(lineNumber, line, metadata);
            headerModified = true;
        } else {
            if (headerModified) {
                if (inBlock)
                    endBlock();

                headerModified = false;

                startBlock();
            }
            if (startChar == UTT_CHAR) {
                metadata.put("uttid", uttId);
                treatUtt(line, metadata);

                int endspk = line.indexOf(':');
                if (endspk < 0)
                    log("error in line: " + line);
                String entry = line.substring(endspk + 2);
                String cleanEntry = cleanText(entry);
                writeToCleanFile(entry, cleanEntry);
                checkLine(line, cleanEntry, lineNumber);
                addWords(cleanEntry);
                uttId++;
            } else if (startChar == ANNO_CHAR) {
                // to be implemented
            } else {
                addWords(line);
            }
        }
        return new ImmutablePair<>(uttId, headerModified);
    }

    private static String getCleanEntry(List<String> entryList, int i) {
        int lentrylist = entryList.size();
        if (lentrylist > i)
            return entryList.get(i).trim();
        return "";
    }

    @SuppressWarnings("unchecked")
    private void treatMetadataLine(int lineNumber, String headerLine, Map<String, Object> metadata) {
        int headerNameEnd = headerLine.indexOf(HEADERLINE_END_SYMB);
        if (headerNameEnd < 0) {
            String cleanHeaderLine = headerLine.trim().toLowerCase();
            switch (cleanHeaderLine) {
            case "@utf8":
                metadata.put("charencoding", "UTF8");
                break;
            case "@begin":
            case "@end":
            case "@blank":
                // (pass)
                break;
            default:
                log("Warning: unknown header " + headerLine + " encountered in line " + lineNumber);
                break;
            }

        } else {
            String headerName = headerLine.substring(1, headerNameEnd);
            String entry = headerLine.substring(headerNameEnd + 1);
            String cleanEntry = entry.trim();
            List<String> entryList = Arrays.asList(cleanEntry.split(",", -1));
            String cleanHeaderName = headerName.trim();
            String cleanHeaderNameBase = slice(cleanHeaderName, 0, -3).trim();
            String headerParameter = slice(cleanHeaderName, -3);
            cleanHeaderName = cleanHeaderName.toLowerCase();
            if (cleanHeaderName.equals("font")) {
                // (pass)
            } else if (cleanHeaderName.equals("languages")) {
                metadata.put("languages", entryList);
            } else if (cleanHeaderName.equals("colorwords")) {
                metadata.put("colorwords", entryList);
            } else if (cleanHeaderName.equals("options")) {
                // (pass)
            } else if (cleanHeaderName.equals("participants")) {
                treatParticipants(entryList, metadata);
            } else if (cleanHeaderName.equals("id")) {
                treatId(entry, metadata);
            } else if (cleanHeaderName.equals("date")) {
                metadata.put(cleanHeaderName, normalizeDate(cleanEntry));
            } else if (SIMPLE_HEADERNAMES.contains(cleanHeaderName)) {
                metadata.put(cleanHeaderName, cleanEntry);
            } else if (SKIP_HEADER_NAMES.contains(cleanHeaderName)) {
                // (pass)
            } else if (SIMPLE_INT_HEADERNAMES.contains(cleanHeaderName)) {
                int i;
                try {
                    i = Integer.parseInt(cleanEntry);
                } catch (NumberFormatException e) {
                    log("Warning: couldn't parse integer for header " + cleanHeaderName + ": '" + cleanEntry
                            + "'. Using -1.");
                    i = -1;
                }
                metadata.put(cleanHeaderName, i);
            } else if (SIMPLE_COUNTER_HEADERS.contains(cleanHeaderName)) {
                counter.put(cleanHeaderName, counter.get(cleanHeaderName) + 1);
                metadata.put(cleanHeaderName, counter.get(cleanHeaderName));
            } else if (PARTICIPANT_SPECIFIC_HEADERS.contains(cleanHeaderNameBase)) {
                if (!metadata.containsKey("id"))
                    metadata.put("id", new HashMap<String, Object>());
                Map<String, Object> mdid = (Map<String, Object>) metadata.get("id");
                if (!mdid.containsKey(headerParameter))
                    mdid.put(headerParameter, new HashMap<String, String>());
                Map<String, Object> hp = (Map<String, Object>) mdid.get(headerParameter);
                if (cleanHeaderNameBase.equals("birth of")) {
                    Date date = normalizeDate(cleanEntry);
                    hp.put(cleanHeaderNameBase, date);
                } else if (cleanHeaderNameBase.equals("age of")) {
                    hp.put("age", cleanEntry);
                    int months = getMonths(cleanEntry);
                    if (months != 0)
                        hp.put("months", months);
                } else {
                    hp.put(cleanHeaderNameBase, cleanEntry);
                }

            } else {
                log("Warning: unknown metadata element encountered: " + cleanHeaderName);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void treatParticipants(List<String> entryList, Map<String, Object> metadata) {
        for (String el : entryList) {
            String[] ellist = el.split(StringUtil.REGEX_WHITESPACE, -1);
            //int ctr = 0;
            String code = "";
            String name = "";
            String role = "";
            if (ellist.length == 3) {
                code = ellist[0];
                name = ellist[1];
                role = ellist[2];
            } else if (ellist.length == 2) {
                code = ellist[0];
                name = "";
                role = ellist[1];
            } else {
                log("error in participants: too few elements " + entryList);
            }
            if (!code.isEmpty()) {
                if (!metadata.containsKey("participants"))
                    metadata.put("participants", new HashMap<String, Object>());
                Map<String, Object> par = (Map<String, Object>) metadata.get("participants");
                if (!par.containsKey(code))
                    par.put(code, new HashMap<String, String>());
                Map<String, Object> codeMap = (Map<String, Object>) par.get(code);
                if (!role.isEmpty())
                    codeMap.put("role", role);
                if (!name.isEmpty())
                    codeMap.put("name", name);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void treatId(String entry, Map<String, Object> metadata) {
        String cleanEntry = entry.trim();
        List<String> entrylist = Arrays.asList(cleanEntry.split(ID_SEP, -1));
        int lEntryList = entrylist.size();
        if (lEntryList != 11)
            log("Warning in id: " + lEntryList + " elements instead of 11 in " + entry);
        String language = getCleanEntry(entrylist, 0);
        String corpus = getCleanEntry(entrylist, 1);
        String code = getCleanEntry(entrylist, 2);
        String age = getCleanEntry(entrylist, 3);
        String sex = getCleanEntry(entrylist, 4);
        String group = getCleanEntry(entrylist, 5);
        String ses = getCleanEntry(entrylist, 6);
        String role = getCleanEntry(entrylist, 7);
        String education = getCleanEntry(entrylist, 8);
        String custom = getCleanEntry(entrylist, 9);
        if (code.isEmpty()) {
            log("error in id: no code element in " + entry);
        } else {
            if (!metadata.containsKey("id"))
                metadata.put("id", new HashMap<String, Object>());
            Map<String, Object> mdid = (Map<String, Object>) metadata.get("id");
            if (!mdid.containsKey(code))
                mdid.put(code, new HashMap<String, Object>());
            Map<String, Object> codeMap = (Map<String, Object>) mdid.get(code);
            if (!language.isEmpty())
                codeMap.put("language", language);
            if (!corpus.isEmpty())
                codeMap.put("corpus", corpus);
            codeMap.put("age", age);
            String months;
            if (!age.isEmpty())
                months = Integer.toString(getMonths(age));
            else
                months = "";
            codeMap.put("months", months);
            codeMap.put("sex", sex);
            codeMap.put("group", group);
            codeMap.put("SES", ses);
            codeMap.put("role", role);
            codeMap.put("education", education);
            codeMap.put("custom", custom);
        }
    }

    private void treatUtt(String line, Map<String, Object> metadata) {
        int endSpk = line.indexOf(':');
        if (endSpk < 0)
            log("WARNING, No : in line: " + line);
        String code = line.substring(1, endSpk);
        metadata.put("speaker", code);
        metadata.put("origutt", line.substring(endSpk + 1, line.length() - 1));
    }

    private void writeToCleanFile(String entry, String cleanEntry) {
        if (!entry.equals(cleanEntry)) {
            printToCleanfile(entry);
            printToCleanfile(cleanEntry);
        }
    }

    // constants

    private static final char MD_CHAR = '@';
    private static final char UTT_CHAR = '*';
    private static final char ANNO_CHAR = '%';
    private static final char SPACE_CHAR = ' ';
    private static final String HEADERLINE_END_SYMB = ":";
    private static final String ID_SEP = "\\|";
    private static final String SPACE = " ";

    private static final String SEPS = "[-.,/;:_!~\\\\]";
    private static final String ONE_OR_MORE_DIGITS = "[0-9]+";
    private static final String DIGITS_ONE_OR_TWO = "[0-9]{1,2}";
    private static final String OPT_DAYS = "(" + ONE_OR_MORE_DIGITS + ")?";
    private static final String OPT_SEP_DAYS = "(\\." + OPT_DAYS + ")?";
    private static final String OPT_MONTHS = "(" + DIGITS_ONE_OR_TWO + OPT_SEP_DAYS + ")?";
    private static final String OPT_SEP_MONTHS = "(;" + OPT_MONTHS + ")?";
    private static final String AGE_REGEX = "^" + ONE_OR_MORE_DIGITS + OPT_SEP_MONTHS + "$";

    private static final List<String> SIMPLE_HEADERNAMES = Arrays.asList(
            "pid", "transcriber", "coder", "date", "location",
            "situation", "number", "interaction type", "activities",
            "comment", "bck", "warning", "transcription",
            "time start", "time duration", "tape location", "room layout",
            "recording quality", "number", "media");
    private static final List<String> SIMPLE_INT_HEADERNAMES = Arrays.asList("g", "page");
    private static final List<String> SIMPLE_COUNTER_HEADERS = List.of("new episode");
    private static final List<String> SKIP_HEADER_NAMES = List.of("exceptions");
    private static final List<String> PARTICIPANT_SPECIFIC_HEADERS = Arrays.asList("birth of", "birthplace of", "l1 of",
            "age of");
    private static final List<String> CREATED_MD_NAMES = Arrays.asList("charencoding", "parsefile", "speaker",
            "origutt");
    private static final List<String> DO_NOT_PRINT_IN_HEADERS = Arrays.asList(
            "id", "participants", "languages", "colorwords",
            "options", "uttid", "parsefile", "speaker", "origutt");
    private static final List<String> ALL_HEADERS = new ArrayList<>();
    private static final List<String> PRINT_IN_HEADERS = new ArrayList<>();
    private static final List<Character> START_CHARS_TO_CHECK = new ArrayList<>();

    static {
        ALL_HEADERS.addAll(SIMPLE_HEADERNAMES);
        ALL_HEADERS.addAll(SIMPLE_INT_HEADERNAMES);
        ALL_HEADERS.addAll(SIMPLE_COUNTER_HEADERS);
        ALL_HEADERS.addAll(CREATED_MD_NAMES);
        ALL_HEADERS.addAll(PARTICIPANT_SPECIFIC_HEADERS);

        for (String headeratt : ALL_HEADERS) {
            if (!DO_NOT_PRINT_IN_HEADERS.contains(headeratt))
                PRINT_IN_HEADERS.add(headeratt);
        }

        START_CHARS_TO_CHECK.add(MD_CHAR);
        START_CHARS_TO_CHECK.add(UTT_CHAR);
        START_CHARS_TO_CHECK.add(ANNO_CHAR);
        START_CHARS_TO_CHECK.add(SPACE_CHAR);
    }

    private Map<String, Object> metadata;

    private Map<String, Integer> counter;

    private String currentFileBaseName;

    public static String scoped(String str) {
        return "<(([^<>]|\\[<\\]|\\[>\\])*)>\\s*" + str;
    }

    static final String EMPTY_STRING = "";

    static final int SKIP_LINES = 1;
    static final int HEADER = 1;
    static final int LCTR = 0;

    static final String GT_REPL = "\u00A9"; // copyright sign
    static final String LT_REPL = "\u00AE"; // Registered sign
    static final Pattern GT_REPL_SCOPED = Pattern.compile(scoped(GT_REPL));
    static final Pattern LT_REPL_SCOPED = Pattern.compile(scoped(LT_REPL));
    static final Pattern GT_REPL_UNSCOPED = Pattern.compile(GT_REPL);
    static final Pattern LT_REPL_UNSCOPED = Pattern.compile(LT_REPL);

    static final Pattern PAUSES3 = Pattern.compile("\\(\\.\\.\\.\\)");
    static final Pattern PAUSES2 = Pattern.compile("\\(\\.\\.\\)");
    static final Pattern PAUSES1 = Pattern.compile("\\([0-9]*\\.[0-9]*\\)");
    static final Pattern LEFT_BRACKET = Pattern.compile("\\(");
    static final Pattern RIGHT_BRACKET = Pattern.compile("\\)");
    static final Pattern AT_SIGN_LETTERS = Pattern.compile("@[\\w:]+");
    static final Pattern WWW = Pattern.compile("www");
    static final Pattern PHON_FRAG1 = Pattern.compile("& = [\\w:]+");
    static final Pattern PHON_FRAG2 = Pattern.compile("&[\\w:]+");
    static final Pattern ZERO_STR = Pattern.compile("0(\\w+)");
    static final Pattern BARE_ZERO = Pattern.compile("0");
    static final Pattern PLUS_DOT_DOT = Pattern.compile("\\+\\.\\.");
    static final String LT_STR = "\\[<]";
    static final Pattern LT_REGEX = Pattern.compile(LT_STR);

    static final String DOUBLE_SLASH_STR = "\\[//]";
    static final Pattern DOUBLE_SLASH_SCOPED = Pattern.compile(scoped(DOUBLE_SLASH_STR));
    static final Pattern DOUBLE_SLASH_UNSCOPED = Pattern.compile(DOUBLE_SLASH_STR);
    static final Pattern EXCLAM2 = Pattern.compile("\\[!]");
    static final Pattern EXCLAM1 = Pattern.compile("<([^>]*)>\\s*\\[!]");
    static final String SLASH_STR = "\\[/]";
    static final Pattern SLASH_SCOPED = Pattern.compile(scoped(SLASH_STR));
    static final Pattern SLASH_UNSCOPED = Pattern.compile(SLASH_STR);
    static final String GT_STR = "\\[>]";
    static final Pattern GT_REGEX = Pattern.compile(GT_STR);

    static final String Q_STR = "\\[\\?]";
    static final Pattern Q_REGEX_SCOPED = Pattern.compile(scoped(Q_STR));
    static final Pattern Q_REGEX_UNSCOPED = Pattern.compile(Q_STR);
    static final Pattern EQ_EXCLAM = Pattern.compile("<([^>]*)>\\s*\\[ = ![^]]*]");
    static final Pattern EQ_TEXT1 = Pattern.compile("<([^>]*)>\\s*\\[ = [^]]*]");
    static final Pattern EQ_TEXT2 = Pattern.compile("\\[ = [^]]*]");
    static final Pattern COLON_REGEX = Pattern.compile("[^ ]+\\s+\\[:([^]]*)]");
    static final Pattern DOUBLE_EXCLAM = Pattern.compile("\\[!!]");
    static final Pattern PLUS3 = Pattern.compile("\\+/(/)?[.?]");
    static final Pattern PLUS2 = Pattern.compile("\\+[.^<,+\"]");
    static final Pattern PLUS_QUOTE = Pattern.compile("\\+(\\+\"\\.|!\\?)");

    public static String bracket(String str) {
        return "(" + str + ")";
    }

    public static String regexOr(List<String> strList) {
        return bracket(StringUtils.join(strList, "|"));
    }

    public static String regexStar(String str) {
        return bracket(str) + "*";
    }

    // JN fixed(?)
    private final Pattern CHECK_PATTERN = Pattern.compile(
            "[]\\[\\\\()&%@/ =><_0^~\u2193\u2191\u21D7\u2197\u2192\u2198\u21D8\u221E" +
                    "\u2248\u224B\u2261\u2219\u2308\u2309\u230A\u230B\u2206\u2207\u204E\u2047\u00B0\u25C9" +
                    "\u2581\u2594\u263A\u222C\u03AB123456789\u00B7\u22A5\u0001]");

    // + should not occur except as compund marker black+board
    private final Pattern PLUS_PATTERN = Pattern.compile("\\W\\+|\\+\\W");

    private void checkLine(String line, String newline, int lineNumber) {
        if (log == null)
            return;
        if (CHECK_PATTERN.matcher(newline).find() || PLUS_PATTERN.matcher(newline).find()) {
            log(currentFileBaseName + " " + lineNumber + " suspect character");
            log("input = <" + line.substring(0, line.length() - 1) + ">");
            log("output = <" + newline.substring(0, newline.length() - 1) + ">");
            List<Pair<Character, String>> thecodes = stringToCodes(newline.substring(0, newline.length() - 1));
            log("charcodes = <" + thecodes + ">");
        }
    }

    private static final String EMBED = "(<[^<>]*>)";
    private static final String OTHER = "[^<>]";
    private static final String EMBED_OR_OTHER = regexOr(Arrays.asList(EMBED, OTHER));
    private static final String NEST_STR = "(<" + regexStar(OTHER) + EMBED + regexStar(EMBED_OR_OTHER) + ">)";
    private static final Pattern NESTING = Pattern.compile(NEST_STR);

    private static final String TIMES_STR = "\\[x[^]]*]";
    private static final Pattern TIMES_UNSCOPED = Pattern.compile(TIMES_STR);
    private static final Pattern TIMES_SCOPED = Pattern.compile(scoped(TIMES_STR));
    private static final Pattern INLINE_COM_SCOPED = Pattern.compile("<([^<>]*)>\\s*\\[% [^]]*]");
    private static final Pattern INLINE_COM_UNSCOPED = Pattern.compile("\\[% [^]]*]");
    private static final String TRIPLE_SLASH = "\\[///]";
    private static final Pattern REFORMUL_UNSCOPED = Pattern.compile(TRIPLE_SLASH);
    private static final Pattern REFORMUL_SCOPED = Pattern.compile(scoped(TRIPLE_SLASH));
    private static final Pattern END_QUOTE = Pattern.compile("\\+\"/\\.");
    private static final String ERROR_MARK_STR = "\\[\\*]";
    private static final Pattern ERROR_MARK_UNSCOPED = Pattern.compile(ERROR_MARK_STR);
    private static final Pattern ERROR_MARK_SCOPED = Pattern.compile(scoped(ERROR_MARK_STR));
    private static final Pattern DEPENDENT_TIER = Pattern.compile("\\[%(act|add|gpx|int|sit|spe):[^]]*]"); // JN fixed(?)
    private static final Pattern POST_CODES = Pattern.compile("\\[\\+[^]]*]");
    private static final Pattern PRE_CODES = Pattern.compile("\\[-[^]]*]");
    private static final Pattern BCH = Pattern.compile("\\[\\+\\s*bch]");
    private static final Pattern TRN = Pattern.compile("\\[\\+\\s*trn]");
    private static final Pattern SYLLABLE_PAUSE = Pattern.compile("(\\w)\\^");
    private static final Pattern COMPLEX_LOCAL_EVENT = Pattern.compile("\\[\\^[^]]*]");
    private static final Pattern CLITIC_LINK = Pattern.compile("~");
    // NOTE JN: used https://r12a.github.io/apps/conversion/ to convert unicode characters to escape sequences
    private static final Pattern CHAT_CA_SYMS = Pattern.compile(
            "[\u2193\u2191\u21D7\u2197\u2192\u2198\u21D8\u221E\u2248\u224B\u2261\u2219\u2308\u2309" +
                    "\u230A\u230B\u2206\u2207\u204E\u2047\u00B0\u25C9\u2581\u2594\u263A\u222C\u03AB\u222E\u00A7" +
                    "\u223E\u21BB\u1F29\u201E\u2021\u0323\u02B0\u0304\u02940]");
    private static final Pattern TIME_ALIGN = Pattern.compile("\u0015[0123456789_ ]+\u0015");

    private String cleanText(String str) {
        String result = str;

        // page references are to MacWhinney chat manual version 21 april 2015

        // replace [<] and [>]

        result = LT_REGEX.matcher(result).replaceAll(LT_REPL);
        result = GT_REGEX.matcher(result).replaceAll(GT_REPL);

        Matcher m = NESTING.matcher(result);
        if (m.find()) {
            int b = m.start(1) + 1;
            int e = m.end(1) - 1;
            String midStr = result.substring(b, e);
            String newMidStr = cleanText(midStr);
            String leftStr = result.substring(0, b);
            String rightStr = result.substring(e);
            result = leftStr + newMidStr + rightStr;
        }

        // remove scoped times <...> [x ...] keeping the ... betwen <> not officially defined
        result = TIMES_SCOPED.matcher(result).replaceAll("$1");

        // remove scoped inlinecom <...> [% ...] keeping the ... betwen <> not officially defined
        result = INLINE_COM_SCOPED.matcher(result).replaceAll("$1");

        // remove pauses
        result = PAUSES3.matcher(result).replaceAll(SPACE);
        result = PAUSES2.matcher(result).replaceAll(SPACE);
        result = PAUSES1.matcher(result).replaceAll(SPACE);

        // remove round brackets
        result = LEFT_BRACKET.matcher(result).replaceAll(EMPTY_STRING);
        result = RIGHT_BRACKET.matcher(result).replaceAll(EMPTY_STRING);

        // remove multiple wordmarker p. 43, 73-74
        result = TIMES_UNSCOPED.matcher(result).replaceAll(EMPTY_STRING);

        // remove @letters+:
        result = AT_SIGN_LETTERS.matcher(result).replaceAll(EMPTY_STRING);

        // remove inline comments [% ...] p70, 78, 85
        result = INLINE_COM_UNSCOPED.matcher(result).replaceAll(EMPTY_STRING);

        // remove scoped reformulation symbols [///] p 73
        result = REFORMUL_SCOPED.matcher(result).replaceAll("$1");

        // remove reformulation symbols [///] p 73
        result = REFORMUL_UNSCOPED.matcher(result).replaceAll(SPACE);

        // remover errormark1 [*] and preceding <>
        result = ERROR_MARK_SCOPED.matcher(result).replaceAll("$1 ");

        // remover errormark2 [*]
        result = ERROR_MARK_UNSCOPED.matcher(result).replaceAll(EMPTY_STRING);

        // remove inline dependent tier [%xxx: ...]

        result = DEPENDENT_TIER.matcher(result).replaceAll(EMPTY_STRING);

        // remove    postcodes p. 75-76
        result = POST_CODES.matcher(result).replaceAll(EMPTY_STRING);

        // remove precodes p.75-76
        result = PRE_CODES.matcher(result).replaceAll(EMPTY_STRING);

        // remove bch p. 75-76
        result = BCH.matcher(result).replaceAll(EMPTY_STRING);

        // remove trn p.75-76
        result = TRN.matcher(result).replaceAll(EMPTY_STRING);

        // remove xxx should we do this? or something else? add xxx as a word in Alpino?
        // no we keep this
        //    result = result.replaceAll(r"xxx", '')

        // remove yyy should we do this? or something else? add xxx as a word in Alpino?
        // we keep this too
        //    result = result.replaceAll(r"yyy", '')

        // remove phonological fragments p. 61
        result = PHON_FRAG1.matcher(result).replaceAll(EMPTY_STRING);

        // remove phonological fragments p.61
        result = PHON_FRAG2.matcher(result).replaceAll(EMPTY_STRING);

        // remove www intentionally after phonological fragments
        result = WWW.matcher(result).replaceAll(EMPTY_STRING);

        // replace 0[A-z] works ok now, raw replacement string!
        result = ZERO_STR.matcher(result).replaceAll("$1");

        // delete any remaining 0's
        result = BARE_ZERO.matcher(result).replaceAll(SPACE);

        // remove underscore
        result = result.replaceAll("_", EMPTY_STRING);

        // remove +..  p. 63
        result = PLUS_DOT_DOT.matcher(result).replaceAll(EMPTY_STRING);

        // remove [<] and preceding <> on purpose before [//]
        result = LT_REPL_SCOPED.matcher(result).replaceAll("$1 ");

        // remove [<]   on purpose before [//]
        result = LT_REPL_UNSCOPED.matcher(result).replaceAll(SPACE);

        // remove [>] and preceding <>
        result = GT_REPL_SCOPED.matcher(result).replaceAll("$1 ");

        // remove [>]
        result = GT_REPL_UNSCOPED.matcher(result).replaceAll(SPACE);

        // remove [//] keep preceding part between <>, drop <>
        result = DOUBLE_SLASH_SCOPED.matcher(result).replaceAll("$1");

        // remove [//] keep preceding word
        result = DOUBLE_SLASH_UNSCOPED.matcher(result).replaceAll(EMPTY_STRING);

        // remove [!] and <> around preceding text    p.68
        result = EXCLAM1.matcher(result).replaceAll("$1");

        // remove [!] p.68
        result = EXCLAM2.matcher(result).replaceAll(SPACE);

        // remove [/] keep preceding part between <> this line and following one: crucial order
        result = SLASH_SCOPED.matcher(result).replaceAll("$1");

        // remove [/] keep the word before
        result = SLASH_UNSCOPED.matcher(result).replaceAll(EMPTY_STRING);
        //    result = result.replaceAll(r"\[<\]", '')

        // remove [?] and preceding <>
        result = Q_REGEX_SCOPED.matcher(result).replaceAll("$1 ");

        // remove [?]
        result = Q_REGEX_UNSCOPED.matcher(result).replaceAll(SPACE);

        // remove [=! <text>] and preceding <>
        result = EQ_EXCLAM.matcher(result).replaceAll("$1 ");

        // remove [= <text> ] and preceding <>  p 68/69 explanation
        result = EQ_TEXT1.matcher(result).replaceAll("$1 ");

        // remove [= <text>]
        result = EQ_TEXT2.matcher(result).replaceAll(SPACE);

        // replace word [: text] by text
        result = COLON_REGEX.matcher(result).replaceAll("$1 ");

        // remove [!!]
        result = DOUBLE_EXCLAM.matcher(result).replaceAll(SPACE);

        // remove +"/. p. 64-65
        result = END_QUOTE.matcher(result).replaceAll(EMPTY_STRING);

        // remove +/. +/? +//. +//?
        result = PLUS3.matcher(result).replaceAll(" ");

        // remove +.  +^ +< +, ++ +" (p. 64-66)
        result = PLUS2.matcher(result).replaceAll(" ");

        // remove +".    (p. 65)  +!? (p. 63)
        result = PLUS_QUOTE.matcher(result).replaceAll(" ");

        // remove silence marks (.) (..) (...) done above see pauses
        //    result = re.sub(r"\(\.(\.)?(\.)?\)", r" ", result)

        // remove syllablepauses p. 60
        result = SYLLABLE_PAUSE.matcher(result).replaceAll("$1");

        // remove complexlocalevent p. 61
        result = COMPLEX_LOCAL_EVENT.matcher(result).replaceAll(SPACE);

        // replace clitic link ~by space
        result = CLITIC_LINK.matcher(result).replaceAll(SPACE);

        // replace chat-ca codes by space p. 86, 87
        result = CHAT_CA_SYMS.matcher(result).replaceAll(SPACE);

        // remove time alignment p. 67
        result = TIME_ALIGN.matcher(result).replaceAll(SPACE);

        // remove superfluous spaces etc. this also removes CR etc
        //    result = result.strip()
        return result;
        // end function cleantext
    }

    private static List<Pair<Character, String>> stringToCodes(String str) {
        List<Pair<Character, String>> result = new ArrayList<>();
        for (int i = 0; i < str.length(); i++) {
            char curChar = str.charAt(i);
            String curCode = Integer.toHexString(curChar); //hexformat.format(ord(str[i]));
            result.add(new ImmutablePair<>(curChar, curCode));
        }
        return result;
    }

}
