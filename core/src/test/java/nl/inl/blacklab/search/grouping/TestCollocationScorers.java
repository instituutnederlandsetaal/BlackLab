package nl.inl.blacklab.search.grouping;

import java.io.StringReader;
import java.util.List;
import java.util.Map;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermInSetQuery;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.util.BytesRef;
import org.junit.Assert;
import org.junit.Test;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.index.DocumentFormats;
import nl.inl.blacklab.indexers.config.ConfigInputFormat;
import nl.inl.blacklab.queryParser.corpusql.BcqlQueryLanguageParser;
import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.resultproperty.HitPropertyHitText;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.AnnotationSensitivity;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.results.CorpusSize;
import nl.inl.blacklab.search.results.hitresults.HitGroup;
import nl.inl.blacklab.search.results.hitresults.HitGroupCollocationScorer;
import nl.inl.blacklab.search.results.hitresults.HitGroupScorer;
import nl.inl.blacklab.search.results.hitresults.HitGroupScorerDice;
import nl.inl.blacklab.search.results.hitresults.HitGroupScorerSalience;
import nl.inl.blacklab.search.results.hitresults.HitGroupScorerSize;
import nl.inl.blacklab.search.results.hitresults.HitGroups;
import nl.inl.blacklab.search.results.hitresults.HitResults;
import nl.inl.blacklab.search.textpattern.CompleteQuery;
import nl.inl.blacklab.search.textpattern.TextPattern;
import nl.inl.blacklab.testutil.TestIndex;
import nl.inl.util.LuceneUtil;

public class TestCollocationScorers {

    private final TestIndex testIndex = TestIndex.getReusable();

    private final BlackLabIndex index = testIndex.index();

    @Test
    public void testTotalFrequencyRequirements() {
        // Salience inherits the default requirement for scorer plugins.
        Assert.assertTrue(new HitGroupScorerSalience().needsTotalFrequency());
        Assert.assertFalse(new HitGroupScorerDice().needsTotalFrequency());
        Assert.assertFalse(new HitGroupScorerSize().needsTotalFrequency());
    }

    @Test
    public void testSalienceWithFilterInLaterSegment() throws InvalidQuery {
        AnnotatedField contents = index.mainAnnotatedField();
        Query filter = new TermQuery(new Term("title", "learning"));
        // The term-statistics and search paths must agree, including case normalization and regex subclasses.
        for (TextPattern findPattern: List.of(
                BcqlQueryLanguageParser.parseQuery("[lemma=\"mier\"]"),
                BcqlQueryLanguageParser.parseQuery("[lemma=\"MIER\"]"),
                TextPattern.term("MIER", "lemma", MatchSensitivity.INSENSITIVE),
                TextPattern.regex("mi.*", "lemma", MatchSensitivity.INSENSITIVE))) {
            HitGroupScorer scorer = HitGroupScorer.fromConfig(contents, Map.of(
                    HitGroupScorer.KEY_ID, HitGroupScorerSalience.TYPE_ID,
                    HitGroupCollocationScorer.KEY_DOC_FILTER, filter,
                    HitGroupCollocationScorer.KEY_PATTERN, findPattern,
                    HitGroupCollocationScorer.KEY_ANNOTATION, contents.mainAnnotation().name(),
                    HitGroupCollocationScorer.KEY_SENSITIVITY, MatchSensitivity.INSENSITIVE.toString()));

            HitProperty groupBy = new HitPropertyHitText(index, contents.mainAnnotation(), MatchSensitivity.INSENSITIVE);
            HitGroups groups = testIndex.find("meet([], [lemma=\"mier\"], -5, 5)", filter)
                    .group(groupBy, 0, scorer);

            HitGroup aap = null;
            for (HitGroup group: groups) {
                Assert.assertTrue("Non-finite score for " + group.identity(), Double.isFinite(group.score()));
                if (group.identity().toString().equals("aap"))
                    aap = group;
            }
            Assert.assertNotNull(aap);
            // f=10 nearby pairs, f1=4 occurrences of "mier", f2=5 occurrences of "aap", N=12 subcorpus tokens.
            Assert.assertEquals(10, aap.size());
            double expected = StrictMath.log(10) * StrictMath.log(10 * 12.0 / (4 * 5)) / StrictMath.log(2.0);
            Assert.assertEquals(findPattern.toString(), expected, aap.score(), 1e-12);
        }
    }

