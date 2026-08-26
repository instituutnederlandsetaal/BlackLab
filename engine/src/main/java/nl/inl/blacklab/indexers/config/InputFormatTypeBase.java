package nl.inl.blacklab.indexers.config;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.util.BytesRef;

import nl.inl.blacklab.exceptions.BlackLabException;
import nl.inl.blacklab.exceptions.ErrorIndexingFile;
import nl.inl.blacklab.exceptions.InvalidIndex;
import nl.inl.blacklab.exceptions.InvalidInputFormatConfig;
import nl.inl.blacklab.exceptions.MalformedInputFile;
import nl.inl.blacklab.exceptions.MaxDocsReached;
import nl.inl.blacklab.index.BLFieldType;
import nl.inl.blacklab.index.BLInputDocument;
import nl.inl.blacklab.index.DocWriter;
import nl.inl.blacklab.index.DocumentFormats;
import nl.inl.blacklab.index.FileProcessor;
import nl.inl.blacklab.index.IndexerStats;
import nl.inl.blacklab.index.InputFormat;
import nl.inl.blacklab.index.InputFormatInfo;
import nl.inl.blacklab.index.annotated.AnnotatedFieldWriter;
import nl.inl.blacklab.index.annotated.AnnotationWriter;
import nl.inl.blacklab.plugins.InputFormatType;
import nl.inl.blacklab.search.BlackLab;
import nl.inl.blacklab.search.indexmetadata.FieldType;
import nl.inl.blacklab.search.indexmetadata.IndexMetadataWriter;
import nl.inl.blacklab.search.indexmetadata.MetadataField;
import nl.inl.blacklab.search.indexmetadata.MetadataFieldImpl;
import nl.inl.blacklab.search.indexmetadata.RelationsStrategy;
import nl.inl.blacklab.search.indexmetadata.UnknownCondition;
import nl.inl.util.DownloadCache;
import nl.inl.util.StringUtil;
import nl.inl.util.TextContent;
import nl.inl.util.fileprocessor.FileReference;

public abstract class InputFormatTypeBase extends InputFormatType {

    protected static final Logger logger = LogManager.getLogger(InputFormatTypeBase.class);

    /** A document in this format currently being indexed. Contains all the variable state. */
    public interface Doc extends AutoCloseable {
        IndexerStats index();

        IndexerStats indexSpecificDocument(String documentPath, Doc linkingDoc, String storeWithName);

        void close();

        Collection<String> getMetadataField(String name);

        BLInputDocument getCurrentDoc();
    }

    protected abstract static class InputFormatBase implements InputFormat {

        protected abstract class DocBase implements Doc {

            private final DocWriter docWriter;

            private final RelationsStrategy relationsStrategy;

            /**
             * File we're currently parsing. This can be useful for storing the original
             * filename in the index.
             */
            protected String documentName;

            /**
             * The Lucene Document we're currently constructing (corresponds to the document
             * we're indexing)
             */
            protected BLInputDocument currentDoc;

            /**
             * If true, we're indexing into an existing Lucene document. Don't overwrite it
             * with a new one.
             */
            protected boolean indexingIntoExistingDoc = false;

            /**
             * Document metadata. Added at the end to deal with unknown values, multiple occurrences
             * (only the first is actually indexed, because of DocValues, among others), etc.
             */
            protected Map<String, Collection<String>> metadataFieldValues = new HashMap<>();

            /** The list of fragments found for each annotated field, if any */
            Map<String, List<Fragment>> fragsPerField = new HashMap<>();

            /** Behaviour of metadata fields that occur in at least one fragment.
             * Depending on the behaviour, we may or may not want to index these at the document level,
             * and we may or may not want to "inherit" from the document level to the fragment level.
             * Fields that are not in this map are not indexed at the fragment level.
             * (so fields set to fragmentBehaviour: ignore never end up here)
             */
            Map<String, ConfigMetadataField.FragmentBehaviour> metadataFieldsFragmentBehaviour = new HashMap<>();

            protected DocBase(DocWriter docWriter, FileReference file) {
                this.docWriter = docWriter;
                this.relationsStrategy =
                        docWriter == null/*test*/ ? RelationsStrategy.forNewIndex() : docWriter.getRelationsStrategy();
                resetStats();
                setDocument(file);
            }

            /**
             * Returns our DocWriter object
             *
             * @return the DocWriter object
             */
            protected DocWriter getDocWriter() {
                return docWriter;
            }

            protected void setDocument(FileReference file) {
                if (documentName == null)
                    documentName = file.getPath();
                if (file.getAssociatedFile() != null)
                    setDocumentDirectory(file.getAssociatedFile().getParentFile()); // for XInclude resolution
            }

