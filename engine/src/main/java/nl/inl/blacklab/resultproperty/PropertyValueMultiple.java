package nl.inl.blacklab.resultproperty;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.util.PropertySerializeUtil;

public class PropertyValueMultiple extends PropertyValue {
    final PropertyValue[] value;

    public PropertyValueMultiple(PropertyValue... value) {
        this.value = value;
    }

    public PropertyValueMultiple(List<PropertyValue> result) {
        this.value = result.toArray(new PropertyValue[0]);
    }

    @Override
    public PropertyValue[] value() {
        return value;
    }

    @Override
    public int compareTo(Object o) {
        return compareHitPropValueArrays(value, ((PropertyValueMultiple) o).value);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(value);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj instanceof PropertyValueMultiple)
            return Arrays.equals(value, ((PropertyValueMultiple) obj).value);
        return false;
    }

    public static PropertyValueMultiple deserialize(BlackLabIndex index, AnnotatedField field, String info) {
        String[] strValues = PropertySerializeUtil.splitMultiple(info);
        PropertyValue[] values = new PropertyValue[strValues.length];
        int i = 0;
        for (String strValue : strValues) {
            values[i] = PropertyValue.deserialize(index, field, strValue);
            i++;
        }
        return new PropertyValueMultiple(values);
    }

    @Override
    public String toString() {
        return StringUtils.join(value, " / ");
    }

    @Override
    public String serialize() {
        String[] valuesSerialized = new String[value.length];
        for (int i = 0; i < value.length; i++) {
            valuesSerialized[i] = value[i].serialize();
        }
        return PropertySerializeUtil.combineMultiple(valuesSerialized);
    }

    @Override
    public List<PropertyValue> valuesList() {
        return List.of(value);
    }

    public List<String> propValues() {
        List<String> l = new ArrayList<>();
        for (PropertyValue v: valuesList())
            l.addAll(v.propValues());
        return l;
    }

    /**
     * Compare two arrays of PropertyValue objects, by comparing each one in
     * succession.
     *
     * The first difference encountered determines the result. If the arrays are of
     * different length but otherwise equal, the longest array will be ordered after
     * the shorter.
     *
     * @param a first array
     * @param b second array
     * @return 0 if equal, negative if a &lt; b, positive if a &gt; b
     */
    private static int compareHitPropValueArrays(PropertyValue[] a, PropertyValue[] b) {
        int n = Math.min(a.length, b.length); // min
        for (int i = 0; i < n; i++) {
            // Does this element decide the comparison?
            int cmp = a[i].compareTo(b[i]);
            if (cmp != 0) {
                return cmp; // yep, done
            }
        }
        return a.length - b.length; // sort short arrays before long arrays when all values up to the trailing values in the longer array are the same
    }
}
