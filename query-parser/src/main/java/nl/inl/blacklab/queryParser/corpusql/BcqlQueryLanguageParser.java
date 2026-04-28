package nl.inl.blacklab.queryParser.corpusql;

import java.util.ArrayList;
import java.util.List;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.plugins.param.PluginParams;
import nl.inl.blacklab.search.BLQueryParser;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.lucene.RelationInfo;
import nl.inl.blacklab.search.textpattern.CompleteQuery;
import nl.inl.blacklab.search.textpattern.RelationOperatorInfo;
import nl.inl.blacklab.search.textpattern.RelationTarget;
import nl.inl.blacklab.search.textpattern.TextPattern;
import nl.inl.blacklab.search.textpattern.TextPatternRelationMatch;
import nl.inl.util.StringUtil;

public class BcqlQueryLanguageParser implements BLQueryParser {

    /**
     * Parse a Contextual Query Language query.
     * 
     * @param query our query
     * @return the parsed query
     * @throws InvalidQuery on parse error
     */
    public static TextPattern parse(BlackLabIndex index, PluginParams config, String query) throws InvalidQuery {
        return parseQuery(query);
    }

    public BcqlQueryLanguageParser(BlackLabIndex index, PluginParams config) {
    }

    @Override
    public CompleteQuery parse(String query) throws InvalidQuery {
        TextPattern tp = parseQuery(query);
        return new CompleteQuery(tp, null);
    }

    public static TextPattern parseQuery(String query) throws InvalidQuery {
        try {

            // Create a CharStream from the query string
            CharStream input = CharStreams.fromString(query);

            // Create a lexer that feeds off of input CharStream
            BcqlLexer lexer = new BcqlLexer(input);

            // Create a buffer of tokens pulled from the lexer
            CommonTokenStream tokens = new CommonTokenStream(lexer);

            // Create a parser that feeds off the tokens buffer
            BcqlParser parser = new BcqlParser(tokens);
            List<String> syntaxErrors = new ArrayList<>();
            parser.addErrorListener(new BaseErrorListener() {
                @Override
                public void syntaxError(Recognizer<?, ?> recognizer,
                        Object offendingSymbol,
                        int line, int charPositionInLine,
                        String msg, RecognitionException e)
                {
                    syntaxErrors.add("Syntax error at position " + charPositionInLine + ": " + msg);
                }
            });

            // Begin parsing at the 'query' rule
            BcqlParser.QueryContext tree = parser.query();

            // Detect syntax errors
            if (!syntaxErrors.isEmpty()) {
                throw new InvalidQuery("Syntax error(s) in query: " + query + "\n" + String.join("\n", syntaxErrors));
            }

            BcqlAstVisitor visitor = new BcqlAstVisitor();

            return visitor.visit(tree);

        } catch (Exception e) {
            throw new InvalidQuery("Error parsing query: " + e.getMessage(), e);
        }
    }

    public static String chopEnds(String input) {
        if (input.length() >= 2)
            return input.substring(1, input.length() - 1);
        throw new IllegalArgumentException("Cannot chop ends off string shorter than 2 chars");
    }

    /** Get a regex from a quoted (possibly literal) string.
     *
     * If the first quote is preceded by 'l', it is a literal string:
     * escape any special regex characters.
     * Otherwise, just chop off the quotes and unescape any escaped
     * quotes in the string.
     */
    public static String getRegexFromQuotedString(String input) {
        boolean isLiteral = input.charAt(0) == 'l';
        if (isLiteral)
            input = input.substring(1);

        String quoteUsed = input.substring(0, 1);
        if (!quoteUsed.equals("'") && !quoteUsed.equals("\""))
            throw new IllegalArgumentException("String does not start with a quote: " + input);
        input = chopEnds(input); // eliminate quotes

        // Unescape ONLY the quotes found around this string
        // Leave other escaped characters as-is for Lucene's regex engine
        String quotedUnescaped = StringUtil.unescapeQuote(input, quoteUsed);
        if (isLiteral) {
            // We want to find this string as-is; create a regex that will match this
            return StringUtil.escapeLuceneRegexCharacters(quotedUnescaped);
        }
        return quotedUnescaped;
    }

    public record ChildRelationStruct(RelationOperatorInfo type, TextPattern target, String captureAs) {}

    public static TextPattern relationQuery(TextPattern parent, List<ChildRelationStruct> childRels) {
        List<RelationTarget> children = new ArrayList<>();
        for (ChildRelationStruct childRel: childRels) {
            RelationTarget child = new RelationTarget(childRel.type, childRel.target,
                    RelationInfo.SpanMode.SOURCE, childRel.captureAs);
            children.add(child);
        }
        if (childRels.isEmpty())
            return parent;
        return new TextPatternRelationMatch(parent, children);
    }

    public static TextPattern rootRelationQuery(ChildRelationStruct childRel) {
        assert !childRel.type.isNegate() : "Cannot negate root query";
        return new TextPatternRelationMatch(null,
                List.of(new RelationTarget(childRel.type, childRel.target,
                RelationInfo.SpanMode.TARGET, childRel.captureAs)));
    }

}