            /**
             * Set the current document's directory.
             * This may e.g. be used to resolve XIncludes, e.g. by {@link InputFormatTypeXml}.
             */
            protected void setDocumentDirectory(File dir) {
            }

            protected BLInputDocument createNewDocument() {
                return getDocWriter().indexObjectFactory().createInputDocument();
            }

            /**
             * Get the strategy to use for indexing relations.
             */
            public RelationsStrategy getRelationsStrategy() {
                return relationsStrategy;
            }

            public RelationsStrategy.PayloadCodec getPayloadCodec() {
                return relationsStrategy.getPayloadCodec();
            }

            /**
             * Index documents contained in a file.
             *
             * @throws ErrorIndexingFile if there was an error indexing the file
             */
            public abstract IndexerStats index() throws ErrorIndexingFile;

            @Override
            public BLInputDocument getCurrentDoc() {
                return currentDoc;
            }

            // ------------------------------- Metadata ----------------------------------

            /**
             * Translate a field name before adding it.
             * By default, simply returns the input. May be overridden to change the name of
             * a metadata field as it is indexed.
             *
             * @param from original metadata field name
             * @return new name
             */
            protected String optTranslateMetadataFieldName(String from) {
                return from;
            }

            public Collection<String> getMetadataField(String name) {
                return metadataFieldValues.get(name);
            }

            public void addMetadataField(String name, String value) {
                name = optTranslateMetadataFieldName(name);

                if (name == null || value == null) {
                    warn("Incomplete metadata field: " + name + "=" + value + " (skipping)");
                    return;
                }

                value = StringUtil.trimWhitespace(value);
                if (!value.isEmpty()) {
                    metadataFieldValues.computeIfAbsent(name, __ -> new ArrayList<>()).add(value);
                    IndexMetadataWriter indexMetadata = getDocWriter().metadata();
                    indexMetadata.registerMetadataField(name);
                }
            }

            /**
             * When all metadata values have been set, call this to add the to the Lucene document.
             * We do it this way because we don't want to add multiple values for a field (DocValues and
             * Document.get() only deal with the first value added), and we want to set an "unknown value"
             * in certain conditions, depending on the configuration.
             *
             * @param metadataFieldValues metadata to add
             * @param atFragmentLevel whether we're adding metadata for a fragment (true) or the main document (false)
             */
            private void addMetadataToDocument(Map<String, Collection<String>> metadataFieldValues, boolean atFragmentLevel) {
                // See what metadatafields are missing or empty and add unknown value if desired.
                IndexMetadataWriter indexMetadata = getDocWriter().metadata();
                Map<String, String> unknownValuesToUse = new HashMap<>();
                List<String> fields = indexMetadata.metadataFields().names();
                for (String field: fields) {
                    MetadataField fd = indexMetadata.metadataField(field);
                    if (fd.type() == FieldType.NUMERIC)
                        continue;
                    boolean missing = false, empty = false;
                    Collection<String> currentValue = getMetadataField(fd.name());
                    if (currentValue == null)
                        missing = true;
                    else if (currentValue.isEmpty() || currentValue.stream().allMatch(String::isEmpty))
                        empty = true;
                    UnknownCondition cond = UnknownCondition.fromStringValue(
                            fd.custom().get("unknownCondition", "never"));
                    boolean useUnknownValue = false;
                    switch (cond) {
                    case EMPTY:
                        useUnknownValue = empty;
                        break;
                    case MISSING:
                        useUnknownValue = missing;
                        break;
                    case MISSING_OR_EMPTY:
                        useUnknownValue = missing || empty;
                        break;
                    case NEVER:
                        // (useUnknownValue is already false)
                        break;
                    }
                    if (useUnknownValue) {
                        if (empty) {
                            // Don't count this as a value, count the unknown value
                            for (String value: currentValue) {
                                ((MetadataFieldImpl) indexMetadata.metadataFields().get(fd.name())).removeValue(value);
                            }
                        }
                        unknownValuesToUse.put(fd.name(), fd.custom().get("unknownValue", "unknown"));
                    }
                }
                for (Map.Entry<String, String> e: unknownValuesToUse.entrySet()) {
                    metadataFieldValues.put(e.getKey(), List.of(e.getValue()));
                }
                // Index the metadata fields in order of increasing size, so that the largest
                // field is last.
                // (see https://lucene.apache.org/core/9_0_0/changes/Changes.html
                // LUCENE-6898: In the default codec, the last stored field value will not be fully read from disk if the supplied
                // StoredFieldVisitor doesn't want it. So put your largest text field value last to benefit.)
                List<Map.Entry<String, Collection<String>>> entries = metadataFieldValues.entrySet().stream()
                        .sorted(Comparator.comparingInt(
                                e -> e.getValue().stream().map(String::length).reduce(0, Integer::sum)))
                        .toList();
                for (Map.Entry<String, Collection<String>> e: entries) {
                    // Determine fragment behaviour
                    // (if this field did not occur in a fragment, just index at the document level, that is, IGNORE as the default)
                    ConfigMetadataField.FragmentBehaviour fragBehaviour = metadataFieldsFragmentBehaviour.getOrDefault(
                            e.getKey(), ConfigMetadataField.FragmentBehaviour.DOC_VALUE);
                    if (!atFragmentLevel && !fragBehaviour.indexAtDocLevel()) {
                        // Don't index this metadata field at the document level (because of configured fragment behaviour)
                        continue;
                    }
                    addMetadataFieldToDocument(e.getKey(), e.getValue());
                }
            }

