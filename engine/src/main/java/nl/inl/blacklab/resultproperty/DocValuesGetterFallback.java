package nl.inl.blacklab.resultproperty;

import java.io.IOException;
import java.util.Set;

import org.apache.lucene.index.LeafReaderContext;

import nl.inl.blacklab.exceptions.InvalidIndex;
import nl.inl.blacklab.search.BlackLabIndex;

/**
 * We don't have DocValues. Just get them from the document.
 */
class DocValuesGetterFallback implements DocValuesGetter {

    BlackLabIndex index;

    LeafReaderContext lrc;

    String fieldName;

    public DocValuesGetterFallback(BlackLabIndex index, LeafReaderContext lrc, String fieldName) {
        this.index = index;
        this.lrc = lrc;
        this.fieldName = fieldName;
    }

    @Override
    public String[] get(int docId) {
        try {
            // We don't have DocValues for this field; just get the property from the document.
            if (lrc == null)
                return index.reader().storedFields().document(docId, Set.of(fieldName)).getValues(fieldName);
            else
                return lrc.reader().storedFields().document(docId, Set.of(fieldName)).getValues(fieldName);
        } catch (IOException e) {
            throw new InvalidIndex(e);
        }
    }

    @Override
    public boolean isValid() {
        return true;
    }
}
