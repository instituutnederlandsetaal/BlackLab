package nl.inl.blacklab.server.lib.results;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.document.Document;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.ibm.icu.text.Collator;

import nl.inl.blacklab.exceptions.InterruptedSearch;
import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.exceptions.MatchInfoNotFound;
import nl.inl.blacklab.index.IndexListener;
import nl.inl.blacklab.index.IndexListenerReportConsole;
import nl.inl.blacklab.index.Indexer;
import nl.inl.blacklab.plugins.FileConverter;
import nl.inl.blacklab.resultproperty.DocGroupPropertySize;
import nl.inl.blacklab.resultproperty.DocProperty;
import nl.inl.blacklab.resultproperty.PropertyValue;
import nl.inl.blacklab.search.BlackLab;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.TermFrequencyList;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.AnnotationSensitivity;
import nl.inl.blacklab.search.indexmetadata.IndexMetadata;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.indexmetadata.MetadataField;
import nl.inl.blacklab.search.indexmetadata.MetadataFieldGroup;
import nl.inl.blacklab.search.indexmetadata.MetadataFieldValues;
import nl.inl.blacklab.search.indexmetadata.MetadataFields;
import nl.inl.blacklab.search.indexmetadata.TruncatableFreqList;
import nl.inl.blacklab.search.results.CorpusSize;
import nl.inl.blacklab.search.results.docs.DocGroup;
import nl.inl.blacklab.search.results.docs.DocGroups;
import nl.inl.blacklab.search.results.hitresults.ContextSize;
import nl.inl.blacklab.search.results.hitresults.HitResults;
import nl.inl.blacklab.search.results.hits.EphemeralHit;
import nl.inl.blacklab.search.results.hits.Hits;
import nl.inl.blacklab.server.BlsMain;
import nl.inl.blacklab.server.config.BLSConfig;
import nl.inl.blacklab.server.exceptions.BadRequest;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.exceptions.InternalServerError;
import nl.inl.blacklab.server.exceptions.NotAuthorized;
import nl.inl.blacklab.server.exceptions.NotFound;
import nl.inl.blacklab.server.index.FinderInputFormatUserFormats;
import nl.inl.blacklab.server.index.Index;
import nl.inl.blacklab.server.index.IndexManager;
import nl.inl.blacklab.server.jobs.WindowSettings;
import nl.inl.blacklab.server.lib.User;
import nl.inl.blacklab.server.lib.requests.RequestCorpusInfo;
import nl.inl.blacklab.server.lib.requests.RequestDocs;
import nl.inl.blacklab.server.lib.requests.RequestHits;
import nl.inl.blacklab.server.lib.requests.RequestOldCollocations;
import nl.inl.blacklab.server.lib.requests.RequestTermFrequencies;
import nl.inl.blacklab.server.search.SearchManager;
import nl.inl.util.Json;
import nl.inl.util.LuceneUtil;
import nl.inl.util.fileprocessor.FileReference;

public class WebserviceOperations {

    static final Logger logger = LogManager.getLogger(WebserviceOperations.class);

    private static final int MAX_FIELD_VALUES_TO_RETURN = 500;

    private WebserviceOperations() {}

    /**
     * Get metadata field groups.
     * <p>
     * This includes adding any uncategorized fields to the "default" group.
     * <p>
     * (part of custom properties; should eventually be removed from the API)
     *
     * @param index index
     * @return metadata field groups
     */
    public static Map<String, List<String>> getMetadataFieldGroupsWithRest(BlackLabIndex index) {
        Map<String, ? extends MetadataFieldGroup> metaGroups = index.metadata().metadataFields().groups();
        Set<MetadataField> metadataFieldsNotInGroups = index.metadata().metadataFields().stream()
                .collect(Collectors.toSet());
        for (MetadataFieldGroup metaGroup1: metaGroups.values()) {
            for (String fieldName: metaGroup1) {
                MetadataField field1 = index.metadata().metadataFields().get(fieldName);
                metadataFieldsNotInGroups.remove(field1);
            }
        }

        Map<String, List<String>> metadataFieldGroups = new LinkedHashMap<>();
        boolean addedRemaining = false;
        for (MetadataFieldGroup metaGroup : metaGroups.values()) {
            List<String> metadataFieldGroup = new ArrayList<>();
            for (String field: metaGroup) {
                metadataFieldGroup.add(field);
            }
            if (!addedRemaining && metaGroup.addRemainingFields()) {
                addedRemaining = true;
                List<MetadataField> rest = new ArrayList<>(metadataFieldsNotInGroups);
                rest.sort(Comparator.comparing(a -> a.name().toLowerCase()));
                for (MetadataField field: rest) {
                    metadataFieldGroup.add(field.name());
                }
            }
            metadataFieldGroups.put(metaGroup.name(), metadataFieldGroup);
        }
        return metadataFieldGroups;
    }