    @Test
    public void testFilteredFrequenciesAcrossSegments() {
        Query filter = new TermInSetQuery("title",
                new BytesRef("pangram"), new BytesRef("star"), new BytesRef("bastardized"));
        AnnotationSensitivity lemma = index.mainAnnotatedField().annotation("lemma")
                .sensitivity(MatchSensitivity.INSENSITIVE);

        Assert.assertEquals(4, LuceneUtil.getTermFrequency(lemma, "the", filter, false));
        CorpusSize corpusSize = index.queryDocuments(filter).subcorpusSize();
        Assert.assertEquals(25, corpusSize.getTotalCount().getTokens());
        Assert.assertEquals(25,
                corpusSize.getCountsPerField().get(index.mainAnnotatedField().name()).getTokens());
    }

    @Test
    public void testFragmentFrequenciesCountOverlapsAndParentMatchesOnce() throws Exception {
        ConfigInputFormat format = ConfigInputFormat.read(new StringReader("""
                documentPath: /doc
                annotatedFields:
                  contents:
                    wordPath: .//w
                    annotations:
                    - name: word
                      valuePath: .
                      sensitivity: sensitive_insensitive
                    inlineTags:
                    - path: .//frag
                      type: fragment
                corpusConfig:
                  specialFields:
                    pidField: pid
                metadata:
                  fields:
                  - name: pid
                    valuePath: "@pid"
                    type: untokenized
                    fragments: separate
                  - name: selection
                    valuePath: "@selection"
                    fragments: separate
                """), false, "test-collocation-fragments", null);
        DocumentFormats.add(format);
        try (TestIndex fragments = TestIndex.get(format.getName(), """
                <doc pid="doc1" selection="document">
                  <w>outside</w>
                  <frag pid="outer" selection="outer"><w>seed</w>
                    <frag pid="inner" selection="inner"><w>near</w><w>seed</w></frag>
                    <w>near</w>
                  </frag>
                  <w>outside</w><w>near</w>
                </doc>
                """, "<doc pid='doc2' selection='other'><w>seed</w><w>near</w></doc>")) {
            // Outer [1,5) and inner [2,4) overlap: N=4, seed=2, near=2, matching near positions=2.
            assertFragmentScore(fragments.index(), new TermInSetQuery("selection",
                    new BytesRef("outer"), new BytesRef("inner")), 4, 2);
            // A matching parent supersedes its fragments: N=7, seed=2, near=3, matching near positions=2.
            assertFragmentScore(fragments.index(), new TermInSetQuery("selection",
                    new BytesRef("document"), new BytesRef("outer"), new BytesRef("inner")), 7, 3);
        }
    }

    private static void assertFragmentScore(BlackLabIndex index, Query filter, int tokens, int collocates) {
        Assert.assertTrue(index.isFragmentQuery(filter));
        AnnotatedField field = index.mainAnnotatedField();
        HitGroupScorer scorer = HitGroupScorer.fromConfig(field, Map.of(
                HitGroupScorer.KEY_ID, HitGroupScorerSalience.TYPE_ID,
                HitGroupCollocationScorer.KEY_DOC_FILTER, filter,
                HitGroupCollocationScorer.KEY_PATTERN, BcqlQueryLanguageParser.parseQuery("[word=\"seed\"]"),
                HitGroupCollocationScorer.KEY_ANNOTATION, "word",
                HitGroupCollocationScorer.KEY_SENSITIVITY, MatchSensitivity.INSENSITIVE.toString()));
        HitProperty groupBy = new HitPropertyHitText(index, field.mainAnnotation(), MatchSensitivity.INSENSITIVE);
        TextPattern pattern = BcqlQueryLanguageParser.parseQuery("meet([word=\"near\"], [word=\"seed\"], -1, 1)");
        HitGroups groups = index.search(field, false).find(new CompleteQuery(pattern, filter))
                .groupStats(groupBy, 0, scorer).execute();
        Assert.assertEquals(1, groups.size());
        HitGroup group = groups.get(0);
        Assert.assertEquals("near", group.identity().toString());
        // meet returns positions 2 and 4. Position 2 has a seed on either side but is returned once.
        Assert.assertEquals(2, group.size());
        double expected = StrictMath.log(2) * StrictMath.log(2.0 * tokens / (2 * collocates)) / StrictMath.log(2.0);
        Assert.assertEquals(expected, group.score(), 1e-12);
    }

