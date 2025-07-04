package nl.inl.blacklab.search;

import java.util.Collection;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import nl.inl.blacklab.exceptions.BlackLabException;
import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.queryParser.corpusql.CorpusQueryLanguageParser;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldNameUtil;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.optimize.ClauseCombinerNfa;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.textpattern.TextPattern;
import nl.inl.blacklab.testutil.TestIndex;

@RunWith(Parameterized.class)
public class TestQueryRewrite {

    @Parameterized.Parameters(name = "index type {0}")
    public static Collection<TestIndex> typeToUse() {
        return TestIndex.typesForTests();
    }

    @Parameterized.Parameter
    public TestIndex testIndex;

    private BlackLabIndex index;

    /** Name of the relations annotation */
    private String relName;

    @Before
    public void setUp() {
        index = testIndex.index();
        relName = AnnotatedFieldNameUtil.relationAnnotationName(index.getType());
        ClauseCombinerNfa.setForwardIndexMatchingEnabled(false);
    }

    @After
    public void tearDown() {
        ClauseCombinerNfa.setForwardIndexMatchingEnabled(true);
    }

    static TextPattern getPatternFromCql(String cqlQuery) {
        try {
            cqlQuery = cqlQuery.replaceAll("'", "\""); // makes queries more readable in tests
            return CorpusQueryLanguageParser.parse(cqlQuery);
        } catch (InvalidQuery e) {
            throw BlackLabException.wrapRuntime(e);
        }
    }

    void assertNoRewrite(String cql, String result) {
        assertRewrite(cql, result, result);
    }

    void assertRewrite(String cql, String before, String after) {
        try {
            BLSpanQuery q = getPatternFromCql(cql).toQuery(QueryInfo.create(index));
            QueryExplanation explanation = index.explain(q);
            if (before != null) {
                BLSpanQuery original = explanation.originalQuery();
                Assert.assertEquals(before, original.toString());
            }
            BLSpanQuery rewritten = explanation.rewrittenQuery();
            Assert.assertEquals(after, rewritten.toString());
        } catch (InvalidQuery e) {
            throw BlackLabException.wrapRuntime(e);
        }
    }

    void assertRewriteResult(String cql, String after) {
        assertRewrite(cql, null, after);
    }

    @Test
    public void testRewrite() {
        assertRewrite("[!(word != 'Water')]",
                "NOT(NOT(TERM(contents%word@i:water)))",
                "TERM(contents%word@i:water)");
    }

    @Test
    public void testRewriteInsensitive() {
        assertNoRewrite("[word = '(?i)Fox']", "TERM(contents%word@i:fox)");
    }

    @Test
    public void testRewriteInsensitive2() {
        assertRewrite("[word = '(?i)b.*']",
                "contents%word@i:/b.*/",
                "OR(TERM(contents%word@i:be), TERM(contents%word@i:brown))");
    }

    @Test
    public void testRewriteInsensitive3() {
        assertRewrite("[word = '(?i).*s']",
                "contents%word@i:/.*s/",
                "OR(TERM(contents%word@i:is), TERM(contents%word@i:jumps))");
    }

    @Test
    public void testRewriteInsensitive4() {
        assertRewrite("[word = '(?i)(th|b)e']",
                "contents%word@i:/(th|b)e/",
                "OR(TERM(contents%word@i:be), TERM(contents%word@i:the))");
    }

    @Test
    public void testRewriteSensitive() {
        assertNoRewrite("[word = '(?-i)Bla']", "TERM(contents%word@s:Bla)");

        assertNoRewrite("[word = '(?c)Bla']", "TERM(contents%word@s:Bla)");
    }

    @Test
    public void testRewriteNestedAnd() {
        assertRewrite("[word = 'a' & lemma = 'b' & pos != 'c']",
                "AND(TERM(contents%word@i:a), TERM(contents%lemma@i:b), NOT(TERM(contents%pos@i:c)))",
                "POSFILTER(AND(TERM(contents%word@i:a), TERM(contents%lemma@i:b)), TERM(contents%pos@i:c), notmatches)");
    }

    @Test
    public void testRewriteNestedOr() {
        assertRewrite("[word = 'a' | word = 'b' | word = 'c']",
                "OR(TERM(contents%word@i:a), TERM(contents%word@i:b), TERM(contents%word@i:c))",
                "OR(TERM(contents%word@i:a), TERM(contents%word@i:b), TERM(contents%word@i:c))");
    }

