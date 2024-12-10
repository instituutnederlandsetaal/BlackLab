package nl.inl.blacklab.search;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.QueryVisitor;
import org.apache.lucene.search.RegexpQuery;
import org.apache.lucene.search.TermQuery;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.forwardindex.Terms;
import nl.inl.blacklab.queryParser.corpusql.CorpusQueryLanguageParser;
import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.resultproperty.HitPropertyBeforeHit;
import nl.inl.blacklab.resultproperty.HitPropertyHitText;
import nl.inl.blacklab.resultproperty.HitPropertyMultiple;
import nl.inl.blacklab.resultproperty.PropertyValue;
import nl.inl.blacklab.resultproperty.PropertyValueContextWords;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.indexmetadata.RelationUtil;
import nl.inl.blacklab.search.lucene.BLSpanMultiTermQueryWrapper;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.BLSpanTermQuery;
import nl.inl.blacklab.search.lucene.MatchInfo;
import nl.inl.blacklab.search.lucene.RelationInfo;
import nl.inl.blacklab.search.lucene.RelationListInfo;
import nl.inl.blacklab.search.lucene.SpanQueryFiltered;
import nl.inl.blacklab.search.results.DocResult;
import nl.inl.blacklab.search.results.DocResults;
import nl.inl.blacklab.search.results.Hits;
import nl.inl.blacklab.search.textpattern.TextPattern;
import nl.inl.blacklab.search.textpattern.TextPatternFixedSpan;
import nl.inl.blacklab.search.textpattern.TextPatternTerm;
import nl.inl.blacklab.testutil.TestIndex;

@RunWith(Parameterized.class)
public class TestSearches {

    @Parameterized.Parameters(name = "index type {0}")
    public static Collection<TestIndex> typeToUse() {
        return TestIndex.typesForTests();
    }

    @Parameterized.Parameter
    public TestIndex testIndex;

    @Test
    public void testSimple() {
        List<String> expected = Arrays.asList(
                "[The] quick",
                "over [the] lazy",
                "May [the] Force",
                "is [the] question");
        Assert.assertEquals(expected, testIndex.findConc(" 'the' "));

        expected = Arrays.asList(
                "over [the] lazy",
                "May [the] Force",
                "is [the] question");
        Assert.assertEquals(expected, testIndex.findConc(" '(?-i)the' "));

        expected = Arrays.asList(
                "brown [fox] jumps",
                "lazy [dog]",
                "the [Force] be",
                "the [question]");
        Assert.assertEquals(expected, testIndex.findConc(" [pos='nou'] "));
    }

    @Test
    public void testSimpleDocFilter() {
        List<String> expected = List.of("May [the] Force");
        int docId = testIndex.getDocIdForDocNumber(2);
        Assert.assertEquals(expected, testIndex.findConc(" 'the' ", new SingleDocIdFilter(docId)));
    }

    @Test
    public void testSimpleTitleFilter() {
        List<String> expected = List.of("May [the] Force");
        // metadata is tokenized and lowercased by default
        Query filter = new TermQuery(new Term("title", "star"));
        Assert.assertEquals(expected, testIndex.findConc(" 'the' ", filter));
    }

    @Test
    public void testFilteredQuery() {
        List<String> expected = Arrays.asList("[The] quick", "over [the] lazy");
        BLSpanTermQuery patternQuery = new BLSpanTermQuery(null, new Term("contents%word@i", "the"));
        TermQuery filterQuery = new TermQuery(new Term("contents%word@i", "fox"));
        Assert.assertEquals(expected, testIndex.findConc(new SpanQueryFiltered(patternQuery, filterQuery)));
    }

    @Test
    public void testSequences() {
        List<String> expected = Arrays.asList(
                "quick [brown fox] jumps",
                "the [lazy dog]");
        Assert.assertEquals(expected, testIndex.findConc(" [pos='adj'] [pos='nou'] "));
        // Also test that forward index matching either the first or the second clause produces the same results
        Assert.assertEquals(expected, testIndex.findConc(" _FI1([pos='adj'], [pos='nou']) "));
        Assert.assertEquals(expected, testIndex.findConc(" _FI2([pos='adj'], [pos='nou']) "));
    }

