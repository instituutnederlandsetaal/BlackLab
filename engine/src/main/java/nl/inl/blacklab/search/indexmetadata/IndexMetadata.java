package nl.inl.blacklab.search.indexmetadata;

import java.io.File;
import java.util.Map;

import nl.inl.blacklab.indexers.config.TextDirection;
import nl.inl.blacklab.search.results.CorpusSize;

/**
 * Runtime information about the shape of a BlackLab index, such as its fields structure.
 * Note: this has some overlap with {@link nl.inl.blacklab.indexers.config.ConfigInputFormat},
 * with some differences:
 * The ConfigInputFormat defines how fields etc. should be extracted from an input document, and serves as a sort of "template" for the index.
 * This class contains no such info, it only contains info that is actually present in the index, things like: the name, all present fields (annotations and metadata both), total size, etc.
 * 
 * During indexing/writing, this class is mutable and constantly updated whenever a new field is encountered.
 * During searching/reading, this class is readonly ("frozen"). At the end of the indexing process, the metadata is stored in/along the index.
 */
public interface IndexMetadata extends Freezable {

    static String indexNameFromDirectory(File directory) {
        String name = directory.getName();
        if (name.equals("index"))
            name = directory.getAbsoluteFile().getParentFile().getName();
        return name;
    }

    String indexFlag(String name);

    AnnotatedFields annotatedFields();
	
	default AnnotatedField mainAnnotatedField() {
	    return annotatedFields().main();
	}
	
    default AnnotatedField annotatedField(String name) {
        return annotatedFields().get(name);
    }
    
	MetadataFields metadataFields();
	
	default MetadataField metadataField(String name) {
	    return metadataFields().get(name);
	}

    /**
     * Get the custom properties for this corpus.
     *
     * Custom properties are not used by BlackLab, but are passed on by BLS
     * for use by applications, e.g. a search GUI.
     *
     * Examples: displayName, description, textDirection.
     *
     * @return map of custom properties
     */
    CustomProps custom();

	/**
	 * Get the display name for the index.
	 *
	 * If no display name was specified, returns the name of the index directory.
	 *
	 * @return the display name
     * @deprecated use {@link #custom()} and get("displayName", "") instead
	 */
    @Deprecated
	String displayName();

	/**
	 * Get a description of the index, if specified
	 * @return the description
     * @deprecated use {@link #custom()} and get("description", "") instead
	 */
    @Deprecated
	String description();

	/**
	 * Is the content freely viewable by all users, or is it restricted?
	 * @return true if the full content may be retrieved by anyone
	 */
	boolean contentViewable();

    /**
     * What's the text direction of this corpus?
     * @return text direction
     * @deprecated use {@link #custom()} and get("textDirection", "ltr") instead
     */
    @Deprecated
	TextDirection textDirection();

	/**
	 * What format(s) is/are the documents in?
	 *
	 * This is in the form of a format identifier as understood
	 * by the DocumentFormats class (either an abbreviation or a
	 * (qualified) class name).
	 *
	 * @return the document format(s)
	 */
	String documentFormat();

	/**
	 * What version of the index format is this?
	 * @return the index format version
	 */
	String indexFormat();

	/**
	 * When was this index created?
	 * @return date/time
	 */
	String timeCreated();

	/**
	 * When was this index last modified?
	 * @return date/time
	 */
	String timeModified();

	/**
	 * When was the BlackLab.jar used for indexing built?
	 * @return date/time
	 */
	String indexBlackLabBuildTime();

	/**
	 * When was the BlackLab.jar used for indexing built?
	 * @return date/time stamp
	 */
	String indexBlackLabVersion();

    /**
     * What SCM revision (i.e. Git hash) was this indexed with?
     *
     * @return the SCM revision
     */
    String indexBlackLabScmRevision();

	/**
	 * How many tokens are in the main annotated field?
     *
	 * @return number of tokens
	 */
	long tokenCount();

    /**
     * How many tokens are in the annotated fields?
     *
     * @return number of tokens per field
     */
    Map<String, CorpusSize.Count> countPerField();

    /**
     * How many documents are in the index?
     *
     * This reports the number of live documents in the index
     * that have a value for the main annotated field.
     *
     * This does therefore not include the index metadata document
     * (if using integrated index format).
     *
     * @return number of documents
     */
    int documentCount();

    /**
     * How many documents are in the index?
     *
     * This reports the number of live documents in the index
     * that have a value for the main annotated field.
     *
     * This does therefore not include the index metadata document
     * (if using integrated index format).
     *
     * @return number of documents
     */
    default int documentVersionCount() {
        return -1;
    }

	/**
	 * Is this a new, empty index?
	 *
	 * An empty index is one that doesn't have a main contents field yet,
	 * or has a main contents field but no indexed tokens yet.
	 *
	 * @return true if it is, false if not.
	 */
	boolean isNewIndex();

    /**
     * Return the id of the index metadata document.
     *
     * This document, if it exists, should be skipped when searching.
     *
     * @return special docId, or -1 if none
     */
    default int metadataDocId() {
        return -1;
    }

    /**
     * Debug method to retrieve the index metadata in string form (JSON)
     * @return index metadata
     */
    default String getIndexMetadataAsString() {
        throw new UnsupportedOperationException("Not supported for this index type (check dir for existing indexmetadata.yaml)");
    }

    default void setIndexMetadataFromString(String indexmetadata) {
        throw new UnsupportedOperationException("Not supported for this index type (check dir for existing indexmetadata.yaml)");
    }

    default Annotation annotationFromFieldAndName(String fieldAndAnnotationName, AnnotatedField defaultField) {
        String annotationName = fieldAndAnnotationName;
        if (annotationName.contains(Annotation.FIELD_ANNOTATION_SEPARATOR)) {
            // Annotation is from a different field than the main search field.
            String[] fieldAndAnnotation = annotationName.split(Annotation.FIELD_ANNOTATION_SEPARATOR);

            // If we specified a parallel version, not a field (e.g. "nl%lemma" instead of "contents__nl%lemma",
            // resolve the actual field name)
            String fieldName = fieldAndAnnotation[0];
            if (!annotatedFields().exists(fieldName)) {
                fieldName = AnnotatedFieldNameUtil.changeParallelFieldVersion(defaultField.name(),
                        fieldName);
            }

            defaultField = annotatedField(fieldName);
            if (defaultField == null)
                throw new IllegalArgumentException("Unknown field '" + fieldName +
                        "' as part of field+annotation '" + fieldAndAnnotationName + "'");
            annotationName = fieldAndAnnotation[1];
        }
        Annotation annotation = defaultField.annotation(annotationName);
        if (annotation == null)
            throw new IllegalArgumentException("Unknown annotation reference: " + fieldAndAnnotationName);
        return annotation;
    }
}
