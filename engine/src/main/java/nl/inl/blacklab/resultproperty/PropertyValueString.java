package nl.inl.blacklab.resultproperty;

import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import com.ibm.icu.text.CollationKey;

import nl.inl.blacklab.util.PropertySerializeUtil;

public class PropertyValueString extends PropertyValue {

    private static final String MULTIPLE_VALUES_DELIMITER = " · ";

    public static String joinValues(String[] values) {
        return StringUtils.join(values, MULTIPLE_VALUES_DELIMITER);
    }

    /** Convert an array of string values to a PropertyValueString. */
    public static PropertyValueString fromArray(String[] values, Map<String, CollationKey> collationCache) {
        if (values.length == 1)
            return new PropertyValueString(values[0], collationCache);
        return new PropertyValueString(joinValues(values), collationCache);
    }

    private final CollationKey collationKey;

    public PropertyValueString(String value, Map<String, CollationKey> collationCache) {
        this.collationKey = collationCache == null ? PropertyValue.collator.getCollationKey(value) :
                collationCache.computeIfAbsent(value, PropertyValue.collator::getCollationKey);
    }

    @Override
    public String value() {
        return collationKey.getSourceString();
    }

    @Override
    public int compareTo(Object o) {
        return collationKey.compareTo(((PropertyValueString)o).collationKey);
    }

    @Override
    public int hashCode() {
        return collationKey.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this)
            return true;
        if (obj instanceof PropertyValueString) {
            return collationKey.equals(((PropertyValueString) obj).collationKey);
        }
        return false;
    }

    @Override
    public String toString() {
        return value();
    }

    @Override
    public String serialize() {
        return PropertySerializeUtil.combineParts("str", value());
    }

    public int length() {
        return value().length();
    }
    
    public boolean isEmpty() {
        return value().isEmpty();
    }
}
