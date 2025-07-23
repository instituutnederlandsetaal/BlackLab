package nl.inl.blacklab.resultproperty;

import java.util.Comparator;

import nl.inl.blacklab.search.results.HitGroup;
import nl.inl.blacklab.util.PropertySerializeUtil;

/**
 * Abstract base class for a property of a hit, like document title, hit text,
 * right context, etc.
 */
public abstract class HitGroupProperty extends GroupProperty implements Comparator<HitGroup> {

    HitGroupProperty(HitGroupProperty prop, boolean invert) {
        super(prop, invert);
    }

    protected HitGroupProperty() {
        super();
    }

    public abstract PropertyValue get(HitGroup result);

    /**
     * Compares two groups on this property
     *
     * @param a first group
     * @param b second group
     * @return 0 if equal, negative if a < b, positive if a > b.
     */
    @Override
    public abstract int compare(HitGroup a, HitGroup b);

    /**
     * Used by subclasses to add a dash for reverse when serializing
     *
     * @return either a dash or the empty string
     */
    @Override
    public String serializeReverse() {
        return reverse ? "-" : "";
    }

    public static HitGroupProperty deserialize(String serialized) {
        if (PropertySerializeUtil.isMultiple(serialized)) {
            boolean reverse = false;
            if (serialized.startsWith("-(") && serialized.endsWith(")")) {
                reverse = true;
                serialized = serialized.substring(2, serialized.length() - 1);
            }
            HitGroupProperty result = HitGroupPropertyMultiple.deserializeProp(serialized);
            if (reverse)
                result = result.reverse();
            return result;
        }

        boolean reverse = false;
        if (!serialized.isEmpty() && serialized.charAt(0) == '-') {
            reverse = true;
            serialized = serialized.substring(1);
        }
        String propName = ResultProperty.ignoreSensitivity(serialized);
        HitGroupProperty result;
        if (propName.equalsIgnoreCase(HitGroupPropertyIdentity.ID))
            result = HitGroupPropertyIdentity.get();
        else
            result = HitGroupPropertySize.get();
        if (reverse)
            result = result.reverse();
        return result;
    }

    /**
     * Is the comparison reversed?
     *
     * @return true if it is, false if not
     */
    @Override
    public boolean isReverse() {
        return reverse;
    }

    /**
     * Reverse the comparison.
     *
     * @return doc group property with reversed comparison
     */
    @Override
    public abstract HitGroupProperty reverse();

    @Override
    public String toString() {
        return serialize();
    }

}