    /**
     * Get the special metadata fields.
     * <p>
     * (special metadata fields except pidField are part of custom properties;
     *  this method should eventually be removed from the API)
     *
     * @param index index
     * @return doc fields
     */
    public static Map<String, String> getDocFields(BlackLabIndex index) {
        IndexMetadata indexMetadata = index.metadata();
        Map<String, String> docFields = new LinkedHashMap<>();
        MetadataField pidField = indexMetadata.metadataFields().pidField();
        if (pidField != null)
            docFields.put(MetadataFields.SPECIAL_FIELD_SETTING_PID, pidField.name());
        for (String propName: List.of("titleField", "authorField", "dateField")) {
            String fieldName = indexMetadata.custom().get(propName, "");
            if (!fieldName.isEmpty())
                docFields.put(propName, fieldName);
        }
        return docFields;
    }

    /**
     * Get display names for metadata fields.
     * <p>
     * (part of custom properties; should eventually be removed from the API)
     *
     * @param index index
     * @return display names
     */
    public static Map<String, String> getMetaDisplayNames(BlackLabIndex index) {
        Map<String, String> metaDisplayNames = new LinkedHashMap<>();
        for (MetadataField f: index.metadata().metadataFields()) {
            String displayName = f.custom().get("displayName", "");
            if (!f.name().equals("lengthInTokens") && !f.name().equals("mayView")) {
                metaDisplayNames.put(f.name(),displayName);
            }
        }
        return metaDisplayNames;
    }

    /**
     * Get the pid for the specified document.
     *
     * @param index where we got this document from
     * @param luceneDocId Lucene document id
     * @param document the document object, or null if not available
     * @return the pid string (or Lucene doc id in string form if index has no pid
     *         field)
     */
    public static String getDocumentPid(BlackLabIndex index, int luceneDocId, Document document) {
        MetadataField pidField = index.metadataFields().pidField();
        if (document == null)
            document = index.luceneDoc(luceneDocId);
        String pid = pidField == null ? null : document.get(pidField.name());
        if (pid == null)
            return Integer.toString(luceneDocId);
        return pid;
    }

    /**
     * Get metadata for a list of documents.
     *
     * @param index index
     * @param luceneDocs documents to get metadata from
     * @param metadataToInclude fields to get
     * @return metadata for the documents
     */
    public static Map<String, ResultDocInfo> getDocInfos(BlackLabIndex index, Map<Integer, Document> luceneDocs,
            Collection<MetadataField> metadataToInclude) {
        Map<String, ResultDocInfo> docInfos = new LinkedHashMap<>();
        for (Map.Entry<Integer, Document> e: luceneDocs.entrySet()) {
            Integer docId = e.getKey();
            Document luceneDoc = e.getValue();
            String pid = getDocumentPid(index, docId, luceneDoc);
            ResultDocInfo docInfo = new ResultDocInfo(index, pid, luceneDoc, metadataToInclude);
            docInfos.put(pid, docInfo);
        }
        return docInfos;
    }

