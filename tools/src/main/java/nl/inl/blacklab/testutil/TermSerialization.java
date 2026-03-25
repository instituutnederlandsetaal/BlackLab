package nl.inl.blacklab.testutil;

import java.io.File;
import java.io.IOException;

import org.apache.lucene.index.LeafReaderContext;

import nl.inl.blacklab.codec.BLTerms;
import nl.inl.blacklab.forwardindex.Terms;
import nl.inl.blacklab.search.BlackLab;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;

public class TermSerialization {

    private static int termsDone;

    private TermSerialization() {
    }

    public static void main(String[] args) throws IOException {
        String path = args.length >= 1 ? args[0] : ".";
        String annotationName = args.length >= 2 ? args[1] : "";
        try {
            BlackLabIndex index = BlackLab.open(new File(path));
            AnnotatedField field = index.annotatedField("contents");
            Annotation annotation = annotationName.isEmpty() ?
                    field.mainAnnotation() :
                    field.annotation(annotationName);
            String luceneField = annotation.forwardIndexSensitivity().luceneField();
            System.out.println("Checking all terms...");
            termsDone = 0;
            for (LeafReaderContext lrc: index.reader().leaves()) {
                doTerm(lrc, luceneField);
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("\nUsage: TermSerialization [indexdir] [annotation]");
        }
    }

    private static void doTerm(LeafReaderContext lrc, String luceneField) {
        Terms terms = BLTerms.forSegment(lrc, luceneField).reader();

        System.out.flush();
        for (int termId = 0; termId < terms.numberOfTerms(); termId++) {
            String term = terms.get(termId);
            if (term == null) {
                System.out.println("term == null! id = " + termId);
                System.out.flush();
            } else {
                checkTerm(term, termId, terms);
            }
            termsDone++;
            if (termsDone % 100000 == 0) {
                System.out.println(termsDone + " terms checked...");
                System.out.flush();
            }
        }
    }

    private static void checkTerm(String term, int termId, Terms terms) {
        // DEBUG
        if (!term.equals("Daß")) {
            return;
        }

        int sortPos1 = terms.idToSortPosition(termId, MatchSensitivity.SENSITIVE);
        int sortPos2 = terms.termToSortPosition(term, MatchSensitivity.SENSITIVE);
        if (sortPos1 != sortPos2) {
            System.out.println("SENSITIVE sortPos1 != sortPos2: " + sortPos1 + " != " + sortPos2 + " for term '" + term
                    + "'");
            System.out.flush();
        }
        sortPos1 = terms.idToSortPosition(termId, MatchSensitivity.INSENSITIVE);
        sortPos2 = terms.termToSortPosition(term, MatchSensitivity.INSENSITIVE);
        if (sortPos1 != sortPos2) {
            System.out.println("INSENSITIVE sortPos1 != sortPos2: " + sortPos1 + " != " + sortPos2 + " for term '" + term
                    + "'");
            System.out.flush();
        }
    }
}