    @Test
    public void testSequences2() {
        List<String> expected = List.of(
                "mier [aap mier] mier"
        );
        String query = "'aap' 'mier'";
        List<String> result = testIndex.findConc(query);
        Assert.assertEquals(1, result.size());
        Assert.assertEquals(expected, result);
    }

    @Test
    public void testMoreSequencesFiMatch() {
        int expected = 33;
        Assert.assertEquals(expected, testIndex.findConc(" [] [] ").size());
        // Also test that forward index matching either the first or the second clause produces the same results
        Assert.assertEquals(expected, testIndex.findConc(" _FI1([], []) ").size());
        Assert.assertEquals(expected, testIndex.findConc(" _FI2([], []) ").size());
    }

    @Test
    public void testMatchAll() {
        List<String> expected = Arrays.asList(
                "brown [fox jumps] over",
                "the [Force be] with");
        Assert.assertEquals(expected, testIndex.findConc(" [pos='nou'] [] "));

        expected = Arrays.asList(
                "quick [brown fox] jumps",
                "the [lazy dog]",
                "May [the Force] be",
                "is [the question]");
        Assert.assertEquals(expected, testIndex.findConc(" [] [pos='nou'] "));
    }

    @Test
    public void testOptional1() {
        List<String> expected = Arrays.asList(
                "be [with you]",
                "with [you]",
                "to [find That] is",
                "find [That] is");
        Assert.assertEquals(expected, testIndex.findConc(" []? [pos='pro'] "));
    }

    @Test
    public void testOptional2() {
        List<String> expected = Arrays.asList(
                "with [you]",
                "find [That] is",
                "find [That is] the");
        Assert.assertEquals(expected, testIndex.findConc(" [pos='pro'] []? "));

    }

    @Test
    public void testOptional3() {
        List<String> expected = Arrays.asList(
                "be [with] you",
                "be [with you]",
                "with [you]",
                "To [find] or",
                "to [find] That",
                "to [find That] is",
                "find [That] is");
        Assert.assertEquals(expected, testIndex.findConc(" 'with|find'? [pos='pro']? "));
    }

    @Test
    public void testRepetition() {
        List<String> expected = List.of(
                "The [quick brown] fox");
        Assert.assertEquals(expected, testIndex.findConc(" [pos='adj']{2} "));

        expected = Arrays.asList(
                "The [quick] brown",
                "The [quick brown] fox",
                "quick [brown] fox",
                "the [lazy] dog");
        Assert.assertEquals(expected, testIndex.findConc(" [pos='adj']{1,} "));
    }

    @Test
    public void testRepetitionNonConsecutive() {
        List<String> expected = List.of(
                "mier [aap mier mier mier noot noot aap aap] aap",
                "mier [aap mier mier mier noot noot aap aap aap] aap",
                "mier [aap mier mier mier noot noot aap aap aap aap]",
                "noot [aap aap aap aap]");
        Assert.assertEquals(expected, testIndex.findConc("('aap' [word != 'noot']{1, 3} 'noot'{0,2}){2,}"));
    }

    @Test
    public void testRepetitionNoResults() {
        List<String> expected = Collections.emptyList();
        Assert.assertEquals(expected, testIndex.findConc("[pos='PD.*']+ '(?i)getal'"));

    }

    @Test
    public void testStringRegexes() {
        List<String> expected = Arrays.asList(
                "quick [brown] fox",
                "Force [be] with");
        Assert.assertEquals(expected, testIndex.findConc(" 'b.*' "));

        expected = Arrays.asList(
                "brown [fox] jumps",
                "the [Force] be");
        Assert.assertEquals(expected, testIndex.findConc(" 'fo[xr].*' "));
    }

    @Test
    public void testUniq() {
        List<String> expected = List.of(
                "fox [jumps] over");
        Assert.assertEquals(expected, testIndex.findConc("[word = 'jumps' | lemma = 'jump']"));
    }

    @Test
    public void testOr() {
        List<String> expected = Arrays.asList(
                "fox [jumps] over",
                "jumps [over] the");
        Assert.assertEquals(expected, testIndex.findConc("[word = 'jumps' | lemma = 'over']"));
    }

    @Test
    public void testAnd() {
        List<String> expected = List.of(
                "The [quick] brown");
        Assert.assertEquals(expected, testIndex.findConc("[pos = 'adj' & lemma = '.*u.*']"));
    }