    /**
     * Get relevant facets info for display.
     * <p>
     * Returns lists of value+count for every property faceted on.
     * Grouped by descending size.
     *
     * @param counts faceting results
     * @return faceting info for display
     */
    public static Map<String, List<Pair<String, Long>>> getFacetInfo(Map<DocProperty, DocGroups> counts) {
        Map<String, List<Pair<String,  Long>>> facetInfo = new LinkedHashMap<>();
        for (Map.Entry<DocProperty, DocGroups> e : counts.entrySet()) {
            DocProperty facetBy = e.getKey();
            DocGroups facetCounts = e.getValue();
            facetCounts = facetCounts.sort(DocGroupPropertySize.get());
            String facetName = facetBy.name();
            List<Pair<String,  Long>> facetItems = new ArrayList<>();
            int n = 0, maxFacetValues = 10;
            int totalSize = 0;
            for (DocGroup count : facetCounts) {
                String value = count.identity().toString();
                long size = count.size();
                facetItems.add(Pair.of(value, size));
                totalSize += size;
                n++;
                if (n >= maxFacetValues)
                    break;
            }
            if (totalSize < facetCounts.sumOfGroupSizes()) {
                facetItems.add(Pair.of("[REST]", facetCounts.sumOfGroupSizes() - totalSize));
            }
            facetInfo.put(facetName, facetItems);
        }
        return facetInfo;
    }

    /**
     * Get a map of doc id to document pid for the documents in a list of hits.
     *
     * @param index index
     * @param hits hits we want the doc pids for
     * @param luceneDocs map of doc id to Lucene document, to look up the pids
     */
    public static Map<Integer, String> collectDocsAndPids(BlackLabIndex index, Hits hits,
            Map<Integer, Document> luceneDocs) {
        // Collect Lucene docs (for writing docInfos later) and find pids
        Map<Integer, String> docIdToPid = new HashMap<>();
        for (EphemeralHit hit: hits) {
            Document document = luceneDocs.computeIfAbsent(hit.doc(),
                    __ -> index.luceneDoc(hit.doc()));
            String docPid = getDocumentPid(index, hit.doc(), document);
            docIdToPid.put(hit.doc(), docPid);
        }
        return docIdToPid;
    }

    /**
     * Calculate collocations from hits.
     *
     * @param requestOldCollocations operation parameters
     * @param hitResults hits
     * @return collocations
     */
    public static TermFrequencyList getCollocations(RequestOldCollocations requestOldCollocations, HitResults hitResults) {
        Annotation annotation = hitResults.field().mainAnnotation();
        MatchSensitivity sensitivity = MatchSensitivity.caseAndDiacriticsSensitive(requestOldCollocations.sensitive());
        ensureHasSensitivity(annotation, sensitivity);
        ContextSize contextSize = requestOldCollocations.contextSize();
        return hitResults.collocations(annotation, contextSize, sensitivity, true);
    }

    public static void ensureHasSensitivity(Annotation annotation, MatchSensitivity sensitivity) {
        if (!annotation.hasSensitivity(sensitivity)) {
            throw new BadRequest("SENSITIVITY_NOT_FOUND",
                    "The annotation '" + annotation.name() + "' does not have the requested sensitivity '" + sensitivity
                            + "'",
                    Map.of("annotationName", annotation.name(), "sensitivity", sensitivity.toString()));
        }
    }

    /**
     * Add a user file format.
     *
     * @param params operation parameters
     * @param fileName name of the uploaded file
     * @param fileContents contents of the uploaded file
     */
    public static void addUserFileFormat(SearchManager searchMan, User user, String fileName, InputStream fileContents) {
        FinderInputFormatUserFormats formatMan = searchMan.getIndexManager().getUserFormatManager();
        if (formatMan == null)
            throw new BadRequest("CANNOT_CREATE_INDEX ",
                    "Could not create/overwrite format. The server is not configured with support for user content.");
        formatMan.createUserFormat(user, fileName, fileContents);
    }

