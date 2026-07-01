package nl.inl.blacklab.server.lib;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.document.Document;
import org.jspecify.annotations.NonNull;

import nl.inl.blacklab.resultproperty.DocProperty;
import nl.inl.blacklab.resultproperty.PropertyValue;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.BlackLabIndexAbstract;
import nl.inl.blacklab.search.Kwic;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.Field;
import nl.inl.blacklab.search.indexmetadata.IndexMetadata;
import nl.inl.blacklab.search.indexmetadata.MetadataField;
import nl.inl.blacklab.search.indexmetadata.RelationUtil;
import nl.inl.blacklab.search.indexmetadata.RelationsStats;
import nl.inl.blacklab.search.lucene.MatchInfo;
import nl.inl.blacklab.search.lucene.RelationInfo;
import nl.inl.blacklab.search.lucene.RelationListInfo;
import nl.inl.blacklab.search.results.CorpusSize;
import nl.inl.blacklab.search.results.ResultGroups;
import nl.inl.blacklab.search.results.SampleParameters;
import nl.inl.blacklab.search.results.docs.DocGroup;
import nl.inl.blacklab.search.results.docs.DocGroups;
import nl.inl.blacklab.search.results.docs.DocResult;
import nl.inl.blacklab.search.results.docs.DocResults;
import nl.inl.blacklab.search.results.hitresults.HitGroup;
import nl.inl.blacklab.search.results.hitresults.HitGroups;
import nl.inl.blacklab.search.results.hitresults.HitResults;
import nl.inl.blacklab.search.results.hitresults.Kwics;
import nl.inl.blacklab.search.results.hits.EphemeralHit;
import nl.inl.blacklab.search.results.hits.Hits;
import nl.inl.blacklab.search.results.stats.ResultsStats;
import nl.inl.blacklab.search.textpattern.TextPattern;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.exceptions.InternalServerError;
import nl.inl.blacklab.server.lib.requests.CsvSettings;
import nl.inl.blacklab.server.lib.requests.HitsResponseSettings;
import nl.inl.blacklab.server.lib.requests.RequestDocs;
import nl.inl.blacklab.server.lib.requests.RequestHits;
import nl.inl.blacklab.server.lib.requests.RequestHitsGrouped;
import nl.inl.blacklab.server.lib.results.ResponseStreamer;
import nl.inl.blacklab.server.lib.results.ResultDocs;
import nl.inl.blacklab.server.lib.results.ResultHits;
import nl.inl.blacklab.server.lib.results.ResultHitsGrouped;
import nl.inl.blacklab.server.lib.results.ResultSummaryCommonFields;
import nl.inl.blacklab.server.lib.results.ResultSummaryNumHits;
import nl.inl.blacklab.server.lib.results.WebserviceOperations;
import nl.inl.blacklab.webservice.WsParam;

/**
 * Utility methods for writing CSV responses.
 *
 * Unlike the DataStream stuff, we can likely re-use this class for other implementations
 * of the webservice, so calls to WebserviceOperations haven't been factored out here.
 */
public class WriteCsv {

    private static final List<String> writeRowTemp = new ArrayList<>();

    public static final String CSV_VALUE_UNKNOWN = "[unknown]";

    private WriteCsv() {
    }

