package nl.inl.blacklab.resultproperty;

import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermRangeQuery;
import org.apache.lucene.util.BytesRef;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.results.docs.DocResult;
import nl.inl.blacklab.util.PropertySerializeUtil;

/**
 * For grouping DocResult objects by decade based on a stored field containing a
 * year.
 */
public class DocPropertyDecade extends DocProperty {

    public static final String ID = "decade";

    private final BlackLabIndex index;
    
    private final String fieldName;
    
    private final DocPropertyStoredField docPropStoredField;

    DocPropertyDecade(DocPropertyDecade prop, LeafReaderContext lrc, boolean invert) {
        super(prop, lrc, invert);
        index = prop.index;
        fieldName = prop.fieldName;
        docPropStoredField = prop.docPropStoredField;
    }

    public DocPropertyDecade(BlackLabIndex index, String fieldName) {
        this.index = index;
        this.fieldName = fieldName;
        docPropStoredField = new DocPropertyStoredField(index, fieldName);
    }

    @Override
    public Class<? extends PropertyValue> getValueType() {
        return PropertyValueInt.class;
    }

    /** Parses the value, UNKNOWN_VALUE is returned if the string is unparseable */
    public int getDecade(String strYear) {
        int year;
        try {
            year = Integer.parseInt(strYear);
            year -= year % 10;
        } catch (NumberFormatException e) {
            year = HitPropertyDocumentDecade.UNKNOWN_VALUE;
        }
        return year;
    }

    public PropertyValueDecade get(int docId) {
        return new PropertyValueDecade(getDecade(docPropStoredField.getFirstValue(docId)));
    }
 
    @Override
    public PropertyValueDecade get(DocResult result) {
        return new PropertyValueDecade(getDecade(docPropStoredField.getFirstValue(result)));
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
        return compareGeneric(docPropStoredField.getFirstValue(a), docPropStoredField.getFirstValue(b));
    }
    
    public int compare(int docIdA, int docIdb) {
        return compareGeneric(docPropStoredField.getFirstValue(docIdA), docPropStoredField.getFirstValue(docIdb));
    }

    private int compareGeneric(String strYear1, String strYear2) {
        if (strYear1.isEmpty()) { // sort missing year at the end
            if (strYear2.isEmpty())
                return 0;
            else
                return reverse ? -1 : 1;
        } else if (strYear2.isEmpty()) // sort missing year at the end
            return reverse ? 1 : -1;
        int year1 = getDecade(strYear1);
        int year2 = getDecade(strYear2);
        return reverse ? year2 - year1 : year1 - year2;
    }

    @Override
    public String name() {
        return ID;
    }

    @Override
    public String serialize() {
        return serializeReverse() + PropertySerializeUtil.combineParts(ID, fieldName);
    }

    @Override
    public DocPropertyDecade copyWith(LeafReaderContext lrc, boolean invert) {
        return new DocPropertyDecade(this, lrc, invert);
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
        DocPropertyDecade other = (DocPropertyDecade) obj;
        if (fieldName == null) {
            if (other.fieldName != null)
                return false;
        } else if (!fieldName.equals(other.fieldName))
            return false;
        return true;
    }

    @Override
    public Query query(BlackLabIndex index, PropertyValue value) {
        int decade = Integer.parseInt(value.toString());
        String lowerValue = Integer.toString(decade);
        String upperValue = Integer.toString(decade + 9);
        return new TermRangeQuery(fieldName, new BytesRef(lowerValue), new BytesRef(upperValue), true, true);
    }
    
    @Override
    public boolean canConstructQuery(BlackLabIndex index, PropertyValue value) {
        return true;
    }

}