    @Test
    public void testRewriteNegativeAnd() {
        assertRewrite("[word != 'a' & word != 'b']",
                "AND(NOT(TERM(contents%word@i:a)), NOT(TERM(contents%word@i:b)))",
                "NOT(OR(TERM(contents%word@i:a), TERM(contents%word@i:b)))");
    }

    @Test
    public void testRewriteNegativeOr() {
        assertRewrite("[word != 'a' | lemma != 'b']",
                "OR(NOT(TERM(contents%word@i:a)), NOT(TERM(contents%lemma@i:b)))",
                "NOT(AND(TERM(contents%word@i:a), TERM(contents%lemma@i:b)))");
    }

    @Test
    public void testRewriteAndNot() {
        assertRewrite("[word = 'a' & lemma != 'b']",
                "AND(TERM(contents%word@i:a), NOT(TERM(contents%lemma@i:b)))",
                "POSFILTER(TERM(contents%word@i:a), TERM(contents%lemma@i:b), notmatches)");
    }

    @Test
    public void testRewriteNotAndNot() {
        assertRewrite("[ !(word = 'a' & lemma != 'b') ]",
                "NOT(AND(TERM(contents%word@i:a), NOT(TERM(contents%lemma@i:b))))",
                "NOT(POSFILTER(TERM(contents%word@i:a), TERM(contents%lemma@i:b), notmatches))");
    }

    @Test
    public void testRewriteRepetitionWord() {
        assertRewrite("'the' 'the'",
                "SEQ(TERM(contents%word@i:the), TERM(contents%word@i:the))",
                "REP(TERM(contents%word@i:the), 2, 2)");
        assertRewrite("'the.*' 'the.*'",
                "SEQ(contents%word@i:/the.*/, contents%word@i:/the.*/)",
                "REP(TERM(contents%word@i:the), 2, 2)");
    }

    @Test
    public void testRewriteRepetitionLemma() {
        assertRewrite("[lemma='a'] [lemma='a']",
                "SEQ(TERM(contents%lemma@i:a), TERM(contents%lemma@i:a))",
                "REP(TERM(contents%lemma@i:a), 2, 2)");
        assertNoRewrite("[lemma='a'] [lemma='b']",
                "SEQ(TERM(contents%lemma@i:a), TERM(contents%lemma@i:b))");
        assertNoRewrite("[lemma='a'] [word='a']",
                "SEQ(TERM(contents%lemma@i:a), TERM(contents%word@i:a))");
    }

    String repTags(String tagName, int attrValue) {
        if (index.getType() == BlackLabIndex.IndexType.INTEGRATED)
            return "TAGS(" + tagName + ", {test=" + attrValue + "})";
        return "POSFILTER(TAGS(" + tagName + "), TERM(contents%" + relName + "@s:@test__" + attrValue + "), starts_at)";
    }

    @Test
    public void testRewriteRepetitionAndOr() {
        assertRewriteResult("('a'|'b') ('a'|'b')",
                "REP(OR(TERM(contents%word@i:a), TERM(contents%word@i:b)), 2, 2)");
        assertRewriteResult("('a'|'b') ('a'|'c')",
                "SEQ(OR(TERM(contents%word@i:a), TERM(contents%word@i:b)), OR(TERM(contents%word@i:a), TERM(contents%word@i:c)))");

        assertRewriteResult("('a'&'b') ('a'&'b')",
                "REP(AND(TERM(contents%word@i:a), TERM(contents%word@i:b)), 2, 2)");
        assertRewriteResult("('a'&'b') ('a'&'c')",
                "SEQ(AND(TERM(contents%word@i:a), TERM(contents%word@i:b)), AND(TERM(contents%word@i:a), TERM(contents%word@i:c)))");

        assertRewriteResult("('a'& [word != 'b']) ('a'& [word != 'b'])",
                "REP(POSFILTER(TERM(contents%word@i:a), TERM(contents%word@i:b), notmatches), 2, 2)");
        assertRewriteResult("('a'& [word != 'b']) ('a'& [word != 'c'])",
                "POSFILTER(POSFILTER(REP(TERM(contents%word@i:a), 2, 2), TERM(contents%word@i:c), notmatches, 1, 0), TERM(contents%word@i:b), notmatches, 0, -1)");
    }

    @Test
    public void testRewriteAny() {
        assertRewriteResult("[]{0,1}", "ANYTOKEN(0, 1)");
        assertRewriteResult("[]{2,3}", "ANYTOKEN(2, 3)");
        assertRewriteResult("[]{2,}", "ANYTOKEN(2, INF)");
    }

