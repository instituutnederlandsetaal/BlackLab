package nl.inl.blacklab.querytool;

import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.WordUtils;
import org.apache.lucene.document.Document;

import nl.inl.blacklab.resultproperty.PropertyValueDoc;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.Concordance;
import nl.inl.blacklab.search.ConcordanceType;
import nl.inl.blacklab.search.DocFragment;
import nl.inl.blacklab.search.Kwic;
import nl.inl.blacklab.search.TermFrequency;
import nl.inl.blacklab.search.TermFrequencyList;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldNameUtil;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.FieldType;
import nl.inl.blacklab.search.indexmetadata.IndexMetadata;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.indexmetadata.MetadataField;
import nl.inl.blacklab.search.indexmetadata.MetadataFields;
import nl.inl.blacklab.search.indexmetadata.RelationUtil;
import nl.inl.blacklab.search.lucene.MatchInfo;
import nl.inl.blacklab.search.lucene.RelationInfo;
import nl.inl.blacklab.search.lucene.RelationListInfo;
import nl.inl.blacklab.search.results.Concordances;
import nl.inl.blacklab.search.results.DocResults;
import nl.inl.blacklab.search.results.Group;
import nl.inl.blacklab.search.results.Hit;
import nl.inl.blacklab.search.results.HitGroups;
import nl.inl.blacklab.search.results.Hits;
import nl.inl.blacklab.search.results.Kwics;
import nl.inl.blacklab.search.results.QueryTimings;
import nl.inl.blacklab.search.results.ResultsStats;
import nl.inl.blacklab.tools.QueryTool;
import nl.inl.util.LuceneUtil;
import nl.inl.util.XmlUtil;

class Output {

    /**
     * Our output writer.
     */
    private PrintWriter out = new PrintWriter(new OutputStreamWriter(System.out, Charset.defaultCharset()), true);

    /**
     * Our error writer (if null, output errors to out as well)
     */
    private PrintWriter err = new PrintWriter(new OutputStreamWriter(System.err, Charset.defaultCharset()), true);

    /** Show extra information about query being processed? */
    private boolean verbose = false;

    /**
     * Was a commands file specified (using -f)?
     */
    private boolean batchMode = false;

    /**
     * Show output of commands? (useful for correctness tests, not for performance tests; see --mode)
     */
    private boolean showOutput = true;

    /**
     * Show output of commands? (useful for performance tests, not for correctness tests; see --mode)
     */
    private boolean showStats = true;

    /**
     * Show doc ids in the results? (makes results incomparable between indexes)
     */
    private boolean showDocIds = true;

    /**
     * Show match info in the results?
     */
    private boolean showMatchInfo = false;

    /** Show document titles between hits? */
    private boolean showDocTitle = false;

    /** Show concordances or not? (if not, just shows number of hits) */
    private boolean showConc = true;

    private static String getContextPart(DocFragment fragment, Map<String, MatchInfo> matchInfo,
            AnnotatedField annotatedField, int pos, String mainAnnot, boolean includePunctBefore, String punctAfter) {
        StringBuilder contextPart = new StringBuilder();
        for (int i = 0; i < fragment.length(); i++) {
            List<String> open = new ArrayList<>();
            List<String> close = new ArrayList<>();
            for (Map.Entry<String, MatchInfo> e: matchInfo.entrySet()) {
                MatchInfo mi = e.getValue();
                String name = e.getKey();
                addMatchInfoIndicator(annotatedField, pos, mi, name, open, close, -1);
            }
            if (i > 0 || includePunctBefore) {
                contextPart
                        .append(fragment.getValue(i, AnnotatedFieldNameUtil.PUNCTUATION_ANNOT_NAME));
            }
            Collections.reverse(close);
            contextPart
                    .append(StringUtils.join(open, ""))
                    .append(fragment.getValue(i, mainAnnot))
                    .append(StringUtils.join(close, ""));
            pos++;
        }
        if (punctAfter != null) {
            contextPart.append(punctAfter);
        }
        return contextPart.toString();
    }