            private void addMetadataFieldToDocument(String name, Collection<String> values) {
                IndexMetadataWriter indexMetadata = getDocWriter().metadata();
                //indexMetadata.registerMetadataField(name);

                MetadataFieldImpl desc = (MetadataFieldImpl) indexMetadata.metadataFields().get(name);

                FieldType type = desc.type();
                if (type != FieldType.NUMERIC) {
                    for (String value: values) {
                        BLFieldType blFieldType = switch (type) {
                            case NUMERIC -> throw new IllegalArgumentException(
                                    "Numeric types should be indexed using IntField, etc.");
                            case TOKENIZED -> getDocWriter().metadataFieldType(true);
                            case UNTOKENIZED -> getDocWriter().metadataFieldType(false);
                        };
                        currentDoc.addTextualMetadataField(name, value, blFieldType);
                    }
                }
                if (type == FieldType.NUMERIC) {
                    boolean firstValue = true;
                    for (String value: values) {
                        // Index these fields as numeric too, for faster range queries
                        // (we do both because fields sometimes aren't exclusively numeric)
                        int n;
                        try {
                            n = Integer.parseInt(value);
                        } catch (NumberFormatException e) {
                            // This just happens sometimes, e.g. given multiple years, or
                            // descriptive text like "around 1900". OK to ignore.
                            n = 0;
                        }
                        currentDoc.addStoredNumericField(name, n, firstValue);
                        if (!firstValue) {
                            warn(documentName + " contains multiple values for single-valued numeric field " + name
                                    + "(values: " + StringUtils.join(values, "; ") + ")");
                        }
                        firstValue = false;
                    }
                }
            }

            // ------------------------------- Annotated fields ----------------------------------

            /**
             * Annotated fields we're indexing.
             */
            private final Map<String, AnnotatedFieldWriter> annotatedFields = new LinkedHashMap<>();

            /**
             * The first annotated field added is designated as main annotated field.
             */
            private AnnotatedFieldWriter mainAnnotatedField;

            /**
             * The indexing object for the annotated field we're currently processing.
             */
            protected AnnotatedFieldWriter currentAnnotatedField;

            /**
             * The _relation annotation (where inline tags and dependency relations are stored)
             * for the annotated field we're currently processing.
             */
            private AnnotationWriter annotRelation;

            /**
             * The main annotation for the annotated field we're currently processing.
             */
            private AnnotationWriter annotMain;

            /**
             * The main annotation for the annotated field we're currently processing.
             */
            private AnnotationWriter annotPunct;

            /**
             * If true, the next word gets no default punctuation even if
             * addDefaultPunctuation is true. Useful for implementing glue tag behaviour
             * (Sketch Engine WPL format)
             */
            private boolean preventNextDefaultPunctuation = false;

            /**
             * For capturing punctuation between words.
             */
            private StringBuilder punctuation = new StringBuilder();

            /**
             * Position of start tags and their index in the annotation arrays, so we can add
             * payload when we find the end tags
             */
            private record OpenTagInfo(String name, int index, int position, int relationId,
                                       Map<String, List<String>> attributes) {
            }

            /**
             * Currently opened inline tags we still need to add length payload to
             */
            private final List<OpenTagInfo> openInlineTags = new ArrayList<>();

            protected void addAnnotatedField(AnnotatedFieldWriter field) {
                annotatedFields.put(field.name(), field);
                if (getDocWriter() != null) {
                    IndexMetadataWriter indexMetadata = getDocWriter().metadata();
                    indexMetadata.registerAnnotatedField(field);
                }
            }