    @Test
    public void testRewriteRepetitionAny() {
        assertRewriteResult("'a' []{2,3}", "EXPAND(TERM(contents%word@i:a), R, 2, 3)");
        assertRewriteResult("'a' ([]){2,3}", "EXPAND(TERM(contents%word@i:a), R, 2, 3)");
        assertRewriteResult("'a' ([]{2}){3}", "EXPAND(TERM(contents%word@i:a), R, 6, 6)");
        assertRewriteResult("'a' []{1,2} []{3,4}", "EXPAND(TERM(contents%word@i:a), R, 4, 6)");
    }

    @Test
    public void testRewriteSequenceExpand() {
        assertRewriteResult("'a' 'b' 'c' []{1,2}",
                "EXPAND(SEQ(TERM(contents%word@i:a), TERM(contents%word@i:b), TERM(contents%word@i:c)), R, 1, 2)");
    }

    @Test
    public void testRewriteProblematicNegativeClauses() {
        assertRewriteResult("'b' [word != 'a']",
                "POSFILTER(EXPAND(TERM(contents%word@i:b), R, 1, 1), TERM(contents%word@i:a), notcontaining, 1, 0)");
        assertRewriteResult("'b' [word != 'a']{2}",
                "POSFILTER(EXPAND(TERM(contents%word@i:b), R, 2, 2), TERM(contents%word@i:a), notcontaining, 1, 0)");
        assertRewriteResult("'b' 'c' [word != 'a']{2}",
                "POSFILTER(EXPAND(SEQ(TERM(contents%word@i:b), TERM(contents%word@i:c)), R, 2, 2), TERM(contents%word@i:a), notcontaining, 2, 0)");
        assertRewriteResult("[word != 'a']{2} 'b' 'c'",
                "POSFILTER(EXPAND(SEQ(TERM(contents%word@i:b), TERM(contents%word@i:c)), L, 2, 2), TERM(contents%word@i:a), notcontaining, 0, -2)");
        assertRewriteResult("'a' [word != 'b']{1,20} 'c'",
                "POSFILTER(SEQ(EXPAND(TERM(contents%word@i:a), R, 1, 20), TERM(contents%word@i:c)), TERM(contents%word@i:b), notcontaining, 1, -1)");
        assertRewriteResult("[word != 'a']? 'b' [word != 'c']?",
                "OR(POSFILTER(EXPAND(POSFILTER(EXPAND(TERM(contents%word@i:b), R, 1, 1), TERM(contents%word@i:c), notcontaining, 1, 0), L, 1, 1), TERM(contents%word@i:a), notcontaining, 0, -2), POSFILTER(EXPAND(TERM(contents%word@i:b), R, 1, 1), TERM(contents%word@i:c), notcontaining, 1, 0), POSFILTER(EXPAND(TERM(contents%word@i:b), L, 1, 1), TERM(contents%word@i:a), notcontaining, 0, -1), TERM(contents%word@i:b))");
        assertRewriteResult("[word != 'a'] [pos='V.*']?",
                "OR(POSFILTER(EXPAND(TERM(contents%pos@i:vrb), L, 1, 1), TERM(contents%word@i:a), notcontaining, 0, -1), NOT(TERM(contents%word@i:a)))");
        assertRewriteResult("[pos='V.*']? [word != 'a']",
                "OR(POSFILTER(EXPAND(TERM(contents%pos@i:vrb), R, 1, 1), TERM(contents%word@i:a), notcontaining, 1, 0), NOT(TERM(contents%word@i:a)))");
    }

