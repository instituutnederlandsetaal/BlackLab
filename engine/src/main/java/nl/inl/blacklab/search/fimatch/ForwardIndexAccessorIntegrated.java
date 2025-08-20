package nl.inl.blacklab.search.fimatch;

import java.util.ArrayList;
import java.util.List;

import org.apache.lucene.index.LeafReaderContext;

import net.jcip.annotations.NotThreadSafe;
import net.jcip.annotations.ThreadSafe;
import nl.inl.blacklab.codec.BlackLabPostingsReader;
import nl.inl.blacklab.forwardindex.AnnotForwardIndex;
import nl.inl.blacklab.forwardindex.Terms;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.BlackLabIndexAbstract;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.lucene.DocFieldLengthGetter;

/**
 * Allows the forward index matching subsystem to access the forward indices,
 * including an easy and fast way to read any annotation at any position from a
 * document.
 */
@ThreadSafe
public class ForwardIndexAccessorIntegrated extends ForwardIndexAccessorAbstract {

    public ForwardIndexAccessorIntegrated(BlackLabIndex index, AnnotatedField searchField) {
        super(index, searchField);
    }

    @Override
    public ForwardIndexAccessorLeafReader getForwardIndexAccessorLeafReader(LeafReaderContext readerContext) {
        return new ForwardIndexAccessorLeafReaderIntegrated(readerContext);
    }

    /**
     * Forward index accessor for a single LeafReader.
     *
     * Not thread-safe (only used from Spans).
     */
    @NotThreadSafe
    class ForwardIndexAccessorLeafReaderIntegrated implements ForwardIndexAccessorLeafReader {

        protected final LeafReaderContext readerContext;

        private final BlackLabPostingsReader postingsReader;

        private final DocFieldLengthGetter lengthGetter;

        private final List<AnnotForwardIndex> fiPerSegment = new ArrayList<>();

        ForwardIndexAccessorLeafReaderIntegrated(LeafReaderContext readerContext) {
            this.readerContext = readerContext;
            postingsReader = BlackLabPostingsReader.forSegment(readerContext);
            for (String luceneField: luceneFields) {
                AnnotForwardIndex e = postingsReader.forwardIndex(luceneField);
                fiPerSegment.add(e);
            }
            lengthGetter = new DocFieldLengthGetter(readerContext.reader(), annotatedField.name());
        }

        @Override
        public ForwardIndexDocument getForwardIndexDoc(int segmentDocId) {
            return new ForwardIndexDocumentImpl(this, segmentDocId);
        }

        @Override
        public int getDocLength(int segmentDocId) {
            // NOTE: we subtract one because we always have an "extra closing token" at the end that doesn't
            //       represent a word, just any closing punctuation after the last word.
            return lengthGetter.getFieldLength(segmentDocId)
                    - BlackLabIndexAbstract.IGNORE_EXTRA_CLOSING_TOKEN;
        }

        @Override
        public int[] getChunkSegmentTermIds(int annotIndex, int segmentDocId, int start, int end) {
            return fiPerSegment.get(annotIndex).retrievePart(segmentDocId, start, end);
        }

        @Override
        public int getNumberOfAnnotations() {
            return ForwardIndexAccessorIntegrated.this.numberOfAnnotations();
        }

        @Override
        public Terms terms(int annotIndex) {
            if (annotIndex < 0 || annotIndex >= fiPerSegment.size())
                throw new IllegalArgumentException("Invalid annotation index: " + annotIndex +
                        " (there are " + fiPerSegment.size() + " annotations)");
            return fiPerSegment.get(annotIndex).terms();
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof ForwardIndexAccessorIntegrated that))
            return false;
        return index.equals(that.index) && annotatedField.equals(that.annotatedField);
    }

}