    @Test
    public void testAnd2() {
        List<String> expected = List.of(
                "fox [jumps] over");
        Assert.assertEquals(expected, testIndex.findConc("[word = 'jumps' & lemma = 'jump']"));
    }

    @Test
    public void testAndAnyToken() {
        List<String> expected = List.of("fox [jumps] over");

        // AND with any token repetition is optimized; check that this works properly
        Assert.assertEquals(expected, testIndex.findConc("[] & 'jumps'"));
        Assert.assertEquals(expected, testIndex.findConc("[]* & 'jumps'"));
        Assert.assertEquals(expected, testIndex.findConc("[]+ & 'jumps'"));
        Assert.assertEquals(expected, testIndex.findConc("[]+ & [] & 'jumps'"));
        Assert.assertEquals(expected, testIndex.findConc("[]{1,2} & 'jumps'"));

        // Contradictary clauses should not return any results
        Assert.assertEquals(Collections.emptyList(), testIndex.findConc("[]{2,2} & 'jumps'"));
        Assert.assertEquals(Collections.emptyList(), testIndex.findConc("[] & ('jumps' 'over')"));
    }

    @Test
    public void testTags() {
        List<String> expected = List.of(
                "[The quick brown fox] jumps", "over [the lazy dog]", "May [the Force] be");
        Assert.assertEquals(expected, testIndex.findConc("<entity/>"));

        expected = List.of(
                "quick [brown] fox");
        Assert.assertEquals(expected, testIndex.findConc(" 'b.*' within <entity/> "));

        expected = List.of(
                "[The quick brown fox] jumps");
        Assert.assertEquals(expected, testIndex.findConc(" <entity/> containing 'b.*' "));

        expected = List.of(
                "[The] quick");
        Assert.assertEquals(expected, testIndex.findConc(" <s> 'the' "));

        expected = List.of(
                "lazy [dog]");
        Assert.assertEquals(expected, testIndex.findConc(" 'dog' </s> "));
    }

    @Test
    public void testRegexTags() {
        List<String> expected = List.of(
                "[The quick brown fox] jumps", "over [the lazy dog]", "May [the Force] be");
        Assert.assertEquals(expected, testIndex.findConc("<'e.*'/>"));

        expected = List.of(
                "[The] quick");
        Assert.assertEquals(expected, testIndex.findConc(" <'s'> 'the' "));

        expected = List.of(
                "over [the lazy] dog", "[To find] or");
        Assert.assertEquals(expected, testIndex.findConc("<'.*'> [] 'lazy|find' "));
    }

    @Test
    public void testWithSpans() {
        if (testIndex.getIndexType() == BlackLabIndex.IndexType.INTEGRATED) {
            Hits hits = testIndex.find("with-spans('quick' 'brown')");
            Assert.assertEquals(1, hits.size());
            MatchInfo[] matchInfo = hits.get(0).matchInfo();
            Assert.assertEquals(1, matchInfo.length);
            Assert.assertEquals(MatchInfo.Type.LIST_OF_RELATIONS, matchInfo[0].getType());
            List<RelationInfo> rels = ((RelationListInfo) matchInfo[0]).getRelations();
            Assert.assertEquals(2, rels.size());
            Assert.assertEquals("entity", rels.get(0).getRelationType());
            Assert.assertEquals("s", rels.get(1).getRelationType());
        }
    }

    @Test
    public void testRelRspan() {
        if (testIndex.getIndexType() == BlackLabIndex.IndexType.INTEGRATED) {
            List<String> allRelations = List.of(
                    "[The quick brown fox] jumps", "over [the lazy dog]", "May [the Force] be");
            List<String> targets = List.of("fox [] jumps", "dog []", "Force [] be");
            List<String> sources = List.of("[] The", "over [] the", "May [] the");
            List<String> none = Collections.emptyList();
            String type = RelationUtil.fullType(RelationUtil.CLASS_INLINE_TAG, "entity");

            Assert.assertEquals(allRelations, testIndex.findConc("rel('" + type + "', _, 'full')"));
            Assert.assertEquals(allRelations, testIndex.findConc("rspan(rel('" + type + "'), 'full')"));
            Assert.assertEquals(allRelations, testIndex.findConc("rspan(rel('" + type + "', _, 'source'), 'full')"));

            Assert.assertEquals(targets, testIndex.findConc("rel('" + type + "', _, 'target')"));
            Assert.assertEquals(targets, testIndex.findConc("rspan(rel('" + type + "', _, 'full'), 'target')"));
            Assert.assertEquals(targets, testIndex.findConc("rspan(rel('" + type + "'), 'target')"));

            Assert.assertEquals(sources, testIndex.findConc("rel('" + type + "')"));
            Assert.assertEquals(sources, testIndex.findConc("rspan(rel('" + type + "', _, 'full'), 'source')"));
            Assert.assertEquals(sources, testIndex.findConc("rspan(rel('" + type + "', _, 'target'), 'source')"));
        }
    }

