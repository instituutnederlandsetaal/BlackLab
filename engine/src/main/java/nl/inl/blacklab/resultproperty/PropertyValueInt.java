package nl.inl.blacklab.resultproperty;

import nl.inl.blacklab.util.PropertySerializeUtil;

public class PropertyValueInt extends PropertyValue {
    final long value;

    @Override
    public Long value() {
        return value;
    }

    public PropertyValueInt(long value) {
        this.value = value;
    }

    @Override
    public int compareTo(Object o) {
        return Long.compare(value, ((PropertyValueInt) o).value);
    }

    @Override
    public int hashCode() {
        return Long.hashCode(value);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this)
            return true;
        if (obj instanceof PropertyValueInt) {
            return value == ((PropertyValueInt) obj).value;
        }
        return false;
    }

    public static PropertyValue deserialize(String value) {
        int v;
        try {
            v = Integer.parseInt(value);
        } catch (NumberFormatException e) {
            logger.warn("PropertyValueInt.deserialize(): '" + value + "' is not a valid integer.");
            v = 0;
        }
        return new PropertyValueInt(v);
    }

    @Override
    public String toString() {
        return Long.toString(value);
    }

    @Override
    public String serialize() {
        return PropertySerializeUtil.combineParts("int", Long.toString(value));
    }
}