    @Test
    public void testRewriteRepetition() {
        assertRewriteResult("('a'*)* 'b'",
                "OR(SEQ(REP(TERM(contents%word@i:a), 1, INF), TERM(contents%word@i:b)), TERM(contents%word@i:b))");
        assertRewriteResult("('a'+)* 'b'",
                "OR(SEQ(REP(TERM(contents%word@i:a), 1, INF), TERM(contents%word@i:b)), TERM(contents%word@i:b))");
        assertRewriteResult("('a'*)+ 'b'",
                "OR(SEQ(REP(TERM(contents%word@i:a), 1, INF), TERM(contents%word@i:b)), TERM(contents%word@i:b))");
        assertRewriteResult("('a'+)+", "REP(TERM(contents%word@i:a), 1, INF)");
        assertRewriteResult("('a'?)? 'b'",
                "OR(SEQ(TERM(contents%word@i:a), TERM(contents%word@i:b)), TERM(contents%word@i:b))");
        assertRewriteResult("('a'{2,3}){1,1}", "REP(TERM(contents%word@i:a), 2, 3)");
        assertRewriteResult("('a'{1,1}){2,3}", "REP(TERM(contents%word@i:a), 2, 3)");
        assertRewriteResult("'a'{1,1}", "TERM(contents%word@i:a)");
        assertRewriteResult("'a'? 'b'?",
                "OR(SEQ(TERM(contents%word@i:a), TERM(contents%word@i:b)), TERM(contents%word@i:b), TERM(contents%word@i:a))");
        assertRewriteResult("'a' 'a'*", "REP(TERM(contents%word@i:a), 1, INF)");
        assertRewriteResult("'a'? 'a'? 'b'",
                "OR(SEQ(REP(TERM(contents%word@i:a), 1, 2), TERM(contents%word@i:b)), TERM(contents%word@i:b))");
        assertRewriteResult("'a'* 'a'", "REP(TERM(contents%word@i:a), 1, INF)");
        assertRewriteResult("'a'* 'a'* 'b'",
                "OR(SEQ(REP(TERM(contents%word@i:a), 1, INF), TERM(contents%word@i:b)), TERM(contents%word@i:b))");
        assertRewriteResult("'a' 'a'+", "REP(TERM(contents%word@i:a), 2, INF)");
        assertRewriteResult("'a'+ 'a'", "REP(TERM(contents%word@i:a), 2, INF)");
        assertRewriteResult("'a'+ 'a'+", "REP(TERM(contents%word@i:a), 2, INF)");
    }

    @Test
    public void testRewriteTags() {
        if (index.getType() == BlackLabIndex.IndexType.EXTERNAL_FILES)
            return; // rewrite differently, and will be obsolete soon(ish)
        assertRewriteResult("<s/> containing 'a' 'b'",
                "POSFILTER(TAGS(s, cap:s), SEQ(TERM(contents%word@i:a), TERM(contents%word@i:b)), containing)");
        assertRewriteResult("<s> []* 'a' 'b' []* </s>",
                "POSFILTER(TAGS(s, cap:s), SEQ(TERM(contents%word@i:a), TERM(contents%word@i:b)), containing)");
        assertRewriteResult("<s> 'a' 'b' []* </s>",
                "POSFILTER(TAGS(s, cap:s), SEQ(TERM(contents%word@i:a), TERM(contents%word@i:b)), containing_at_start)");
        assertRewriteResult("<s> []* 'a' 'b' </s>",
                "POSFILTER(TAGS(s, cap:s), SEQ(TERM(contents%word@i:a), TERM(contents%word@i:b)), containing_at_end)");
        assertRewriteResult("<s> 'a' 'b' </s>",
                "POSFILTER(TAGS(s, cap:s), SEQ(TERM(contents%word@i:a), TERM(contents%word@i:b)), matches)");
        assertRewriteResult("<s> ('a' 'b') 'c' </s>",
                "POSFILTER(TAGS(s, cap:s), SEQ(TERM(contents%word@i:a), TERM(contents%word@i:b), TERM(contents%word@i:c)), matches)");
    }

    @Test
    public void testRewriteNGramFilter() {
        assertRewriteResult("[]{2,4} containing 'a' 'b'",
                "FILTERNGRAMS(SEQ(TERM(contents%word@i:a), TERM(contents%word@i:b)), containing, 2, 4)");
        assertRewriteResult("[]{1,2} within 'a' 'b' 'c'",
                "FILTERNGRAMS(SEQ(TERM(contents%word@i:a), TERM(contents%word@i:b), TERM(contents%word@i:c)), within, 1, 2)");
    }

    @Test
    public void testRewriteRegexWithExclude() {
        assertRewriteResult("'qu[^a]ck'", "OR(TERM(contents%word@i:quick))");
    }

    @Test
    public void testGlobalConstraint() {
        assertRewriteResult("a:[] 'en' b:[] :: a.word = b.word",
                "CONSTRAINT(CAPTURE(CAPTURE(EXPAND(EXPAND(TERM(contents%word@i:en), R, 1, 1), L, 1, 1), b, 2, 0), a, 0, -2), a.word = b.word)");
    }

    @Test
    public void testRewritePropertyRegexMatchAll() {
        assertRewriteResult("[lemma='.*']", "ANYTOKEN(1, 1)");
        assertRewriteResult("[lemma='.*' & word='de']", "TERM(contents%word@i:de)");
        assertRewriteResult("[lemma='.*' & word='.*']", "ANYTOKEN(1, 1)");
    }

}