    @Test
    public void testNfa4() {
        List<String> expected = List.of("[May the Force be with] you");
        Assert.assertEquals(expected, testIndex.findConc(" 'May' '.*e'+ 'with' "));
    }

    @Test
    public void testOnlyRepetition() {
        List<String> expected = Arrays.asList("[The] quick", "over [the] lazy", "May [the] Force", "is [the] question");
        Assert.assertEquals(expected, testIndex.findConc("[lemma='.*he']{0,10}"));
    }

    @Test
    public void testConstraintSimple0() {
        List<String> expected = List.of("the [Force] be");
        Assert.assertEquals(expected, testIndex.findConc("a:'Force' :: a.word = 'Force'"));
    }

    @Test
    public void testConstraintSimple1() {
        List<String> expected = Arrays.asList("noot [mier aap mier] mier", "noot [aap aap aap] aap", "aap [aap aap aap]");
        Assert.assertEquals(expected, testIndex.findConc("a:[] 'aap' b:[] :: a.word = b.word"));
    }

    @Test
    public void testConstraintSimple2() {
        List<String> expected = Arrays.asList("noot [mier aap mier] mier", "noot [aap aap aap] aap", "aap [aap aap aap]");
        Assert.assertEquals(expected, testIndex.findConc("a:[] 'aap' b:[] :: a.word = b.lemma"));
    }

    @Test
    public void testConstraintSimple3() {
        List<String> expected = List.of("noot [mier aap mier mier] mier");
        Assert.assertEquals(expected, testIndex.findConc("a:[] 'aap' 'mier' b:[] :: a.word = b.word"));
    }

    @Test
    public void testConstraintSimple4() {
        List<String> expected = List.of("[The quick brown fox jumps over the] lazy");
        Assert.assertEquals(expected,
                testIndex.findConc("a:[] ([]{1,5} containing 'brown') b:[] :: a.lemma = b.lemma"));
    }

    @Test
    public void testConstraintSimple4a() {
        List<String> expected = Arrays.asList(
                "[The quick brown fox] jumps",
                "[The quick brown fox jumps] over",
                "[The quick brown fox jumps over] the",
                "[The quick brown fox jumps over the] lazy",
                "The [quick brown fox] jumps",
                "The [quick brown fox jumps] over",
                "The [quick brown fox jumps over] the",
                "The [quick brown fox jumps over the] lazy",
                "The [quick brown fox jumps over the lazy] dog"
                );
        Assert.assertEquals(expected,
                testIndex.findConc("a:[] ([]{1,5} containing 'brown') b:[]"));
    }

    @Test
    public void testLocalConstraint() {
        List<String> expected = List.of("[The quick brown fox jumps over the lazy] dog");
        Assert.assertEquals(expected,
                testIndex.findConc("(a:[] ([]{1,5} containing 'brown') b:[] :: a.lemma = b.lemma) 'lazy'"));
    }

    @Test
    public void testLocalConstraintAnyTokens() {
        List<String> expected = List.of("noot [mier aap mier mier] mier", "aap [mier mier mier noot] noot", "noot [aap aap aap aap]");
        Assert.assertEquals(expected,
                testIndex.findConc("(a:[] [] b:[] :: a.lemma = b.lemma) []"));
    }

    @Test
    public void testNGramContainingWithAdjustment() {
        List<String> expected = Arrays.asList(
            "[The quick brown] fox",
            "[The quick brown fox] jumps",
            "[The quick brown fox jumps] over",
            "[The quick brown fox jumps over] the",
            "The [quick brown] fox",
            "The [quick brown fox] jumps",
            "The [quick brown fox jumps] over",
            "The [quick brown fox jumps over] the",
            "The [quick brown fox jumps over the] lazy"
            );
        Assert.assertEquals(expected,
                testIndex.findConc("[] ([]{1,5} containing 'brown')"));
    }

