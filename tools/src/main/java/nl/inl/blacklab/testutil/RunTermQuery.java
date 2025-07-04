package nl.inl.blacklab.testutil;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.BitSet;

import org.apache.commons.text.WordUtils;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.PostingsEnum;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.SimpleCollector;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.queries.spans.SpanQuery;
import org.apache.lucene.queries.spans.SpanTermQuery;
import org.apache.lucene.queries.spans.SpanWeight;
import org.apache.lucene.queries.spans.SpanWeight.Postings;
import org.apache.lucene.queries.spans.Spans;
import org.apache.lucene.store.FSDirectory;

import nl.inl.blacklab.exceptions.BlackLabException;
import nl.inl.util.LuceneUtil;

public class RunTermQuery {

    static int matchingDoc = -1;

    static int matchPosition = -1;

    static boolean docsFound = false;

    private RunTermQuery() {
    }

    public static void main(String[] args) throws IOException {
        String word = "koekenbakker";
        String fieldName = "contents%word@s";
        if (args.length >= 1)
            word = args[0];
        if (args.length >= 2)
            fieldName = args[1];

        // Open the index
        IndexReader reader = null;
        try {
            reader = DirectoryReader.open(FSDirectory.open(Paths.get(".")));
        } catch (Exception e) {
            System.err.println("Error opening index; is the current directory a Lucene index?");
            usage();
            System.exit(1);
        }

        Term term = new Term(fieldName, word);
        System.out.println("TERM: " + term.text());

        System.out.println("  Total frequency: " + term.text() + ": " + reader.totalTermFreq(term) + "\n");

        doQuery(term, reader);
        doSpanQuery(term, reader);
        if (matchingDoc != -1)
            doTermVector(matchingDoc, term, reader);

        if (matchPosition != -1)
            doSnippet(matchingDoc, matchPosition, term, reader);
    }

    private static void doSnippet(int docId, int position, Term term,
            IndexReader reader) {

        System.out.println("\nSNIPPET");

        int first = position - 10;
        if (first < 0)
            first = 0;
        int number = 20;
        String[] words = LuceneUtil.getWordsFromTermVector(reader, docId, term.field(), first, first + number, true);

        // Print result
        int i = 0;
        StringBuilder b = new StringBuilder();
        for (String word : words) {
            if (word == null)
                word = "[MISSING]";
            b.append(i + first).append(":").append(word).append(" ");
            i++;
        }
        System.out.println(WordUtils.wrap(b.toString(), 80));
    }

    private static void doTermVector(int doc, Term term, IndexReader reader) {
        System.out.println("\nTERM VECTOR FOR DOC " + doc);
        String luceneName = term.field();
        String word = term.text();
        try {
            org.apache.lucene.index.Terms terms = reader.getTermVector(doc, luceneName);
            if (terms == null) {
                System.out.println("Field " + luceneName + " has no Terms");
                return;
            }
            System.out.println(
                    "  Doc count:           " + terms.getDocCount() + "\n" +
                            "  Sum doc freq:        " + terms.getSumDocFreq() + "\n" +
                            "  Sum total term freq: " + terms.getSumDocFreq() + "\n" +
                            "  Has offsets:         " + terms.hasOffsets() + "\n" +
                            "  Has payloads:        " + terms.hasPayloads() + "\n" +
                            "  Has positions:       " + terms.hasPositions() + "\n");

            TermsEnum termsEnum = terms.iterator();
            if (!termsEnum.seekExact(term.bytes())) {
                System.out.println("Term " + word + " not found.");
                return;
            }

            System.out.println("\n" +
                    "  TERM '" + termsEnum.term().utf8ToString() + "':\n" +
                    "    Doc freq:        " + termsEnum.docFreq() + "\n" +
                    "    Ord:             " + termsEnum.ord() + "\n" +
                    "    Total term freq: " + termsEnum.totalTermFreq() + "\n");

            if (terms.hasPositions()) {
                // Verzamel concordantiewoorden uit term vector
                PostingsEnum docPosEnum = termsEnum.postings(null, PostingsEnum.POSITIONS);
                System.out.println("    POSITIONS:");
                while (docPosEnum.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {

                    System.out.println(
                            "      Doc id: " + docPosEnum.docID() + "\n" +
                                    "      Freq:   " + docPosEnum.freq() + "\n");

                    for (int i = 0; i < docPosEnum.freq(); i++) {
                        int position = docPosEnum.nextPosition();
                        int start = docPosEnum.startOffset();
                        if (start >= 0) {
                            // Character offsets stored
                            int end = docPosEnum.startOffset();
                            System.out.println("      " + position + " (offsets: " + start + "-" + end + ")");
                        } else {
                            // No character offsets stored
                            System.out.println("      " + position);
                        }
                        if (matchPosition < 0)
                            matchPosition = position;
                    }
                }
            } else {
                // No positions
                PostingsEnum postingsEnum = termsEnum.postings(null, PostingsEnum.FREQS);
                System.out.println("\n    DOCS:");
                while (postingsEnum.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
                    System.out.println(
                            "      Doc id: " + postingsEnum.docID() + "\n" +
                                    "      Freq:   " + postingsEnum.freq() + "\n");
                }
            }
        } catch (IOException e) {
            throw BlackLabException.wrapRuntime(e);
        }
    }

    private static void doQuery(Term term, IndexReader reader) throws IOException {
        Query query = new TermQuery(term);
        query = query.rewrite(reader);
        System.out.println("REGULAR QUERY");

        IndexSearcher searcher = new IndexSearcher(reader);
        final BitSet bits = new BitSet(reader.maxDoc());
        docsFound = false;
        searcher.search(query, new SimpleCollector() {
            private int docBase;

            @Override
            protected void doSetNextReader(LeafReaderContext context)
                    throws IOException {
                docBase = context.docBase;
                super.doSetNextReader(context);
            }

            @Override
            public void collect(int doc) {
                bits.set(doc + docBase);
                System.out.printf("  doc %7d%n", doc + docBase);
                matchingDoc = doc + docBase;
                docsFound = true;
            }

			@Override
			public ScoreMode scoreMode() {
				return ScoreMode.COMPLETE_NO_SCORES;
			}
        });
        if (!docsFound)
            System.out.println("  (no matching docs)");
        System.out.println();
    }

    private static void doSpanQuery(Term term, IndexReader reader) throws IOException {
        IndexSearcher searcher = new IndexSearcher(reader);

        SpanQuery spanQuery = new SpanTermQuery(term);
        spanQuery = (SpanQuery) spanQuery.rewrite(reader);

        System.out.println("SPANQUERY");

        System.out.println("USING LEAVES:");
        boolean hitsFound = false;
        float boost=1.0f;
        SpanWeight weight = spanQuery.createWeight(searcher, ScoreMode.COMPLETE_NO_SCORES, boost);
        for (LeafReaderContext arc : reader.leaves()) {
            Spans spans = weight.getSpans(arc, Postings.OFFSETS);
            while (spans != null && spans.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
                while (spans.nextStartPosition() != Spans.NO_MORE_POSITIONS) {
                    int doc = arc.docBase + spans.docID();
                    System.out.printf("  doc %7d, pos %4d-%4d%n", doc, spans.startPosition(), spans.endPosition());
                    hitsFound = true;
                }
            }
        }
        if (!hitsFound)
            System.out.println("  (no hits)");
        System.out.println();
    }

    private static void usage() {
        System.out
                .println("\nThis tool searches for a term and prints index information for this term.\n");
        System.out.println("  RunTermQuery <term> [fieldName]");
    }

}
