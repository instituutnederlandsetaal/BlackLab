package nl.inl.blacklab.search;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import nl.inl.blacklab.exceptions.FeatureNotPresentInCorpus;
import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldNameUtil;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.AnnotationSensitivity;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.webservice.WsParam;

/**
 * Represents the context while evaluating a TextPattern.
 *
 * While evaluating a TextPattern (i.e. translating it to a BLSpanQuery),
 * this context may vary: a different annotation may be "selected" to
 * search in, the case sensitivity setting may change, etc. This object
 * is passed to the translation methods and keeps track of this context.
 *
 * The context also keeps track of whether we're in the query part or the
 * constraint part (after :: ), as this affects what object type the patterns
 * evaluate to (BLSpanQuery vs MatchFilter).
 */
public class TextPatternContext {

    public static TextPatternContext get(String annotatedField, String annotation,
            MatchSensitivity matchSensitivity, BlackLabIndex index) {
        String defaultRelationClass = index.getRelationsStats(index.annotatedField(annotatedField),
                        WsParam.DEF_VAL_LIMIT_VALUES).getDefaultClass();
        return new TextPatternContext(annotatedField, null, annotation,
                matchSensitivity, defaultRelationClass, null, false, index);
    }

    /** Our query info object */
    private final BlackLabIndex index;

    /** Name of the annotated field we're searching.
     * <p>
     * Note that this may differ from the original search field if this is a parallel corpus and this part of the query
     * is searching a different version.
     */
    private final String field;

    /** Version of the document we're searching, or null if this is not a parallel corpus. */
    private final String version;

    /** Annotation to search */
    private final String annotationName;

    /** The originally requested match sensitivity.
     *
     *  This might be different from the AnnotationSensitivity we search, because not all
     *  fields support all sensitivities. */
    private final MatchSensitivity requestedSensitivity;

    /** The sensitivity variant of our annotation we'll search. */
    private final AnnotationSensitivity sensitivity;

    /** Default relation class to search, or null to use index default */
    private final String defaultRelationClass;

    /** Registered capture names */
    private final Set<String> captures;

    /** Are we currently in a constraint (e.g. after :: )? */
    private final boolean inConstraint;

    /**
     * Construct a query execution context object.
     *
     * @param annotatedField the annotated field to search
     * @param version the version to search (if this is a parallel corpus; otherwise null)
     * @param annotationName the annotation to search
     * @param matchSensitivity whether search defaults to case-/diacritics-sensitive
     * @param defaultRelationClass default relation class to search (or null to use global default)
     * @param captures unique capture names assigned so far
     * @param inConstraint true if we're currently inside a constraints (i.e. after ::), false if not
     * @param index index to use
     */
    private TextPatternContext(String annotatedField, String version, String annotationName,
            MatchSensitivity matchSensitivity, String defaultRelationClass, Set<String> captures, boolean inConstraint,
            BlackLabIndex index) throws InvalidQuery {
        if (defaultRelationClass == null)
            throw new IllegalArgumentException("defaultRelationClass == null");
        if (index == null)
            throw new IllegalArgumentException("index == null");
        if (annotatedField == null)
            throw new IllegalArgumentException("field == null");
        this.field = version == null ? annotatedField : AnnotatedFieldNameUtil.changeParallelFieldVersion(annotatedField, version);
        this.version = version;
        AnnotatedField annotatedFieldDef = index.annotatedField(this.field);
        this.annotationName = annotationName == null ?
                annotatedFieldDef.defaultSearchAnnotation().name() : annotationName;
        this.requestedSensitivity = matchSensitivity;
        sensitivity = determineSensitivity(annotatedFieldDef, this.annotationName, matchSensitivity);
        this.defaultRelationClass = defaultRelationClass;
        this.captures = captures == null ? new HashSet<>() : captures;
        this.inConstraint = inConstraint;
        this.index = index;
    }

    private AnnotationSensitivity determineSensitivity(AnnotatedField annotatedField, String annotationName,
            MatchSensitivity matchSensitivity) {
        Annotation annotation = annotatedField.annotation(annotationName);
        if (annotation == null)
            throw new FeatureNotPresentInCorpus("Annotation doesn't exist: " + annotationName + " on field " + annotatedField.name());
        return getAppropriateSensitivity(annotation, matchSensitivity);
    }

    /**
     * Return sensitivity to use.
     *
     * Because not all annotations have all sensitivities, we may have to fall back to a different one.
     *
     * @param annotation annotation to search
     * @param requestedSensitivity what sensitivity we'd ideally like
     * @return sensitivity to use
     */
    private static AnnotationSensitivity getAppropriateSensitivity(Annotation annotation,
            MatchSensitivity requestedSensitivity) {
        switch (requestedSensitivity) {
        case INSENSITIVE:
            // search insensitive if available
            if (annotation.hasSensitivity(MatchSensitivity.INSENSITIVE))
                return annotation.sensitivity(MatchSensitivity.INSENSITIVE);
            return annotation.sensitivity(MatchSensitivity.SENSITIVE);
        case SENSITIVE:
            // search fully-sensitive if available
            if (annotation.hasSensitivity(MatchSensitivity.SENSITIVE))
                return annotation.sensitivity(MatchSensitivity.SENSITIVE);
            return annotation.sensitivity(MatchSensitivity.INSENSITIVE);
        case DIACRITICS_INSENSITIVE:
            // search diacritics-insensitive if available
            if (annotation.hasSensitivity(MatchSensitivity.DIACRITICS_INSENSITIVE))
                return annotation.sensitivity(MatchSensitivity.DIACRITICS_INSENSITIVE);
            if (annotation.hasSensitivity(MatchSensitivity.SENSITIVE))
                return annotation.sensitivity(MatchSensitivity.SENSITIVE);
            return annotation.sensitivity(MatchSensitivity.INSENSITIVE);
        case CASE_INSENSITIVE:
        default:
            // search case-insensitive if available
            if (annotation.hasSensitivity(MatchSensitivity.CASE_INSENSITIVE))
                return annotation.sensitivity(MatchSensitivity.CASE_INSENSITIVE);
            if (annotation.hasSensitivity(MatchSensitivity.SENSITIVE))
                return annotation.sensitivity(MatchSensitivity.SENSITIVE);
            return annotation.sensitivity(MatchSensitivity.INSENSITIVE);
        }
    }

