package nl.inl.blacklab.search.textpattern;

import java.util.regex.Pattern;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.RegexpQuery;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.exceptions.RegexpTooLarge;
import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.lucene.BLSpanMultiTermQueryWrapper;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.util.StringUtil;

/**
 * A TextPattern matching a regular expression.
 */
public class TextPatternRegex extends TextPatternTerm {
    public TextPatternRegex(String value) {
        this(value, null, null);
    }

    public TextPatternRegex(String value, String annotation, MatchSensitivity sensitivity) {
        super(value, annotation, sensitivity);
    }

    @Override
    public BLSpanQuery translate(QueryExecutionContext context) throws InvalidQuery {
        // Rewrite pseudo-annotation to extension function call
        TextPattern rewrittenPseudoAnnot = rewriteIfPseudoAnnotation(context, false);
        if (rewrittenPseudoAnnot != null)
            return rewrittenPseudoAnnot.translate(context);

        // See if this is really a regex query or just a term query maskerading as one...
        TextPattern result = rewriteForQuery();
        if (result != this) {
            // Rewritten into a regular term query; translate that instead
            return result.translate(context);
        }
        // We're dealing with an actual regex query.
        context = context.withAnnotationAndSensitivity(annotation, sensitivity);
        String valueDesensitized = optInsensitive(context, value);

        // Lucene's regex engine requires double quotes to be escaped, unlike most others.
        // Escape double quotes
        valueDesensitized = StringUtil.escapeQuote(valueDesensitized, "\"");

        try {
            Term term = new Term(context.luceneField(), valueDesensitized);
            RegexpQuery regexpQuery = new RegexpQuery(term); //, RegExp.COMPLEMENT); causes issues with NFA matching!
            return new BLSpanMultiTermQueryWrapper<>(context.queryInfo(), regexpQuery);
        } catch (IllegalArgumentException e) {
            throw new InvalidQuery("Invalid query: " + e.getMessage() + " (while parsing regex)");
        } catch (StackOverflowError e) {
            // If we pass in a really large regular expression, like a huge
            // list of words combined with OR, stack overflow occurs inside
            // Lucene's automaton building code and we may end up here.
            throw new RegexpTooLarge();
        }
    }

    static final Pattern onlyLettersAndDigits = Pattern.compile("[\\w\\d]+", Pattern.UNICODE_CHARACTER_CLASS);

    /**
     * Rewrite to TextPatternTerm if value only contains letters and numbers.
     *
     * Also looks at (?i), (?-i), (?c) at the start of the pattern and converts that
     * into an appropriate TextPatternSensitive() wrapper.
     *
     * In all other cases, we keep TextPatternRegex because Lucene's regex, wildcard
     * and prefix queries all work in the same basic way (are converted into
     * AutomatonQuery's), so they are equally fast.
     *
     * @return the TextPattern
     */
    private TextPattern rewriteForQuery() {
        // Do we want to force an (in)sensitive search?
        boolean forceSensitive = false;
        boolean forceInsensitive = false;
        String newValue = value;
        if (newValue.startsWith("(?-i)")) {
            forceSensitive = true;
            newValue = newValue.substring(5);
        } else if (newValue.startsWith("(?c)")) {
            forceSensitive = true;
            newValue = newValue.substring(4);
        } else if (newValue.startsWith("(?i)")) {
            forceInsensitive = true;
            newValue = newValue.substring(4);
        }

        // Is it "any token"?
        if (value.equals(".*")) {
            return new TextPatternAnyToken(1, 1);
        }

        // If this contains no funny characters, only (Unicode) letters and digits,
        // surrounded by ^ and $, turn it into a TermQuery, which might be a little
        // faster than doing it via RegexpQuery (which has to build an Automaton).
        TextPatternTerm result = null;
        if (onlyLettersAndDigits.matcher(newValue).matches()) {
            // No regex characters, so we can turn it into a term query
            result = new TextPatternTerm(newValue, annotation, sensitivity);
        }
        if (result == null) {
            // Not a term query. Did we strip off a sensitivity flag above?
            if (!forceSensitive && !forceInsensitive) {
                // Nope. Nothing to rewrite.
                return this;
            }
            // Yes. Create new TP from remaining regex, and add TextPatternSensitive below.
            result = new TextPatternRegex(newValue, annotation, sensitivity);
        }

        if (forceSensitive) {
            // Pattern started with (?-i) or (?c) to force it to be sensitive
            result = result.withAnnotationAndSensitivity(null, MatchSensitivity.SENSITIVE);
        } else if (forceInsensitive) {
            // Pattern started with (?i) to force it to be insensitive
            result = result.withAnnotationAndSensitivity(null, MatchSensitivity.INSENSITIVE);
        }

        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof TextPatternRegex) {
            return super.equals(obj);
        }
        return false;
    }

    // appease PMD
    @Override
    public int hashCode() {
        return super.hashCode();
    }

    @Override
    public TextPatternRegex withAnnotationAndSensitivity(String annotation, MatchSensitivity sensitivity) {
        if (annotation == null)
            annotation = this.annotation;
        if (sensitivity == null)
            sensitivity = this.sensitivity;
        return new TextPatternRegex(value, annotation, sensitivity);
    }
}
