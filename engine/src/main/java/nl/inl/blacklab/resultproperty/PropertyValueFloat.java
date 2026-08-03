package nl.inl.blacklab.resultproperty;

import nl.inl.util.PropertySerializeUtil;

public class PropertyValueFloat extends PropertyValue {
    final double value;

    @Override
    public Double value() {
        return value;
    }

    public PropertyValueFloat(double value) {
        this.value = value;
    }

    @Override
    public int compareTo(Object o) {
        return Double.compare(value, ((PropertyValueFloat) o).value);
    }

    @Override
    public int hashCode() {
        return Double.hashCode(value);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this)
            return true;
        if (obj instanceof PropertyValueFloat) {
            return value == ((PropertyValueFloat) obj).value;
        }
        return false;
    }

    public static PropertyValue deserialize(String value) {
        double v;
        try {
            v = Double.parseDouble(value);
        } catch (NumberFormatException e) {
            logger.warn("PropertyValueInt.deserialize(): '" + value + "' is not a valid integer.");
            v = 0;
        }
        return new PropertyValueFloat(v);
    }

    @Override
    public String toString() {
        return Double.toString(value);
    }

    @Override
    public String serialize() {
        return PropertySerializeUtil.combineParts("float", Double.toString(value));
    }
}