    public static String hitsGroupsResponse(ResultHitsGrouped resultHitsCsv, ResponseStreamer rs) throws BlsException {
        RequestHitsGrouped reqGroup = resultHitsCsv.getReqGroup();
        CsvSettings csvSettings = reqGroup.requestHits().csvSettings();
        HitGroups groups = resultHitsCsv.getGroups();

        DocProperty metadataGroupProperties = groups.groupCriteria().docPropsOnly();

        try {
            // Write the header
            List<String> row = new ArrayList<>(groups.groupCriteria().propNames());
            row.add("count");

            if (metadataGroupProperties != null) {
                row.add(ResponseStreamer.KEY_NUMBER_OF_DOCS);
                row.add(ResponseStreamer.KEY_SUBCORPUS_SIZE + "." + rs.KEY_SUBCORPUS_SIZE_DOCUMENTS);
                row.add(ResponseStreamer.KEY_SUBCORPUS_SIZE + "." + rs.KEY_SUBCORPUS_SIZE_TOKENS);
            }
            CSVPrinter printer = createHeader(row, csvSettings.declareSeparator());
            if (csvSettings.includeSummary()) {
                summaryCsvHits(printer, row.size(),
                        rs,
                        resultHitsCsv.getSummaryFields(), resultHitsCsv.getSummaryNumHits());
            }

            // write the groups
            for (HitGroup group : groups) {
                row.clear();
                row.addAll(group.identity().propValues());
                row.add(Long.toString(group.resultsStats().countedSoFar())); // count

                if (metadataGroupProperties != null) {
                    // Find size of corresponding subcorpus group
                    PropertyValue docPropValues = groups.groupCriteria().docPropValues(group.identity());
                    CorpusSize groupSubcorpusSize = WebserviceOperations.findSubcorpusSize(reqGroup.index(),
                            resultHitsCsv.getSubcorpusQuery(), metadataGroupProperties, docPropValues);
                    long numberOfDocsInGroup = group.docsStats().countedTotal();

                    row.add(Long.toString(numberOfDocsInGroup));
                    CorpusSize.Count totalCount = groupSubcorpusSize.getTotalCount();
                    row.add(totalCount.hasDocumentCount() ?
                            Long.toString(totalCount.getDocuments()) :
                            CSV_VALUE_UNKNOWN);
                    row.add(totalCount.hasTokenCount() ?
                            Long.toString(totalCount.getTokens()) :
                            CSV_VALUE_UNKNOWN);
                }

                printer.printRecord(row);
            }

            printer.flush();
            return printer.getOut().toString();
        } catch (IOException e) {
            throw new InternalServerError("Cannot write response: " + e.getMessage(), "INTERR_WRITING_HITS_CSV1");
        }
    }

    public static String hitsResponse(ResultHits resultHitsCsv, ResponseStreamer rs) throws BlsException {
        RequestHits reqHits = resultHitsCsv.getReqHits();
        BlackLabIndex index = reqHits.index();
        HitResults hitResults = resultHitsCsv.getHits();
        AnnotatedField annotatedField = hitResults.field();
        final Annotation mainTokenProperty = annotatedField.mainAnnotation();
        try {
            // Build the table headers
            // The first few columns are fixed, and an additional columns is appended per annotation of tokens in this corpus.

            String headerContext = "context";
            String headerBefore = rs.KEY_BEFORE + "_" + headerContext; // e.g. before_context (API v5)
            String headerAfter = rs.KEY_AFTER + "_" + headerContext;   // e.g. after_context  (API v5)
            List<String> row = new ArrayList<>(Arrays.asList(ResponseStreamer.KEY_DOC_PID, headerBefore, headerContext, headerAfter));
            for (Annotation a: resultHitsCsv.getAnnotationsToWrite()) {
                row.add(a.name());
            }

            // Add requested span attributes

            HitsResponseSettings hitsResponseSettings = reqHits.hitsResponseSettings();
            List<SpanAndAttributeName> spanAttributes = hitsResponseSettings.spanAttributes();
            if (spanAttributes.contains(new SpanAndAttributeName("*", "*"))) {
                // We want all span attributes. Look them up.
                // (listspanattributes=* or listspanattributes=*.*)
                spanAttributes = getAllSpanAttributes(annotatedField);
            } else {
                // We don't (yet) support e.g. listspanattributes=span.* to get all attributes of a specific span
                if (spanAttributes.stream().anyMatch(sa -> sa.attributeName().equals("*"))) {
                    throw new IllegalArgumentException("listspanattributes=span.* is not supported. To get all span attributes, use listspanattributes=*");
                }
            }

            for (SpanAndAttributeName spanAttr : spanAttributes) {
                row.add(escape("span " + spanAttr.spanName() + "." + spanAttr.attributeName()));
            }

            // Only output metadata if explicitly passed, do not print all fields if the parameter was omitted like the
            // normal hit response does (because that can result in a MASSIVE amount of repeated data)
            List<MetadataField> metadataFields = reqHits.metadataToInclude();
            for (MetadataField f: metadataFields) {
                 row.add(f.name());
            }

            CSVPrinter printer = createHeader(row, reqHits.csvSettings().declareSeparator());
            if (reqHits.csvSettings().includeSummary()) {
                summaryCsvHits(printer, row.size(),
                        rs, resultHitsCsv.getSummaryCommonFields(), resultHitsCsv.getSummaryNumHits());
            }

            Map<Integer, Document> luceneDocs = new HashMap<>();
            Hits hitsList = hitResults.getHits();
            Kwics kwics = hitsList.kwics(reqHits.contextSettings().size());
            for (EphemeralHit hit: hitsList) {
                Document doc = luceneDocs.get(hit.doc());
                if (doc == null) {
                    doc = index.luceneDoc(hit.doc());
                    luceneDocs.put(hit.doc(), doc);
                }
                String docPid = WebserviceOperations.getDocumentPid(index, hit.doc(), doc);
                writeHit(hit, kwics.get(hit), doc, mainTokenProperty,
                        resultHitsCsv.getAnnotationsToWrite(), docPid,
                        spanAttributes,
                        metadataFields, printer);
            }
            printer.flush();
            return printer.getOut().toString();
        } catch (IOException e) {
            throw new InternalServerError("Cannot write response: " + e.getMessage(), "INTERR_WRITING_HITS_CSV2");
        }
    }