    /**
     * Get field value distribution in the right order for the response.
     * <p>
     * The right order is: display order first, then sorted by displayValue
     * as a fallback, or regular value as the second fallback.
     *
     * @param fd field to get values for
     * @return properly sorted values
     */
    public static Map<String, Long> getFieldValuesInOrder(MetadataField fd, MetadataFieldValues values) {
        Map<String, String> displayValues = fd.custom().get("displayValues", Collections.emptyMap());
        List<String> displayOrder = fd.custom().get("displayOrder", Collections.emptyList());

        // Show values in display order (if defined)
        // If not all values are mentioned in display order, show the rest at the end,
        // sorted by their displayValue (or regular value if no displayValue specified)
        Map<String, Long> fieldValues = new LinkedHashMap<>();
        Map<String, Long> valueDistribution = values.valueList().getValues();
        final Collator collator = BlackLab.getFieldValueSortCollator();
        if (!displayOrder.isEmpty()) {
            Set<String> valuesLeft = new HashSet<>(valueDistribution.keySet());
            for (String value: displayOrder) {
                fieldValues.put(value, valueDistribution.get(value));
                valuesLeft.remove(value);
            }
            List<String> sortedLeft = new ArrayList<>(valuesLeft);
            sortedLeft.sort((o1, o2) -> {
                String d1 = displayValues.getOrDefault(o1, o1);
                String d2 = displayValues.getOrDefault(o2, o2);
                return collator.compare(d1, d2);
            });
            for (String value: sortedLeft) {
                fieldValues.put(value, valueDistribution.get(value));
            }
        } else {
            // No displayOrder
            if (!displayValues.isEmpty()) {
                List<String> sortedLeft = new ArrayList<>(valueDistribution.keySet());
                sortedLeft.sort((o1, o2) -> {
                    String d1 = displayValues.getOrDefault(o1, o1);
                    String d2 = displayValues.getOrDefault(o2, o2);
                    return collator.compare(d1, d2);
                });
                for (String value: sortedLeft) {
                    fieldValues.put(value, valueDistribution.get(value));
                }
            } else {
                // No displayValues either
                List<String> sortedLeft = new ArrayList<>(valueDistribution.keySet());
                sortedLeft.sort(collator::compare);
                for (String value: sortedLeft) {
                    fieldValues.put(value, valueDistribution.get(value));
                }
            }
        }
        return fieldValues;
    }

    /**
     * Get the list of values for an annotation.
     * <p>
     * No more than {@link #MAX_FIELD_VALUES_TO_RETURN} will be returned.
     * valueListComplete[0] will indicate if all values were returned or not
     *
     * @param index index
     * @param annotation annotation to get values for
     * @param limitValues maximum number of values to return
     * @return values for this annotation
     */
    public static TruncatableFreqList getAnnotationValues(BlackLabIndex index, Annotation annotation, long limitValues) {
        final TruncatableFreqList terms = new TruncatableFreqList(limitValues < 0 ? MAX_FIELD_VALUES_TO_RETURN : limitValues);
        MatchSensitivity sensitivity = annotation.hasSensitivity(MatchSensitivity.INSENSITIVE) ?
                MatchSensitivity.INSENSITIVE :
                MatchSensitivity.SENSITIVE;
        AnnotationSensitivity as = annotation.sensitivity(sensitivity);
        String luceneField = as.luceneField();
        if (annotation.isRelationAnnotation()) {
            throw new IllegalArgumentException("Spans (tags) and relations are reported in the relations section.");
        } else {
            // Regular annotated field.
            LuceneUtil.getFieldTerms(index.reader(), luceneField, null, (term, freq) -> {
                terms.add(term, freq);
                return true;
            });
        }
        return terms;
    }

    /**
     * Translate a thrown exception into a BlsException.
     * <p>
     * BlsException will eventually be caught and returned as an error response.
     *
     * @param e exception thrown
     * @return translated exception
     */
    public static BlsException translateSearchException(Exception e) {
        if (e instanceof InterruptedException) {
            throw new InterruptedSearch(e);
        } else if (e instanceof InvalidQuery) {
            if (e instanceof MatchInfoNotFound e2) {
                return new BadRequest("UNKNOWN_MATCH_INFO",
                        "Reference to unknown match info (i.e. capture group) '" + e2.getMatchInfoName() + "'",
                        Map.of("name", e2.getMatchInfoName()));
            }
            return new BadRequest("INVALID_QUERY", e.getMessage());
        } else if (e instanceof ExecutionException) {
            // See if we recognize the cause of this exception
            if (e.getCause() instanceof BlsException e2) {
                return e2;
            } else if (e.getCause() instanceof MatchInfoNotFound e2) {
                return new BadRequest("UNKNOWN_MATCH_INFO",
                        "Reference to unknown match info (i.e. capture group) '" + e2.getMatchInfoName() + "'",
                        Map.of("name", e2.getMatchInfoName()));
            } else if (e.getCause() instanceof InvalidQuery e2) {
                return new BadRequest("INVALID_QUERY", "Invalid query: " + e2.getMessage(), e2);
            } else if (e.getCause() instanceof Exception e2) {
                logger.error(e2);
                return new InternalServerError("Internal error while searching", "INTERR_WHILE_SEARCHING", e2);
            }
        }
        return new InternalServerError("Internal error while searching", "INTERR_WHILE_SEARCHING", e);
    }

