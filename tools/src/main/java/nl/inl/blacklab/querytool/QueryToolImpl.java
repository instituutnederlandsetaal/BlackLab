package nl.inl.blacklab.querytool;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.WordUtils;
import org.apache.logging.log4j.Level;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.Query;

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.exceptions.ErrorOpeningIndex;
import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.resultproperty.HitGroupProperty;
import nl.inl.blacklab.resultproperty.HitGroupPropertyIdentity;
import nl.inl.blacklab.resultproperty.HitGroupPropertySize;
import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.resultproperty.HitPropertyAfterHit;
import nl.inl.blacklab.resultproperty.HitPropertyBeforeHit;
import nl.inl.blacklab.resultproperty.HitPropertyDocumentId;
import nl.inl.blacklab.resultproperty.HitPropertyDocumentStoredField;
import nl.inl.blacklab.resultproperty.HitPropertyHitText;
import nl.inl.blacklab.resultproperty.HitPropertyLeftContext;
import nl.inl.blacklab.resultproperty.HitPropertyMultiple;
import nl.inl.blacklab.resultproperty.HitPropertyRightContext;
import nl.inl.blacklab.search.BlackLab;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.Concordance;
import nl.inl.blacklab.search.ConcordanceType;
import nl.inl.blacklab.search.ContentAccessor;
import nl.inl.blacklab.search.DocUtil;
import nl.inl.blacklab.search.TermFrequencyList;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldNameUtil;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFields;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.results.Concordances;
import nl.inl.blacklab.search.results.ContextSize;
import nl.inl.blacklab.search.results.DocResults;
import nl.inl.blacklab.search.results.Group;
import nl.inl.blacklab.search.results.Hit;
import nl.inl.blacklab.search.results.HitGroups;
import nl.inl.blacklab.search.results.Hits;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.results.QueryTimings;
import nl.inl.blacklab.search.results.Results;
import nl.inl.blacklab.search.textpattern.TextPattern;
import nl.inl.blacklab.searches.SearchHits;
import nl.inl.util.FileUtil;
import nl.inl.util.LogUtil;
import nl.inl.util.LuceneUtil;
import nl.inl.util.StringUtil;
import nl.inl.util.Timer;

/**
 * Simple command-line querying tool for BlackLab indices.
 */
public class QueryToolImpl {

    static final Charset INPUT_FILE_ENCODING = StandardCharsets.UTF_8;

    public static final String MATCH = "match";

    /** How many results to show per page? (default is increased for correctness testing) */
    static int defaultPageSize = 20;

    /** Should results always be sorted? Useful for correctness testing */
    static String alwaysSortBy = null;

    /** Our BlackLab index object. */
    BlackLabIndex index;

    /** The hits that are the result of our query. */
    private Hits hits = null;

    /** The docs that are the result of our query. */
    private DocResults docs = null;

    /** The groups, or null if we haven't grouped our results. */
    private HitGroups groups = null;

    /**
     * If all hits or the current group of hits have been sorted, this contains the
     * sorted hits.
     */
    private Hits sortedHits = null;

    /** The collocations, or null if we're not looking at collocations. */
    private TermFrequencyList collocations = null;

    /** What annotation to use for collocations */
    private Annotation collocAnnotation = null;

    /** The first hit or group to show on the current results page. */
    private long firstResult;

    /** Number of hits or groups to show per results page. */
    private long resultsPerPage = defaultPageSize;

    /** Show total number of hits (takes extra time for large sets) */
    private boolean determineTotalNumberOfHits = true;

    /** The filter query, if any. */
    private Query filterQuery = null;

    /** We record the timings of different parts of the operation here. */
    private QueryTimings timings = new QueryTimings();

    private boolean exitProgram = false;

    /** What results view do we want to see? */
    enum ShowSetting {
        HITS,
        DOCS,
        GROUPS,
        COLLOC
    }

    /**
     * What results view do we want to see? (hits, groups or collocations)
     */
    private ShowSetting showSetting = ShowSetting.HITS;

    /**
     * If we're looking at hits in one group, this is the index of the group number.
     * Otherwise, this is -1.
     */
    private int showWhichGroup = -1;

