package nl.inl.blacklab.resultproperty;

import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermRangeQuery;
import org.apache.lucene.util.BytesRef;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.results.DocResult;
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

    DocPropertyDecade(DocPropertyDecade prop, boolean invert) {
        super(prop, invert);
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
    public int parse(String strYear) {
        int year;
        try {
            year = Integer.parseInt(strYear);
            year -= year % 10;
        } catch (NumberFormatException e) {
            year = HitPropertyDocumentDecade.UNKNOWN_VALUE;
        }
        return year;
    }
    
    public int getRaw(int docId) {
        return parse(docPropStoredField.getFirstValue(docId));
    }
    
    public int getRaw(DocResult result) {
        return parse(docPropStoredField.getFirstValue(result));
    }
    
    public PropertyValueDecade get(int docId) {
        return new PropertyValueDecade(getRaw(docId));
    }
 
    @Override
    public PropertyValueDecade get(DocResult result) {
        return new PropertyValueDecade(getRaw(result));
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
        String strYearA = docPropStoredField.getFirstValue(a);
        String strYearB = docPropStoredField.getFirstValue(b);
        if (strYearA.isEmpty()) { // sort missing year at the end
            if (strYearB.isEmpty())
                return 0;
            else
                return reverse ? -1 : 1;
        }
        if (strYearB.isEmpty()) // sort missing year at the end
            return reverse ? 1 : -1;

        int year1 = parse(strYearA);
        int year2 = parse(strYearB);
        return reverse ? year2 - year1 : year1 - year2;
    }
    
    public int compare(int docIdA, int docIdb) {
        String strYearA = docPropStoredField.getFirstValue(docIdA);
        String strYearB = docPropStoredField.getFirstValue(docIdb);
        if (strYearA.isEmpty()) { // sort missing year at the end
            if (strYearB.isEmpty())
                return 0;
            else
                return reverse ? -1 : 1;
        }
        if (strYearB.isEmpty()) // sort missing year at the end
            return reverse ? 1 : -1;

        int year1 = parse(strYearA);
        int year2 = parse(strYearB);
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
    public DocProperty reverse() {
        return new DocPropertyDecade(this, true);
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