            protected AnnotatedFieldWriter getMainAnnotatedField() {
                if (mainAnnotatedField == null) {
                    // The main annotated field is the first annotated field
                    for (AnnotatedFieldWriter field: annotatedFields.values()) {
                        if (mainAnnotatedField == null)
                            mainAnnotatedField = field;
                    }
                }
                return mainAnnotatedField;
            }

            protected AnnotatedFieldWriter getAnnotatedField(String name) {
                return annotatedFields.get(name);
            }

            protected Map<String, AnnotatedFieldWriter> getAnnotatedFields() {
                return Collections.unmodifiableMap(annotatedFields);
            }

            protected void setCurrentAnnotatedFieldName(String name) {
                currentAnnotatedField = getAnnotatedField(name);
                if (currentAnnotatedField == null)
                    throw new InvalidInputFormatConfig("Tried to index annotated field " + name
                            + ", but field wasn't created. Likely cause: init() wasn't called. Did you call the base class method in index()?");
                annotRelation = currentAnnotatedField.tagsAnnotation();
                annotMain = currentAnnotatedField.mainAnnotation();
                annotPunct = currentAnnotatedField.punctAnnotation();
            }

            protected void addStartChar(int pos) {
                currentAnnotatedField.addStartChar(pos);
            }

            protected void addEndChar(int pos) {
                currentAnnotatedField.addEndChar(pos);
            }

            protected AnnotationWriter getAnnotation(String name) {
                return currentAnnotatedField.annotation(name);
            }

            protected int getCurrentTokenPosition() {
                return annotMain.lastValuePosition() + 1;
            }

            /**
             * Character position within the current document.
             */
            protected abstract int getCharacterPosition();

            /**
             * For parallel corpora where a document has multiple versions,
             * this is the character position within the version. For other
             * corpora, this is the same as {@link #getCharacterPosition()}.
             * Only supported by {@link InputFormatTypeXml} at the moment.
             */
            protected int getCharacterPositionWithinVersion() {
                return getCharacterPosition();
            }

            protected AnnotationWriter tagsAnnotation() {
                return annotRelation;
            }

            protected AnnotationWriter punctAnnotation() {
                return annotPunct;
            }

            protected void setPreventNextDefaultPunctuation() {
                preventNextDefaultPunctuation = true;
            }

            /**
             * What annotations where skipped because they were not declared?
             */
            final Set<String> skippedAnnotations = new HashSet<>();

            // ---- Storing documents ----

            protected void storeContent(ConfigAnnotatedField field, TextContent content) {
                getDocWriter().storeInContentStore(currentDoc, content, field.getName());
            }

            /**
             * Store the entire document at once.
             * Subclasses that simply capture the entire document can use this in their
             * storeDocument implementation.
             *
             * @param document document to store
             */
            protected void storeWholeDocument(String document) {
                storeWholeDocument(TextContent.from(document));
            }

            /**
             * Store the entire document at once.
             * Subclasses that simply capture the entire document can use this in their
             * storeDocument implementation.
             *
             * @param document document to store
             */
            protected void storeWholeDocument(TextContent document) {
                // Finish storing the document in the content store.
                // (Note that we do this after adding the "extra closing token", so the character
                // positions for the closing token still make (some) sense)
                String contentStoreName = getLinkedDocumentContentStoreName();
                if (contentStoreName == null) {
                    AnnotatedFieldWriter main = getMainAnnotatedField();
                    if (main != null) {
                        // Regular case. Store content for the main annotated field.
                        contentStoreName = main.name();
                    } else {
                        throw new InvalidIndex("No main annotated field defined, can't store document");
                    }
                }
                getDocWriter().storeInContentStore(currentDoc, document, contentStoreName);
            }

            /**
             * Store (or finish storing) the document in the content store.
             * Also set the content id field so we know how to retrieve it later.
             */
            public abstract void storeDocument();

            // ------------------------------- Linked documents ----------------------------------

            /**
             * The content store we should store this document in. Also stored the content
             * store id in the field with this name with "Cid" appended, e.g. "metadataCid"
             * if useContentStore equals "metadata". This is used for storing linked
             * document, if desired. Normally null, meaning document should be stored in the
             * default field and content store (usually "contents", with the id in field
             * "contents#cid").
             */
            protected String linkedDocumentContentStoreName = null;

            /**
             * Doc that linked to us (using linkedDocument), or null if not applicable.
             */
            protected Doc linkingDoc;

            protected String getLinkedDocumentContentStoreName() {
                return linkedDocumentContentStoreName;
            }