    /** Lists of words read from file to choose random word from (for batch mode) */
    private final Map<String, List<String>> wordLists = new HashMap<>();

    private final List<Parser> parsers = Arrays.asList(new ParserCorpusQl(), new ParserContextQl());

    private int currentParserIndex = 0;

    /** For stats output (batch mode), extra info (such as # hits) */
    private String statInfo;

    /** If false, command was not a query, prefix stats line with # */
    private boolean commandWasQuery;

    /** Size of larger snippet */
    private ContextSize snippetSize = ContextSize.get(50, Integer.MAX_VALUE);

    /** Strip XML tags when displaying concordances? */
    private boolean stripXML = true;

    private AnnotatedField contentsField;

    /** Types of concordances we want to show */
    private ConcordanceType concType = ConcordanceType.FORWARD_INDEX;

    /**
     * Run the query tool.
     *
     * @param args program arguments
     */
    public static void queryToolMain(String[] args) throws ErrorOpeningIndex {
        BlackLab.setConfigFromFile(); // read blacklab.yaml if exists and set config from that
        LogUtil.setupBasicLoggingConfig(Level.WARN);

        Output output = new Output();
        Config config = Config.fromCommandline(args, output); // configure Output and get Config object
        if (config.getError() != null) {
            output.error(config.getError());
            Output.usage();
            return;
        }

        try (BufferedReader in = config.getInput()) {
            QueryToolImpl c = new QueryToolImpl(config.getIndexDir(), in, output);
            c.run();
        } catch (IOException e) {
            throw BlackLabRuntimeException.wrap(e);
        }
    }

    /**
     * Construct the query tool object.
     *
     * @param indexDir directory our index is in
     * @param input where to read commands from
     * @param output where and how to produce output
     * @throws ErrorOpeningIndex if we couldn't open the index
     */
    public QueryToolImpl(File indexDir, BufferedReader input, Output output) throws ErrorOpeningIndex {
        this.output = output;
        printProgramHead();
        try {
            output.line("Opening index " + indexDir.getCanonicalPath() + "...");
        } catch (IOException e) {
            throw BlackLabRuntimeException.wrap(e);
        }

        // Create the BlackLab index object
        Timer t = new Timer();
        index = BlackLab.open(indexDir);
        output.verbose("Opening index took " + t.elapsedDescription());
        contentsField = index.mainAnnotatedField();

        contextSize = index.defaultContextSize();

        wordLists.put("test", Arrays.asList("de", "het", "een", "over", "aan"));
        commandReader = new CommandReader(input, output);
    }

    /**
     * Switch to a different index.
     *
     * @param index the new BlackLabIndex to use
     */
    public void setIndex(BlackLabIndex index) {
        if (shouldCloseIndex)
            index.close();
        this.index = index;
        contentsField = index.mainAnnotatedField();
        shouldCloseIndex = false; // caller is responsible

        // Reset results
        hits = null;
        groups = null;
        sortedHits = null;
        collocations = null;
    }

    /**
     * Parse and execute commands and queries.
     */
    public void run() {
        output.printHelp(getCurrentParser());

        while (!exitProgram) {
            String prompt = getCurrentParser().getPrompt() + "> ";
            String cmd = commandReader.readCommand(prompt);
            if (cmd == null)
                break;

            Timer t = new Timer();
            timings.clear();
            statInfo = "";
            commandWasQuery = false;
            try {
                processCommand(cmd);
            } catch (Exception e) {
                // Report exception but don't crash right away, so we can try other queries while debugging.
                e.printStackTrace();
            }
            if (!commandReader.lastCommandWasSilenced())
                output.stats((commandWasQuery ? "" : "@ ") + cmd + "\t" + t.elapsed() + "\t" + statInfo);

            System.err.flush(); // if there were error messages, make sure they are shown right away
        }
        cleanup();
    }

    int parseInt(String str, int min) {
        try {
            int n = Integer.parseInt(str);
            if (min >= 0 && n < min)
                return min;
            return n;
        } catch (NumberFormatException e) {
            return min;
        }
    }

