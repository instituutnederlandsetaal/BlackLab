package nl.inl.blacklab.resultproperty;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BooleanQuery.Builder;
import org.apache.lucene.search.Query;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.results.DocResult;
import nl.inl.blacklab.util.PropertySerializeUtil;

/**
 * A collection of GroupProperty's identifying a particular group.
 */
public class DocPropertyMultiple extends DocProperty implements Iterable<DocProperty> {
    final List<DocProperty> criteria;

    DocPropertyMultiple(DocPropertyMultiple mprop, boolean invert) {
        super(mprop, invert);
        this.criteria = mprop.criteria;
    }

    @Override
    public Class<? extends PropertyValue> getValueType() {
        return PropertyValueMultiple.class;
    }

    /**
     * Quick way to create group criteria. Just call this method with the
     * GroupCriterium object(s) you want.
     *
     * @param criteria the desired criteria
     */
    public DocPropertyMultiple(List<DocProperty> criteria) {
        this.criteria = new ArrayList<>(criteria);
    }

    /**
     * Quick way to create group criteria. Just call this method with the
     * GroupCriterium object(s) you want.
     *
     * @param criteria the desired criteria
     */
    public DocPropertyMultiple(DocProperty... criteria) {
        this.criteria = new ArrayList<>(Arrays.asList(criteria));
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + ((criteria == null) ? 0 : criteria.hashCode());
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
        DocPropertyMultiple other = (DocPropertyMultiple) obj;
        if (criteria == null) {
            if (other.criteria != null)
                return false;
        } else if (!criteria.equals(other.criteria))
            return false;
        return true;
    }

    @Override
    public String toString() {
        StringBuilder str = new StringBuilder();
        int i = 0;
        for (DocProperty prop : criteria) {
            if (i > 0)
                str.append(",");
            str.append(prop.toString());
            i++;
        }
        return str.toString();
    }

    @Override
    public Iterator<DocProperty> iterator() {
        return criteria.iterator();
    }

    @Override
    public PropertyValueMultiple get(DocResult result) {
        PropertyValue[] rv = new PropertyValue[criteria.size()];
        int i = 0;
        for (DocProperty crit : criteria) {
            rv[i] = crit.get(result);
            i++;
        }
        return new PropertyValueMultiple(rv);
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
        for (DocProperty crit : criteria) {
            int cmp = reverse ? crit.compare(b, a) : crit.compare(a, b);
            if (cmp != 0)
                return cmp;
        }
        return 0;
    }

    @Override
    public String name() {
        return criteria.stream().map(DocProperty::name).collect(Collectors.joining(", "));
    }

    @Override
    public String serialize() {
        return PropertySerializeUtil.serializeMultiple(reverse, criteria);
    }
    
    @Override
    public boolean isCompound() {
        return true;
    }
    
    @Override
    public List<DocProperty> props() {
        return Collections.unmodifiableList(criteria);
    }

    public static DocPropertyMultiple deserialize(BlackLabIndex index, String info) {
        String[] strValues = PropertySerializeUtil.splitMultiple(info);
        DocProperty[] values = new DocProperty[strValues.length];
        int i = 0;
        for (String strValue : strValues) {
            values[i] = DocProperty.deserialize(index, strValue);
            i++;
        }
        return new DocPropertyMultiple(values);
    }

    @Override
    public DocProperty reverse() {
        return new DocPropertyMultiple(this, true);
    }

    @Override
    public Query query(BlackLabIndex index, PropertyValue value) {
        PropertyValue[] values = ((PropertyValueMultiple)value).value();
        Builder bqb = new BooleanQuery.Builder();
        int n = 0;
        for (int i = 0; i < criteria.size(); i++) {
            Query q = criteria.get(i).query(index, values[i]);
            if (q != null) {
                bqb.add(q, Occur.MUST);
                n++;
            }
        }
        if (n > 0)
            return bqb.build();
        return null;
    }
    
    @Override
    public boolean canConstructQuery(BlackLabIndex index, PropertyValue value) {
        PropertyValue[] values = ((PropertyValueMultiple)value).value();
        for (int i = 0; i < criteria.size(); i++) {
            if (!criteria.get(i).canConstructQuery(index, values[i]))
                return false;
        }
        return true;
    }
    
}