    /**
     * Find the size of documents matching a filter query and/or property+value.
     * <p>
     *
     * @param index corpus we're searching
     * @param metadataFilterQuery filter query
     * @param property document property to find subcorpus size for
     * @param value value the document property must have to be included
     * @return size of subcorpus
     */
    public static CorpusSize findSubcorpusSize(BlackLabIndex index, Query metadataFilterQuery,
            DocProperty property, PropertyValue value) {
        if (!property.canConstructQuery(index, value))
            return CorpusSize.EMPTY; // cannot determine subcorpus size of empty value
        // Construct a query that matches this propery value
        Query query = property.query(index, value); // analyzer....!
        if (query == null) {
            query = metadataFilterQuery;
        } else {
            // Combine with subcorpus query
            BooleanQuery.Builder builder = new BooleanQuery.Builder();
            builder.add(metadataFilterQuery, BooleanClause.Occur.MUST);
            builder.add(query, BooleanClause.Occur.MUST);
            query = builder.build();
        }
        // Determine number of tokens in this subcorpus
        return index.queryDocuments(query).subcorpusSize(true);
    }

    public static TermFrequencyList getTermFrequencies(RequestTermFrequencies reqTermFreq) {
        //TODO: use background job?

        Set<String> terms = reqTermFreq.terms();
        TermFrequencyList tfl = reqTermFreq.index().termFrequencies(reqTermFreq.annotation(),
                reqTermFreq.filterQuery(), terms);

        if (terms == null || terms.isEmpty()) { // apply pagination only when requesting all terms
            WindowSettings window = reqTermFreq.window();
            long first = window.first();
            if (first < 0 || first >= tfl.size())
                first = 0;
            long number = window.size();
            long last = first + number;
            if (last > tfl.size())
                last = tfl.size();
            tfl = tfl.subList(first, last);
        }
        return tfl;
    }

    public static List<String> getUsersToShareWith(User user, Index index) {
        if (!index.userMayRead(user)) {
            if (index.isUserIndex())
                throw new NotAuthorized("You (" + user.getId() + ") are not authorized to access corpus " + index.blIndex().name() + "; you are not the owner, and it is not shared with you");
            else
                throw new NotAuthorized("You (" + user.getId() + ") are not authorized to access this global (non-user) index.");
        }
        return index.getShareWithUsers();
    }

    public static List<String> getCorporaSharedWithMe(User user, IndexManager indexMan) {
        List<String> results = new ArrayList<>();
        // BUG: because private user indices aren't all loaded by default, we may
        //      miss unloaded corpora shared with you. To fix this, we should probably
        //      find all corpora and which users they're shared with on startup,
        //      but not open them until they're actually used.
        indexMan.getAvailablePublicCorpora(); // trigger loading of all user corpora (kinda hacky)
        for (Index index: indexMan.getAllLoadedCorpora()) {
            if (index.sharedWith(user)) {
                results.add(index.getId());
            }
        }
        return results;
    }

    public static void setUsersToShareWith(User user, Index index, String[] users) {
        if (!index.isUserIndex())
            throw new NotAuthorized("You cannot share global corpus " + index.name() + "; it is not a user index.");
        if (!index.userMayRead(user))
            throw new NotAuthorized("You (" + user.getId() + ") are not authorized to share " + index.name() + "; you are not the owner.");
        // Update the list of users to share with
        List<String> shareWithUsers = Arrays.stream(users).map(String::trim).toList();
        index.setShareWithUsers(shareWithUsers);
    }

    static ResultDocs docs(RequestDocs requestDocs) throws InvalidQuery {
        return ResultDocs.docsResponse(requestDocs);
    }