    public void processCommand(String cmd) {
        cmd = cmd.trim();

        // Strips comments
        // (note that for mid-line comment, whitespace before hash is required, so hash in query doesn't count)
        cmd = cmd.replaceAll("(?:^|\\s)#.+$", "").trim();
        if (cmd.isEmpty()) {
            output.stats(""); // output empty lines in stats
            return; // no actual command on line, skip
        }

        // Split into action and arguments
        Matcher m = Pattern.compile("^(\\S+)(?:\\s+(.+))?$").matcher(cmd);
        if (!m.matches()) {
            output.error("Invalid command: " + cmd);
            return;
        }
        String action = m.group(1).toLowerCase();
        String arguments = m.group(2) != null ? m.group(2) : "";

        if (action.equals("exit")) {
            exitProgram = true;
            return;
        } else if (action.equals("repeat")) {
            // We want to loop a command
            Pattern p = Pattern.compile("^(\\d+)\\s+(.+)$");
            Matcher m2 = p.matcher(arguments);
            if (m2.find()) {
                int repCount = parseInt(m2.group(1), 1);
                String commandToRepeat = m.group(2);
                output.line("Repeating " + repCount + " times: " + commandToRepeat);
                for (int i = 0; i < repCount; i++) {
                    processCommand(commandToRepeat);
                }
            } else {
                output.error("Malformed repeat command, correct is e.g.: repeat 3 [lemma='test']");
            }
            return;
        }

        // In batch mode, we can use the chain operator (&&) to
        // time several commands together. See if we're chaining
        // commands here.
        if (cmd.contains("&&")) {
            for (String part : cmd.split("&&")) {
                processCommand(part);
            }
            return;
        }

        switch (action) {
        case "clear":
        case "reset":
            hits = null;
            docs = null;
            groups = null;
            sortedHits = null;
            collocations = null;
            filterQuery = null;
            showSetting = ShowSetting.HITS;
            output.line("Query and results cleared.");
            break;
        case "prev":
        case "p":
            prevPage();
            break;
        case "next":
        case "n":
            nextPage();
            break;
        case "page":
            showPage((long)parseInt(arguments, 1) - 1);
            break;
        case "pagesize":
            resultsPerPage = parseInt(arguments, 1);
            firstResult = 0;
            showResultsPage();
            break;
        case "context":
            contextSize = ContextSize.get(parseInt(arguments, 0), Integer.MAX_VALUE);
            collocations = null;
            showResultsPage();
            break;
        case "snippet": {
            int hitId = parseInt(arguments, 1) - 1;
            Hits currentHitSet = getCurrentSortedHitSet();
            if (hitId >= currentHitSet.size()) {
                output.error("Hit number out of range.");
            } else {
                Hits singleHit = currentHitSet.window(hitId, 1);
                Concordances concordances = singleHit.concordances(snippetSize, concType);
                Hit h = currentHitSet.get(hitId);
                Concordance conc = concordances.get(h);
                String[] concParts;
                if (stripXML)
                    concParts = conc.partsNoXml();
                else
                    concParts = conc.parts();
                output.line(
                        "\n" + WordUtils.wrap(concParts[0] + Output.hlStart(MATCH) +
                                concParts[1] + Output.hlEnd(MATCH) + concParts[2], 80));
            }
            break;
        }
        case "snippetsize":
            snippetSize = ContextSize.get(parseInt(arguments, 0), Integer.MAX_VALUE);
            output.line("Snippets will show " + snippetSize + " words of context.");
            break;
        case "doc":
            showMetadata(parseInt(arguments, 0));
            break;
        case "doccontents":
            // Get plain document contents (no highlighting)
            showContents(parseInt(arguments, 0));
            break;
        case "highlight ": {
            // Get highlighted document contents
            int docId = parseInt(arguments, 1) - 1;
            Hits currentHitSet = getCurrentSortedHitSet();
            if (currentHitSet == null) {
                output.error("No set of hits for highlighting.");
            } else {
                Hits hitsInDoc = hits.getHitsInDoc(docId);
                output.line(WordUtils.wrap(DocUtil.highlightDocument(index, contentsField, docId, hitsInDoc), 80));
            }
            break;
        }
        case "filter":
            if (arguments.isEmpty()) {
                filterQuery = null; // clear filter
                output.line("Filter cleared.");
            } else {
                try {
                    filterQuery = LuceneUtil.parseLuceneQuery(index, arguments, index.analyzer(), "title");
                    output.line("Filter created: " + filterQuery);
                    output.verbose(filterQuery.getClass().getName());
                } catch (ParseException e) {
                    output.error("Error parsing filter query: " + e.getMessage());
                }
            }
            docs = null;
            break;
        case "concfi":
            concType = parseBoolean(arguments) ? ConcordanceType.FORWARD_INDEX : ConcordanceType.CONTENT_STORE;
            break;
        case "stripxml":
            stripXML = parseBoolean(arguments);
            break;
        case "sensitive":
            MatchSensitivity sensitivity = switch (arguments) {
                case "on", "yes", "true" -> MatchSensitivity.SENSITIVE;
                case "case" -> MatchSensitivity.DIACRITICS_INSENSITIVE;
                case "diac", "diacritics" -> MatchSensitivity.CASE_INSENSITIVE;
                default -> MatchSensitivity.INSENSITIVE;
            };
            index.setDefaultMatchSensitivity(sensitivity);
            output.line("Search defaults to "
                    + (sensitivity.isCaseSensitive() ? "case-sensitive" : "case-insensitive") + " and "
                    + (sensitivity.isDiacriticsSensitive() ? "diacritics-sensitive" : "diacritics-insensitive"));
            break;
        case "doctitle":
            boolean b = parseBoolean(arguments);
            output.setShowDocTitle(b);
            System.out.println("Show document titles: " + (b ? "ON" : "OFF"));
            break;
        case "struct":
        case "structure":
            output.showIndexMetadata(index);
            break;
        case "sort":
            sortBy(arguments);
            break;
        case "group":
            if (arguments.matches("\\d+")) {
                firstResult = 0; // reset for paging through group
                changeShowSettings("group " + arguments);
            } else {
                String[] parts = arguments.split(StringUtil.REGEX_WHITESPACE, 2);
                groupBy(parts[0], parts.length > 1 ? parts[1] : null);
            }
            break;
        case "groups":
        case "hits":
        case "docs":
        case "colloc":
            changeShowSettings(action);
            break;
        case "switch":
        case "sw":
            currentParserIndex++;
            if (currentParserIndex >= parsers.size())
                currentParserIndex = 0;
            output.line("Switching to " + getCurrentParser().getName() + ".\n");
            output.printQueryHelp(getCurrentParser());
            break;
        case "help":
            output.printHelp(getCurrentParser());
            break;
        case "sleep":
            try {
                Thread.sleep((int) (Float.parseFloat(arguments) * 1000));
            } catch (NumberFormatException e1) {
                output.error("Sleep takes a float, the number of seconds to sleep");
            } catch (InterruptedException e) {
                // OK
            }
            break;
        case "wordlist":
            if (arguments.isEmpty()) {
                // Show loaded wordlists
                output.line("Available word lists:");
                for (String listName : wordLists.keySet()) {
                    output.line(" " + listName);
                }
            } else {
                // Load new wordlist or display existing wordlist
                String[] parts = arguments.trim().split(StringUtil.REGEX_WHITESPACE, 2);
                String name = "word", fn = parts[0];
                if (parts.length == 2) {
                    name = parts[1];
                }
                File f = new File(fn);
                if (f.exists()) {
                    // Second arg is a file
                    wordLists.put(name, FileUtil.readLines(f));
                    output.line("Loaded word list '" + name + "'");
                } else {
                    if (wordLists.containsKey(fn)) {
                        // Display existing wordlist
                        for (String word : wordLists.get(fn)) {
                            output.line(" " + word);
                        }
                    } else {
                        output.error("File " + fn + " not found.");
                    }
                }
            }
            break;
        case "showconc":
            output.setShowConc(parseBoolean(arguments));
            System.out.println("Show concordances: " + (output.isShowConc() ? "ON" : "OFF"));
            break;
        case "verbose":
        case "v":
            output.setVerbose(arguments.isEmpty() || parseBoolean(arguments));
            if (output.isVerbose())
                output.setShowMatchInfo(true);
            output.line("Verbose: " + (output.isVerbose() ? "ON" : "OFF"));
            break;
        case "total":
            determineTotalNumberOfHits = parseBoolean(arguments);
            output.line("Determine total number of hits: " + (determineTotalNumberOfHits ? "ON" : "OFF"));
            break;
        case "field":
            if (arguments.isEmpty()) {
                contentsField = index.mainAnnotatedField();
                output.line("Searching main annotated field: " + contentsField.name());
            } else {
                AnnotatedFields annotatedFields = index.metadata().annotatedFields();
                if (!annotatedFields.exists(arguments)) {
                    // See if it's a version (e.g. different language in parallel corpus) of the main annotated field
                    String v2 = AnnotatedFieldNameUtil.changeParallelFieldVersion(index.mainAnnotatedField().name(), arguments);
                    if (annotatedFields.exists(v2))
                        arguments = v2;
                }
                if (annotatedFields.exists(arguments)) {
                    contentsField = annotatedFields.get(arguments);
                    output.line("Searching annotated field: " + contentsField.name());
                } else {
                    output.error("Annotated field '" + arguments + "' does not exist.");
                }
            }
            break;
        default:
            // Not a command; assume it's a query
            parseAndExecuteQuery(cmd);
            break;
        }
    }

