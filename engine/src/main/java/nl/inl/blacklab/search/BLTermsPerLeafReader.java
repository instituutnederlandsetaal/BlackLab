package nl.inl.blacklab.search;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.lucene.index.LeafReaderContext;

import nl.inl.blacklab.codec.BLTerms;

/**
 * A cache for terms objects, keyed by leafreader context.
 */
class BLTermsPerLeafReader {
    /**
     * A cache of terms objects per segment ord.
     */
    private final Map<Integer, BLTerms> termsPerSegment = new ConcurrentHashMap<>();

    /**
     * Given a leafreadercontext (index segment), return the terms object.
     *
     * @param lrc segment
     * @return terms object
     */
    public BLTerms terms(LeafReaderContext lrc) {
        return termsPerSegment.computeIfAbsent(lrc.ord, __ -> BLTerms.getTerms(lrc));
    }
}
