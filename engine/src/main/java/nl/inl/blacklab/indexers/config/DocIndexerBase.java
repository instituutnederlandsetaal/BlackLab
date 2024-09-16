package nl.inl.blacklab.indexers.config;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.lucene.util.BytesRef;

import nl.inl.blacklab.analysis.PayloadUtils;
import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.exceptions.InvalidInputFormatConfig;
import nl.inl.blacklab.exceptions.MalformedInputFile;
import nl.inl.blacklab.exceptions.MaxDocsReached;
import nl.inl.blacklab.index.DocIndexer;
import nl.inl.blacklab.index.DocIndexerAbstract;
import nl.inl.blacklab.index.DocumentFormats;
import nl.inl.blacklab.index.InputFormat;
import nl.inl.blacklab.index.annotated.AnnotatedFieldWriter;
import nl.inl.blacklab.index.annotated.AnnotationWriter;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldNameUtil;
import nl.inl.blacklab.search.indexmetadata.IndexMetadataWriter;
import nl.inl.util.DownloadCache;
import nl.inl.util.FileProcessor;
import nl.inl.util.FileReference;
import nl.inl.util.StringUtil;
import nl.inl.util.TextContent;

public abstract class DocIndexerBase extends DocIndexerAbstract {

    private static final boolean TRACE = false;

    /**
     * Position of start tags and their index in the annotation arrays, so we can add
     * payload when we find the end tags
     */
    private static final class OpenTagInfo {

        public final String name;

        public final int index;

        private final int position;

        private final int relationId;

        public OpenTagInfo(String name, int index, int position, int relationId) {
            this.name = name;
            this.index = index;
            this.position = position;
            this.relationId = relationId;
        }
    }

    /** Annotated fields we're indexing. */
    private final Map<String, AnnotatedFieldWriter> annotatedFields = new LinkedHashMap<>();

    /**
     * The first annotated field added is designated as main annotated field.
     */
    private AnnotatedFieldWriter mainAnnotatedField;

    /** The indexing object for the annotated field we're currently processing. */
    protected AnnotatedFieldWriter currentAnnotatedField;

    /** The _relation annotation (where inline tags and dependency relations are stored)
        for the annotated field we're currently processing. */
    private AnnotationWriter annotRelation;

    /** The main annotation for the annotated field we're currently processing. */
    private AnnotationWriter annotMain;

    /** The main annotation for the annotated field we're currently processing. */
    private AnnotationWriter annotPunct;

    /**
     * If no punctuation expression is defined, add a space between each word by
     * default.
     */
    private boolean addDefaultPunctuation = true;

    /**
     * If true, the next word gets no default punctuation even if
     * addDefaultPunctuation is true. Useful for implementing glue tag behaviour
     * (Sketch Engine WPL format)
     */
    private boolean preventNextDefaultPunctuation = false;

    /** For capturing punctuation between words. */
    private StringBuilder punctuation = new StringBuilder();

    /**
     * If true, we're indexing into an existing Lucene document. Don't overwrite it
     * with a new one.
     */
    private boolean indexingIntoExistingDoc = false;

    /** Currently opened inline tags we still need to add length payload to */
    private final List<OpenTagInfo> openInlineTags = new ArrayList<>();

    /**
     * Store documents? Can be set to false in ConfigInputFormat to if no content
     * store is desired, or via indexSpecificDocument to prevent storing linked
     * documents.
     */
    private boolean storeDocuments = true;

    /**
     * The content store we should store this document in. Also stored the content
     * store id in the field with this name with "Cid" appended, e.g. "metadataCid"
     * if useContentStore equals "metadata". This is used for storing linked
     * document, if desired. Normally null, meaning document should be stored in the
     * default field and content store (usually "contents", with the id in field
     * "contents#cid").
     */
    private String linkedDocumentContentStoreName = null;

    /**
     * Total words processed by this indexer. Used for reporting progress, do not
     * reset except when finished with file.
     */
    protected int wordsDone = 0;
    private int wordsDoneAtLastReport = 0;
    private int charsDoneAtLastReport = 0;

    /**
     * What annotations where skipped because they were not declared?
     */
    final Set<String> skippedAnnotations = new HashSet<>();

    /** Indexer that linked to us (linkedDocument), or null if not applicable. */
    protected DocIndexerBase linkingIndexer;

