package nl.inl.blacklab.forwardindex;

import com.ibm.icu.text.Collator;
import com.ibm.icu.text.RuleBasedCollator;

import nl.inl.blacklab.exceptions.BlackLabException;
import nl.inl.blacklab.search.BlackLab;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;

/**
 * Collators to use for term equality testing for different sensitivity
 * settings.
 */
public class Collators {

    private Collator sensitive;

    private Collator insensitive;

    public Collators(Collator base) {
        super();

        // A note on the different collator strengths:
        // PRIMARY    cares only about largest (primary) differences
        //            Collators with this strength are therefore LESS STRICT
        //            (e.g. 'APE' and 'ape' are considered identical)
        //
        // TERTIARY   also cares about smaller (tertiary) differences
        //            Collators with this strength are therefore STRICTER
        //            (e.g. 'APE' and 'ape' are considered different, and 'APE' comes before 'ape')

        sensitive = base.cloneAsThawed();
        // NOTE: TERTIARY considers differently-normalized characters to be
        // identical, which could cause problems if the input data is not consistently normalized the same way.
        // But we do normalize data while indexing, so this should not be an issue.
        sensitive.setStrength(Collator.TERTIARY);
        sensitive.freeze();
        insensitive = desensitize(base);
        insensitive.freeze();
    }

    public Collator get(MatchSensitivity sensitivity) {
        if (sensitivity.isCaseSensitive() != sensitivity.isDiacriticsSensitive()) {
            throw new UnsupportedOperationException(
                    "Different values for case- and diac-sensitive not supported here yet.");
        }
        return sensitivity.isCaseSensitive() ? sensitive : insensitive;
    }

    /**
     * Returns a case-/accent-insensitive version of the specified collator that
     * also doesn't ignore dash or space (as most collators do by default in PRIMARY
     * mode). This way, the comparison is identical to lowercasing and stripping
     * accents before calling String.equals(), which is what we use everywhere else
     * for case-/accent-insensitive comparison.
     *
     * @param coll collator to make insensitive
     * @return insensitive collator
     */
    private static Collator desensitize(Collator coll) {
        if (coll instanceof RuleBasedCollator) {
            // Case- and accent-insensitive collator that doesn't
            // ignore dash and space like the regular insensitive collator (V1) does.
            String rules = ((RuleBasedCollator)coll).getRules().replace(",'-'", ""); // don't ignore dash
            rules += "&' ' < '-' < '_'"; // sort dash and space before underscore
            try {
                coll = new RuleBasedCollator(rules);
            } catch (Exception e) {
                throw BlackLabException.wrapRuntime(e);
            }
        } else {
            coll = coll.cloneAsThawed();
        }
        coll.setStrength(Collator.PRIMARY); // ignore case and accent differences
        return coll;
    }

    private static Collators defaultInstance;

    public static synchronized Collators getDefault() {
        if (defaultInstance == null) {
            defaultInstance = new Collators(BlackLab.defaultCollator());
        }
        return defaultInstance;
    }
}