    @Test
    public void testExpandTwice() {
        List<String> expected = List.of(
                "[The quick brown fox jumps over] the"
        );
        Assert.assertEquals(expected,
                testIndex.findConc("'The' []{1,2} 'fox' []{1, 2} 'over' "));
    }

    @Test
    public void testConstraintOr1() {
        List<String> expected = Arrays.asList("noot [mier aap mier] mier", "noot [aap aap aap] aap", "aap [aap aap aap]");
        Assert.assertEquals(expected, testIndex.findConc("a:[] 'aap' b:[] :: a.word = b.lemma | a.word = b.pos"));
    }

    @Test
    public void testConstraintOr2() {
        List<String> expected = Arrays.asList("noot [mier aap mier] mier", "noot [aap aap aap] aap", "aap [aap aap aap]");
        Assert.assertEquals(expected, testIndex.findConc("a:[] 'aap' b:[] :: a.word = b.lemma | a.lemma = b.word"));
    }

    @Test
    public void testConstraintAnd1() {
        List<String> expected = List.of();
        Assert.assertEquals(expected, testIndex.findConc("a:[] 'aap' b:[] :: a.word = b.lemma & a.word = b.pos"));
    }

    @Test
    public void testConstraintAnd2() {
        List<String> expected = Arrays.asList("noot [mier aap mier] mier", "noot [aap aap aap] aap", "aap [aap aap aap]");
        Assert.assertEquals(expected, testIndex.findConc("a:[] 'aap' b:[] :: a.word = b.lemma & a.word != b.pos"));
    }

    @Test
    public void testConstraintAnd3() {
        List<String> expected = Arrays.asList("noot [mier aap mier] mier", "noot [aap aap aap] aap", "aap [aap aap aap]");
        Assert.assertEquals(expected, testIndex.findConc("a:[] 'aap' b:[] :: a.word = b.lemma & a.pos = b.pos"));
    }

    @Test
    public void testConstraintImplication1() {
        List<String> expected = Arrays.asList(
                "[noot mier aap mier] mier", // left side matches, right side holds
                "noot [mier aap mier] mier", // left side doesn't match
                "noot [noot aap aap] aap", // left side doesn't match
                "noot [noot aap aap aap] aap", // left side matches, right side holds
                "noot [aap aap aap] aap", // left side doesn't match
                "aap [aap aap aap]" // left side doesn't match
        );
        // If left side of implication is true, right side must also be true
        Assert.assertEquals(expected, testIndex.findConc("(c:'noot')? a:[] 'aap' b:[] :: c -> (a.word = b.word)"));
    }

    @Test
    public void testConstraintImplication2() {
        List<String> expected = Arrays.asList(
                "noot [mier aap mier] mier",
                "noot [noot aap aap] aap",
                "noot [aap aap aap] aap",
                "aap [aap aap aap]");
        // If left side of implication is always false, right side is ignored
        Assert.assertEquals(expected, testIndex.findConc("(c:'NOTININDEX')? a:[] 'aap' b:[] :: c -> a.word = b.word"));
    }

    @Test
    public void testSortSimple() {
        List<String> expected = Arrays.asList(
                "[The] quick",
                "is [the] question",
                "May [the] Force",
                "over [the] lazy"
        );
        HitProperty sortBy = new HitPropertyBeforeHit(testIndex.index(), null,
                MatchSensitivity.INSENSITIVE, 5);
        Assert.assertEquals(expected, testIndex.findConc("'the'", sortBy));
    }

    @Test
    public void testSort() {
        List<String> expected = Arrays.asList(
                "aap [aap aap aap]",
                "noot [aap aap aap] aap",
                "noot [mier aap mier] mier",
                "noot [noot aap aap] aap"
                );
        // If left side of implication is always false, right side is ignored
        HitProperty hit = new HitPropertyHitText(testIndex.index(), MatchSensitivity.INSENSITIVE);
        HitProperty left = new HitPropertyBeforeHit(testIndex.index(), null, MatchSensitivity.INSENSITIVE, -1);
        HitProperty sortBy = new HitPropertyMultiple(hit, left);
        Assert.assertEquals(expected, testIndex.findConc("(c:'NOTININDEX')? a:[] 'aap' b:[] :: c -> a.word = b.word", sortBy));
    }

