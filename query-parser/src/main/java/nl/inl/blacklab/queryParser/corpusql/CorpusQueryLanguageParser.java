package nl.inl.blacklab.queryParser.corpusql;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldNameUtil;
import nl.inl.blacklab.search.lucene.RelationInfo;
import nl.inl.blacklab.search.textpattern.MatchValue;
import nl.inl.blacklab.search.textpattern.RelationOperatorInfo;
import nl.inl.blacklab.search.textpattern.TextPattern;
import nl.inl.blacklab.search.textpattern.TextPatternRegex;
import nl.inl.blacklab.search.textpattern.TextPatternRelationMatch;
import nl.inl.blacklab.search.textpattern.RelationTarget;
import nl.inl.blacklab.search.textpattern.TextPatternTerm;
import nl.inl.util.StringUtil;

public class CorpusQueryLanguageParser {

    private static final boolean USE_TP_RELATION = true;

    /**
     * Parse a Contextual Query Language query.
     * 
     * @param query our query
     * @return the parsed query
     * @throws InvalidQuery on parse error
     */
    public static TextPattern parse(String query, String defaultAnnotation) throws InvalidQuery {
        CorpusQueryLanguageParser parser = new CorpusQueryLanguageParser();
        parser.setDefaultAnnotation(defaultAnnotation);
        return parser.parseQuery(query);
    }

    public static TextPattern parse(String query) throws InvalidQuery {
        return parse(query, AnnotatedFieldNameUtil.DEFAULT_MAIN_ANNOT_NAME);
    }

    /** Allow strings to be quoted using single quotes? */
    private boolean allowSingleQuotes = true;

    private String defaultAnnotation;

    public CorpusQueryLanguageParser() {
    }

    public TextPattern parseQuery(String query) throws InvalidQuery {
        try {
            GeneratedCorpusQueryLanguageParser parser = new GeneratedCorpusQueryLanguageParser(new StringReader(query));
            parser.wrapper = this;
            return parser.query();
        } catch (ParseException | TokenMgrError e) {
            throw new InvalidQuery("Error parsing query: " + e.getMessage(), e);
        }
    }

    int num(Token t) {
        return Integer.parseInt(t.toString());
    }

    String chopEnds(String input) {
        if (input.length() >= 2)
            return input.substring(1, input.length() - 1);
        throw new BlackLabRuntimeException("Cannot chop ends off string shorter than 2 chars");
    }

    String getStringBetweenQuotes(String input) throws SingleQuotesException {
        boolean isLiteral = input.charAt(0) == 'l';
        if (isLiteral)
            input = input.substring(1);

        String quoteUsed = input.substring(0, 1);
        input = chopEnds(input); // eliminate quotes
        if (!allowSingleQuotes && quoteUsed.equals("\'"))
            throw new SingleQuotesException();

        // Unescape ONLY the quotes found around this string
        // Leave other escaped characters as-is for Lucene's regex engine
        String quotedUnescaped = StringUtil.unescapeQuote(input, quoteUsed);
        if (isLiteral) {
            // We want to find this string as-is; create a regex that will match this
            return StringUtil.escapeLuceneRegexCharacters(quotedUnescaped);
        }
        return quotedUnescaped;
    }

    TextPatternTerm simplePattern(MatchValue str) {
        return str.textPattern();
    }

    /** Allow strings to be quoted using single quotes? [default: yes] 
     * @param b whether single quotes are allowed */
    public void setAllowSingleQuotes(boolean b) {
        allowSingleQuotes = b;
    }

    /** Allow strings to be quoted using single quotes? */
    boolean isAllowSingleQuotes() {
        return allowSingleQuotes;
    }

    /**
     * Set the default annotation.
     * @param annotation default annotation
     */
    public void setDefaultAnnotation(String annotation) {
        defaultAnnotation = annotation;
    }

    public String getDefaultAnnotation() {
        return defaultAnnotation;
    }

    TextPattern annotationClause(String annot, TextPatternTerm value) {
        // Main annotation has a name. Use that.
        if (annot == null || annot.length() == 0)
            annot = defaultAnnotation;
        return value.withAnnotationAndSensitivity(annot, null);
    }

    static class ChildRelationStruct {

        public final RelationOperatorInfo type;

        public final TextPattern target;

        public final String captureAs;

        public ChildRelationStruct(RelationOperatorInfo type, TextPattern target, String captureAs) {
            this.type = type;
            this.target = target;
            this.captureAs = captureAs;
        }
    }

    TextPattern relationQuery(TextPattern parent, List<ChildRelationStruct> childRels) {
        List<RelationTarget> children = new ArrayList<>();
        for (ChildRelationStruct childRel: childRels) {
            RelationTarget child = new RelationTarget(childRel.type, childRel.target,
                    RelationInfo.SpanMode.SOURCE, childRel.captureAs);
            children.add(child);
        }
        return new TextPatternRelationMatch(parent, children);
    }

    TextPattern rootRelationQuery(ChildRelationStruct childRel) {
        assert !childRel.type.isNegate() : "Cannot negate root query";
        return new TextPatternRelationMatch(null,
                List.of(new RelationTarget(childRel.type, childRel.target,
                RelationInfo.SpanMode.TARGET, childRel.captureAs)));
    }

}
