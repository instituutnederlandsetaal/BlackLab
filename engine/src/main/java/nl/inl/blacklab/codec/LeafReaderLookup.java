package nl.inl.blacklab.codec;

import org.apache.lucene.index.LeafReaderContext;

public interface LeafReaderLookup {
    LeafReaderContext forId(int id);
}
