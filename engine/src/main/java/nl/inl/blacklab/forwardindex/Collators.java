package nl.inl.blacklab.forwardindex;

import java.text.Collator;
import java.text.RuleBasedCollator;

import nl.inl.blacklab.exceptions.BlackLabException;
import nl.inl.blacklab.search.BlackLab;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;

/**
 * Collators to use for term equality testing for different sensitivity
 * settings.
 */
public class Collators {

    private final Collator sensitive;

    private final Collator insensitive;

    public Collators(Collator base) {
        super();
        sensitive = (Collator) base.clone();
        sensitive.setStrength(Collator.TERTIARY); // NOTE: TERTIARY considers differently-normalized characters to be,
        // identical which can cause problems if the input data is not consistently normalized the same way.
        insensitive = desensitize(base);
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
            rules = rules.replace("<'_'", "<' '<'-'<'_'"); // sort dash and space before underscore
            try {
                coll = new RuleBasedCollator(rules);
            } catch (Exception e) {
                throw BlackLabException.wrapRuntime(e);
            }
        } else {
            coll = (Collator)coll.clone();
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