    private static @NonNull List<SpanAndAttributeName> getAllSpanAttributes(AnnotatedField field) {
        List<SpanAndAttributeName> spanAttributes;
        spanAttributes = new ArrayList<>();
        RelationsStats relationsStats = field.getRelationsStats(0); // 0 is fine, we don't need values here
        RelationsStats.ClassStats tags = relationsStats.getClasses().get(RelationUtil.CLASS_INLINE_TAG);
        for (Map.Entry<String, RelationsStats.TypeStats> e : tags.getRelationTypes().entrySet()) {
            String tagName = e.getKey();
            RelationsStats.TypeStats stats = e.getValue();
            Set<String> attNames = stats.getAttributes().keySet();
            for (String attName : attNames) {
                spanAttributes.add(new SpanAndAttributeName(tagName, attName));
            }
        }
        return spanAttributes;
    }

    public static CSVPrinter createHeader(List<String> row, boolean declareSeparator) throws IOException {
        // Create the header, then explicitly declare the separator, as excel normally uses a locale-dependent CSV-separator...
        CSVFormat format = CSVFormat.EXCEL.withHeader(row.toArray(new String[0]));
        return format.print(new StringBuilder(declareSeparator ? "sep=,\r\n" : ""));
    }

    private static void writeHit(
            EphemeralHit hit,
            Kwic kwic,
            Document doc,
            Annotation mainTokenProperty,
            List<Annotation> otherTokenProperties,
            String docPid,
            List<SpanAndAttributeName> spanAttributes,
            List<MetadataField> metadataFieldsToWrite,
            CSVPrinter printer
    ) throws IOException {


        /*
         * Order of kwic/hitProps is always the same:
         * - punctuation (always present)
         * - other (non-internal) properties (in order of declaration in the index)
         * - word itself
         */
        // Only kwic supported, original document output not supported in csv currently.
        Annotation punct = mainTokenProperty.field().annotations().punct();
        printer.print(docPid);
        printer.print(interleave(kwic.before(punct), kwic.before(mainTokenProperty)));
        printer.print(interleave(kwic.match(punct), kwic.match(mainTokenProperty)));
        printer.print(interleave(kwic.after(punct), kwic.after(mainTokenProperty)));

        // Add all other properties in this word
        for (Annotation otherProp : otherTokenProperties)
            printer.print(StringUtils.join(kwic.match(otherProp), " "));

        // Add requested span attributes
        for (SpanAndAttributeName spanAttr : spanAttributes) {
            List<String> values = new ArrayList<>();
            Arrays.stream(hit.matchInfos())
                    .forEach(mi -> spanAttrValue(mi, spanAttr, values));
            printer.print(escape(values.toArray(new String[0])));
        }

        // other fields in order of appearance
        for (MetadataField field : metadataFieldsToWrite)
            printer.print(escape(doc.getValues(field.name())));
        printer.println();
    }