    private Parser getCurrentParser() {
        return parsers.get(currentParserIndex);
    }

    private boolean parseBoolean(String v) {
        return v.equals("on") || v.equals("yes") || v.equals("true");
    }

    private void showContents(int docId) {
        if (!index.docExists(docId)) {
            output.line("Document " + docId + " does not exist.");
            return;
        }
        Document doc = index.luceneDoc(docId);
        ContentAccessor ca = index.contentAccessor(contentsField);
        output.line(ca.getDocumentContents(docId, doc));
    }

    private void showMetadata(int docId) {
        if (!index.docExists(docId)) {
            output.line("Document " + docId + " does not exist.");
            return;
        }
        Document doc = index.luceneDoc(docId);
        Map<String, String> metadata = new TreeMap<>(); // sort by key
        for (IndexableField f : doc.getFields()) {
            metadata.put(f.name(), f.stringValue());
        }
        for (Map.Entry<String, String> e : metadata.entrySet()) {
            String value = e.getValue();
            if (value.length() > 255) {
                output.line(e.getKey() + ": " + StringUtils.abbreviate(value, 255) + " (total length: " + value.length() + ")");
            } else {
                output.line(e.getKey() + ": " + value);
            }
        }
    }

    Output output;

    CommandReader commandReader;

    /**
     * Show the program head.
     */
    private void printProgramHead() {
        output.line("BlackLab Query Tool");
        output.line("===================");
    }

