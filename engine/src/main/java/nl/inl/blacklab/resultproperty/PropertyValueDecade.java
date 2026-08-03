package nl.inl.blacklab.resultproperty;

import nl.inl.util.PropertySerializeUtil;

public class PropertyValueDecade extends PropertyValueInt {

    private static final String STR_UNKNOWN = "unknown";

    public PropertyValueDecade(int value) {
        super(value);
    }

    public static PropertyValue deserialize(String value) {
        if (value.equals(STR_UNKNOWN))
            return new PropertyValueDecade(HitPropertyDocumentDecade.UNKNOWN_VALUE);
        int decade;
        try {
            decade = Integer.parseInt(value);
        } catch (NumberFormatException e) {
            logger.warn("PropertyValueDecade.deserialize(): '" + value + "' is not a valid integer.");
            decade = 0;
        }
        return new PropertyValueDecade(decade);
    }

    @Override
    public String toString() {
        if (value == HitPropertyDocumentDecade.UNKNOWN_VALUE)
            return STR_UNKNOWN;
        long year = value - value % 10;
        return year + "-" + (year + 9);
    }

    @Override
    public String serialize() {
        if (value == HitPropertyDocumentDecade.UNKNOWN_VALUE)
            return STR_UNKNOWN;
        return PropertySerializeUtil.combineParts("dec", Long.toString(value));
    }
}
