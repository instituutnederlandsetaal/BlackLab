package nl.inl.blacklab.search;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Assert;
import org.junit.Test;

import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.textpattern.RegexStripped;

public class TestRegexStripped {
    public static final String STR_TERM = "tesꞱ123";

    public static final RegexStripped RS_DEF = new RegexStripped(STR_TERM, null);
    public static final RegexStripped RS_CI = new RegexStripped(STR_TERM, MatchSensitivity.CASE_INSENSITIVE);
    public static final RegexStripped RS_DI = new RegexStripped(STR_TERM, MatchSensitivity.DIACRITICS_INSENSITIVE);
    public static final RegexStripped RS_S = new RegexStripped(STR_TERM, MatchSensitivity.SENSITIVE);
    public static final RegexStripped RS_I = new RegexStripped(STR_TERM, MatchSensitivity.INSENSITIVE);
    public static final RegexStripped RS_ANY1 = new RegexStripped(".*");

    @Test
    public void testRegexStripped() {
        // Default sensitivity
        assertRewritesTo(RS_DEF, STR_TERM);

        // Prefixes
        assertRewritesTo(RS_I, "(?i)" + STR_TERM);
        assertRewritesTo(RS_S, "(?s)" + STR_TERM);
        assertRewritesTo(RS_DI, "(?c)" + STR_TERM);
        assertRewritesTo(RS_CI, "(?d)" + STR_TERM);

        // Any token
        assertTrue(RS_ANY1.isAnyToken());
        assertRewritesTo(RS_ANY1, "^.*$");

        // Anchors are implied and will be stripped
        assertRewritesTo(RS_DEF, "^" + STR_TERM + "$");
        assertRewritesTo(RS_DEF, "^" + STR_TERM);
        assertRewritesTo(RS_S, "(?s)^" + STR_TERM);
        assertRewritesTo(RS_S, "^(?s)" + STR_TERM);
        assertRewritesTo(RS_DEF, STR_TERM + "$");

        // Regexes that can't be simplified to a single term
        assertRewritesTo(RS_ANY1, ".*");
        assertFalse(RS_ANY1.isLettersAndDigits());
        assertFalse(new RegexStripped(STR_TERM + ".*").isLettersAndDigits());
        assertFalse(new RegexStripped(".*" + STR_TERM).isLettersAndDigits());
        assertFalse(new RegexStripped("t.st").isLettersAndDigits());
    }

    private static void assertRewritesTo(RegexStripped expected, String regex) {
        RegexStripped stripped = new RegexStripped(regex);
        Assert.assertEquals(expected, stripped);
    }
}
