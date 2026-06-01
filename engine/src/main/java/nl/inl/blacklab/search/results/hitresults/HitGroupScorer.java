package nl.inl.blacklab.search.results.hitresults;

import java.util.LinkedHashMap;
import java.util.Map;

import nl.inl.blacklab.plugins.HitGroupScorerType;
import nl.inl.blacklab.plugins.PluginManager;
import nl.inl.blacklab.resultproperty.PropertyValue;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;

/**
 * A function that calculates the score for a group of hits.
 */
public interface HitGroupScorer {

    /** Pass if no scoring needed */
    HitGroupScorer NONE = null;

    String KEY_ID = "id";

    String DEFAULT_TYPE_ID = "coll-dice";

    static HitGroupScorer fromConfig(AnnotatedField field, Map<String, Object> config) {
        String scorerId = config.get(KEY_ID).toString();
        HitGroupScorerType scorerType = PluginManager.type(HitGroupScorerType.class).get(scorerId);

        // There is only one type for now
        if (scorerType.getType() != HitGroupScorerType.Type.COLLOCATION)
            throw new IllegalArgumentException("Scorer '" + scorerId + "' is not a collocation scorer");
        Map<String, Object> parameters = new LinkedHashMap<>(config);
        parameters.remove(KEY_ID);
        return HitGroupCollocationScorer.get(field, scorerType, parameters);
    }

    /** Scorer type id, e.g. coll-dice */
    HitGroupScorerType getType();

    /**
     * Calculate the score for a group with this identity and size.
     *
     * @param identity the identity of the group, e.g. the collocate (e.g. "sea")
     * @param size     the size of the group, e.g. how many times "sea" occurs within 5 words of "ship"
     * @return the score for this group according to this score formula
     */
    double score(PropertyValue identity, long size);
}
