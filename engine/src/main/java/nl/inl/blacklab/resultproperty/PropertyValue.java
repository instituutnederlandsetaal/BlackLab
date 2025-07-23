package nl.inl.blacklab.resultproperty;

import java.text.Collator;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import nl.inl.blacklab.search.BlackLab;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.results.Hits;
import nl.inl.blacklab.util.PropertySerializeUtil;

/**
 * A concrete value of a HitProperty of a Hit
 */
public abstract class PropertyValue implements Comparable<Object> {
    protected static final Logger logger = LogManager.getLogger(PropertyValue.class);

    /** String to use to indicate there was no value.
     *
     * For example: you're grouping by the word left of the match, but the
     * match occurred at the start of the document.
     */
    public static final String NO_VALUE_STR = "(no value)";

    /**
     * Collator to use for string comparison while sorting/grouping
     */
    public static final Collator collator = BlackLab.defaultCollator();

    /**
     * Convert the String representation of a HitPropValue back into the
     * HitPropValue
     * 
     * @param hits our hits object
     * @param serialized the serialized object
     * @return the HitPropValue object, or null if it could not be deserialized
     */
    public static PropertyValue deserialize(Hits hits, String serialized) {
        return deserialize(hits.queryInfo().index(), hits.queryInfo().field(), serialized);
    }
    
    /**
     * Convert the String representation of a HitPropValue back into the
     * HitPropValue
     * 
     * @param index our index 
     * @param field field we're searching
     * @param serialized the serialized object
     * @return the HitPropValue object, or null if it could not be deserialized
     */
    public static PropertyValue deserialize(BlackLabIndex index, AnnotatedField field, String serialized) {
        if (serialized == null || serialized.isEmpty())
            return null;

        if (PropertySerializeUtil.isMultiple(serialized))
            return PropertyValueMultiple.deserialize(index, field, serialized);

        List<String> parts = PropertySerializeUtil.splitPartsList(serialized);
        String type = parts.get(0).toLowerCase();
        List<String> infos = parts.subList(1, parts.size());
        return switch (type) {
            case "cws", "cwsr" -> // cwsr (context words, reverse order. e.g. left context)
                    PropertyValueContextWords.deserialize(index, field, infos, type.equals("cwsr"));
            case "dec" -> PropertyValueDecade.deserialize(infos.isEmpty() ? "unknown" : infos.get(0));
            case "int" -> PropertyValueInt.deserialize(infos.isEmpty() ? "-1" : infos.get(0));
            case "str" -> new PropertyValueString(infos.isEmpty() ? "" : infos.get(0));
            case "doc" -> PropertyValueDoc.deserialize(index, infos.isEmpty() ? "NO_DOC_ID_SPECIFIED" : infos.get(0));
            default -> {
                logger.debug("Unknown HitPropValue '" + type + "'");
                yield null;
            }
        };
    }
    
    @Override
    public abstract int compareTo(Object o);

    @Override
    public abstract int hashCode();

    @Override
    public abstract boolean equals(Object obj);

    /**
     * Convert the object to a String representation, for use in e.g. URLs.
     * 
     * @return the serialized object
     */
    public abstract String serialize();

    @Override
    public abstract String toString();

    /**
     * Return the list of values.
     *
     * If this is PropertValueMultiple, the list will contain multiple items,
     * otherwise just 1.
     *
     * @return list of values
     */
    public List<PropertyValue> valuesList() {
        return List.of(this);
    }

    public List<String> propValues() {
        return List.of(toString());
    }
    
    public abstract Object value();
}