    @Test
    public void testRelationScores() throws Exception {
        ConfigInputFormat format = ConfigInputFormat.read(new StringReader("""
                fileType: conll-u
                annotatedFields:
                  contents:
                    annotations:
                    - name: word
                      valuePath: 2
                      sensitivity: sensitive_insensitive
                    - name: lemma
                      valuePath: 3
                      sensitivity: sensitive_insensitive
                """), false, "test-collocation-relations", null);
        DocumentFormats.add(format);
        // The words we'll construct document out of
        List<String[]> docsWords = List.of(
            new String[] { "eat",  "eat",  "Apples", "apple" },
            new String[] { "eats", "eat",  "apple",  "apple" },
            new String[] { "peel", "peel", "Apples", "apple" }
        );
        // Construct the CoNLL-U documents
        String[] documents = new String[docsWords.size()];
        int docIndex = 0;
        for (String[] docWords: docsWords) {
            String[][] tokens = new String[][] {
                // Construct documents with two tokens: a verb and its object.
                // So the first token has a dependency relation "obj" to the second token.
                // ID  FORM         LEMMA        UPOS    XPOS FEATS HEAD DEPREL  DEPS MISC
                { "1", docWords[0], docWords[1], "VERB", "_", "_",  "0", "root", "_", "_" },
                { "2", docWords[2], docWords[3], "NOUN", "_", "_",  "1", "obj",  "_", "_" }
            };
            StringBuilder doc = new StringBuilder();
            for (String[] token: tokens) {
                doc.append(String.join("\t", token)).append("\n");
            }
            doc.append("\n");
            documents[docIndex] = doc.toString();
            docIndex++;
        }

        try (TestIndex relations = TestIndex.get(format.getName(), documents)) {

            HitResults results = relations.find("[] -obj-> [lemma=\"apple\"]");
            Assert.assertEquals(3, results.size());

            // Target for lemma "eat" is lemma "apple"
            assertRelationCollocationScores(relations.index(), HitGroupScorerDice.TYPE_ID,
                    "reltargets", "[lemma=\"eat\"]", "lemma", MatchSensitivity.INSENSITIVE,
                    Map.of("apple", 2L), Map.of("apple", 0.8));
            // Target for lemma "eat" is word "Apples" and "apple" (case-sensitive)
            assertRelationCollocationScores(relations.index(), HitGroupScorerDice.TYPE_ID,
                    "reltargets", "[lemma=\"eat\"]", "word", MatchSensitivity.SENSITIVE,
                    Map.of("Apples", 1L, "apple", 1L), Map.of("Apples", 0.5, "apple", 2.0 / 3));
            // Salience also needs the population: three obj relations, rather than six tokens.
            assertRelationCollocationScores(relations.index(), HitGroupScorerSalience.TYPE_ID,
                    "reltargets", "[lemma=\"eat\"]", "lemma", MatchSensitivity.INSENSITIVE,
                    Map.of("apple", 2L), Map.of("apple", 0.0));

            // Source for lemma "apple" is lemmas "eat" and "peel"
            assertRelationCollocationScores(relations.index(), HitGroupScorerDice.TYPE_ID,
                    "relsources", "[lemma=\"apple\"]", "lemma", MatchSensitivity.INSENSITIVE,
                    Map.of("eat", 2L, "peel", 1L), Map.of("eat", 0.8, "peel", 0.5));
            // Salience also needs the population: three obj relations, rather than six tokens.
            assertRelationCollocationScores(relations.index(), HitGroupScorerSalience.TYPE_ID,
                    "relsources", "[lemma=\"apple\"]", "lemma", MatchSensitivity.INSENSITIVE,
                    Map.of("eat", 2L, "peel", 1L), Map.of("eat", 0.0, "peel", 0.0));
        }
    }

    private static void assertRelationCollocationScores(BlackLabIndex index, String scorerType, String type, String pattern,
            String annotation, MatchSensitivity sensitivity, Map<String, Long> counts, Map<String, Double> scores) {
        AnnotatedField field = index.mainAnnotatedField();
        HitGroupScorer scorer = HitGroupScorer.fromConfig(field, Map.of(
                HitGroupScorer.KEY_ID, scorerType,
                HitGroupCollocationScorer.KEY_PATTERN, BcqlQueryLanguageParser.parseQuery(pattern),
                HitGroupCollocationScorer.KEY_ANNOTATION, annotation,
                HitGroupCollocationScorer.KEY_SENSITIVITY, sensitivity.toString(),
                HitGroupCollocationScorer.KEY_COLL_TYPE, type,
                HitGroupCollocationScorer.KEY_REL_TYPE, "obj"));
        String query = type.equals("relsources") ? "[] -obj-> (" + pattern + ")":
                "rspan((" + pattern + ") -obj-> [], \"target\")";
        HitProperty groupBy = new HitPropertyHitText(index, field.annotation(annotation), sensitivity);
        HitGroups groups = index.search(field, false)
                .find(new CompleteQuery(BcqlQueryLanguageParser.parseQuery(query)))
                .groupStats(groupBy, 0, scorer).execute();
        Assert.assertEquals(counts.size(), groups.size());
        for (HitGroup group: groups) {
            String term = group.identity().toString();
            Assert.assertEquals(counts.get(term).longValue(), group.size());
            Assert.assertEquals(scores.get(term), group.score(), 1e-12);
        }
    }
}