    public static ResultHits hits(RequestHits reqHits) throws InvalidQuery {
        if (StringUtils.isEmpty(reqHits.viewGroup())) {
            return ResultHits.get(reqHits);
        } else {
            return ResultHits.getViewGroup(reqHits);
        }
    }

    public static long getMaxWindowSize(BLSConfig config, boolean isCsv) {
        return isCsv ?
                config.getSearch().getMaxHitsToRetrieve() :
                config.getParameters().getPageSize().getMax();
    }

    public static class UploadedFile {
        private final String name;

        private final byte[] data;

        public UploadedFile(String name, byte[] data) {
            this.name = name;
            this.data = data;
        }

        public String getName() {
            return name;
        }

        public byte[] getData() {
            return data;
        }
    }

    public static String addToIndex(User user, Index index, String converters, Iterator<UploadedFile> dataFiles, Map<String, File> linkedFiles) {
        IndexMetadata indexMetadata = index.getIndexMetadata();

        if (!index.userMayAddData(user))
            throw new NotAuthorized("You (" + user.getId() + ") may not add data to " + index.name() + "; you are not the owner.");

        long maxTokenCount = BlackLab.config().getIndexing().getUserIndexMaxTokenCount();
        if (indexMetadata.countPerField().values().stream().anyMatch(count -> count.getTokens() > maxTokenCount)) {
            throw new NotAuthorized("Sorry, this index is already larger than the maximum of " + maxTokenCount
                    + " tokens allowed in a user index. Cannot add any more data to it.");
        }

        Indexer indexer = index.createIndexer();
        final String[] indexErr = { null }; // array because we set it from closure
        indexer.setListener(new IndexListenerReportConsole() {
            @Override
            public synchronized boolean errorOccurred(Throwable e, String path, File f) {
                super.errorOccurred(e, path, f);
                indexErr[0] = e.getMessage() + " in " + path;
                return false; // Don't continue indexing
            }
        });
        String indexError = indexErr[0];

        indexer.setLinkedFileResolver(fileName -> linkedFiles.get(FilenameUtils.getName(fileName).toLowerCase()));

        try {
            // See if we want to apply any extra FileConverters, specifically for this set of files.
            FileConverter.ExtraConverters extraConverters = !StringUtils.isEmpty(converters) ?
                    WebserviceOperations.getExtraConvertersFromJsonParam(converters) :
                    FileConverter.ExtraConverters.NONE;
            while (dataFiles.hasNext()) {
                UploadedFile df = dataFiles.next();
                String fileName = df.getName();
                byte[] contents = df.getData();
                FileReference fileRef = FileReference.fromBytes(fileName, contents, null);
                indexer.index(fileRef, null, extraConverters);
            }
        } finally {
            if (indexError == null) {
                if (indexer.listener().getFilesProcessed() == 0)
                    indexError = "No files were found during indexing.";
                else if (indexer.listener().getDocsDone() == 0)
                    indexError = "No documents were found during indexing, are the files in the correct format?";
                else if (indexer.listener().getTokensProcessed() == 0)
                    indexError = "No tokens were found during indexing, are the files in the correct format?";
            }

            // It's important we roll back on errors, or incorrect index metadata might be written.
            // See Indexer#hasRollback
            if (indexError != null)
                indexer.rollback();

            indexer.close();
        }

        return indexError;
    }

    private static FileConverter.ExtraConverters getExtraConvertersFromJsonParam(String jsonConverters) {
        // Structure of the "converters" URL parameter, with lists of converter(s) to apply before and after
        // the ones declared in .blf.yaml.
        record ExtraConverterConfigs(List<Map<String, Object>> first, List<Map<String, Object>> last) {}
        try {
            ExtraConverterConfigs extraConverterIds = Json.getJsonObjectMapper().readValue(jsonConverters,
                    ExtraConverterConfigs.class);
            return FileConverter.ExtraConverters.fromConfig(extraConverterIds.first, extraConverterIds.last);
        } catch (JsonProcessingException e) {
            throw new BadRequest("INVALID_CONVERTERS",
                    "The converters parameter does not have the correct JSON structure, please consult the documentation: "
                            + e.getMessage(), e);
        }
    }