    private static void spanAttrValue(MatchInfo mi, SpanAndAttributeName spanAttr, List<String> result) {
        if (mi == null)
            return;
        if (mi instanceof RelationInfo ri) {
            // If this is the requested span attribute, return its value(s)
            if (ri.getRelationClass().equals(RelationUtil.CLASS_INLINE_TAG) &&
                    ri.getRelationType().equals(spanAttr.spanName())) {
                List<String> values = ri.getAttributes().getOrDefault(spanAttr.attributeName(), List.of());
                result.addAll(values);
            }
        } else if (mi instanceof RelationListInfo rli) {
            // Recursively collect values from the list
            rli.getRelations().forEach(relInfo -> spanAttrValue(relInfo, spanAttr, result));
        }
    }

    private static String interleave(List<String> a, List<String> b) {
        StringBuilder result = new StringBuilder();

        List<String> smallest = a.size() < b.size() ? a : b;
        List<String> largest = a.size() > b.size() ? a : b;
        for (int i = 0; i < smallest.size(); ++i) {
            result.append(a.get(i));
            result.append(b.get(i));
        }

        for (int i = largest.size() - 1; i >= smallest.size(); --i)
            result.append(largest.get(i));

        return result.toString();
    }

    /*
     * Create a single string value from (potentially) multiple input values.
     *
     * Multiple values are concatenated by a pipe symbol.
     * Pipe symbols, newlines, carriage returns and backslashes in the input
     * values are escaped with a backslash.
     * Any other required escaping should be taken care of by Commons CSV.
     */
    static String escape(String[] strings) {
        return Arrays.stream(strings)
                .map(value -> escape(value)
                        .replaceAll("\\|", "\\\\|"))
                .collect(Collectors.joining("|"));
    }

    /*
     * Escape symbols, newlines, carriage returns and backslashes.
     */
    static String escape(String value) {
        return value.replaceAll("\\\\", "\\\\\\\\")
                        .replaceAll("\n", "\\\\n")
                        .replaceAll("\r", "\\\\r");
    }

    static synchronized void writeRow(CSVPrinter printer, int numColumns, Object... values) {
        for (Object o : values)
            writeRowTemp.add(o.toString());
        for (int i = writeRowTemp.size(); i < numColumns; ++i)
            writeRowTemp.add("");
        try {
            printer.printRecord(writeRowTemp);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot write response");
        }
        writeRowTemp.clear();
    }