            /**
             * Index a linked document.
             *
             * @param inputFile             where the linked document can be found (file or http(s)
             *                              reference)
             * @param pathInsideArchive     if input file is an archive: the path to the file we
             *                              need inside the archive
             * @param documentPath          XPath to the specific linked document we need
             * @param inputFormatIdentifier input format of the linked document
             * @param storeWithName         if set, store the linked document and store the id to it
             *                              in a field with this name with "Cid" (content id) appended to it
             * @throws IOException on error
             */
            protected void indexLinkedDocument(String inputFile, String pathInsideArchive, String documentPath,
                    String inputFormatIdentifier, String storeWithName) throws IOException {
                // Fetch the input file (either by downloading it to a temporary location, or opening it from disk)
                File f = resolveFileReference(inputFile);

                // Get the data
                FileReference data;
                if (inputFile.endsWith(".zip") || inputFile.endsWith(".tar") || inputFile.endsWith(".tar.gz")
                        || inputFile.endsWith(".tgz")) {
                    // It's an archive. Unpack the right file from it.
                    data = FileProcessor.fetchFileFromArchive(f, pathInsideArchive);
                } else {
                    // Regular file.
                    data = FileReference.fromFile(f);
                }
                if (data == null)
                    throw new ErrorIndexingFile("Error reading linked document");

                // Index the data
                InputFormatInfo inputFormatInfo = DocumentFormats.getFormat(inputFormatIdentifier).orElseThrow();
                InputFormat inputFormat = inputFormatInfo.getInputFormat();
                if (inputFormat == null)
                    throw new ErrorIndexingFile(
                            "Could not instantiate linked input format, format not found? (" + inputFormatIdentifier
                                    + ")");
                inputFormat.indexSpecificDocument(getDocWriter(), data, documentPath, this, storeWithName);
            }

            /**
             * Index a specific document.
             *
             * @param documentExpr Expression (e.g. XPath) used to find the document to
             *                     index in the file
             */
            public IndexerStats indexSpecificDocument(String documentExpr, Doc linkingDoc, String storeWithName) {
                // documentExpr is ignored because plain text files always contain 1 document
                this.linkingDoc = linkingDoc;
                indexingIntoExistingDoc = true;
                linkedDocumentContentStoreName = storeWithName;
                return index();
            }

            /**
             * Given a URL or file reference, either download to a temp file or find file
             * and return it.
             *
             * @param inputFile URL or (relative) file reference
             * @return the file
             */
            private File resolveFileReference(String inputFile) throws IOException {
                if (inputFile.startsWith("http://") || inputFile.startsWith("https://")) {
                    return DownloadCache.downloadFile(inputFile);
                }
                if (inputFile.startsWith("file://"))
                    inputFile = inputFile.substring(7);
                File f = getDocWriter().linkedFile(inputFile);
                if (f == null)
                    throw new FileNotFoundException("Referenced file not found: " + inputFile);
                if (!f.canRead())
                    throw new IOException("Cannot read referenced file: " + f);
                return f;
            }

            // ------------------------------- Indexing process ----------------------------------

            protected void startDocument() {
                metadataFieldValues.clear();
                if (!indexingIntoExistingDoc) {
                    currentDoc = createNewDocument();
                    addMetadataField("fromInputFile", documentName);
                } else {
                    currentDoc = linkingDoc.getCurrentDoc();
                }
                if (getDocWriter() != null && !indexingIntoExistingDoc)
                    getDocWriter().listener().documentStarted(documentName);
            }

