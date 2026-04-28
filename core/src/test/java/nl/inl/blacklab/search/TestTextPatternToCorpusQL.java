package nl.inl.blacklab.search;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.plugins.param.PluginParams;
import nl.inl.blacklab.queryParser.corpusql.BcqlQueryLanguageParser;
import nl.inl.blacklab.search.textpattern.TextPattern;
import nl.inl.blacklab.search.textpattern.TextPatternSerializerBcql;

public class TestTextPatternToCorpusQL {

    @BeforeClass
    public static void beforeClass() {
        BlackLab.implicitInstance(); // init plugin system
    }

    private static void assertCanonicalized(String expected, String input) throws InvalidQuery {
        TextPattern p = BcqlQueryLanguageParser.parse(null, PluginParams.NONE, input);
        String cql = TextPatternSerializerBcql.serialize(p);
        Assert.assertEquals(expected, cql);
    }

    private static void assertRoundtrip(String cql) throws InvalidQuery {
        assertCanonicalized(cql, cql);
    }

    @Test
    public void testAndOrEscape() throws InvalidQuery {
        assertRoundtrip("(\"the\" & (\"c\\\\at\" | \"do\\\"g\")) \"turtle\"");

        assertRoundtrip("((\"the\" & \"c\\\\at\") | \"do\\\"g\") \"turtle\"");

        // NOTE: & and | have same precedence. Parsed as a left-leaning binary tree, then flattened per operator.
        assertCanonicalized("((\"a\" & \"b\") | \"c\") & \"d\" & \"e\"", "\"a\" & \"b\" | \"c\" & \"d\" & \"e\"");
    }

    @Test
    public void testBrackets() throws InvalidQuery {
        assertRoundtrip("[!(word = \"a\" & word = \"b\")]");
        assertRoundtrip("[word != \"the\" & (lemma = \"cat\" | pos = \"dog\")] \"turtle\"");
        assertRoundtrip("!\"cat\"");
        assertRoundtrip("[!(word = \"a\") & !(word = \"b\")]");
    }

    @Test
    public void testAndNot() throws InvalidQuery {
        assertRoundtrip("\"the\" & \"cat\" & !(\"dog\" & \"turtle\")");
    }

    @Test
    public void testAny() throws InvalidQuery {
        assertRoundtrip("\"the\" [] \"cat\"");
        assertRoundtrip("\"the\" []? \"cat\"");
        assertRoundtrip("\"the\" []* \"cat\"");
        assertRoundtrip("\"the\" []+ \"cat\"");
        assertRoundtrip("\"the\" []{2,3} \"cat\"");
        assertRoundtrip("\"the\" []{2,} \"cat\"");
    }

    @Test
    public void testRepetition() throws InvalidQuery {
        assertRoundtrip("(\"a\" | \"the\")? \"cat\"");
        assertRoundtrip("\"the\" \"cat\"");
        assertRoundtrip("\"the\"* \"cat\"");
        assertRoundtrip("\"the\"+ \"cat\"");
        assertRoundtrip("\"the\"{2,3} \"cat\"");
        assertRoundtrip("\"the\"{2,} \"cat\"");
    }

    @Test
    public void testCaptureTags() throws InvalidQuery {
        assertRoundtrip("A:\"the\" within B:<s/>");
        assertRoundtrip("A:(\"the\" | \"a\") containing <s/>");
        assertRoundtrip("A:(\"the\" | \"a\") within <s1/> | <s2/>");
        assertRoundtrip("<\"s.*\"/>");
    }

    @Test
    public void testOverlap() {
        assertRoundtrip("<s/> overlap <person/>");
    }

    @Test
    public void testEscape() throws InvalidQuery {
        assertCanonicalized("\"c\\\\at\"", "'c\\\\at'");
        assertCanonicalized("\"c'at\"", "'c\\'at'");
        assertCanonicalized("\"c\\at\"", "'c\\at'");
        assertCanonicalized("\"c\\\"at\"", "\"c\\\"at\"");
        assertCanonicalized("\"c\\?at\"", "'c\\?at'");
    }

    @Test
    public void testExtraParens() throws InvalidQuery {
        assertCanonicalized("(\"the\" & (\"c\\\\at\" | \"do\\\"g\")) \"turtle\"",
                "((\"the\" & (\"c\\\\at\" | \"do\\\"g\")) (\"turtle\"))");
    }

    @Test
    public void testQueryFunction() throws InvalidQuery {
        assertRoundtrip("rel(\"test\", _)");
        assertRoundtrip("rel(\"test\", []+)");
        assertRoundtrip("rspan(<s/>, \"full\")");
        assertRoundtrip("[word = len(\"cat\")]");
        assertRoundtrip("<s typeId=len(\"bleh\")/>");
    }

    @Test
    public void testRelationsWithoutRspan() throws InvalidQuery {
        assertRoundtrip("_ -test-> _");
        assertRoundtrip("[]+ -test-> []+");
        assertRoundtrip("^--> _");
        assertRoundtrip("\"cat\" =w=>nl \"kat\"");
    }

    @Test
    public void testRelations() throws InvalidQuery {
        assertRoundtrip("rspan(_ -test-> _, \"all\")");
        assertRoundtrip("rspan([]+ -test-> []+, \"all\")");
        assertRoundtrip("rspan(^--> _, \"all\")");
    }

    @Test
    public void testConstraints() throws InvalidQuery {
        assertRoundtrip("A:[] B:[] :: A.lemma = B.lemma | A.word = B.word");
        assertRoundtrip("A:[] B:[] :: start(A) <= end(B)");
        assertRoundtrip("[] (A:[] B:[] :: A.lemma = B.lemma & start(A) <= end(B)) []");
    }

    @Test
    public void testTypes() throws InvalidQuery {
        assertRoundtrip("\"the\" :: 1 = 1");
        assertCanonicalized("\"the\" :: ((1 = 1) = 2) = 2", "\"the\" :: 1 = 1 = 2 = 2");
        assertRoundtrip("\"the\" :: (1 = 1) = (2 = 2)");
        assertRoundtrip("\"the\" :: true != false");
        assertRoundtrip("\"the\" :: \"me\" != \"you\"");
        assertRoundtrip("\"the\" :: in[1,2] != in[3,4]");
    }

    @Test
    public void testIntRange() throws InvalidQuery {
        assertRoundtrip("[number = in[24,42]]");
        assertRoundtrip("<s number=in[123,4567]/>");
    }

    @Test
    public void testOperatorChaining() throws InvalidQuery {
        assertRoundtrip("!(!\"de\")");
        assertCanonicalized("[] :: (1 = 1) != false", "[] :: 1=1!=false");
        assertCanonicalized("([] :: true) :: true", "[] :: true :: true");
        assertCanonicalized("[] :: (1 = 1 & 2 = 2) | 3 = 3", "[] :: 1 = 1 & 2 = 2 | 3 = 3");
        assertCanonicalized("A:(B:[])", "A:B:[]");
    }
}