    /**
     * Output most of the fields of the search summary.
     *
     * @param printer       the output printer
     * @param numColumns    number of columns to output per row, minimum 2
     * @param rs            response streamer
     * @param scf           common fields for the summary
     * @param snh           number of hits etc. information for the summary
     */
    private static void addSummaryCsvCommon(
            CSVPrinter printer,
            int numColumns,
            ResponseStreamer rs,
            ResultSummaryCommonFields scf,
            ResultSummaryNumHits snh) {
        CorpusSize subcorpusSize = snh.subcorpusSize();
        String summ = ResponseStreamer.KEY_SUMMARY + ".";
        ParamsForResponse params = scf.getParamsForResponse();
        ResultGroups groups = scf.getGroups();
        for (Map.Entry<WsParam, Object> param : params.getTypedParameters().entrySet()) {
            WsParam par = param.getKey();
            if (par == WsParam.LIST_VALUES_FOR_ANNOTATIONS ||
                    par == WsParam.LIST_VALUES_FOR_METADATA_FIELDS ||
                    par == WsParam.LIST_VALUES_FOR_SPAN_ATTR)
                continue;
            writeRow(printer, numColumns, summ + rs.KEY_PARAMS + "." + par, param.getValue());
        }

        if (subcorpusSize != null) {
            String subcorpSize = summ + ResponseStreamer.KEY_SUBCORPUS_SIZE + ".";
            writeRow(printer, numColumns, subcorpSize + rs.KEY_SUBCORPUS_SIZE_DOCUMENTS,
                    subcorpusSize.getTotalCount().getDocuments());
            writeRow(printer, numColumns, subcorpSize + rs.KEY_SUBCORPUS_SIZE_TOKENS,
                    subcorpusSize.getTotalCount().getTokens());
        }

        if (groups != null) {
            writeRow(printer, numColumns, summ + ResponseStreamer.KEY_NUMBER_OF_GROUPS, groups.size());
            writeRow(printer, numColumns, summ + ResponseStreamer.KEY_LARGEST_GROUP_SIZE, groups.largestGroupSize());
        }

        SampleParameters sample = scf.sampleParams();
        if (sample != null) {
            writeRow(printer, numColumns, summ + ResponseStreamer.KEY_SAMPLE_SEED, sample.seed());
            if (sample.isPercentage()) {
                double percentage = Math.round(sample.percentageOfHits() * 100 * 100) / 100.0;
                writeRow(printer, numColumns, summ + ResponseStreamer.KEY_SAMPLE_PERCENTAGE, percentage);
            } else
                writeRow(printer, numColumns, summ + ResponseStreamer.KEY_SAMPLE_SIZE, sample.numberOfHitsSet());
        }
    }

    /**
     *
     * @param printer CSV printer
     * @param numColumns number of columns
     * @param rs response streamer
     * @param scf common fields for the summary
     * @param snh number of hits etc. information for the summary
     */
    private static void summaryCsvHits(CSVPrinter printer, int numColumns,
            ResponseStreamer rs, ResultSummaryCommonFields scf, ResultSummaryNumHits snh) {
        addSummaryCsvCommon(printer, numColumns, rs, scf, snh);
        String summ = ResponseStreamer.KEY_SUMMARY + ".";
        long numHits = snh.hitsStats().countedTotal();
        long numDocs = snh.docsStats().countedTotal();
        writeRow(printer, numColumns, summ + ResponseStreamer.KEY_NUMBER_OF_HITS, numHits);
        writeRow(printer, numColumns, summ + ResponseStreamer.KEY_NUMBER_OF_DOCS, numDocs);
    }

    /**
     * @param printer CSV printer
     * @param numColumns number of columns
     * @param docResults all docs as the input for groups, or contents of a specific group (viewgroup)
     */
    public static void summaryCsvDocs(
            CSVPrinter printer,
            int numColumns,
            DocResults docResults,
            ResponseStreamer rs,
            ResultSummaryCommonFields scf,
            ResultSummaryNumHits snh
    ) {
        addSummaryCsvCommon(printer, numColumns, rs, scf, snh);
        String summ = ResponseStreamer.KEY_SUMMARY + ".";
        writeRow(printer, numColumns, summ + ResponseStreamer.KEY_NUMBER_OF_DOCS, docResults.size());
        writeRow(printer, numColumns, summ + ResponseStreamer.KEY_NUMBER_OF_HITS,
                StreamSupport.stream(docResults.spliterator(), false).mapToLong(DocResult::size).sum());
    }

