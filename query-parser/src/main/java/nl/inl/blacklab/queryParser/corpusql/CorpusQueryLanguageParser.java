package nl.inl.blacklab.queryParser.corpusql;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.BLQueryParser;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.lucene.RelationInfo;
import nl.inl.blacklab.search.textpattern.CompleteQuery;
import nl.inl.blacklab.search.textpattern.MatchValue;
import nl.inl.blacklab.search.textpattern.RelationOperatorInfo;
import nl.inl.blacklab.search.textpattern.RelationTarget;
import nl.inl.blacklab.search.textpattern.TextPattern;
import nl.inl.blacklab.search.textpattern.TextPatternRelationMatch;
import nl.inl.blacklab.search.textpattern.TextPatternTerm;
import nl.inl.util.StringUtil;

public class CorpusQueryLanguageParser implements BLQueryParser {

    /**
     * Parse a Contextual Query Language query.
     * 
     * @param query our query
     * @return the parsed query
     * @throws InvalidQuery on parse error
     */
    public static TextPattern parse(BlackLabIndex index, Map<String, Object> config, String query) throws InvalidQuery {
        CorpusQueryLanguageParser parser = new CorpusQueryLanguageParser(index, config);
        return parser.parseQuery(query);
    }

    /** Allow strings to be quoted using single quotes? */
    private final boolean allowSingleQuotes;

    private final String defaultAnnotation;

    public CorpusQueryLanguageParser(BlackLabIndex index, Map<String, Object> config) {
        defaultAnnotation = cfgString(config, "defaultAnnotation",
                index == null ? "word" : index.mainAnnotatedField().mainAnnotation().name());
        if (index != null && index.mainAnnotatedField().annotation(defaultAnnotation) == null)
            throw new IllegalArgumentException("Default annotation '" + defaultAnnotation + "' not found in index");
        allowSingleQuotes = cfgBoolean(config, "allowSingleQuotes", true);
    }

    private boolean cfgBoolean(Map<String, Object> config, String name, boolean defVal) {
        Object allow = config.get(name);
        if (allow == null)
            return defVal;
        return Boolean.parseBoolean(allow.toString());
    }

    private static String cfgString(Map<String, Object> config, String name, String defVal) {
        String defAnnot = config.getOrDefault(name, "").toString();
        return defAnnot.isEmpty() ? defVal : defAnnot;
    }

    public CompleteQuery parse(String query) throws InvalidQuery {
        TextPattern tp = parseQuery(query);
        return new CompleteQuery(tp, null);
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
        throw new IllegalArgumentException("Cannot chop ends off string shorter than 2 chars");
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

    public String getDefaultAnnotation() {
        return defaultAnnotation;
    }

    TextPattern annotationClause(String annot, TextPatternTerm value) {
        // Main annotation has a name. Use that.
        if (annot == null || annot.isEmpty())
            annot = defaultAnnotation;
        return value.withAnnotationAndSensitivity(annot, null);
    }

    record ChildRelationStruct(RelationOperatorInfo type, TextPattern target, String captureAs) {}

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