    private static void addMatchInfoIndicator(AnnotatedField annotatedField, int pos, MatchInfo mi, String name,
            List<String> open, List<String> close, int number) {
        if (number > 0)
            name += "#" + number;
        String namePrefix = "", nameSuffix = "";
        int start = -1, end = -1;
        if (mi instanceof RelationListInfo) {
            int i = 1;
            for (RelationInfo rel: ((RelationListInfo) mi).getRelations()) {
                addMatchInfoIndicator(annotatedField, pos, rel, name, open, close, i);
                i++;
            }
        } else if (mi instanceof RelationInfo && mi.getType() == MatchInfo.Type.RELATION) { // (rel, not tag)
            // For relations, we have to either highlight the source or the target, which may be in different
            // fields.
            RelationInfo rel = (RelationInfo) mi;

            boolean isSourceField = rel.getField().equals(annotatedField.name());
            if ((pos == rel.getSourceStart() || pos + 1 == rel.getSourceEnd()) && isSourceField) {
                // Highlight relation source
                start = rel.getSourceStart();
                end = rel.getSourceEnd();
                String relType = RelationUtil.typeFromFullType(rel.getFullRelationType());
                if (relType.length() > 5) {
                    relType = relType.substring(0, 5).replaceAll("[\\s_\\-]+$", "");
                    relType += "…";
                }
                String optRelType = (name.contains(relType) ? "" : " " + relType);
                nameSuffix = optRelType + " →";
            }
            boolean isTargetField = rel.getTargetField().equals(annotatedField.name());
            if ((pos == rel.getTargetStart() || pos + 1 == rel.getTargetEnd()) && isTargetField) {
                // Highlight relation target
                start = rel.getTargetStart();
                end = rel.getTargetEnd();
                namePrefix = "→";
            }
        } else if (mi.getField().equals(annotatedField.name())) {
            // Match info is in this field; not a relation, so just use the full span
            start = mi.getSpanStart();
            end = mi.getSpanEnd();
        } else {
            // Some other field; ignore
            return;
        }
        if (start == pos)
            open.add(hlStart(namePrefix + name + nameSuffix));
        if (end == pos + 1)
            close.add(hlEnd(name));
    }

    static String hlStart(String name) {
        return "[" + name + "]";
    }

    static String hlEnd(String name) {
        return "[/" + name + "]";
    }

    static void usage() {
        System.err.println(
                "Usage: " + QueryTool.class.getName() + " [options] <indexDir>\n" +
                        "\n" +
                        "Options (mostly useful for batch testing):\n" +
                        "-f <file>            Execute batch commands from file and exit\n" +
                        "-v                   Start in verbose mode (show query & rewrite)\n" +
                        "--mode all           Show results and timings (default without -f)\n" +
                        "--mode correctness,  Show results but no timings (default for -f)\n" +
                        "--mode c\n" +
                        "--mode performance,  Show timings but no results\n" +
                        "--mode p,\n" +
                        "-e <encoding>        Specify what output encoding to use [system default]\n" +
                        "\n" +
                        WordUtils.wrap("Batch command files should contain one command per line, or multiple " +
                        "commands on a single line separated by && (use this e.g. to time " +
                        "querying and sorting together). Lines starting with # are comments. " +
                        "Comments are printed on stdout as well. Lines starting with - will " +
                        "not be reported. Start a line with -# for an unreported comment.", 80));
    }

    void showIndexMetadata(BlackLabIndex index) {
        IndexMetadata s = index.metadata();
        line("INDEX STRUCTURE FOR INDEX " + index.name() + "\n");
        line("ANNOTATED FIELDS");
        showAnnotatedField(index, s.mainAnnotatedField()); // show the main field first
        for (AnnotatedField cf: s.annotatedFields()) {
            if (cf != s.mainAnnotatedField())
                showAnnotatedField(index, cf);
        }

        line("\nMETADATA FIELDS");
        MetadataFields mf = s.metadataFields();
        for (MetadataField field: mf) {
            String special = "";
            if (field.name().equals(s.custom().get("titleField", "")))
                special = "TITLEFIELD";
            else if (field.name().equals(s.custom().get("authorField", "")))
                special = "AUTHORFIELD";
            else if (field.name().equals(s.custom().get("dateField", "")))
                special = "DATEFIELD";
            else if (mf.pidField() != null && field.name().equals(mf.pidField().name()))
                special = "PIDFIELD";
            if (!special.isEmpty())
                special = " (" + special + ")";
            FieldType type = field.type();
            line("- " + field.name() + (type == FieldType.TOKENIZED ? "" : " (" + type + ")")
                    + special);
        }
    }

