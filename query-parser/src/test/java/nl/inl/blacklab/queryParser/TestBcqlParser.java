package nl.inl.blacklab.queryParser;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.junit.Test;

import nl.inl.blacklab.queryParser.corpusql.BcqlAstVisitor;
import nl.inl.blacklab.queryParser.corpusql.BcqlLexer;
import nl.inl.blacklab.queryParser.corpusql.BcqlParser;
import nl.inl.blacklab.search.textpattern.TextPattern;

public class TestBcqlParser {

    @Test
    public void testParser()  {
        // Example BCQL query string
        String query = "[word = \"example\"]";

        // Create a CharStream from the query string
        CharStream input = CharStreams.fromString(query);

        // Create a lexer that feeds off of input CharStream
        BcqlLexer lexer = new BcqlLexer(input);

        // Create a buffer of tokens pulled from the lexer
        CommonTokenStream tokens = new CommonTokenStream(lexer);

        // Create a parser that feeds off the tokens buffer
        BcqlParser parser = new BcqlParser(tokens);

        // Begin parsing at the 'query' rule
        BcqlParser.QueryContext tree = parser.query();

        // Print LISP-style tree
//        Assert.assertEquals("(query (settingsQuery (constrainedQuery (containingWithinQuery (relationQuery (booleanQuery (sequence (captureQuery (sequencePartNoCapture (position [ (constraint (simpleConstraint (constraintValue (simpleConstraintValue (captureLabel word))) (comparisonOperator =) (constraintValue (simpleConstraintValue (quotedString \"example\"))))) ]))))))))) <EOF>)",
//                tree.toStringTree(parser));

        BcqlAstVisitor visitor = new BcqlAstVisitor();
        TextPattern result = visitor.visit(tree);
        System.out.println(result);
    }

}
