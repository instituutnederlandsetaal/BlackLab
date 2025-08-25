package nl.inl.blacklab.resultproperty;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.results.hitresults.ContextSize;
import nl.inl.blacklab.util.PropertySerializeUtil;

/**
 * A collection of GroupProperty's identifying a particular group.
 */
public class HitPropertyMultiple extends HitProperty implements Iterable<HitProperty> {
    
    static HitProperty deserializeProp(BlackLabIndex index, AnnotatedField field, String info, ContextSize contextSize) {
        String[] strValues = PropertySerializeUtil.splitMultiple(info);
        List<HitProperty> values = new ArrayList<>();
        for (String strValue: strValues) {
            if (!strValue.isEmpty()) {
                HitProperty prop = HitProperty.deserialize(index, field, strValue, contextSize);
                if (prop == null)
                    throw new IllegalArgumentException("Incorrect hit property string: '" + strValue + "'");
                values.add(prop);
            }
        }
        if (values.size() == 1) {
            // E.g. "docid,"
            return values.get(0);
        }
        return new HitPropertyMultiple(values);
    }

    /** The properties we're combining */
    final List<HitProperty> properties;
    
    HitPropertyMultiple(HitPropertyMultiple mprop, PropContext context, boolean invert) {
        super(mprop, context, invert);
        int n = mprop.properties.size();
        this.properties = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            HitProperty prop = mprop.properties.get(i);
            HitProperty nprop = prop.copyWith(context);
            this.properties.add(nprop);
        }
    }

    /**
     * Quick way to create group criteria. Just call this method with the
     * GroupCriterium object(s) you want.
     *
     * @param properties the desired criteria
     */
    public HitPropertyMultiple(List<HitProperty> properties) {
        this(false, properties);
    }

    /**
     * Quick way to create group criteria. Just call this method with the
     * GroupCriterium object(s) you want.
     *
     * @param reverse reverse sort?
     * @param properties the desired criteria
     */
    public HitPropertyMultiple(boolean reverse, List<HitProperty> properties) {
        this(reverse, properties.toArray(HitProperty[]::new));
    }

    /**
     * Quick way to create group criteria. Just call this method with the
     * GroupCriterium object(s) you want.
     *
     * @param properties the desired criteria
     */
    public HitPropertyMultiple(HitProperty... properties) {
        this(false, properties);
    }
    
    /**
     * Quick way to create group criteria. Just call this method with the
     * GroupCriterium object(s) you want.
     * 
     * @param reverse reverse sort? 
     * @param properties the desired criteria
     */
    public HitPropertyMultiple(boolean reverse, HitProperty... properties) {
        super();
        this.properties = new ArrayList<>(Arrays.asList(properties));
        this.reverse = reverse;
    }

    @Override
    public Class<? extends PropertyValue> getValueType() {
        return PropertyValueMultiple.class;
    }

    @Override
    public HitProperty copyWith(PropContext context, boolean invert) {
        return new HitPropertyMultiple(this, context, invert);
    }

    @Override
    public void fetchContext() {
        for (HitProperty prop: properties) {
            prop.fetchContext();
        }
    }

    @Override
    public void disposeContext() {
        for (HitProperty prop: properties) {
            prop.disposeContext();
        }
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + ((properties == null) ? 0 : properties.hashCode());
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
        HitPropertyMultiple other = (HitPropertyMultiple) obj;
        if (properties == null) {
            if (other.properties != null)
                return false;
        } else if (!properties.equals(other.properties))
            return false;
        return true;
    }

    @Override
    public String toString() {
        StringBuilder str = new StringBuilder();
        int i = 0;
        for (HitProperty prop: properties) {
            if (i > 0)
                str.append(",");
            str.append(prop.toString());
            i++;
        }
        return str.toString();
    }

    @Override
    public Iterator<HitProperty> iterator() {
        return properties.iterator();
    }

    @Override
    public PropertyValueMultiple get(long hitIndex) {
        PropertyValue[] rv = new PropertyValue[properties.size()];
        int i = 0;
        for (HitProperty crit: properties) {
            rv[i] = crit.get(hitIndex);
            i++;
        }
        return new PropertyValueMultiple(rv);
    }

    @Override
    public int compare(long indexA, long indexB) {
        for (HitProperty crit: properties) {
            int cmp = reverse ? crit.compare(indexB, indexA) : crit.compare(indexA, indexB);
            if (cmp != 0)
                return cmp;
        }
        return 0;
    }

    @Override
    public String name() {
        return properties.stream().map(HitProperty::name).collect(Collectors.joining(", "));
    }
    
    @Override
    public boolean isCompound() {
        return true;
    }
    
    @Override
    public List<HitProperty> propsList() {
        return Collections.unmodifiableList(properties);
    }

    @Override
    public String serialize() {
        return PropertySerializeUtil.serializeMultiple(reverse, properties);
    }

    @Override
    public DocProperty docPropsOnly() {
        List<DocProperty> crit = properties.stream().map(HitProperty::docPropsOnly).filter(Objects::nonNull).toList();
        if (crit.isEmpty())
            return null;
        if (crit.size() == 1) {
            return crit.get(0);
        }
        return new DocPropertyMultiple(crit);
    }

    @Override
    public PropertyValue docPropValues(PropertyValue value) {
        List<PropertyValue> result = new ArrayList<>();
        List<PropertyValue> values = value.valuesList();
        int i = 0;
        for (HitProperty prop: properties) {
            PropertyValue v = prop.docPropValues(values.get(i));
            if (v != null)
                result.add(v);
            i++;
        }
        return new PropertyValueMultiple(result);
    }

    @Override
    public boolean isDocPropOrHitText() {
        for (HitProperty p: properties) {
            if (!p.isDocPropOrHitText()) 
                return false;
        }
        return true;
    }

}