            protected void endDocument() {
                Map<String, Integer> docLengthsPerField = new HashMap<>();
                for (AnnotatedFieldWriter field: getAnnotatedFields().values()) {
                    AnnotationWriter propMain = field.mainAnnotation();

                    // Make sure all the annotations have an equal number of values.
                    // See what annotation has the highest position
                    // (in practice, only starttags and endtags should be able to have
                    // a position one higher than the rest)
                    int lastValuePos = 0;
                    for (AnnotationWriter prop: field.annotationWriters()) {
                        if (prop.lastValuePosition() > lastValuePos)
                            lastValuePos = prop.lastValuePosition();
                    }

                    // Make sure we always have one more token than the number of
                    // words, so there's room for any tags after the last word, and we
                    // know we should always skip the last token when matching.
                    if (propMain.lastValuePosition() == lastValuePos)
                        lastValuePos++;

                    // Add empty values to all lagging annotations
                    for (AnnotationWriter prop: field.annotationWriters()) {
                        if (prop.hasForwardIndex() || prop == propMain) {
                            while (prop.lastValuePosition() < lastValuePos) {
                                prop.addValue("");
                                if (prop.hasPayload())
                                    prop.addPayload(null);
                                if (prop == propMain) {
                                    field.addFinalStartEndChars();
                                }
                            }
                        }
                    }
                    // Store the different annotations of the annotated field that
                    // were gathered in lists while parsing.
                    field.addToDoc(currentDoc);

                    // Keep track of doc length for fragments later
                    docLengthsPerField.put(field.name(), lastValuePos);
                }

                if (isStoreDocuments()) {
                    storeDocument();
                }

                addMetadataToDocument(metadataFieldValues, false);
                try {
                    // Add Lucene doc to indexer, if not existing already
                    if (getDocWriter() != null && !indexingIntoExistingDoc) {
                        // Set the doc type field so we know this is a regular full document (as opposed to a fragment)
                        currentDoc.setType(BLInputDocument.DocType.DOCUMENT);
                        getDocWriter().add(currentDoc);
                    }

                    // Are there document fragments to store as well?
                    // (each fragment is stored in a separate Lucene document that references the main document)
                    if (!fragsPerField.isEmpty()) {
                        // For each annotated field that has fragments...
                        MetadataField pidField = getDocWriter().metadata().metadataFields().pidField();
                        BLFieldType untokenizedFieldType = getDocWriter().metadataFieldType(false);
                        if (pidField == null)
                            throw new InvalidInputFormatConfig("Cannot store fragments, input format config .blf.yaml has no pidField configured");
                        String pid = currentDoc.get(pidField.name());
                        for (Map.Entry<String, List<Fragment>> entry: fragsPerField.entrySet()) {
                            String annotatedFieldName = entry.getKey();
                            // Merge fragments with the same span, and chop overlapping fragments into non-overlapping fragments
                            List<Fragment> fragments = entry.getValue();
                            fragments = Fragment.mergeFragmentsWithSameSpan(fragments);
                            int docLength = docLengthsPerField.get(annotatedFieldName);
                            Map<String, Collection<String>> valuesToInheritFromDoc = new HashMap<>();
                            for (Map.Entry<String, Collection<String>> e: metadataFieldValues.entrySet()) {
                                ConfigMetadataField.FragmentBehaviour b = metadataFieldsFragmentBehaviour.getOrDefault(e.getKey(), ConfigMetadataField.FragmentBehaviour.DEFAULT);
                                if (!b.inheritFromDocLevel()) {
                                    // Should explicitly not inherit to document level (e.g. doc and fragment may each have a separate pid)
                                    continue;
                                }
                                if (e.getValue() != null && !e.getValue().isEmpty())
                                    valuesToInheritFromDoc.put(e.getKey(), e.getValue());
                            }
                            fragments = Fragment.chopOverlappingFragments(fragments, valuesToInheritFromDoc, docLength);
                            // Store each fragment in a separate Lucene document, with a reference to the main document
                            for (Fragment fragment: fragments) {
                                currentDoc = createNewDocument();
                                currentDoc.addField(BLInputDocument.FRAG_FIELD_DOC, pid, untokenizedFieldType);
                                currentDoc.addField(BLInputDocument.FRAG_FIELD_ANNOTATED_FIELD, annotatedFieldName, untokenizedFieldType);
                                currentDoc.addStoredNumericField(BLInputDocument.FRAG_FIELD_START, fragment.span().start(), true);
                                currentDoc.addStoredNumericField(BLInputDocument.FRAG_FIELD_END, fragment.span().end(), true);
                                addMetadataToDocument(fragment.metadata(), true);
                                // Set the doc type field so we know this is a fragment, not a full document
                                currentDoc.setType(BLInputDocument.DocType.FRAGMENT);
                                getDocWriter().add(currentDoc);
                            }
                            // Keep track of which metadata fields occur in fragments, so we can optimize queries on them
                            for (String fragmentField: metadataFieldsFragmentBehaviour.keySet()) {
                                getDocWriter().metadata().metadataFields().setOccursInFragment(fragmentField);
                            }
                        }
                    }
                } catch (Exception e) {
                    throw BlackLabException.wrapRuntime(e);
                }

                for (AnnotatedFieldWriter annotatedField: getAnnotatedFields().values()) {
                    // Reset annotated field for next document
                    // don't reuse buffers, they're still referenced by the lucene doc.
                    annotatedField.clear();
                }

                // Report progress
                if (getDocWriter() != null) {
                    reportTokensAndCharsProcessed();
                }
                if (getDocWriter() != null && !indexingIntoExistingDoc)
                    documentDone(documentName);

                currentDoc = null;

                // Stop if required
                if (getDocWriter() != null) {
                    if (!getDocWriter().continueIndexing())
                        throw new MaxDocsReached();
                }
            }