    public static String docGroups(ResultDocs result, ResponseStreamer rs) throws BlsException {
        DocResults inputDocsForGroups = result.getDocs();
        DocGroups groups = result.getGroups();
        DocResults subcorpusResults = result.getSubcorpusResults();
        try {
            // Write the header
            List<String> row = new ArrayList<>(groups.groupCriteria().propNames());
            row.add(ResponseStreamer.KEY_GROUP_SIZE); // size of the group in documents
            row.add(rs.KEY_NUMBER_OF_TOKENS); // tokens across all documents with hits in group
            // tokens across all document in group including docs without hits
            // might be equal to size+numberOfTokens, if the query didn't include a cql query
            // but don't bother omitting this data.
            row.add(ResponseStreamer.KEY_SUBCORPUS_SIZE + "." + rs.KEY_SUBCORPUS_SIZE_TOKENS);
            row.add(ResponseStreamer.KEY_SUBCORPUS_SIZE + "." + rs.KEY_SUBCORPUS_SIZE_DOCUMENTS);

            RequestDocs requestDocs = result.getRequestDocs();
            CsvSettings csvSettings = requestDocs.csvSettings();
            CSVPrinter printer = createHeader(row, csvSettings.declareSeparator());
            if (csvSettings.includeSummary()) {
                TextPattern pattern = requestDocs.optHits() == null ? null : requestDocs.optHits().patternOriginal();
                ResultsStats docsStats = inputDocsForGroups.resultsStats();
                ResultSummaryNumHits summaryNumHits = new ResultSummaryNumHits(null, docsStats, true, null,
                        subcorpusResults.subcorpusSize());
                ResultSummaryCommonFields summaryFields = new ResultSummaryCommonFields(pattern, null,
                        null, groups, null, null,
                        null, requestDocs.sampleParams(), result.paramsForResponse(),
                        null, summaryNumHits
                );
                summaryCsvDocs(printer, row.size(), inputDocsForGroups,
                        rs, summaryFields, summaryNumHits);
            }

            // write the groups
            for (DocGroup group : groups) {
                row.clear();
                row.addAll(group.identity().propValues());
                row.add(Long.toString(group.size()));
                row.add(Long.toString(group.totalTokens()));

                if (requestDocs.optHits() != null) {
                    PropertyValue docPropValues = group.identity();
                    CorpusSize groupSubcorpusSize = WebserviceOperations.findSubcorpusSize(requestDocs.index(),
                            subcorpusResults.query(), groups.groupCriteria(), docPropValues);
                    CorpusSize.Count totalCount = groupSubcorpusSize.getTotalCount();
                    row.add(totalCount.hasTokenCount() ? Long.toString(totalCount.getTokens()) :
                            CSV_VALUE_UNKNOWN);
                    row.add(totalCount.hasDocumentCount() ? Long.toString(totalCount.getDocuments()) :
                            CSV_VALUE_UNKNOWN);
                } else {
                    CorpusSize.Count totalCount = group.storedResults().subcorpusSize().getTotalCount();
                    row.add(Long.toString(totalCount.getTokens()));
                    row.add(Long.toString(totalCount.getDocuments()));
                }

                printer.printRecord(row);
            }

            printer.flush();
            return printer.getOut().toString();
        } catch (IOException e) {
            throw new InternalServerError("Cannot write response: " + e.getMessage(), "INTERR_WRITING_DOCS_CSV1");
        }
    }