    /**
     * Parse and execute a query in the current query format.
     *
     * @param query the query
     */
    private void parseAndExecuteQuery(String query) {
        Timer t = new Timer();
        try {

            // See if we want to choose any random words
            if (query.contains("@@")) {
                query = chooseRandomWords(query);
                if (query == null)
                    return;
            }

            Parser parser = getCurrentParser();
            TextPattern pattern = parser.parse(index, query);
            if (pattern == null) {
                output.error("No query to execute.");
                return;
            }
            output.verbose("TextPattern: " + pattern);

            // If the query included filter clauses, use those. Otherwise use the global filter, if any.
            Query filter = parser.getIncludedFilterQuery();
            if (filter == null)
                filter = filterQuery;

            // Execute search
            BLSpanQuery spanQuery = pattern.toQuery(QueryInfo.create(index, contentsField), filter, false, false);
            output.verbose("SpanQuery: " + spanQuery.toString(contentsField.name()));
            try {
                output.verbose("Rewritten: " + spanQuery.rewrite(index.reader()).toString(contentsField.name()));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            AnnotatedField field = index.annotatedField(spanQuery.getField()); // query may override field, e.g. rfield(...)
            SearchHits search = index.search(field).find(spanQuery);
            timings = search.queryInfo().timings();

            if (alwaysSortBy != null) {
                search = search.sort(HitProperty.deserialize(index, contentsField, alwaysSortBy, contextSize));
            }

            hits = search.execute();
            docs = null;
            groups = null;
            sortedHits = null;
            collocations = null;
            showWhichGroup = -1;
            showSetting = ShowSetting.HITS;
            firstResult = 0;
            showResultsPage();
            if (determineTotalNumberOfHits)
                statInfo = Long.toString(hits.size());
            else
                statInfo = "?";
            commandWasQuery = true;
        } catch (InvalidQuery e) {
            // Parse error
            output.error(e.getMessage());
            output.error("(Type 'help' for examples or see https://blacklab.ivdnt.org/development/query-tool.html)");
        } catch (UnsupportedOperationException e) {
            // Unimplemented part of query language used
            e.printStackTrace(); // DEBUG createWeight bug
            output.error("Cannot execute query; " + e.getMessage());
            output.error("(Type 'help' for examples or see https://blacklab.ivdnt.org/development/query-tool.html)");
        }
    }

    private String chooseRandomWords(String query) {
        StringBuilder resultString = new StringBuilder();
        Pattern regex = Pattern.compile("@@[A-Za-z0-9_\\-]+");
        Matcher regexMatcher = regex.matcher(query);
        while (regexMatcher.find()) {
            // You can vary the replacement text for each match on-the-fly
            String wordListName = regexMatcher.group().substring(2);
            List<String> list = wordLists.get(wordListName);
            if (list == null) {
                output.error("Word list '" + wordListName + "' not found!");
                return null;
            }
            int randomIndex = (int) (Math.random() * list.size());
            regexMatcher.appendReplacement(resultString, list.get(randomIndex));
        }
        regexMatcher.appendTail(resultString);
        query = resultString.toString();
        return query;
    }

    /**
     * Show the a specific page of results.
     *
     * @param pageNumber which page to show
     */
    private void showPage(long pageNumber) {
        if (hits != null) {

            if (determineTotalNumberOfHits) {
                // Clamp page number of total number of hits
                long totalResults = switch (showSetting) {
                    case COLLOC -> collocations.size();
                    case GROUPS -> groups.size();
                    default -> hits.size();
                };

                long totalPages = (totalResults + resultsPerPage - 1) / resultsPerPage;
                if (pageNumber < 0)
                    pageNumber = totalPages - 1;
                if (pageNumber >= totalPages)
                    pageNumber = 0;
            }

            // Next page
            firstResult = pageNumber * resultsPerPage;
            showResultsPage();
        }
    }

    /**
     * Show the next page of results.
     */
    private void nextPage() {
        showPage(firstResult / resultsPerPage + 1);
    }

    /**
     * Show the previous page of results.
     */
    private void prevPage() {
        showPage(firstResult / resultsPerPage - 1);
    }

    /**
     * Sort either hits or groups by the specified property.
     *
     * @param sortBy property to sort by
     */
    private void sortBy(String sortBy) {
        if (hits == null)
            return;

        switch (showSetting) {
        case COLLOC:
            output.error("Sorting collocations not supported");
            break;
        case GROUPS:
            sortGroups(sortBy.toLowerCase());
            break;
        default:
            String[] parts = sortBy.split(StringUtil.REGEX_WHITESPACE, 2);
            String sortByPart = parts[0];
            String propPart = parts.length > 1 ? parts[1] : null;
            sortHits(sortByPart, propPart);
            break;
        }
    }

    /** Desired context size */
    private ContextSize contextSize;

    /** Are we responsible for closing the BlackLabIndex? */
    private boolean shouldCloseIndex = true;

    /**
     * Sort hits by the specified property.
     *
     * @param sortBy hit property to sort by
     * @param annotationName (optional) if sortBy is a context property (say, hit text),
     *            this gives the token annotation to use for the context. Example: if
     *            this is "lemma", will look at the lemma(ta) of the hit text. If
     *            this is null, uses the "main annotation" (word form, usually).
     */
    private void sortHits(String sortBy, String annotationName) {
        Timer t = new Timer();
        timings.start();

        Hits hitsToSort = getCurrentHitSet();

        HitProperty crit = getCrit(sortBy, annotationName, -1);
        if (crit == null) {
            output.error("Invalid hit sort criterium: " + sortBy
                    + " (valid are: match, before, after, doc, <metadatafield>)");
        } else {
            sortedHits = hitsToSort.sort(crit);
            firstResult = 0;
            timings.record("sort");
            showResultsPage();
        }
    }

    /**
     * Sort groups by the specified property.
     *
     * @param sortBy property to sort by
     */
    private void sortGroups(String sortBy) {
        HitGroupProperty crit = null;
        if (sortBy.equals(HitGroupPropertyIdentity.ID) || sortBy.equals("id"))
            crit = HitGroupPropertyIdentity.get();
        else if (sortBy.startsWith(HitGroupPropertySize.ID))
            crit = HitGroupPropertySize.get();
        if (crit == null) {
            output.error("Invalid group sort criterium: " + sortBy
                    + " (valid are: id(entity), size)");
        } else {
            groups = groups.sort(crit);
            firstResult = 0;
            showResultsPage();
        }
    }

    /**
     * Group hits by the specified property.
     *
     * @param groupBy hit property to group by
     * @param annotationName (optional) if groupBy is a context property (say, hit text),
     *            this gives the token annotation to use for the context. Example: if
     *            this is "lemma", will look at the lemma(ta) of the hit text. If
     *            this is null, uses the "main annotation" (word form, usually).
     */
    private void groupBy(String groupBy, String annotationName) {
        if (hits == null)
            return;

        Timer t = new Timer();

        // Group results
        HitProperty crit = getCrit(groupBy, annotationName, 1);
        groups = hits.group(crit, -1);
        showSetting = ShowSetting.GROUPS;
        sortGroups(HitGroupPropertySize.ID);
        timings.record("group");
        output.timings(timings);
    }

    private HitProperty getCrit(String critType, String annotationName, int numberOfContextTokens) {
        HitProperty crit;
        if (StringUtils.isEmpty(annotationName) && contentsField.annotation(critType) != null) {
            // Assume we want to group by matched text if we don't specify it explicitly.
            annotationName = critType;
            critType = "match";
        }
        Annotation annotation;
        try {
            annotation = annotationName == null ? contentsField.mainAnnotation() : contentsField.annotation(annotationName);
        } catch (Exception e) {
            output.error("Unknown annotation: " + annotationName);
            return null;
        }
        switch (critType) {
        case "word":
        case "match":
        case "hit":
            crit = new HitPropertyHitText(index, annotation);
            break;
        case HitPropertyBeforeHit.ID:
            crit = new HitPropertyBeforeHit(index, annotation, null, numberOfContextTokens);
            break;
        case HitPropertyLeftContext.ID:
            crit = new HitPropertyLeftContext(index, annotation, null, numberOfContextTokens);
            break;
        case HitPropertyAfterHit.ID:
            crit = new HitPropertyAfterHit(index, annotation, null, numberOfContextTokens);
            break;
        case HitPropertyRightContext.ID:
            crit = new HitPropertyRightContext(index, annotation, null, numberOfContextTokens);
            break;
        case HitPropertyDocumentId.ID:
            crit = new HitPropertyDocumentId();
            break;
        case "lempos": // special case for testing
            HitProperty p1 = new HitPropertyHitText(index, contentsField.annotation("lemma"));
            HitProperty p2 = new HitPropertyHitText(index, contentsField.annotation("pos"));
            crit = new HitPropertyMultiple(p1, p2);
            break;
        default:
            if (index.metadataFields().exists(critType)) {
                // name of a metadata field. Group/sort on that.
                crit = new HitPropertyDocumentStoredField(index, critType);
            } else {
                // Regular BLS serialized hit property. Decode it.
                crit = HitProperty.deserialize(hits, critType, contextSize);
            }
            break;
        }
        return crit;
    }

    /**
     * Switch between showing all hits, groups, and the hits in one group.
     *
     * @param showWhat what type of results to show
     */
    private void changeShowSettings(String showWhat) {
        sortedHits = null;
        if (showWhat.equals("hits")) {
            showSetting = ShowSetting.HITS;
            showWhichGroup = -1;
        } else if (showWhat.equals("docs")) {
            showSetting = ShowSetting.DOCS;
        } else if (showWhat.startsWith("colloc") && hits != null) {
            showSetting = ShowSetting.COLLOC;
            if (showWhat.length() >= 7) {
                String newCollocAnnot = showWhat.substring(7);
                if (!newCollocAnnot.equals(collocAnnotation.name())) {
                    collocAnnotation = contentsField.annotation(newCollocAnnot);
                    collocations = null;
                }
            }
        } else if (showWhat.equals("groups") && groups != null) {
            showSetting = ShowSetting.GROUPS;
        } else if (showWhat.startsWith("group ") && groups != null) {
            showWhichGroup = parseInt(showWhat.substring(6), 1) - 1;
            if (showWhichGroup < 0 || showWhichGroup >= groups.size()) {
                output.error("Group doesn't exist");
                showWhichGroup = -1;
            } else
                showSetting = ShowSetting.HITS; // Show hits in group, not all the groups
        }
        showResultsPage();
    }

    /**
     * Close the BlackLabIndex object.
     */
    private void cleanup() {
        if (shouldCloseIndex)
            index.close();
    }

    /**
     * Show the current results page (either hits or groups).
     */
    private void showResultsPage() {
        switch (showSetting) {
        case COLLOC:
            showCollocations();
            break;
        case GROUPS:
            output.groups(groups, firstResult, resultsPerPage);
            break;
        case DOCS:
            showDocsPage();
            break;
        default:
            showHitsPage();
            break;
        }
        output.timings(timings);
    }

    /**
     * Show the current page of collocations.
     */
    private void showCollocations() {
        if (collocations == null) {
            // Case-sensitive collocations..?
            if (collocAnnotation == null) {
                AnnotatedField field = hits.field();
                collocAnnotation = field.mainAnnotation();
            }

            collocations = hits.collocations(collocAnnotation, contextSize, index.defaultMatchSensitivity(), true);
        }
        output.collocations(collocations, firstResult, resultsPerPage);
    }

    private void showDocsPage() {
        Hits currentHitSet = getCurrentHitSet();
        if (docs == null) {
            if (currentHitSet != null)
                docs = currentHitSet.perDocResults(Results.NO_LIMIT);
            else if (filterQuery != null) {
                docs = index.queryDocuments(filterQuery);
            } else {
                output.line("No documents to show (set filterquery or search for hits first)");
                return;
            }
        }
        output.docs(docs.window(firstResult, resultsPerPage), docs.size());
    }

    /**
     * Show the current page of hits.
     */
    private void showHitsPage() {
        timings.start();
        Hits hitsToShow = getCurrentSortedHitSet();
        if (hitsToShow == null)
            return; // nothing to show
        output.hits(hitsToShow, hitsToShow.window(firstResult, resultsPerPage), this);
    }

    /**
     * Returns the hit set we're currently looking at.
     * <p>
     * This is either all hits or the hits in one group.
     * <p>
     * If a sort has been applied, returns the sorted hits.
     *
     * @return the hit set
     */
    private Hits getCurrentSortedHitSet() {
        if (sortedHits != null)
            return sortedHits;
        return getCurrentHitSet();
    }

    /**
     * Returns the hit set we're currently looking at.
     * <p>
     * This is either all hits or the hits in one group.
     *
     * @return the hit set
     */
    private Hits getCurrentHitSet() {
        Hits hitsToShow = hits;
        if (showWhichGroup >= 0) {
            Group<Hit> g = groups.get(showWhichGroup);
            hitsToShow = (Hits)g.storedResults();
        }
        return hitsToShow;
    }

    public QueryTimings getTimings() {
        return timings;
    }

    public ConcordanceType getConcType() {
        return concType;
    }

    public ContextSize getContextSize() {
        return contextSize;
    }

    public boolean isStripXml() {
        return stripXML;
    }

    public AnnotatedField getContentsField() {
        return contentsField;
    }

    public boolean isDetermineTotalNumberOfHits() {
        return determineTotalNumberOfHits;
    }

    public long getResultsPerPage() {
        return resultsPerPage;
    }

}
