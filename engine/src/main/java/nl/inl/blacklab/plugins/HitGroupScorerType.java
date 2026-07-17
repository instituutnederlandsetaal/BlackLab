package nl.inl.blacklab.plugins;

import org.apache.lucene.search.Query;

import nl.inl.blacklab.exceptions.PluginException;
import nl.inl.blacklab.search.indexmetadata.AnnotationSensitivity;
import nl.inl.blacklab.search.results.hitresults.HitGroupScorer;

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
     * @param filter document filter
     * @param totalFrequency number of words in the corpus (for proximity collocations), or cardinality of the
     *                       relation (for relation-based collocations).
     * @param wordFrequency total frequency of the search word (or query) we're finding collocations *for* ("ship" in
     *                      the example)
     * @return collocate scorer
     */
    public HitGroupScorer getCollocationScorer(AnnotationSensitivity collocateAnnotation, Query filter, long totalFrequency, long wordFrequency) {
        throw new PluginException("HitGroupScorerType " + localId() + " does not support collocation scoring");
    }

}