            protected void inlineTag(String tagName, boolean isOpenTag, Map<String, List<String>> attributes) {
                int currentPos = getCurrentTokenPosition();
                AnnotationWriter relationsAnnot = tagsAnnotation();
                if (isOpenTag) {
                    int tagIndex = relationsAnnot.indexInlineTag(tagName, currentPos, -1, attributes);
                    // We'll remember the relationId assigned above, even though the payload will updated later, when we encounter
                    // the closing tag. We have to use the same relationId in the updated payload, or it won't match the relationId
                    // stored in the attribute terms (which get a payload that only contains the relation id, so we can match them
                    // to their tag).
                    int relationId = relationsAnnot.getRelationIdAtIndex(tagIndex < 0 ? -tagIndex : tagIndex);
                    openInlineTags.add(new OpenTagInfo(tagName, tagIndex, currentPos, relationId, attributes));
                } else {
                    // Add payload to start tag annotation indicating end position
                    if (openInlineTags.isEmpty())
                        throw new MalformedInputFile("Close tag " + tagName + " found, but that tag is not open");
                    OpenTagInfo openTag = openInlineTags.remove(openInlineTags.size() - 1);
                    if (!openTag.name.equals(tagName))
                        throw new MalformedInputFile(
                                "Close tag " + tagName + " found, but " + openTag.name + " expected");
                    attributes = openTag.attributes;
                    boolean maybeExtraInfo = attributes != null && !attributes.isEmpty();
                    BytesRef payload = getPayloadCodec().inlineTagPayload(openTag.position, currentPos,
                            openTag.relationId, maybeExtraInfo);
                    int index = openTag.index;
                    if (index < 0) {
                        // Negative value means two terms were indexed (one with, one without attributes, for search performance)
                        // and this is the index of the last term. Make sure we update both payloads.
                        index = -index;
                        relationsAnnot.setPayloadAtIndex(index - 1, payload);
                    }
                    relationsAnnot.setPayloadAtIndex(index, payload);
                }
            }

            protected void punctuation(String punct) {
                punctuation.append(punct);
            }

            /**
             * calls {@link #getCharacterPositionWithinVersion()}
             */
            protected void beginWord() {
                addStartChar(getCharacterPositionWithinVersion());
            }

            /**
             * calls {@link #getCharacterPositionWithinVersion()}
             */
            protected void endWord() {
                String punct;
                if (punctuation.isEmpty())
                    punct = addDefaultPunctuation && !preventNextDefaultPunctuation ? " " : "";
                else
                    punct = punctuation.toString();

                preventNextDefaultPunctuation = false;
                // Normalize once more in case we hit more than one adjacent punctuation
                punctAnnotation().addValue(StringUtil.normalizeWhitespace(punct));
                addEndChar(getCharacterPositionWithinVersion());
                wordsDoneNotYetReported++;
                if (wordsDoneNotYetReported >= 5000) {
                    reportTokensAndCharsProcessed();
                }
                if (punctuation.length() > 10_000)
                    punctuation = new StringBuilder(); // let's not hold on to this much memory
                else
                    punctuation.setLength(0);
            }

            protected void annotationValueAppend(String name, String value, int increment) {
                int position = getAnnotation(name).lastValuePosition() + increment;
                annotationValue(name, value, position, null);
            }

            /**
             * Index an annotation.
             *
             * @param name     annotation name
             * @param value    annotation value (or span name or span attribute value)
             * @param position position to index value at
             */
            protected void annotationValue(String name, String value, int position) {
                annotationValue(name, value, position, null);
            }

            /**
             * Get payload for a span or relation.
             *
             * @param source         relation source (or span start, 0-length)
             * @param target         relation target (or span end, 0-length)
             * @param annotType      type of payload to get: token, span or relation
             * @param maybeExtraInfo is there (maybe) extra information to be lookup in the relation index?
             * @param indexPosition  token position this will be indexed at
             * @return null for token annotations; the payload for spans and relations.
             */
            protected BytesRef getPayload(Span source, Span target, AnnotationType annotType,
                    boolean maybeExtraInfo, int indexPosition) {
                BytesRef payload = null;
                switch (annotType) {
                case TOKEN:
                    // no payload for token annotation
                    break;
                case SPAN:
                    // Span: index as a relation from the start of source to the start of target (0-length)
                    //   (and in the classic external index, the payload just contains the end position)
                    payload = getPayloadCodec().inlineTagPayload(indexPosition, target.start(),
                            tagsAnnotation().getNextRelationId(maybeExtraInfo), maybeExtraInfo);
                    break;
                case RELATION:
                    // Relation: index with the full source and target spans
                    boolean onlyHasTarget = !Span.isValid(source); // standoff root annotation

                    // Root relations have no source, so we index them at their target position.
                    // In this case we set source start/end to target start, so it is indexed there and
                    // source length does not need to be stored (because 0 is the default value, see
                    // RelationInfo.serializeRelation).
                    int sourceStart = indexPosition;
                    int sourceEnd = onlyHasTarget ? indexPosition : source.end();

                    payload = getPayloadCodec().relationPayload(onlyHasTarget, sourceStart, sourceEnd,
                            target.start(), target.end(), tagsAnnotation().getNextRelationId(maybeExtraInfo),
                            maybeExtraInfo);
                    break;
                }
                return payload;
            }