    @Test
    public void testSortReverse() {
        List<String> expected = Arrays.asList(
                "noot [noot aap aap] aap",
                "noot [mier aap mier] mier",
                "noot [aap aap aap] aap",
                "aap [aap aap aap]"
                );
        // If left side of implication is always false, right side is ignored
        HitProperty hit = new HitPropertyHitText(testIndex.index(), MatchSensitivity.INSENSITIVE);
        HitProperty left = new HitPropertyBeforeHit(testIndex.index(), null, MatchSensitivity.INSENSITIVE);
        HitProperty sortBy = new HitPropertyMultiple(hit, left).reverse();
        Assert.assertEquals(expected, testIndex.findConc("(c:'NOTININDEX')? a:[] 'aap' b:[] :: c -> a.word = b.word", sortBy));
    }

    @Test
    public void testFilter() {
        List<String> expected = List.of(
                "noot [noot aap aap] aap"
        );
        // If left side of implication is always false, right side is ignored
        BlackLabIndex index = testIndex.index();
        HitProperty prop = new HitPropertyHitText(index, MatchSensitivity.INSENSITIVE);
        Annotation annotation = index.mainAnnotatedField().mainAnnotation();
        Terms terms = index.annotationForwardIndex(annotation).terms();
        int[] words = { terms.indexOf("noot"), terms.indexOf("aap"), terms.indexOf("aap") };
        PropertyValue value = new PropertyValueContextWords(index, annotation, MatchSensitivity.INSENSITIVE, words, null,
                false);
        Assert.assertEquals(expected, testIndex.findConc("(c:'NOTININDEX')? a:[] 'aap' b:[] :: c -> a.word = b.word", prop, value));
    }

    @Test
    public void testNGramsNotContaining() {
        List<String> expected = List.of(
                "noot [noot aap aap] aap"
        );
        BlackLabIndex index = testIndex.index();
        HitProperty prop = new HitPropertyHitText(index, MatchSensitivity.INSENSITIVE);
        Annotation annotation = index.mainAnnotatedField().mainAnnotation();
        Terms terms = index.annotationForwardIndex(annotation).terms();
        int[] words = { terms.indexOf("noot"), terms.indexOf("aap"), terms.indexOf("aap") };
        PropertyValue value = new PropertyValueContextWords(index, annotation, MatchSensitivity.INSENSITIVE, words, null,
                false);
        // Query below will be rewritten using POSFILTER(ANYTOKEN(1,INF), NOTCONTAINING, 'noot');
        // there used to be an issue with determining doc length that messed this up
        Assert.assertEquals(expected, testIndex.findConc("'noot'+ [word != 'noot']+ group:('aap')+", prop, value));
    }

    @Test
    public void testCaptureGroups() {
        Hits hits = testIndex.find("A:'aap'");
        Assert.assertEquals(5, hits.size());
        Assert.assertTrue(hits.hasMatchInfo());
        MatchInfo[] group = hits.get(0).matchInfo();
        Assert.assertNotNull(group);
        Assert.assertEquals(1, group.length);
        Assert.assertEquals(2, group[0].getSpanStart());
        Assert.assertEquals(3, group[0].getSpanEnd());
    }

    @Test
    public void testCaptureGroups2() {
        // There's two ways to capture A, so we expect two identical concordances
        List<String> expected = List.of(
            "mier [aap mier mier] mier",
            "mier [aap mier mier] mier"
        );
        String query = "(A:'aap' 'mier' []|'aap' 'mier' A:[])";
        List<String> result = testIndex.findConc(query);
        Assert.assertEquals(2, result.size());
        Assert.assertEquals(expected, result);
        // Validate the actual captures as well
        Hits hits = testIndex.find(query);
        Assert.assertEquals(2, hits.size());
        Assert.assertTrue(hits.hasMatchInfo());
        Assert.assertEquals(1, hits.get(0).doc());
        Assert.assertEquals(2, hits.get(0).matchInfo()[0].getSpanStart());
        Assert.assertEquals(3, hits.get(0).matchInfo()[0].getSpanEnd());
        Assert.assertEquals(1, hits.get(1).doc());
        Assert.assertEquals(4, hits.get(1).matchInfo()[0].getSpanStart());
        Assert.assertEquals(5, hits.get(1).matchInfo()[0].getSpanEnd());
    }