    private void showAnnotatedField(BlackLabIndex index, AnnotatedField cf) {
        line("- " + cf.name());
        for (Annotation annot: cf.annotations()) {
            line("  * Annotation: " + describeAnnotation(index, annot));
        }
        line("  * " + (cf.hasContentStore() ? "Includes" : "No") + " content store");
    }

    public String describeAnnotation(BlackLabIndex index, Annotation annotation) {
        String sensitivityDesc;
        if (annotation.hasSensitivity(MatchSensitivity.SENSITIVE)) {
            if (annotation.hasSensitivity(MatchSensitivity.INSENSITIVE)) {
                if (annotation.hasSensitivity(MatchSensitivity.CASE_INSENSITIVE)) {
                    sensitivityDesc = "case/diacritics sensitivity separate";
                } else {
                    sensitivityDesc = "sensitive and insensitive";
                }
            } else {
                sensitivityDesc = "sensitive only";
            }
        } else {
            sensitivityDesc = "insensitive only";
        }

        MatchSensitivity s = annotation.hasSensitivity(MatchSensitivity.INSENSITIVE) ? MatchSensitivity.INSENSITIVE : MatchSensitivity.SENSITIVE;
        String fieldName = annotation.sensitivity(s).luceneField();
        long maxTermsPerLeafReader = LuceneUtil.getMaxTermsPerLeafReader(index.reader(), fieldName);
        String luceneFieldInfo = " (lucene: " + fieldName + "; max. LR terms = " + maxTermsPerLeafReader + ") ";

        int numberOfUniqueTerms = annotation.hasForwardIndex() ? index.forwardIndex(annotation.field()).get(annotation).terms().numberOfTerms() : 0;
        return annotation.name() + luceneFieldInfo + (annotation.hasForwardIndex() ? " (+FI, " + numberOfUniqueTerms + " unique terms)" : "") + ", " + sensitivityDesc;
    }

    /**
     * Print command and query help.
     */
    void printHelp(Parser parser) {
        if (isBatchMode())
            return;
        String langAvail = "BCQL, Lucene, ContextQL (EXPERIMENTAL)";

        line("Control commands:");
        line("  p(rev) / n(ext) / page <n>         # Page through results");
        line("  sort {match|before|after} [annot]  # Sort query results  (before = context before match, etc.)");
        line("  sort <property>                    #   (like BLS, e.g. before:lemma:s)");
        line("  group {match|before|after} [annot] # Group query results (annot = e.g. 'word', 'lemma', 'pos')");
        line("  group <property>                   #   (like BLS, e.g. hit:lemma:i or capture:pos:i:A)");
        line("  hits / groups / group <n> / colloc # Switch between results modes");
        line("  field <name>                       # Set the annotated field to search");
        line("  context <n>                        # Set number of words to show around hits");
        line("  pagesize <n>                       # Set number of hits to show per page");
        line("  snippet <x>                        # Show longer snippet around hit x");
        line("  doc <id>                           # Show metadata for doc id");
        line("  doccontents <id>                   # Retrieve contents of doc id");
        line("  snippetsize <n>                    # Words to show around hit in longer snippet");
        line("  sensitive {on|off|case|diac}       # Set case-/diacritics-sensitivity");
        line("  filter <luceneQuery>               # Set document filter, e.g. title:\"Smith\"");
        line("  doctitle {on|off}                  # Show document titles between hits?");
        line("  struct                             # Show index structure");
        line("  help                               # This message");
        line("  sw(itch)                           # Switch languages");
        line("                                     # (" + langAvail + ")");

        line("  exit                               # Exit program");

        line("\nBatch testing commands (start in batch mode with -f <commandfile>):");
        line("  wordlist <file> <listname>         # Load a list of words");
        line("  @@<listname>                       # Substitute a random word from list (use in query)");
        line("  repeat <n> <query>                 # Repeat a query n times (with different random words)");
        line("  sleep <f>                          # Sleep a number of seconds");
        line("");

        printQueryHelp(parser);
    }

    /**
     * Show the current page of group results.
     */
    public void groups(HitGroups groups, long firstResult, long resultsPerPage) {
        for (long i = firstResult; i < groups.size() && i < firstResult + resultsPerPage; i++) {
            Group<Hit> g = groups.get(i);
            line(String.format("%4d. %5d %s", i + 1, g.size(), g.identity().toString()));
        }
        line(groups.size() + " groups");
    }

