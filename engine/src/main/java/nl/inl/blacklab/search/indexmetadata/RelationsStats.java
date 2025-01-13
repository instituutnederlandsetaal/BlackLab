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
     *
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
                .map(cls -> AnnotatedFieldNameUtil.baseFromParallelFieldName(cls))
                .findFirst()
                .orElse(RelationUtil.CLASS_DEFAULT);
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

    /**
     * Is this an old external index that indexes tags differently?
     */
    private boolean oldStyleStarttag;

    /** How relations were indexed */
    private RelationsStrategy relationsStrategy;

    private long limitValues;

    /**
     * What relation classes occur and with what types and attributes
     */
    private Map<String, ClassStats> classes = new TreeMap<>();

    RelationsStats(RelationsStrategy relationsStrategy, long limitValues) {
        this.relationsStrategy = relationsStrategy;
        this.oldStyleStarttag = relationsStrategy instanceof RelationsStrategyNaiveSeparateTerms;
        this.limitValues = limitValues;
    }

    public RelationsStats withLimit(long limitValues) {
        if (limitValues == this.limitValues)
            return this;
        if (limitValues > this.limitValues) //@@@ could be okay if no lists are truncated
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
        if (relationsStrategy.isOptimizationTerm(term)) {
            // Don't count these; they are extra terms to speed up certain searches.
            return true;
        }
        if (term.isEmpty()) {
            // Empty terms are added if no relations are found at a position
            return true;
        }
        if (oldStyleStarttag && term.startsWith("@")) {
            // Attribute value in older index; cannot match to its tag, so cannot count
            return true;
        }
        String relationClass;
        if (oldStyleStarttag) {
            // Old external index. Convert term to new style so we can process it the same way.
            relationClass = RelationUtil.CLASS_INLINE_TAG;
            term = RelationsStrategyNaiveSeparateTerms.indexedTermNoAttributes(RelationUtil.fullType(relationClass, term));
        } else {
            // New integrated index with spans indexed as relations as well.
            relationClass = RelationUtil.classFromFullType(relationsStrategy.fullTypeFromIndexedTerm(term));
        }
        ClassStats relClassStats = classes.computeIfAbsent(relationClass, k -> new ClassStats());
        relClassStats.add(term, freq);
        return true;
    }
}