    public static String docs(ResultDocs result, ResponseStreamer rs) throws BlsException {
        try {
            DocResults docs = result.getDocs();
            DocResults globalSubcorpusSize = result.getSubcorpusResults();

            RequestDocs requestDocs = result.getRequestDocs();
            BlackLabIndex index = requestDocs.index();
            IndexMetadata indexMetadata = index.metadata();
            MetadataField pidField = indexMetadata.metadataFields().pidField();
            String tokenLengthField = index.mainAnnotatedField().tokenLengthField(); // TODO: all annotated fields?

            // Build the header; 2 columns for pid and length, then 1 for each metadata field
            List<String> row = new ArrayList<>();
            row.add(ResponseStreamer.KEY_DOC_PID);
            row.add(ResponseStreamer.KEY_NUMBER_OF_HITS);
            if (tokenLengthField != null)
                row.add(ResponseStreamer.KEY_DOC_LENGTH_TOKENS);

            Collection<String> metadataFieldIds = requestDocs.metadataToInclude().stream()
                    .map(Field::name)
                    .collect(Collectors.toList());
            metadataFieldIds.remove(ResponseStreamer.KEY_DOC_PID); // already included as first column (see above)
            // never show these values even if they exist as actual fields, they're internal/calculated
            metadataFieldIds.remove(ResponseStreamer.KEY_DOC_LENGTH_TOKENS); // already included, not a regular metadata field
            metadataFieldIds.remove(ResponseStreamer.KEY_DOC_MAY_VIEW);

            row.addAll(metadataFieldIds); // NOTE: use the raw field IDs for headers, not the display names, CSVPrinter can't handle duplicate names

            CsvSettings csvSettings = requestDocs.csvSettings();
            CSVPrinter printer = createHeader(row, csvSettings.declareSeparator());
            TextPattern pattern = requestDocs.optHits() == null ? null : requestDocs.optHits().patternOriginal();
            ResultsStats docsStats = docs.resultsStats();
            ResultSummaryNumHits summaryNumHits = new ResultSummaryNumHits(null, docsStats, true, null,
                    globalSubcorpusSize == null ? null : globalSubcorpusSize.subcorpusSize());
            ResultSummaryCommonFields summaryFields = new ResultSummaryCommonFields(pattern, null,
                    null, null, null, null, null,
                    requestDocs.sampleParams(), result.paramsForResponse(), null, summaryNumHits
            );
            summaryCsvDocs(printer, row.size(), docs,
                    rs, summaryFields, summaryNumHits);

            StringBuilder sb = new StringBuilder();

            for (DocResult docResult : docs) {
                Document doc = index.luceneDoc(docResult.docId());
                row.clear();

                // Pid field, use lucene doc id if not provided
                if (pidField != null && doc.get(pidField.name()) != null)
                    row.add(doc.get(pidField.name()));
                else
                    row.add(Integer.toString(docResult.docId()));

                row.add(Long.toString(docResult.size()));

                // Length field, if applicable
                if (tokenLengthField != null)
                    row.add(Integer.toString(Integer.parseInt(doc.get(tokenLengthField)) - BlackLabIndexAbstract.IGNORE_EXTRA_CLOSING_TOKEN)); // lengthInTokens

                // other fields in order of appearance
                for (String fieldId : metadataFieldIds) {
                    // we must support multiple values in a single csv cell
                    // we must also support values containing quotes/whitespace/commas
                    // this mean we must delimit individual values, we do this by surrounding them by quotes and separating them with a single space
                    // existing quotes will be escaped by doubling them as per the csv escaping conventions

                    // essentially transform
                    // a value containing "quotes"
                    // a "value" containing , as well as "quotes"

                    // into
                    // "a value containing ""quotes""" "a ""value"" containing , as well as ""quotes"""

                    // decoders must split the value on whitespace outside quotes, then strip outside quotes, then replace the doubled quotes with singular quotes

                    boolean firstValue = true;
                    for (String value : doc.getValues(fieldId)) {
                        if (!firstValue) {
                            sb.append(" ");
                        }
                        sb.append('"');
                        sb.append(value.replace("\n", "").replace("\r", "").replace("\"", "\"\""));
                        sb.append('"');
                        firstValue = false;
                    }

                    row.add(sb.toString());
                    sb.setLength(0);
                }

                Appendable app = printer.getOut();
                for (String cell : row) {
                    app.append(cell).append(',');
                }
                printer.println();
            }

            printer.flush();
            return printer.getOut().toString();
        } catch (IOException e) {
            throw new InternalServerError("Cannot write response: " + e.getMessage(), "INTERR_WRITING_DOCS_CSV2");
        }
    }

    /** A span attribute to include in the CSV export */
    public record SpanAndAttributeName(String spanName, String attributeName) {
        public static SpanAndAttributeName fromString(String sa) {
            if (sa.equals("*"))
                return new SpanAndAttributeName("*", "*");
            String[] parts = sa.split("\\.", 2);
            if (parts.length == 2) {
                return new SpanAndAttributeName(parts[0], parts[1]);
            } else {
                throw new IllegalArgumentException("Invalid span attribute format (must be \"spanName.attrName\") : " + sa);
            }
        }
    }
}
