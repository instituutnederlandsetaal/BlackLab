package nl.inl.blacklab.resultproperty;

import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.search.Query;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.BlackLabIndexAbstract;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldNameUtil;
import nl.inl.blacklab.search.results.docs.DocResult;
import nl.inl.blacklab.util.PropertySerializeUtil;

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

    private final String lengthTokensFieldName;
    
    private final String friendlyName;

    /** The DocValues per segment (keyed by docBase), or null if we don't have docValues */
    private DocValuesGetter docValuesGetter;

    private final BlackLabIndex index;

    DocPropertyAnnotatedFieldLength(DocPropertyAnnotatedFieldLength prop, LeafReaderContext lrc, boolean invert) {
        super(prop, lrc, invert);
        index = prop.index;
        lengthTokensFieldName = prop.lengthTokensFieldName;
        friendlyName = prop.friendlyName;
        docValuesGetter = (lrc == null || lrc == prop.lrc) ? prop.docValuesGetter : DocValuesGetter.get(index, lrc,
                lengthTokensFieldName);
    }

    public DocPropertyAnnotatedFieldLength(BlackLabIndex index, String annotatedFieldName) {
        this.index = index;
        this.lengthTokensFieldName = AnnotatedFieldNameUtil.lengthTokensField(annotatedFieldName);
        this.friendlyName = annotatedFieldName + " length";
        docValuesGetter = DocValuesGetter.get(index, lrc, this.lengthTokensFieldName);
    }

    @Override
    public Class<? extends PropertyValue> getValueType() {
        return PropertyValueInt.class;
    }

    public long get(int docId) {
        return docValuesGetter.getLong(docId) - BlackLabIndexAbstract.IGNORE_EXTRA_CLOSING_TOKEN;
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
     * @return 0 if equal, negative if a < b, positive if a > b.
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
        return serializeReverse() + PropertySerializeUtil.combineParts(ID, lengthTokensFieldName);
    }

    @Override
    public DocPropertyAnnotatedFieldLength copyWith(LeafReaderContext lrc, boolean invert) {
        return new DocPropertyAnnotatedFieldLength(this, lrc, invert);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + ((lengthTokensFieldName == null) ? 0 : lengthTokensFieldName.hashCode());
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
        if (lengthTokensFieldName == null) {
            if (other.lengthTokensFieldName != null)
                return false;
        } else if (!lengthTokensFieldName.equals(other.lengthTokensFieldName))
            return false;
        return true;
    }

    @Override
    public Query query(BlackLabIndex index, PropertyValue value) {
        return null;
    }

}