            /**
             * Index an annotation, span or relation.
             * Also used to index inline tags (spans). In that case, spanEndOrRelTarget is >= 0.
             * For the external index, this method is called several times, once for the tag
             * name and once for each attribute. For the internal index, this method is
             * called once, with an already-prepared term to index that includes all this information.
             *
             * @param name     annotation name
             * @param value    annotation value (or span name or span attribute value)
             * @param position position to index value at
             * @param payload  payload to add to the annotation value
             */
            protected void annotationValue(String name, String value, int position, BytesRef payload) {
                AnnotationWriter annotation = getAnnotation(name);
                if (annotation != null) {
                    annotation.addValueAtPosition(value, position, payload);
                } else {
                    // Annotation not declared; report, but keep going
                    if (!skippedAnnotations.contains(name)) {
                        skippedAnnotations.add(name);
                        logger.error(documentName + ": skipping undeclared annotation " + name);
                    }
                }
            }

            // --------------- Progress tracking -----------------

            /**
             * How many documents have been processed for the current file
             */
            private int numberOfDocsDone = 0;

            /**
             * How many tokens have been processed for the current file
             */
            private int numberOfTokensDone = 0;

            /**
             * Total words processed by this indexer. Used for reporting progress, do not
             * reset except when finished with file.
             */
            private int wordsDoneNotYetReported = 0;
            private int charsDoneAtLastReport = 0;

            protected final void reportTokensAndCharsProcessed() {
                // Chars
                final int charsDone = getCharacterPosition();
                final int charsDoneSinceLastReport = charsDone - charsDoneAtLastReport;
                getDocWriter().listener().charsDone(charsDoneSinceLastReport);
                charsDoneAtLastReport = charsDone;

                // Tokens
                tokensDone(wordsDoneNotYetReported);
                wordsDoneNotYetReported = 0;
            }

            /**
             * Keep track of how many tokens have been processed.
             */
            public void documentDone(String documentName) {
                numberOfDocsDone++;
                getDocWriter().listener().documentDone(documentName);

                // Force a merge after each document? (debug feature)
                if (Boolean.parseBoolean(BlackLab.featureFlag(BlackLab.FEATURE_DEBUG_FORCE_MERGE)))
                    docWriter.debugForceMerge();
            }

            /**
             * Keep track of how many tokens have been processed.
             */
            public void tokensDone(int n) {
                numberOfTokensDone += n;
                getDocWriter().listener().tokensDone(n);
            }

            protected void warn(String msg) {
                getDocWriter().listener().warning(msg);
            }

            protected IndexerStats getStats() {
                return new IndexerStats(numberOfDocsDone, numberOfTokensDone);
            }

            protected void resetStats() {
                numberOfDocsDone = 0;
                numberOfTokensDone = 0;
            }

        }

        /**
         * Store documents? Can be set to false in ConfigInputFormat to if no content
         * store is desired, or via indexSpecificDocument to prevent storing linked
         * documents.
         */
        private boolean storeDocuments = true;

        public IndexerStats index(DocWriter writer, FileReference file) throws ErrorIndexingFile {
            try (Doc doc = createDoc(writer, file)) {
                return doc.index();
            }
        }

        @Override
        public void indexSpecificDocument(DocWriter writer, FileReference file, String documentPath, Doc linkingDoc,
                String storeWithName) {
            try (Doc doc = createDoc(writer, file)) {
                doc.indexSpecificDocument(documentPath, linkingDoc, storeWithName);
            }
        }

        protected abstract Doc createDoc(DocWriter docWriter, FileReference file);

        /**
         * If no punctuation expression is defined, add a space between each word by
         * default.
         */
        private boolean addDefaultPunctuation = true;

        protected void setAddDefaultPunctuation(boolean addDefaultPunctuation) {
            this.addDefaultPunctuation = addDefaultPunctuation;
        }

        // ---- Storing documents ----

        protected void setStoreDocuments(boolean storeDocuments) {
            this.storeDocuments = storeDocuments;
        }

        protected boolean isStoreDocuments() {
            return storeDocuments;
        }
    }

}