    @Test
    public void testCaptureGroups3() {
        // There's two ways to capture A, so we expect two identical concordances
        List<String> expected = List.of(
                "mier [aap mier mier] mier",
                "mier [aap mier mier mier] noot",  // twice with different captures!
                "mier [aap mier mier mier] noot",
                "mier [aap mier mier mier noot] noot",
                "noot [aap aap aap] aap",
                "noot [aap aap aap aap]",          // twice with different captures!
                "noot [aap aap aap aap]",
                "aap [aap aap aap]"
        );
        String query = "'aap' A:([]{1,2}) []{1,2}";
        List<String> result = testIndex.findConc(query);
        Assert.assertEquals(8, result.size());
        Assert.assertEquals(expected, result);
    }

    @Test
    public void testDocResults() {
        DocResults allDocs = testIndex.index().queryDocuments(new MatchAllDocsQuery());

        // Check that the number is correct (e.g. metadata document is not matched)
        Assert.assertEquals(4, allDocs.size());

        // Check that all pids and titles are there
        Set<String> pids = new HashSet<>();
        Set<String> titles = new HashSet<>();
        for (DocResult d: allDocs) {
            Document doc = testIndex.index().luceneDoc(d.docId());
            pids.add(doc.get("pid"));
            titles.add(doc.get("title"));
        }
        Assert.assertEquals(Set.of("0", "1", "2", "3"), pids);
        Assert.assertEquals(Set.of("Pangram", "Learning words", "Star Wars", "Bastardized Shakespeare"), titles);
    }

    /** Test that equals and hashCode works for TextPattern and BLSpanQuery (essential for caching!) */
    @Test
    public void testTextPatternAndQueryEqualsHashcode() throws InvalidQuery {
        List<String> queries = Arrays.asList(
            // Simple sequence query
            "\"The\" [lemma=\"quick\"]",

            // Any token, repetitions
            "\"brown\"+ ([]* \"fox\")",

            // Within/containing
            "\"dog\" within <s/>",
            "<s/> containing [lemma=\"cat\"]",

            // Global contraints
            "A:[] B:[] :: A.lemma = B.lemma",
            "A:[] B:[] :: start(A) < start(B)",

            // Relations
            "[]* -nmod-> []*",
            "A:[]* -nmod-> B:[]*",
            "A:[]* -nmod-> B:[]* :: A.word > B.word"
        );
        for (String query: queries) {
            TextPattern p1 = CorpusQueryLanguageParser.parse(query);
            TextPattern p2 = CorpusQueryLanguageParser.parse(query);
            Assert.assertEquals(p1, p2);
            Assert.assertEquals(p1.hashCode(), p2.hashCode());
            BlackLabIndex index = testIndex.index();
            QueryExecutionContext context = QueryExecutionContext.get(index,
                    index.mainAnnotatedField().mainAnnotation(), MatchSensitivity.INSENSITIVE);
            BLSpanQuery q1 = p1.translate(context);
            context = QueryExecutionContext.get(index,
                    index.mainAnnotatedField().mainAnnotation(), MatchSensitivity.INSENSITIVE);
            BLSpanQuery q2 = p2.translate(context);
            Assert.assertEquals(q1, q2);
            Assert.assertEquals(q1.hashCode(), q2.hashCode());
        }
    }

    @Test
    public void testFixedSpan() throws InvalidQuery {
        List<String> expected = Arrays.asList(
                "The [quick] brown",
                "noot [mier] aap",
                "May [the] Force",
                "To [find] or");
        TextPattern patt = new TextPatternFixedSpan(1, 2);
        BLSpanQuery query = patt.translate(QueryExecutionContext.get(testIndex.index(),
                testIndex.index().mainAnnotatedField().mainAnnotation(), MatchSensitivity.INSENSITIVE));
        Assert.assertEquals(expected, testIndex.findConc(query));
    }

    @Test
    public void testEscape() throws InvalidQuery {
        // Keys are CQL regexes (i.e. the $ part in [word="$"]), values are the expected Lucene regex
        Map<String, String> bcqlToLucene = new HashMap<>();
        testEscaping("\\.", "\\.");
        testEscaping(".", ".");
        testEscaping("\"", "\\\"");
        testEscaping("\\\\\\\"", "\\\\\\\"");
        testEscaping("\\(", "\\(");
        testEscaping("\\\\\\(", "\\\\\\(");
    }

