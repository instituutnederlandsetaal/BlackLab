package nl.inl.blacklab.search.indexmetadata;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

import nl.inl.util.LimitUtil;

/**
 * Relations stats for a corpus, including classes, types and attributes.
 */
public class RelationsStats {

    /**
     * Return default class for this index.
     * <p>
     * This is the first class alphabetically (except special class "__tag"),
     * or the default value "rel". For parallel corpora, the versions are ignored,
     * so "al__nl" will be returned as "al". This makes sense, because you should specify
     * the target version in the operator, e.g. <pre>==&gt;nl</pre>.
     *
     * @return default class
     */
    public String getDefaultClass() {
        return classes.keySet().stream()
                .filter(cls -> !cls.equals(RelationUtil.CLASS_INLINE_TAG))
                .map(AnnotatedFieldNameUtil::baseFromParallelFieldName)
                .findFirst()
                .orElse(RelationUtil.CLASS_DEFAULT);
    }

    public boolean isEmpty() {
        return classes.values().stream()
                .flatMap(cl -> cl.relationTypes.values().stream())
                .allMatch(t -> t.count == 0);
    }

    /**
     * Information about a relation type (under a class) and the attributes that occur with it.
     */
    public class TypeStats implements LimitUtil.Limitable<TypeStats> {

        /**
         * How often this relation type occurs
         */
        private long count;

        /**
         * What attributes occur and with what values
         */
        private Map<String, TruncatableFreqList> attributesAndValues = new TreeMap<>();

        void add(String term, long freq) {
            count += freq;

            relationsStrategy.attributesInTerm(term).forEach(e -> {
                // Add the attribute
                TruncatableFreqList attrValues = attributesAndValues.computeIfAbsent(e.getKey(),
                        k -> new TruncatableFreqList(limitValues));
                attrValues.add(e.getValue(), freq);
            });
        }

        public long getCount() {
            return count;
        }

        public Map<String, TruncatableFreqList> getAttributes() {
            return Collections.unmodifiableMap(attributesAndValues);
        }

        @Override
        public TypeStats withLimit(long limitValues) {
            if (limitValues == RelationsStats.this.limitValues)
                return this;
            if  (limitValues > RelationsStats.this.limitValues) {
                // Are all our lists complete? Then this is okay.
                if (attributesAndValues.values().stream().noneMatch(TruncatableFreqList::isTruncated))
                    return this;
                throw new IllegalArgumentException("Cannot increase limitValues from " + RelationsStats.this.limitValues + " to " + limitValues);
            }

            // Re-limit this type
            TypeStats result = new TypeStats();
            result.count = count;
            result.attributesAndValues = LimitUtil.limit(attributesAndValues, limitValues);
            return result;
        }
    }

    /**
     * Statistics about a relation class and its types of relations and their attributes.
     */
    public class ClassStats implements LimitUtil.Limitable<ClassStats> {
        /**
         * What relation types occur and with what attributes
         */
        private Map<String, TypeStats> relationTypes = new TreeMap<>();

        void add(String term, long freq) {
            String fullRelationType = relationsStrategy.fullTypeFromIndexedTerm(term);
            String relationType = RelationUtil.typeFromFullType(fullRelationType);
            TypeStats typeStats = relationTypes.computeIfAbsent(relationType, k -> new TypeStats());
            typeStats.add(term, freq);
        }

        public Map<String, TypeStats> getRelationTypes() {
            return Collections.unmodifiableMap(relationTypes);
        }

        @Override
        public ClassStats withLimit(long limitValues) {
            ClassStats result = new ClassStats();
            result.relationTypes = LimitUtil.limit(relationTypes, limitValues);
            return result;
        }
    }

    /** How relations were indexed */
    private final RelationsStrategy relationsStrategy;

    private final long limitValues;

    /**
     * What relation classes occur and with what types and attributes
     */
    private Map<String, ClassStats> classes = new TreeMap<>();

    RelationsStats(RelationsStrategy relationsStrategy, long limitValues) {
        this.relationsStrategy = relationsStrategy;
        this.limitValues = limitValues;
    }

    public RelationsStats withLimit(long limitValues) {
        if (limitValues == this.limitValues)
            return this;
        if (limitValues > this.limitValues) // TODO: could be okay if no lists are truncated?
            throw new IllegalArgumentException("Cannot increase limitValues from " + this.limitValues + " to " + limitValues);

        RelationsStats result = new RelationsStats(relationsStrategy, limitValues);
        result.classes = LimitUtil.limit(classes, limitValues);
        return result;
    }

    /** What limitValues value was used while collecting relationsStats?
     *  (we do this to limit memory usage, but we can only reuse the data
     *   for limitValues <= this value)
     */
    public long getLimitValues() {
        return limitValues;
    }

    public Map<String, ClassStats> getClasses() {
        return Collections.unmodifiableMap(classes);
    }

    boolean addIndexedTerm(String term, long freq) {
        // Count this term?
        // (NOTE: empty term is added if no relations are found at a position)
        if (!term.isEmpty() && relationsStrategy.countTermForStats(term)) {
            String relationClass;
            // New integrated index with spans indexed as relations as well.
            relationClass = RelationUtil.classFromFullType(relationsStrategy.fullTypeFromIndexedTerm(term));
            if (relationClass.startsWith(RelationsStrategySeparateTerms.RELATION_INFO_TERM_PREFIX))
                relationClass = relationClass.substring(1);
            ClassStats relClassStats = classes.computeIfAbsent(relationClass, k -> new ClassStats());
            relClassStats.add(term, freq);
        }
        return true; // "yes, continue processing terms from field"
    }

    /** Older dev index, count separate attribute terms instead (these have been lowercased already).
     *
     * Provided for convenience so older dev index still work with 4.0 for now;
     * we should remove this soon after release, as older dev indexes are not officially supported.
     */
    boolean addIndexedTerm_OLD(String term, long freq) {
        // Count this term?
        // (NOTE: empty term is added if no relations are found at a position)
        if (!term.isEmpty()) {
            String relationClass;
            relationClass = RelationUtil.classFromFullType(relationsStrategy.fullTypeFromIndexedTerm(term));
            if (relationClass.startsWith(RelationsStrategySeparateTerms.RELATION_INFO_TERM_PREFIX))
                relationClass = relationClass.substring(1);
            ClassStats relClassStats = classes.computeIfAbsent(relationClass, k -> new ClassStats());
            relClassStats.add(term, freq);
        }
        return true; // "yes, continue processing terms from field"
    }
}
