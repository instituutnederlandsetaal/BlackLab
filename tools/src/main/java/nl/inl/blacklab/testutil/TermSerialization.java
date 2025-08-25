package nl.inl.blacklab.testutil;

import java.io.File;
import java.io.IOException;
import com.ibm.icu.text.Collator;

import org.apache.lucene.index.LeafReaderContext;
import org.eclipse.collections.api.set.primitive.MutableIntSet;

import nl.inl.blacklab.codec.BLTerms;
import nl.inl.blacklab.forwardindex.AnnotationForwardIndex;
import nl.inl.blacklab.forwardindex.Collators;
import nl.inl.blacklab.forwardindex.Terms;
import nl.inl.blacklab.search.BlackLab;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;

public class TermSerialization {

    private TermSerialization() {
    }

    public static void main(String[] args) throws IOException {
        String path = args.length >= 1 ? args[0] : ".";
        String word = args.length >= 2 ? args[1] : "in";
        String annotationName = args.length >= 3 ? args[2] : "";

        BlackLabIndex index = BlackLab.open(new File(path));
        AnnotatedField field = index.annotatedField("contents");
        Annotation annotation = annotationName.isEmpty() ? field.mainAnnotation() : field.annotation(annotationName);
        AnnotationForwardIndex fi = index.annotationForwardIndex(annotation);

        String luceneField = annotation.forwardIndexSensitivity().luceneField();
        for (LeafReaderContext lrc: index.reader().leaves()) {
            doTerm(word, lrc, luceneField);
        }
    }

    private static void doTerm(String word, LeafReaderContext lrc, String luceneField) {
        Terms terms = BLTerms.forSegment(lrc, luceneField).reader();

        Collators collators = Collators.getDefault();
        Collator collator = collators.get(MatchSensitivity.SENSITIVE);

        System.out.println("Checking all terms...");
        System.out.flush();
        int n = 0;
        for (int termId = 0; termId < terms.numberOfTerms(); termId++) {
            String term = terms.get(termId);
            if (term == null) {
                System.out.println("term == null! id = " + termId);
                System.out.flush();
            }
            else if (term.isEmpty()) {
                System.out.println("term is empty! id = " + termId);
                System.out.flush();
            } else {
                int sortPos1 = terms.idToSortPosition(termId, MatchSensitivity.SENSITIVE);
                int sortPos2 = terms.termToSortPosition(term, MatchSensitivity.SENSITIVE);
                if (sortPos1 != sortPos2) {
                    System.out.println("SENSITIVE sortPos1 != sortPos2: " + sortPos1 + " != " + sortPos2 + " for term '" + term + "'");
                    System.out.flush();
                }
                sortPos1 = terms.idToSortPosition(termId, MatchSensitivity.INSENSITIVE);
                sortPos2 = terms.termToSortPosition(term, MatchSensitivity.INSENSITIVE);
                if (sortPos1 != sortPos2) {
                    System.out.println("INSENSITIVE sortPos1 != sortPos2: " + sortPos1 + " != " + sortPos2 + " for term '" + term + "'");
                    System.out.flush();
                }
            }
            n++;
            if (n % 100000 == 0) {
                System.out.println(n + " terms checked...");
                System.out.flush();
            }
        }
    }

    private static void report(String prompt, MutableIntSet s1, Terms terms) {
        StringBuilder values = new StringBuilder();
        for (int i : s1.toArray()) {
            values.append(i).append(" (").append(terms.get(i)).append("); ");
        }
        System.out.println(prompt + ": " + values);
        System.out.flush();
    }

}