    public void collocations(TermFrequencyList collocations, long firstResult, long resultsPerPage) {
        int i = 0;
        for (TermFrequency coll : collocations) {
            if (i >= firstResult && i < firstResult + resultsPerPage) {
                long j = i - firstResult + 1;
                line(String.format("%4d %7d %s", j, coll.frequency, coll.term));
            }
            i++;
        }
        line(collocations.size() > resultsPerPage ?
                (firstResult + 1) + "-" + i + " of " + collocations.size() + " collocations" :
                collocations.size() + " collocations");
    }

    /**
     * Report how long an operation took
     */
    public void timings(QueryTimings timings) {
        verbose(!timings.isEmpty() ?
                "Query timings:\n" + timings.summarize() :
                "Query took <1ms (no timings recorded)");
    }

    public void line(String str) {
        if (showOutput)
            out.println(str);
    }

    public void noNewLine(String str) {
        if (showOutput)
            out.print(str);
    }

    public void verbose(String str) {
        if (verbose)
            line(str);
    }

    public void error(String str) {
        if (err == null)
            out.println(str);
        else
            err.println(str);
    }

    public void command(String cmd) {
        if (isBatchMode() && !cmd.isEmpty() && !cmd.startsWith("#")) {
            // Verbose batch mode, show command before output
            line("COMMAND: " + cmd);
        }

        // In stats mode, print comments (unless silenced)
        if (cmd.startsWith("#")) {
            // Comment
            stats(cmd);
        }

    }

    public void stats(String str) {
        if (batchMode && showStats)
            out.println(str);
    }

    public void flush() {
        out.flush();
        if (err != null && err != out)
            err.flush();
    }

    public void setOutputWriter(PrintWriter out) {
        this.out = out;
    }

    public void setErrorWriter(PrintWriter err) {
        this.err = err;
    }