    public TextPatternContext withField(String field) {
        if (this.field.equals(field))
            return this;
        return new TextPatternContext(field, version, annotationName, requestedSensitivity,
                defaultRelationClass, captures, inConstraint, index);
    }

    public TextPatternContext withAnnotationAndSensitivity(Annotation annotation, MatchSensitivity matchSensitivity) {
        if (annotation == null)
            annotation = sensitivity.annotation();
        if (matchSensitivity == null)
            matchSensitivity = requestedSensitivity;
        if (annotation == sensitivity.annotation() && matchSensitivity == requestedSensitivity)
            return this;
        return new TextPatternContext(field, version, annotation.name(), matchSensitivity,
                defaultRelationClass, captures, inConstraint, index);
    }

    public TextPatternContext withAnnotationAndSensitivity(String annotationName, MatchSensitivity matchSensitivity)
            throws InvalidQuery {
        Annotation annotation = annotationName == null ? null : field().annotation(annotationName);
        if (annotationName != null && annotation == null)
            throw new FeatureNotPresentInCorpus("Annotation doesn't exist: " + annotationName + " on field " + field);
        return withAnnotationAndSensitivity(annotation, matchSensitivity);
    }

    public TextPatternContext withRelationAnnotation() {
        if (!field().hasRelationAnnotation())
            throw new UnsupportedOperationException("Field " + field().name() + " has no relation annotation!");
        String name = AnnotatedFieldNameUtil.RELATIONS_ANNOT_NAME;
        if (field().annotation(name) == null)
            throw new UnsupportedOperationException("Field " + field().name() + " has no relation annotation named " + name + "!");
        return withAnnotationAndSensitivity(field().annotation(name), null);
    }

    /**
     * Get a copy of this context for a different document version (parallel corpora).
     *
     * @param version document version to search
     * @return new context object
     */
    public TextPatternContext withDocVersion(String version) {
        if (version == null && this.version == null || Objects.equals(version, this.version))
            return this;
        return new TextPatternContext(field, version, annotationName, requestedSensitivity,
                defaultRelationClass, captures, inConstraint, index);
    }

    /**
     * Get a copy of this context for a different default relation class.
     *
     * @param relClass default relation class to use if not overridden
     * @return new context object
     */
    public TextPatternContext withDefaultRelationClass(String relClass) {
        if (relClass == null && defaultRelationClass == null || Objects.equals(relClass, defaultRelationClass))
            return this;
        return new TextPatternContext(field, version, annotationName, requestedSensitivity,
                relClass, captures, inConstraint, index);
    }

    /** Get new context with inConstraint set to true */
    public TextPatternContext withInConstraint() {
        if (inConstraint)
            return this;
        return new TextPatternContext(field, version, annotationName,
                requestedSensitivity, defaultRelationClass, captures, true, index);
    }

    public BlackLabIndexForQuery indexForQuery() {
        return index.forQuery();
    }

    public MatchSensitivity getSensitivity() {
        return sensitivity.sensitivity();
    }

    /**
     * Returns the correct current Lucene field name to use, based on the annotated
     * field name, annotation name and list of alternatives.
     *
     * @return null if field, annotation or alternative not found; valid Lucene field
     *         name otherwise
     */
    public String luceneField() {
        return sensitivity.luceneField();
    }

    public AnnotationSensitivity annotationSensitivity() {
        return sensitivity;
    }

    /**
     * Return the annotated field we're searching.
     *
     * @return annotated field
     */
    public AnnotatedField field() {
        return sensitivity.annotation().field();
    }

    /**
     * Return the annotated field we're searching.
     *
     * @return annotated field
     */
    public String fieldName() {
        return field;
    }

    /** Are we currently in a constraint (e.g. after :: )?
     *
     * TextPatterns before the :: will evaluate to BLSpanQueries,
     * those after to MatchFilters. The first are used to find matches,
     * the second to filter them.
     */
    public boolean isInConstraint() {
        return inConstraint;
    }

    /**
     * Get the default relation class.
     *
     * @return default relation class
     */
    public String defaultRelationClass() {
        return defaultRelationClass;
    }

    public String ensureUniqueCapture(String captureBaseName) {
        String capture = captureBaseName;
        int i = 2;
        while (captures.contains(capture)) {
            capture = captureBaseName + i;
            i++;
        }
        captures.add(capture);
        return capture;
    }

    public BLSpanQuery.Context queryContext() {
        return new BLSpanQuery.Context(indexForQuery(), fieldName());
    }
}
