package nl.inl.blacklab.resultproperty;

import org.apache.lucene.index.LeafReaderContext;

import nl.inl.blacklab.search.BlackLabIndex;

interface DocValuesGetter {
    static DocValuesGetter get(BlackLabIndex index, LeafReaderContext lrc, String fieldName) {
        DocValuesGetter getter = lrc == null ?
                new DocValuesGetterGlobal(index, fieldName) :
                new DocValuesGetterSegment(index, lrc, fieldName);
        return getter.isValid() ? getter : new DocValuesGetterFallback(index, lrc, fieldName);
    }

    String[] get(int docId);

    default long getLong(int docId) {
        String[] values = get(docId);
        return values.length == 0 ? 0 : Long.parseLong(values[0]);
    }

    boolean isValid();
}