    public boolean isVerbose() {
        return verbose;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    public boolean isBatchMode() {
        return batchMode;
    }

    public void setBatchMode(boolean batchMode) {
        this.batchMode = batchMode;
    }

    public void setShowOutput(boolean showOutput) {
        this.showOutput = showOutput;
    }

    public void setShowStats(boolean showStats) {
        this.showStats = showStats;
    }

    public boolean isShowDocIds() {
        return showDocIds;
    }

    public void setShowDocIds(boolean showDocIds) {
        this.showDocIds = showDocIds;
    }

    public void setShowMatchInfo(boolean showMatchInfo) {
        this.showMatchInfo = showMatchInfo;
    }

    public boolean isShowDocTitle() {
        return showDocTitle;
    }

    public void setShowDocTitle(boolean showDocTitle) {
        this.showDocTitle = showDocTitle;
    }

    public boolean isShowConc() {
        return showConc;
    }

    public void setShowConc(boolean showConc) {
        this.showConc = showConc;
    }

    public void matchInfos(Map<String, MatchInfo> matchInfos, String defaultFieldName) {
        if (matchInfos != null && showMatchInfo)
            line("MATCH INFO: " + matchInfoToString(matchInfos, defaultFieldName));
    }

    private String matchInfoToString(Map<String, MatchInfo> matchInfos, String defaultFieldName) {
        return matchInfos.entrySet().stream()
                .sorted( (a, b) -> {
                    MatchInfo ma = a.getValue();
                    MatchInfo mb = b.getValue();
                    if (ma == null && mb == null)
                        return 0;
                    else if (ma == null)
                        return -1;
                    else if (mb == null)
                        return 1;
                    MatchInfo.Type at = ma.getType();
                    MatchInfo.Type bt = mb.getType();
                    // Sort by type
                    if (at != bt)
                        return at.compareTo(bt);
                    if (at == MatchInfo.Type.SPAN) {
                        // Sort capture groups by name
                        return a.getKey().compareTo(b.getKey());
                    } else {
                        // Sort other match info by value
                        // ((list of) relations and inline tags)
                        return a.getValue().compareTo(b.getValue());
                    }
                })
                .map(e -> {
                    MatchInfo mi = e.getValue();
                    if (mi == null)
                        return "(null)";
                    return e.getKey() + "=" + mi.toString(defaultFieldName);
                })
                .collect(Collectors.joining(", "));
    }

    public void hits(Hits total, Hits window, QueryToolImpl queryTool) {
        BlackLabIndex index = window.queryInfo().index();

        if (!isShowConc()) {
            if (queryTool.isDetermineTotalNumberOfHits() || total.resultsStats().done()) {
                // Wait until all collected and show total number of hits, no concordances
                line(total.resultsStats().processedTotal() + " hits");
            } else {
                // No total; just show how many so far
                long i = total.resultsStats().processedSoFar();
                line((i >= queryTool.getResultsPerPage() ? "At least " : "") + i + " hits (total not determined)");
            }
            return;
        }

        // Compile hits display info and calculate necessary width of left context column
        int leftContextMaxSize = 10; // number of characters to reserve on screen for left context
        Concordances concordances = window.concordances(queryTool.getContextSize(), queryTool.getConcType());
        Kwics kwics = queryTool.getConcType() == ConcordanceType.FORWARD_INDEX ? concordances.getKwics() : null;
        List<HitToShow> toShow = new ArrayList<>();
        for (Hit hit: window) {
            HitToShow hitToShow;
            if (kwics != null) {
                Map<String, MatchInfo> matchInfo = window.hasMatchInfo() ? window.getMatchInfoMap(hit) :
                        Collections.emptyMap();
                hitToShow = showHitFromForwardIndex(hit, kwics.get(hit), matchInfo, window.field());

                Map<String, Kwic> fkwics = kwics.getForeignKwics(hit);
                if (fkwics != null) {
                    for (Map.Entry<String, Kwic> e: fkwics.entrySet()) {
                        String fieldName = e.getKey();
                        AnnotatedField annotatedField = index.metadata().annotatedFields().get(fieldName);
                        if (annotatedField == null)
                            throw new RuntimeException();
                        Kwic kwic = e.getValue();
                        Hit fhit = Hit.create(hit.doc(), kwic.fragmentStartInDoc(), kwic.fragmentEndInDoc(),
                                hit.matchInfo());
                        hitToShow.addForeign(fieldName, showHitFromForwardIndex(fhit, kwic, matchInfo, annotatedField));
                    }
                }
            } else {
                hitToShow = showHitFromContentStore(hit, concordances, window, queryTool.isStripXml());
            }
            toShow.add(hitToShow);
            if (leftContextMaxSize < hitToShow.left.length())
                leftContextMaxSize = hitToShow.left.length();
        }

        // Display hits
        String optFormatDocId = isShowDocTitle() || !isShowDocIds() ? "" : " [doc %04d]";
        String format = "%4d." + optFormatDocId +
                " %" + leftContextMaxSize + "s" + hlStart(QueryToolImpl.MATCH) + "%s" +
                hlEnd(QueryToolImpl.MATCH) + "%s";
        int currentDoc = -1;
        String titleField = index.metadata().custom().get("titleField", "");
        long hitNr = window.windowStats().first() + 1;
        for (HitToShow hit : toShow) {
            if (isShowDocTitle() && hit.doc != currentDoc) {
                if (currentDoc != -1)
                    line("");
                currentDoc = hit.doc;
                Document d = index.luceneDoc(currentDoc);
                String title = d.get(titleField);
                if (title == null)
                    title = "(doc #" + currentDoc + ", no " + titleField + " given)";
                else
                    title = title + " (doc #" + currentDoc + ")";
                line("--- " + title + " ---");
            }
            if (isShowDocTitle() || !isShowDocIds())
                line(String.format(format, hitNr, hit.left, hit.hitText, hit.right));
            else
                line(String.format(format, hitNr, hit.doc, hit.left, hit.hitText, hit.right));
            for (Map.Entry<String, HitToShow> e: hit.foreignHits.entrySet()) {
                String fieldName = e.getKey();
                HitToShow fhit = e.getValue();
                line(String.format("    %s: %s%s%s", fieldName, fhit.left, fhit.hitText, fhit.right));
            }
            matchInfos(hit.matchInfos, queryTool.getContentsField().name());
            hitNr++;
        }
        queryTool.getTimings().record("kwics");

        // Summarize
        String msg;
        ResultsStats hitsStats = total.hitsStats();
        if (!queryTool.isDetermineTotalNumberOfHits()) {
            msg = hitsStats.countedSoFar() + " hits counted so far (total not determined)";
        } else {
            long numberRetrieved = hitsStats.processedTotal();
            ResultsStats docsStats = total.docsStats();
            String hitsInDocs = numberRetrieved + " hits in " + docsStats.processedTotal() + " documents";
            if (window.maxStats().hitsProcessedExceededMaximum()) {
                if (window.maxStats().hitsCountedExceededMaximum()) {
                    msg = hitsInDocs + " retrieved, more than " + hitsStats.countedTotal() + " ("
                            + docsStats.countedTotal() + " docs) total";
                } else {
                    msg = hitsInDocs + " retrieved, " + hitsStats.countedTotal() + " (" + docsStats.countedTotal()
                            + " docs) total";
                }
            } else {
                msg = hitsInDocs;
            }
            queryTool.getTimings().record("count");
        }
        line(msg);
    }

    private HitToShow showHitFromForwardIndex(Hit hit, Kwic kwic, Map<String, MatchInfo> matchInfo, AnnotatedField annotatedField) {
        // NOTE: if annotatedField == null, it means this hit is in the main field searched;
        //       it would only ever be non-null for parallel corpora where some match info can be captured in another
        //       field than the main search field.
        String mainAnnotName = annotatedField.mainAnnotation().name();
        DocFragment fragMatch = kwic.fragMatch();
        DocFragment fragBefore = kwic.fragBefore();
        String punctAfter = fragMatch.length() == 0 ? "" : fragMatch.getValue(0, AnnotatedFieldNameUtil.PUNCTUATION_ANNOT_NAME);
        String before = getContextPart(fragBefore, matchInfo, annotatedField, hit.start() - fragBefore.length(), mainAnnotName, true,
                punctAfter);
        String match = getContextPart(fragMatch, matchInfo, annotatedField, hit.start(), mainAnnotName, false, null);
        String after = getContextPart(kwic.fragAfter(), matchInfo, annotatedField, hit.end(), mainAnnotName, true, null);
        return new HitToShow(hit.doc(), before, match, after, matchInfo);
    }

    private HitToShow showHitFromContentStore(Hit hit, Concordances concordances, Hits window,
            boolean stripXML) {
        HitToShow hitToShow;
        Concordance conc = concordances.get(hit);

        // Filter out the XML tags
        String left, hitText, right;
        left = stripXML ? XmlUtil.xmlToPlainText(conc.left()) : conc.left();
        hitText = stripXML ? XmlUtil.xmlToPlainText(conc.match()) : conc.match();
        right = stripXML ? XmlUtil.xmlToPlainText(conc.right()) : conc.right();

        Map<String, MatchInfo> matchInfo = null;
        if (window.hasMatchInfo())
            matchInfo = window.getMatchInfoMap(hit);
        hitToShow = new HitToShow(hit.doc(), left, hitText, right, matchInfo);
        return hitToShow;
    }

    public void docs(DocResults window, long docsCounted) {
        BlackLabIndex index = window.queryInfo().index();
        // Compile hits display info and calculate necessary width of left context column
        String titleField = index.metadata().custom().get("titleField", "");
        long hitNr = window.windowStats().first() + 1;
        for (Group<Hit> result : window) {
            int docId = ((PropertyValueDoc)result.identity()).value();
            Document d = index.luceneDoc(docId);
            String title = d.get(titleField);
            if (title == null)
                title = "(doc #" + docId + ", no " + titleField + " given)";
            else
                title = title + " (doc #" + docId + ")";
            line(String.format("%4d. %s", hitNr, title));
            hitNr++;
        }

        // Summarize
        line(docsCounted + " docs");
    }

    /**
     * Print some examples of the currently selected query language.
     *
     * @param parser
     */
    void printQueryHelp(Parser parser) {
        parser.printHelp(this);
        line("");
    }

    /**
     * A hit we're about to show.
     * <p>
     * We need a separate structure because we filter out XML tags and need to know
     * the longest left context before displaying.
     */
    private static class HitToShow {
        public final int doc;

        public final String left;
        public final String hitText;
        public final String right;

        public final Map<String, MatchInfo> matchInfos;

        /**
         * Hits in other fields (parallel corpora)
         */
        public final Map<String, HitToShow> foreignHits = new TreeMap<>();

        public HitToShow(int doc, String left, String hitText, String right, Map<String, MatchInfo> matchInfos) {
            super();
            this.doc = doc;
            this.left = left;
            this.hitText = hitText;
            this.right = right;
            this.matchInfos = matchInfos;
        }

        public void addForeign(String fieldName, HitToShow hitToShow) {
            foreignHits.put(fieldName, hitToShow);
        }
    }
}
