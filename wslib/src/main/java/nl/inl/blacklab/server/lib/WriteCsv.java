package nl.inl.blacklab.server.lib;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.document.Document;

import nl.inl.blacklab.resultproperty.DocProperty;
import nl.inl.blacklab.resultproperty.PropertyValue;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.BlackLabIndexAbstract;
import nl.inl.blacklab.search.Kwic;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.Field;
import nl.inl.blacklab.search.indexmetadata.IndexMetadata;
import nl.inl.blacklab.search.indexmetadata.MetadataField;
import nl.inl.blacklab.search.results.CorpusSize;
import nl.inl.blacklab.search.results.DocGroup;
import nl.inl.blacklab.search.results.DocGroups;
import nl.inl.blacklab.search.results.DocResult;
import nl.inl.blacklab.search.results.DocResults;
import nl.inl.blacklab.search.results.EphemeralHit;
import nl.inl.blacklab.search.results.HitGroup;
import nl.inl.blacklab.search.results.HitGroups;
import nl.inl.blacklab.search.results.Hits;
import nl.inl.blacklab.search.results.HitsSimple;
import nl.inl.blacklab.search.results.Kwics;
import nl.inl.blacklab.search.results.ResultGroups;
import nl.inl.blacklab.search.results.SampleParameters;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.exceptions.InternalServerError;
import nl.inl.blacklab.server.lib.results.ResponseStreamer;
import nl.inl.blacklab.server.lib.results.ResultHitsCsv;
import nl.inl.blacklab.server.lib.results.WebserviceOperations;
import nl.inl.blacklab.webservice.WebserviceParameter;

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

    public static String hitsGroupsResponse(ResultHitsCsv resultHitsCsv, ResponseStreamer rs) throws BlsException {
        HitGroups groups = resultHitsCsv.getGroups();
        Hits inputHitsForGroups = resultHitsCsv.getHits();
        DocResults subcorpusResults = resultHitsCsv.getSubcorpusResults();
        WebserviceParams params = resultHitsCsv.getParams();

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
            CSVPrinter printer = createHeader(row, params.getCsvDeclareSeparator());
            if (params.getCsvIncludeSummary()) {
                summaryCsvHits(params, printer, row.size(), inputHitsForGroups, groups,
                        subcorpusResults.subcorpusSize(), rs);
            }

            // write the groups
            for (HitGroup group : groups) {
                row.clear();
                row.addAll(group.identity().propValues());
                row.add(Long.toString(group.storedResults().resultsStats().countedSoFar()));

                if (metadataGroupProperties != null) {
                    // Find size of corresponding subcorpus group
                    PropertyValue docPropValues = groups.groupCriteria().docPropValues(group.identity());
                    CorpusSize groupSubcorpusSize = WebserviceOperations.findSubcorpusSize(params, subcorpusResults.query(), metadataGroupProperties, docPropValues);
                    long numberOfDocsInGroup = group.storedResults().docsStats().waitUntil().allCounted();

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

    public static String hitsResponse(ResultHitsCsv resultHitsCsv, ResponseStreamer rs) throws BlsException {
        WebserviceParams params = resultHitsCsv.getParams();
        BlackLabIndex index = params.blIndex();
        Hits hits = resultHitsCsv.getHits();
        HitGroups groups = resultHitsCsv.getGroups();
        DocResults subcorpusResults = resultHitsCsv.getSubcorpusResults();
        final Annotation mainTokenProperty = hits.field().mainAnnotation();
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
            // Only output metadata if explicitly passed, do not print all fields if the parameter was omitted like the
            // normal hit response does (because that can result in a MASSIVE amount of repeated data)
            boolean noMetadataRequested = params.getListMetadataValuesFor().isEmpty();
            List<MetadataField> metadataFieldsToWrite = noMetadataRequested ? Collections.emptyList() :
                    new ArrayList<>(WebserviceOperations.getMetadataToWrite(params));
            for (MetadataField f : metadataFieldsToWrite) {
                 row.add(f.name());
            }

            CSVPrinter printer = createHeader(row, params.getCsvDeclareSeparator());
            if (params.getCsvIncludeSummary()) {
                hits.resultsStats().waitUntil().allCounted(); // block for a bit
                summaryCsvHits(params, printer, row.size(), hits, groups, subcorpusResults.subcorpusSize(),
                        rs);
            }

            Map<Integer, Document> luceneDocs = new HashMap<>();
            HitsSimple hitsList = hits.getHits();
            Kwics kwics = hitsList.kwics(params.contextSettings().size());
            Iterator<EphemeralHit> it = hitsList.ephemeralIterator();
            while (it.hasNext()) {
                EphemeralHit hit = it.next();
                Document doc = luceneDocs.get(hit.doc());
                if (doc == null) {
                    doc = index.luceneDoc(hit.doc());
                    luceneDocs.put(hit.doc(), doc);
                }
                String docPid = WebserviceOperations.getDocumentPid(index, hit.doc(), doc);
                writeHit(kwics.get(hit), doc, mainTokenProperty,
                        resultHitsCsv.getAnnotationsToWrite(), docPid, metadataFieldsToWrite, printer);
            }
            printer.flush();
            return printer.getOut().toString();
        } catch (IOException e) {
            throw new InternalServerError("Cannot write response: " + e.getMessage(), "INTERR_WRITING_HITS_CSV2");
        }
    }

    public static CSVPrinter createHeader(List<String> row, boolean declareSeparator) throws IOException {
        // Create the header, then explicitly declare the separator, as excel normally uses a locale-dependent CSV-separator...
        CSVFormat format = CSVFormat.EXCEL.withHeader(row.toArray(new String[0]));
        return format.print(new StringBuilder(declareSeparator ? "sep=,\r\n" : ""));
    }

    private static void writeHit(
            Kwic kwic,
            Document doc,
            Annotation mainTokenProperty,
            List<Annotation> otherTokenProperties,
            String docPid,
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

        // other fields in order of appearance
        for (MetadataField field : metadataFieldsToWrite)
            printer.print(escape(doc.getValues(field.name())));
        printer.println();
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
     * Pipe symbols, newlines and carriage returns in the input values are
     * escaped with a backslash.
     * Any other required escaping should be taken care of by Commons CSV.
     */
    static String escape(String[] strings) {
        return Arrays.stream(strings)
                .map(value -> value
                        .replaceAll("\\\\", "\\\\\\\\")
                        .replaceAll("\n", "\\\\n")
                        .replaceAll("\r", "\\\\r")
                        .replaceAll("\\|", "\\\\|"))
                .collect(Collectors.joining("|"));
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
     * @param numColumns number of columns to output per row, minimum 2
     * @param printer the output printer
     * @param searchParam original search parameters
     * @param groups (optional) if results are grouped, the groups
     * @param subcorpusSize global sub corpus information (i.e. inter-group)
     */
    // TODO tidy up csv handling
    private static <T> void addSummaryCsvCommon(
            CSVPrinter printer,
            int numColumns,
            WebserviceParams searchParam,
            ResultGroups groups,
            CorpusSize subcorpusSize,
            ResponseStreamer rs
    ) {
        String summ = ResponseStreamer.KEY_SUMMARY + ".";
        for (Map.Entry<WebserviceParameter, String> param : searchParam.getParameters().entrySet()) {
            WebserviceParameter par = param.getKey();
            if (par == WebserviceParameter.LIST_VALUES_FOR_ANNOTATIONS ||
                    par == WebserviceParameter.LIST_VALUES_FOR_METADATA_FIELDS)
                continue;
            writeRow(printer, numColumns, summ + rs.KEY_PARAMS + "." + par, param.getValue());
        }

        String subcorpSize = summ + ResponseStreamer.KEY_SUBCORPUS_SIZE + ".";
        writeRow(printer, numColumns, subcorpSize + rs.KEY_SUBCORPUS_SIZE_DOCUMENTS, subcorpusSize.getTotalCount().getDocuments());
        writeRow(printer, numColumns, subcorpSize + rs.KEY_SUBCORPUS_SIZE_TOKENS, subcorpusSize.getTotalCount().getTokens());

        if (groups != null) {
            writeRow(printer, numColumns, summ + ResponseStreamer.KEY_NUMBER_OF_GROUPS, groups.size());
            writeRow(printer, numColumns, summ + ResponseStreamer.KEY_LARGEST_GROUP_SIZE, groups.largestGroupSize());
        }

        SampleParameters sample = searchParam.sampleSettings();
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
     * @param hits hits to summarize
     * @param groups (optional) if grouped
     * @param subcorpusSize (optional) if available
     */
    private static void summaryCsvHits(WebserviceParams params, CSVPrinter printer, int numColumns, Hits hits,
            ResultGroups groups, CorpusSize subcorpusSize, ResponseStreamer rs) {
        addSummaryCsvCommon(printer, numColumns, params, groups, subcorpusSize, rs);
        String summ = ResponseStreamer.KEY_SUMMARY + ".";
        writeRow(printer, numColumns, summ + ResponseStreamer.KEY_NUMBER_OF_HITS, hits.size());
        writeRow(printer, numColumns, summ + ResponseStreamer.KEY_NUMBER_OF_DOCS, hits.docsStats().countedSoFar());
    }

    /**
     * @param printer CSV printer
     * @param numColumns number of columns
     * @param docResults all docs as the input for groups, or contents of a specific group (viewgroup)
     * @param groups (optional) if grouped
     */
    public static void summaryCsvDocs(
            WebserviceParams params,
            CSVPrinter printer,
            int numColumns,
            DocResults docResults,
            DocGroups groups,
            CorpusSize subcorpusSize,
            ResponseStreamer rs
    ) {
        addSummaryCsvCommon(printer, numColumns, params, groups, subcorpusSize, rs);
        String summ = ResponseStreamer.KEY_SUMMARY + ".";
        writeRow(printer, numColumns, summ + ResponseStreamer.KEY_NUMBER_OF_DOCS, docResults.size());
        writeRow(printer, numColumns, summ + ResponseStreamer.KEY_NUMBER_OF_HITS,
                StreamSupport.stream(docResults.spliterator(), false).mapToLong(DocResult::size).sum());
    }

    public static String docGroups(WebserviceParams params, DocResults inputDocsForGroups, DocGroups groups,
            DocResults subcorpusResults, ResponseStreamer rs) throws BlsException {
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

            CSVPrinter printer = createHeader(row, params.getCsvDeclareSeparator());
            if (params.getCsvIncludeSummary()) {
                summaryCsvDocs(params, printer, row.size(), inputDocsForGroups, groups, subcorpusResults.subcorpusSize(), rs);
            }

            // write the groups
            for (DocGroup group : groups) {
                row.clear();
                row.addAll(group.identity().propValues());
                row.add(Long.toString(group.size()));
                row.add(Long.toString(group.totalTokens()));

                if (params.hasPattern()) {
                    PropertyValue docPropValues = group.identity();
                    CorpusSize groupSubcorpusSize = WebserviceOperations.findSubcorpusSize(params, subcorpusResults.query(), groups.groupCriteria(), docPropValues);
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

    public static String docs(WebserviceParams params, DocResults docs, DocGroups fromGroups,
            DocResults globalSubcorpusSize, ResponseStreamer rs) throws BlsException {
        try {
            BlackLabIndex index = params.blIndex();
            IndexMetadata indexMetadata = index.metadata();
            MetadataField pidField = indexMetadata.metadataFields().pidField();
            String tokenLengthField = index.mainAnnotatedField().tokenLengthField(); // TODO: all annotated fields?

            // Build the header; 2 columns for pid and length, then 1 for each metadata field
            List<String> row = new ArrayList<>();
            row.add(ResponseStreamer.KEY_DOC_PID);
            row.add(ResponseStreamer.KEY_NUMBER_OF_HITS);
            if (tokenLengthField != null)
                row.add(ResponseStreamer.KEY_DOC_LENGTH_TOKENS);

            Collection<String> metadataFieldIds = WebserviceOperations.getMetadataToWrite(params).stream()
                    .map(Field::name)
                    .collect(Collectors.toList());
            metadataFieldIds.remove(ResponseStreamer.KEY_DOC_PID); // already included as first column (see above)
            // never show these values even if they exist as actual fields, they're internal/calculated
            metadataFieldIds.remove(ResponseStreamer.KEY_DOC_LENGTH_TOKENS); // already included, not a regular metadata field
            metadataFieldIds.remove(ResponseStreamer.KEY_DOC_MAY_VIEW);

            row.addAll(metadataFieldIds); // NOTE: use the raw field IDs for headers, not the display names, CSVPrinter can't handle duplicate names

            CSVPrinter printer = createHeader(row, params.getCsvDeclareSeparator());
            summaryCsvDocs(params, printer, row.size(), docs, fromGroups, globalSubcorpusSize.subcorpusSize(), rs);

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
}
