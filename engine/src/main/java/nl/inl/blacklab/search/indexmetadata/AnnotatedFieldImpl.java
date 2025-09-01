package nl.inl.blacklab.search.indexmetadata;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlTransient;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.util.LuceneUtil;

/** An annotated field */
@XmlAccessorType(XmlAccessType.FIELD)
@JsonPropertyOrder({ "custom", "mainAnnotation", "hasContentStore", "hasXmlTags", "annotations" })
public class AnnotatedFieldImpl extends FieldImpl implements AnnotatedField {

    /** Max. number of values per e.g. tag attribute to cache.
     *  Set fairly low because we don't want e.g. unique id attributes eating up a ton of memory.
     *  Higher values of limitValues still work fine, they just take longer because they're not cached.
     */
    public static final double MAX_LIMIT_VALUES_TO_CACHE = 10000;

    public final class AnnotationsImpl implements Annotations {
        @Override
        public Annotation main() {
            if (mainAnnotation == null && mainAnnotationName != null) {
                // Set during indexing, when we don't actually have annotation information
                // available (because the index is being built up, so we couldn't detect
                // it on startup).
                // Just retrieve it now.
                mainAnnotation = annots.get(mainAnnotationName);
                //mainAnnotationName = null;
            }
            return mainAnnotation;
        }

        @Override
        public Iterator<Annotation> iterator() {
            return stream().iterator();
        }

        @Override
        public Stream<Annotation> stream() {
            return annots.values().stream().map(a -> a);
        }

        @Override
        public Annotation get(String name) {
            return annots.get(name);
        }

        @Override
        public boolean exists(String name) {
            return annots.containsKey(name);
        }

        @Override
        public boolean isEmpty() {
            return annots.isEmpty();
        }

        @Override
        public int size() {
            return annots.size();
        }
    }

    protected static final Logger logger = LogManager.getLogger(AnnotatedFieldImpl.class);
    
    /** This field's annotations */
    private final Map<String, AnnotationImpl> annots = new LinkedHashMap<>();

    /** The field's main annotation */
    @XmlTransient
    private AnnotationImpl mainAnnotation = null;

    /**
     * The field's main annotation name (for storing the main annot name before we have
     * the annot. descriptions)
     */
    @JsonProperty("mainAnnotation")
    private String mainAnnotationName;

    /** Are there XML tag locations stored for this field? */
    @JsonProperty("hasXmlTags")
    private boolean xmlTags;

    /** These annotations should not get a forward index. */
    @XmlTransient
    private Set<String> noForwardIndexAnnotations = Collections.emptySet();

    @XmlTransient
    private final AnnotationsImpl annotations = new AnnotationsImpl();

    /** (IGNORE; for compatiblity with old pre-release metadata, to remove eventually) */
    @XmlTransient
    private Map<String, Map<String, Long>> relations;

    /** (IGNORE; for compatiblity with old pre-release metadata, to remove eventually) */
    @XmlTransient
    private boolean relationsInitialized;

    /** The available relation classes, types and their frequencies, plus attribute info. */
    @XmlTransient
    private RelationsStats cachedRelationsStats;

    // For JAXB deserialization
    @SuppressWarnings("unused")
    AnnotatedFieldImpl() {
    }

    public AnnotatedFieldImpl(BlackLabIndex index, String name) {
        super(index, name);
    }

    @Override
    public Annotations annotations() {
        return annotations;
    }

    @Override
    public boolean hasRelationAnnotation() {
        return xmlTags;
    }

    // (public because used in AnnotatedFieldWriter while indexing)
    public Set<String> getNoForwardIndexAnnotations() {
        return noForwardIndexAnnotations;
    }

    // Methods that mutate data
    // ------------------------------------------------------

    public synchronized AnnotationImpl getOrCreateAnnotation(String name) {
        AnnotationImpl pd = annots.get(name);
        if (pd == null) {
            ensureNotFrozen();
            pd = new AnnotationImpl(this, name);
            putAnnotation(pd);
        }
        return pd;
    }

    synchronized void putAnnotation(AnnotationImpl annotDesc) {
        ensureNotFrozen();
        annots.put(annotDesc.name(), annotDesc);
        if (annotDesc.isRelationAnnotation())
            xmlTags = true;
    }

    synchronized void setMainAnnotationName(String mainAnnotationName) {
        if (this.mainAnnotationName == null || !this.mainAnnotationName.equals(mainAnnotationName)) {
            ensureNotFrozen();
            this.mainAnnotationName = mainAnnotationName;
            if (annots.containsKey(mainAnnotationName))
                mainAnnotation = annots.get(mainAnnotationName);
        }
    }

    @Override
    public boolean freeze() {
        boolean b = super.freeze();
        if (b)
            this.annots.values().forEach(AnnotationImpl::freeze);
        return b;
    }
    
    @Override
    public String offsetsField() {
        AnnotationSensitivity offsetsSensitivity = annotations.main().offsetsSensitivity();
        return offsetsSensitivity == null ? null : offsetsSensitivity.luceneField();
    }

    @Override
    public void fixAfterDeserialization(BlackLabIndex index, String fieldName) {
        super.fixAfterDeserialization(index, fieldName);
        for (Map.Entry<String, AnnotationImpl> entry : annots.entrySet()) {
            entry.getValue().fixAfterDeserialization(index, this, entry.getKey());
        }

        // These are no longer used, but we need to keep them around for deserialization of some pre-release indexes
        this.relations = null;
        this.relationsInitialized = false;
    }

    /**
     * Get information about relations in this corpus.
     *
     * Includes classes and types of relations that occur, the frequency for each,
     * and any attributes and their values.
     *
     * @param index the index
     * @param limitValues truncate lists/maps of values to this length
     * @return information about relations in this corpus
     */
    @Override
    public RelationsStats getRelationsStats(BlackLabIndex index, long limitValues) {
        RelationsStats results;
        synchronized (this) {
            results = cachedRelationsStats;
        }
        if (results == null || results.getLimitValues() < limitValues) {
            // We either don't have cached relationsStats, or the limitValues value is too low.
            results = new RelationsStats(index.getRelationsStrategy(), limitValues);

            // Look up the correct field for the _relation annotation (depending on whether it
            // was indexed sensitively or insensitively)
            Annotation annotation = annotation(AnnotatedFieldNameUtil.RELATIONS_ANNOT_NAME);
            AnnotationSensitivity annotationSensitivity = annotation.hasSensitivity(MatchSensitivity.SENSITIVE) ?
                    annotation.sensitivity(MatchSensitivity.SENSITIVE) :
                    annotation.sensitivity(MatchSensitivity.INSENSITIVE);

            LuceneUtil.getFieldTerms(index.reader(), annotationSensitivity.luceneField(),
                    null, results::addIndexedTerm);
            if (results.isEmpty() && index.getRelationsStrategy() instanceof RelationsStrategySeparateTerms) {
                // older dev index without relation info terms, use older version of method
                LuceneUtil.getFieldTerms(index.reader(), annotationSensitivity.luceneField(),
                        null, results::addIndexedTerm_OLD);
            }
        }
        // Should we cache these results?
        synchronized (this) {
            if (results != cachedRelationsStats && limitValues < MAX_LIMIT_VALUES_TO_CACHE) {
                // Reasonable enough to cache.
                cachedRelationsStats = results;
            }
        }
        if (limitValues < results.getLimitValues()) {
            // We have cached relationsStats, but the limitValues value is too low.
            // We can reuse the data, but we need to limit the number of values.
            results = results.withLimit(limitValues);
        }
        return results;
    }

}
