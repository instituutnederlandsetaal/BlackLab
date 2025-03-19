package nl.inl.blacklab.resultproperty;

import nl.inl.blacklab.util.PropertySerializeUtil;

public class PropertyValueString extends PropertyValue {
    /** Indicates no value was found (i.e. group by capture, but the capture wasn't found for this hit) */
    public static final PropertyValueString NO_VALUE = new PropertyValueString(NO_VALUE_STR);

    final String value;

    public PropertyValueString(String value) {
        this.value = value == null ? "" : value;
    }

    @Override
    public String value() {
        return value;
    }

    @Override
    public int compareTo(Object o) {
        return PropertyValue.collator.compare(value, ((PropertyValueString) o).value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this)
            return true;
        if (obj instanceof PropertyValueString) {
            return value.equals(((PropertyValueString) obj).value);
        }
        return false;
    }

    @Override
    public String toString() {
        return value;
    }

    @Override
    public String serialize() {
        return PropertySerializeUtil.combineParts("str", value);
    }

    public int length() {
        return value.length();
    }
    
    public boolean isEmpty() {
        return value.isEmpty();
    }
}
