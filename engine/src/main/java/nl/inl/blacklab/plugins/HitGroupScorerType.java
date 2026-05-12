package nl.inl.blacklab.plugins;

import java.util.List;
import java.util.Map;

import nl.inl.blacklab.exceptions.PluginException;
import nl.inl.blacklab.resultproperty.PropertyValue;
import nl.inl.blacklab.resultproperty.PropertyValueContextWords;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.AnnotationSensitivity;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.results.hitresults.HitGroupScorer;
import nl.inl.util.LuceneUtil;

/** Can provide a scorer for a group of hits.
 * <p>
 * This is used to score groups of collocations, e.g. "which words
 * occur within 5 words of "ship"? There would be a group "sea",
 * and we want to calculate the score for that group, i.e. how important
 * of a collocation "sea" is to ship.
 * <p>
 * An instance of this plugin represents a formula for calculating such a score,
 * e.g. "dice" or "salience".
 */
public abstract class HitGroupScorerType extends Plugin {

    /** Types of hit group scorer */
    public enum Type {
        /** Collocation scorer */
        COLLOCATION,
    }

    /**
     * Check what type of scorer this provides.
     *
     * This indicates which method you should call to instantiate a scorer.
     *
     * There is currently only 1 type, but more may be added in the future, and
     * we don't want to break compatibility if we can prevent it.
     *
     * @return type of scorer
     */
    public abstract Type getType();

    /** Get a scorer for a group representing a collocate of a word or query.
     * <p>
     * Check getType() to make sure this HitGroupScorerProvider does collocation scoring.
     * This method will throw a {@link nl.inl.blacklab.exceptions.PluginException} if the type is not supported.
     * <p>
     * If you're finding collocations for "ship" and want to score the resulting groups of collocates,
     * call this method on a supporting scorer provider to get the scorer.
     *
     * @param collocateAnnotation annotation sensitivity used by the collocate. E.g. if we're looking for words
     *                            around the lemma "ship", and we're looking case-insensitively, this would be the
     *                            case-insensitive alternative of the word annotation. Used to find frequency from
     *                            group identity.
     * @param totalFrequency number of words in the corpus (for proximity collocations), or cardinality of the
     *                       relation (for relation-based collocations).
     * @param wordFrequency total frequency of the search word (or query) we're finding collocations *for* ("ship" in
     *                      the example)
     * @return collocate scorer
     */
    public HitGroupScorer getCollocationScorer(AnnotationSensitivity collocateAnnotation, long totalFrequency, long wordFrequency) {
        throw new PluginException("HitGroupScorerType " + getId() + " does not support collocation scoring");
    }

    public static abstract class HitGroupCollocationScorer implements HitGroupScorer {

        public static final String KEY_TERM = "term";
        public static final String KEY_ANNOTATION = "annotation";
        public static final String KEY_SENSITIVITY = "sensitivity";

        private final BlackLabIndex index;

        private final AnnotationSensitivity collocateAnnotation;

        public HitGroupCollocationScorer(AnnotationSensitivity collocateAnnotation) {
            this.index = collocateAnnotation.annotation().field().index();
            this.collocateAnnotation = collocateAnnotation;
        }

        /** Should getTermFrequency calculate accurate term frequency slowly?
         * If false, uses totalTermFrequency which doesn't take deleted documents into account.
         */
        public static final boolean ACCURATE_TERM_FREQ = false;

        /** Instantiate a collocation scorer from its configuration parameters */
        public static HitGroupScorer get(AnnotatedField field, HitGroupScorerType type,
                Map<String, Object> parameters) {
            // Total number of tokens in this field
            String annotation = parameters.getOrDefault(KEY_ANNOTATION, "").toString();
            if (annotation.isEmpty())
                throw new IllegalArgumentException("Collocation scorer needs annotation");
            MatchSensitivity sensitivity = MatchSensitivity.fromName(parameters.getOrDefault(KEY_SENSITIVITY, "i").toString());
            AnnotationSensitivity annotSensitivity = field.annotation(annotation).sensitivity(sensitivity);
            long totalFrequency = field.index().metadata().countPerField().get(field.name()).getTokens();
            String term = parameters.getOrDefault(KEY_TERM, "").toString();
            long termFrequency;
            // TODO: we don't take a document filter into account here!
            if (term.isEmpty()) {
                throw new IllegalArgumentException("Collocation scorer needs term");
                // TODO: use pattern, and find number of hits for given pattern?
//                String pattern = parameters.getOrDefault("patt", "").toString();
//                if (pattern.isEmpty())
//                    throw new IllegalArgumentException("Collocation scorer needs term");
//                CompleteQuery cq = new CompleteQuery(tp);
//                field.index().find(field, cq);
//                termFrequency = ;
            } else {
                termFrequency = LuceneUtil.getTermFrequency(annotSensitivity, term, ACCURATE_TERM_FREQ);
            }
            return type.getCollocationScorer(annotSensitivity, totalFrequency, termFrequency);
        }

        protected long getCollocateFrequency(PropertyValue identity) {
            if (identity instanceof PropertyValueContextWords pvcw) {
                List<String> terms = pvcw.terms();
                if (terms.size() == 1) {
                    // Determine the term's frequency
                    String string = pvcw.getSensitivity().desensitize(identity.toString());
                    return LuceneUtil.getTermFrequency(collocateAnnotation, string, ACCURATE_TERM_FREQ);
                }
                throw new UnsupportedOperationException("Only single-term collocates are supported for now");
            }
            throw new UnsupportedOperationException("Group identity is not context-based");
        }
    }
}