    protected String getLinkedDocumentContentStoreName() {
        return linkedDocumentContentStoreName;
    }

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
            for (AnnotatedFieldWriter field : annotatedFields.values()) {
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

    protected AnnotationWriter tagsAnnotation() {
        return annotRelation;
    }

    protected AnnotationWriter punctAnnotation() {
        return annotPunct;
    }

    @Deprecated
    protected AnnotationWriter propPunct() {
        return punctAnnotation();
    }

    protected void setPreventNextDefaultPunctuation() {
        preventNextDefaultPunctuation = true;
    }

    /**
     * Index a linked document.
     *
     * @param inputFile where the linked document can be found (file or http(s)
     *            reference)
     * @param pathInsideArchive if input file is an archive: the path to the file we
     *            need inside the archive
     * @param documentPath XPath to the specific linked document we need
     * @param inputFormatIdentifier input format of the linked document
     * @param storeWithName if set, store the linked document and store the id to it
     *            in a field with this name with "Cid" (content id) appended to it
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
            throw new BlackLabRuntimeException("Error reading linked document");

        // Index the data
        InputFormat inputFormat = DocumentFormats.getFormat(inputFormatIdentifier).orElseThrow();
        try (DocIndexer docIndexer = inputFormat.createDocIndexer(getDocWriter(), data)) {
            if (docIndexer instanceof DocIndexerBase) {
                DocIndexerBase ldi = (DocIndexerBase) docIndexer;
                ldi.indexingIntoExistingDoc = true;
                ldi.currentDoc = currentDoc;
                ldi.linkingIndexer = this;
                ldi.metadataFieldValues = metadataFieldValues;
                if (storeWithName != null) {
                    // If specified, store in this content store and under this name instead of the default
                    ldi.linkedDocumentContentStoreName = storeWithName;
                }
                ldi.indexSpecificDocument(documentPath);
            } else {
                if (docIndexer == null)
                    throw new BlackLabRuntimeException("Could not instantiate linked DocIndexer, format not found? (" + inputFormatIdentifier + ")");
                else
                    throw new BlackLabRuntimeException("Linked document indexer must be subclass of DocIndexerBase, but is "
                        + docIndexer.getClass().getName());
            }
        }
    }

    /**
     * Index a specific document.
     *
     * Only supported by DocIndexerBase.
     *
     * @param documentExpr Expression (e.g. XPath) used to find the document to
     *            index in the file
     */
    public abstract void indexSpecificDocument(String documentExpr);

    /**
     * Given a URL or file reference, either download to a temp file or find file
     * and return it.
     *
     * @param inputFile URL or (relative) file reference
     * @return the file
     */
    protected File resolveFileReference(String inputFile) throws IOException {
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

    protected void trace(String msg) {
        if (TRACE)
            System.out.print(msg);
    }

    protected void traceln(String msg) {
        if (TRACE)
            System.out.println(msg);
    }

    protected void setStoreDocuments(boolean storeDocuments) {
        this.storeDocuments = storeDocuments;
    }

    protected boolean isStoreDocuments() {
        return storeDocuments;
    }

    protected void startDocument() {

        traceln("START DOCUMENT");
        if (!indexingIntoExistingDoc) {
            currentDoc = createNewDocument();
            addMetadataField("fromInputFile", documentName);
        }
        if (getDocWriter() != null && !indexingIntoExistingDoc)
            getDocWriter().listener().documentStarted(documentName);
    }

    protected void endDocument() {
        traceln("END DOCUMENT");

        for (AnnotatedFieldWriter field : getAnnotatedFields().values()) {
            AnnotationWriter propMain = field.mainAnnotation();

            // Make sure all the annotations have an equal number of values.
            // See what annotation has the highest position
            // (in practice, only starttags and endtags should be able to have
            // a position one higher than the rest)
            int lastValuePos = 0;
            for (AnnotationWriter prop : field.annotationWriters()) {
                if (prop.lastValuePosition() > lastValuePos)
                    lastValuePos = prop.lastValuePosition();
            }

            // Make sure we always have one more token than the number of
            // words, so there's room for any tags after the last word, and we
            // know we should always skip the last token when matching.
            if (propMain.lastValuePosition() == lastValuePos)
                lastValuePos++;

            // Add empty values to all lagging annotations
            for (AnnotationWriter prop : field.annotationWriters()) {
                while (prop.lastValuePosition() < lastValuePos) {
                    prop.addValue("");
                    if (prop.hasPayload())
                        prop.addPayload(null);
                    if (prop == propMain) {
                        field.addFinalStartEndChars();
                    }
                }
            }
            // Store the different annotations of the annotated field that
            // were gathered in lists while parsing.
            field.addToDoc(currentDoc);

            // Add the field with all its annotations to the forward index
            addToForwardIndex(field);

        }

        if (isStoreDocuments()) {
            storeDocument();
        }

        if (!indexingIntoExistingDoc)
            addMetadataToDocument();
        try {
            // Add Lucene doc to indexer, if not existing already
            if (getDocWriter() != null && !indexingIntoExistingDoc)
                getDocWriter().add(currentDoc);
        } catch (Exception e) {
            throw BlackLabRuntimeException.wrap(e);
        }

        for (AnnotatedFieldWriter annotatedField : getAnnotatedFields().values()) {
            // Reset annotated field for next document
            // don't reuse buffers, they're still referenced by the lucene doc.
            annotatedField.clear();
        }

        // Report progress
        if (getDocWriter() != null) {
            reportCharsProcessed();
            reportTokensProcessed();
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

    protected void storeContent(ConfigAnnotatedField field, TextContent content) {
        getDocWriter().storeInContentStore(currentDoc, content,
                AnnotatedFieldNameUtil.contentIdField(field.getName()), field.getName());
    }

    /**
     * Store the entire document at once.
     *
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
     *
     * Subclasses that simply capture the entire document can use this in their
     * storeDocument implementation.
     *
     * @param document document to store
     */
    protected void storeWholeDocument(TextContent document) {
        // Finish storing the document in the document store,
        // retrieve the content id, and store that in Lucene.
        // (Note that we do this after adding the "extra closing token", so the character
        // positions for the closing token still make (some) sense)
        String contentIdFieldName;
        String contentStoreName = getLinkedDocumentContentStoreName();
        if (contentStoreName != null) {
            contentIdFieldName = contentStoreName + "Cid";
        } else {
            AnnotatedFieldWriter main = getMainAnnotatedField();
            if (main != null) {
                // Regular case. Store content for the main annotated field.
                contentStoreName = main.name();
                contentIdFieldName = AnnotatedFieldNameUtil.contentIdField(main.name());
            } else {
                throw new BlackLabRuntimeException("No main annotated field defined, can't store document");
                /*
                // We're indexing documents and storing the contents,
                // but we don't have a main annotated field in the current indexing configuration.
                // This happens when indexing linked metadata documents, which are stored but don't
                // have annotated content to be indexed in a field.
                // (OLD HACK, NOT USED ANYMORE..?)
                contentStoreName = "metadata";
                contentIdFieldName = "metadataCid";
                */
            }
        }
        getDocWriter().storeInContentStore(currentDoc, document, contentIdFieldName, contentStoreName);
    }

    /**
     * Store (or finish storing) the document in the content store.
     *
     * Also set the content id field so we know how to retrieve it later.
     */
    protected abstract void storeDocument();

    protected void inlineTag(String tagName, boolean isOpenTag, Map<String, String> attributes) {
        int currentPos = getCurrentTokenPosition();
        AnnotationWriter relationsAnnot = tagsAnnotation();
        if (isOpenTag) {
            trace("<" + tagName + ">");
            int relationId = relationsAnnot.getNextRelationId();
            int tagIndex = relationsAnnot.indexInlineTag(tagName, currentPos, -1, attributes,
                    getIndexType(), relationId);
            openInlineTags.add(new OpenTagInfo(tagName, tagIndex, currentPos, relationId));
        } else {
            traceln("</" + tagName + ">");

            // Add payload to start tag annotation indicating end position
            if (openInlineTags.isEmpty())
                throw new MalformedInputFile("Close tag " + tagName + " found, but that tag is not open");
            OpenTagInfo openTag = openInlineTags.remove(openInlineTags.size() - 1);
            if (!openTag.name.equals(tagName))
                throw new MalformedInputFile(
                        "Close tag " + tagName + " found, but " + openTag.name + " expected");
            BytesRef payload = PayloadUtils.inlineTagPayload(openTag.position, currentPos, getIndexType(),
                    openTag.relationId);
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
        trace(punct);
        punctuation.append(punct);
    }

    protected void setAddDefaultPunctuation(boolean addDefaultPunctuation) {
        this.addDefaultPunctuation = addDefaultPunctuation;
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
        if (punctuation.length() == 0)
            punct = addDefaultPunctuation && !preventNextDefaultPunctuation ? " " : "";
        else
            punct = punctuation.toString();

        preventNextDefaultPunctuation = false;
        // Normalize once more in case we hit more than one adjacent punctuation
        punctAnnotation().addValue(StringUtil.normalizeWhitespace(punct));
        addEndChar(getCharacterPositionWithinVersion());
        wordsDone++;
        if (wordsDone > 0 && wordsDone % 5000 == 0) {
            reportCharsProcessed();
            reportTokensProcessed();
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
     * @param name annotation name
     * @param value annotation value (or span name or span attribute value)
     * @param position position to index value at
     */
    protected void annotationValue(String name, String value, int position) {
        annotationValue(name, value, position, null);
    }

    /**
     * Index a token, span or relation.
     *
     * @param name annotation name
     * @param value annotation value (or span name or span attribute value)
     * @param position token position, span start, or relation source
     * @param spanEndOrRelTarget (optional) span end or relation target (or null if this is just a token)
     * @param annotType the type of annotation we're indexing: a token, a span (inline tag) or a relation
     */
    protected void annotationValue(String name, String value, Span position, Span spanEndOrRelTarget,
            AnnotationType annotType) {
        // Start of positionSpan gives the position where this will be indexed, unless it's a root relation,
        // which has no source, so we index it at its target.
        int indexAtPosition = position.start() < 0 ?
                spanEndOrRelTarget.start() : position.start();

        BytesRef payload = null;
        switch (annotType) {
        case TOKEN:
            // no payload for token annotation
            break;
        case SPAN:
            // Span: index as a relation from the start of source to the start of target (0-length)
            //   (and in the classic external index, the payload just contains the end position)
            payload = PayloadUtils.inlineTagPayload(indexAtPosition, spanEndOrRelTarget.start(),
                    getIndexType(), tagsAnnotation().getNextRelationId());
            break;
        case RELATION:
            // Relation: index with the full source and target spans
            boolean onlyHasTarget = !Span.isValid(position); // standoff root annotation

            // Root relations have no source, so we index them at their target position.
            // In this case we set source start/end to target start, so it is indexed there and
            // source length does not need to be stored (because 0 is the default value, see
            // RelationInfo.serializeRelation).
            int sourceStart = indexAtPosition;
            int sourceEnd = onlyHasTarget ? indexAtPosition : position.end();

            payload = PayloadUtils.relationPayload(onlyHasTarget, sourceStart, sourceEnd,
                    spanEndOrRelTarget.start(), spanEndOrRelTarget.end(), tagsAnnotation().getNextRelationId());
            break;
        }
        annotationValue(name, value, indexAtPosition, payload);
    }

    /**
     * Index an annotation, span or relation.
     *
     * Also used to index inline tags (spans). In that case, spanEndOrRelTarget is >= 0.
     * For the external index, this method is called several times, once for the tag
     * name and once for each attribute. For the internal index, this method is
     * called once, with an already-prepared term to index that includes all this information.
     *
     * @param name annotation name
     * @param value annotation value (or span name or span attribute value)
     * @param position position to index value at
     * @param payload payload to add to the annotation value
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

    @Override
    public final void reportCharsProcessed() {
        final int charsDone = getCharacterPosition();
        final int charsDoneSinceLastReport;
        if (charsDoneAtLastReport > charsDone)
            charsDoneSinceLastReport = charsDone;
        else
            charsDoneSinceLastReport = charsDone - charsDoneAtLastReport;

        getDocWriter().listener().charsDone(charsDoneSinceLastReport);
        charsDoneAtLastReport = charsDone;
    }

    /**
     * Report the change in wordsDone since the last report
     */
    @Override
    public final void reportTokensProcessed() {
        final int wordsDoneSinceLastReport;
        if (wordsDoneAtLastReport > wordsDone) // wordsDone reset by child class? report everything then
            wordsDoneSinceLastReport = wordsDone;
        else
            wordsDoneSinceLastReport = wordsDone - wordsDoneAtLastReport;

        tokensDone(wordsDoneSinceLastReport);
        wordsDoneAtLastReport = wordsDone;
    }

    @Override
    public void setDocument(FileReference file) {
        if (documentName == null)
            documentName = file.getPath();
    }

    /**
     * Are dashes forbidden in annotation names?
     *
     * True for classic index format, false for integrated index format.
     * Annotation names must be valid XML element names, which is why we sanitize certain
     * characters. But dashes are valid in XML element names. For compatibility, classic index
     * format still forbids dashes, but newer index formats allow them.
     *
     * @return true if dashes should be sanitized from annotation names
     */
    protected boolean disallowDashInname() {
        return getDocWriter().getIndexType() == BlackLabIndex.IndexType.EXTERNAL_FILES;
    }
}
