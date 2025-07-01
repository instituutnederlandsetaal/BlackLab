package nl.inl.blacklab.resultproperty;

import java.io.IOException;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.NumericDocValues;
import org.apache.lucene.search.Query;

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.BlackLabIndexAbstract;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldNameUtil;
import nl.inl.blacklab.search.results.DocResult;
import nl.inl.blacklab.util.PropertySerializeUtil;
import nl.inl.util.DocValuesUtil;
import nl.inl.util.NumericDocValuesCacher;

/**
 * Retrieves the length of an annotated field (i.e. the main "contents" field) in
 * tokens.
 *
 * This EXCLUDES the extra closing token at the end!
 *
 * This class is thread-safe.
 * (using synchronization on DocValues instance; DocValues are stored for each LeafReader,
 *  and each of those should only be used from one thread at a time)
 */
public class DocPropertyAnnotatedFieldLength extends DocProperty {

    public static final String ID = "fieldlen";

    private final String fieldName;
    
    private final String friendlyName;

    /** The DocValues per segment (keyed by docBase), or null if we don't have docValues */
    private Map<Integer, NumericDocValuesCacher> docValues = null;
    
    private final BlackLabIndex index;

    DocPropertyAnnotatedFieldLength(DocPropertyAnnotatedFieldLength prop, boolean invert) {
        super(prop, invert);
        index = prop.index;
        fieldName = prop.fieldName;
        friendlyName = prop.friendlyName;
    }

    public DocPropertyAnnotatedFieldLength(BlackLabIndex index, String fieldName, String friendlyName) {
        this.index = index;
        this.fieldName = AnnotatedFieldNameUtil.lengthTokensField(fieldName);
        this.friendlyName = friendlyName;
        docValues = new TreeMap<>();
        try {
            for (LeafReaderContext rc : index.reader().leaves()) {
                LeafReader r = rc.reader();
                NumericDocValues numericDocValues = r.getNumericDocValues(this.fieldName);
                if (numericDocValues == null) {
                    // (should never happen)
                    throw new UnsupportedOperationException("no DocValues available");
                }
                if (numericDocValues != null) {
                    docValues.put(rc.docBase, DocValuesUtil.cacher(numericDocValues));
                }
            }
            if (docValues.isEmpty()) {
                // We don't actually have DocValues.
                docValues = null;
            }
        } catch (IOException e) {
            throw BlackLabRuntimeException.wrap(e);
        }
    }

    public DocPropertyAnnotatedFieldLength(BlackLabIndex index, String fieldName) {
        this(index, fieldName, fieldName + " length");
    }

    @Override
    public Class<? extends PropertyValue> getValueType() {
        return PropertyValueInt.class;
    }

    public long get(int docId) {
        if (docValues != null) {
            // Find the value in the correct segment
            Entry<Integer, NumericDocValuesCacher> prev = null;
            for (Entry<Integer, NumericDocValuesCacher> e : docValues.entrySet()) {
                Integer docBase = e.getKey();
                if (docBase > docId) {
                    // Previous segment (the highest docBase lower than docId) is the right one
                    Integer prevDocBase = prev.getKey();
                    NumericDocValuesCacher prevDocValues = prev.getValue();
                    return prevDocValues.get(docId - prevDocBase) - BlackLabIndexAbstract.IGNORE_EXTRA_CLOSING_TOKEN;
                }
                prev = e;
            }
            // Last segment is the right one
            Integer prevDocBase = prev.getKey();
            NumericDocValuesCacher prevDocValues = prev.getValue();
            return prevDocValues.get(docId - prevDocBase) - BlackLabIndexAbstract.IGNORE_EXTRA_CLOSING_TOKEN;
        }
        
        // Not cached; find value by reading stored value from Document now
        return Long.parseLong(index.luceneDoc(docId).get(fieldName)) - BlackLabIndexAbstract.IGNORE_EXTRA_CLOSING_TOKEN;
    }

    private long get(PropertyValueDoc identity) {
        return get(identity.value());
    }

    @Override
    public PropertyValueInt get(DocResult result) {
        return new PropertyValueInt(get(result.identity().value()));
    }

    /**
     * Compares two docs on this property
     * 
     * @param a first doc
     * @param b second doc
     * @return 0 if equal, negative if a &lt; b, positive if a &gt; b.
     */
    @Override
    public int compare(DocResult a, DocResult b) {
        long ia = get(a.identity().value());
        long ib = get(b.identity().value());
        return reverse ? Long.compare(ib, ia) : Long.compare(ia, ib);
    }

    @Override
    public String name() {
        return friendlyName;
    }

    @Override
    public String serialize() {
        return serializeReverse() + PropertySerializeUtil.combineParts(ID, fieldName);
    }

    @Override
    public DocProperty reverse() {
        return new DocPropertyAnnotatedFieldLength(this, true);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + ((fieldName == null) ? 0 : fieldName.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!super.equals(obj))
            return false;
        if (getClass() != obj.getClass())
            return false;
        DocPropertyAnnotatedFieldLength other = (DocPropertyAnnotatedFieldLength) obj;
        if (fieldName == null) {
            if (other.fieldName != null)
                return false;
        } else if (!fieldName.equals(other.fieldName))
            return false;
        return true;
    }

    @Override
    public Query query(BlackLabIndex index, PropertyValue value) {
        return null;
    }

}