    public static void deleteUserFormat(User user, IndexManager indexMan, String formatIdentifier) {
        FinderInputFormatUserFormats formatMan = indexMan.getUserFormatManager();
        if (formatMan == null)
            throw new BadRequest("CANNOT_DELETE_INDEX ",
                    "Could not delete format. The server is not configured with support for user content.");

        if (formatIdentifier == null || formatIdentifier.isEmpty()) {
            throw new NotFound("FORMAT_NOT_FOUND", "Specified format was not found");
        }

        for (Index i : indexMan.getAvailablePrivateCorporaOwnedBy(user)) {
            if (formatIdentifier.equals(i.getIndexMetadata().documentFormat()))
                throw new BadRequest("CANNOT_DELETE_INDEX ",
                        "Could not delete format. The format is still being used by a corpus.");
        }

        FinderInputFormatUserFormats.deleteUserFormat(user, formatIdentifier);
    }

    public static ResultAnnotatedField annotatedField(AnnotatedField fieldDesc,
            Collection<String> listValuesFor, long limitValues, boolean includeIndexName, ResultRelations relations) {
        Map<String, ResultAnnotationInfo> annotInfos = new LinkedHashMap<>();
        boolean all = listValuesFor.contains("*");
        BlackLabIndex index = fieldDesc.index();
        for (Annotation annotation: fieldDesc.annotations()) {
            boolean showValues = (all || listValuesFor.contains(annotation.name())) &&
                    !annotation.isRelationAnnotation(); // spans/relations are reported separately
            ResultAnnotationInfo ai = new ResultAnnotationInfo(index, annotation, showValues, limitValues);
            annotInfos.put(annotation.name(), ai);
        }
        return new ResultAnnotatedField(index, includeIndexName ? index.name() : null, fieldDesc, annotInfos, relations);
    }

    public static ResultIndexStatus resultIndexStatus(Index index) {
        IndexListener indexerListener = index.getIndexerListener();
        long files = 0;
        long docs = 0;
        long tokens = 0;
        if (indexerListener != null) {
            files = indexerListener.getFilesProcessed();
            docs = indexerListener.getDocsDone();
            tokens = indexerListener.getTokensProcessed();
        }
        return new ResultIndexStatus(index, files, docs, tokens);
    }

    public static ResultMetadataField metadataField(long limitValues, MetadataField fieldDesc, String indexName) {
        MetadataFieldValues values = fieldDesc.values(limitValues);
        Map<String, Long> fieldValues = getFieldValuesInOrder(fieldDesc, values);
        return new ResultMetadataField(indexName, fieldDesc, true, fieldValues,
                !values.valueList().isTruncated());
    }

    public static ResultCorpusInfo corpusInfo(RequestCorpusInfo req) {
        Index index = BlsMain.get().getSearchManager().getIndexManager().getIndex(req.corpusName());
        if (index == null)
            throw new IllegalArgumentException("Corpus '" + req.corpusName() + "' not found.");
        ResultIndexStatus progress = resultIndexStatus(index);
        IndexMetadata metadata = progress.getMetadata();

        List<ResultAnnotatedField> afs = new ArrayList<>();
        for (AnnotatedField field: metadata.annotatedFields()) {
            ResultRelations relations = new ResultRelations(req.relations().withAnnotatedField(field));
            afs.add(annotatedField(field, req.listValuesFor(), req.limitValues(), false, relations));
        }
        afs.sort(ResultAnnotatedField::compare);
        List<ResultMetadataField> mfs = new ArrayList<>();
        for (MetadataField f: metadata.metadataFields()) {
            mfs.add(metadataField(req.limitValues(), f, null));
        }

        Map<String, List<String>> metadataFieldGroups = getMetadataFieldGroupsWithRest(index.blIndex());

        AnnotatedField mainAnnotatedField = metadata.mainAnnotatedField();
        String mainAnnotatedFieldName = mainAnnotatedField == null ? null : mainAnnotatedField.name();

        return new ResultCorpusInfo(progress, afs, mainAnnotatedFieldName, mfs, metadataFieldGroups);
    }

}