    private void testEscaping(String expectedLuceneRegex, String bcqlPattern) throws InvalidQuery {
        TextPattern tp = CorpusQueryLanguageParser.parse("\"" + bcqlPattern + "\"");
        Assert.assertTrue(tp instanceof TextPatternTerm);
        BLSpanQuery q = tp.translate(QueryExecutionContext.get(testIndex.index(),
                testIndex.index().mainAnnotatedField().mainAnnotation(), MatchSensitivity.INSENSITIVE));
        Assert.assertTrue(q instanceof BLSpanMultiTermQueryWrapper);
        q.visit(new QueryVisitor() {
            @Override
            public boolean acceptField(String field) {
                return true;
            }

            @Override
            public void visitLeaf(Query query) {
                Assert.assertTrue(query instanceof RegexpQuery);
                Term term = ((RegexpQuery) query).getRegexp();
                Assert.assertEquals("contents%word@i", term.field());
                Assert.assertEquals(expectedLuceneRegex, term.text());
            }
        });
    }

    @Test
    public void testLookAhead() throws InvalidQuery {
        List<String> expected = Arrays.asList("To [find] or");
        TextPattern patt = CorpusQueryLanguageParser.parse("'find' (?= 'or' 'not')");
        BLSpanQuery query = patt.translate(QueryExecutionContext.get(testIndex.index(),
                testIndex.index().mainAnnotatedField().mainAnnotation(), MatchSensitivity.INSENSITIVE));
        Assert.assertEquals(expected, testIndex.findConc(query));
    }

    @Test
    public void testNegativeLookAhead() throws InvalidQuery {
        List<String> expected = Arrays.asList("To [find] or");
        TextPattern patt = CorpusQueryLanguageParser.parse("'find' (?! 'That' 'is')");
        BLSpanQuery query = patt.translate(QueryExecutionContext.get(testIndex.index(),
                testIndex.index().mainAnnotatedField().mainAnnotation(), MatchSensitivity.INSENSITIVE));
        Assert.assertEquals(expected, testIndex.findConc(query));
    }

    @Test
    public void testLookBehind() throws InvalidQuery {
        List<String> expected = Arrays.asList("to [find] That");
        TextPattern patt = CorpusQueryLanguageParser.parse("(?<= 'not' 'to' ) 'find'");
        BLSpanQuery query = patt.translate(QueryExecutionContext.get(testIndex.index(),
                testIndex.index().mainAnnotatedField().mainAnnotation(), MatchSensitivity.INSENSITIVE));
        Assert.assertEquals(expected, testIndex.findConc(query));
    }

    @Test
    public void testNegativeLookBehind() throws InvalidQuery {
        List<String> expected = Arrays.asList("To [find] or");
        TextPattern patt = CorpusQueryLanguageParser.parse("(?<! 'not' 'to' ) 'find'");
        BLSpanQuery query = patt.translate(QueryExecutionContext.get(testIndex.index(),
                testIndex.index().mainAnnotatedField().mainAnnotation(), MatchSensitivity.INSENSITIVE));
        Assert.assertEquals(expected, testIndex.findConc(query));
    }

    @Test
    public void testPunctBefore() throws InvalidQuery {
        List<String> expected = Arrays.asList("To [find or] not");
        TextPattern patt = CorpusQueryLanguageParser.parse("'find' [punctBefore='\\(']");
        BLSpanQuery query = patt.translate(QueryExecutionContext.get(testIndex.index(),
                testIndex.index().mainAnnotatedField().mainAnnotation(), MatchSensitivity.INSENSITIVE));
        Assert.assertEquals(expected, testIndex.findConc(query));
    }

    @Test
    public void testPunctAfter() throws InvalidQuery {
        List<String> expected = Arrays.asList("to [find That] is");
        TextPattern patt = CorpusQueryLanguageParser.parse("[punctAfter='\\)\\.'] 'That'");
        BLSpanQuery query = patt.translate(QueryExecutionContext.get(testIndex.index(),
                testIndex.index().mainAnnotatedField().mainAnnotation(), MatchSensitivity.INSENSITIVE));
        Assert.assertEquals(expected, testIndex.findConc(query));
    }

}
