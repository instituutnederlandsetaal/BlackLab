package nl.inl.blacklab.resultproperty;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import nl.inl.blacklab.util.PropertySerializeUtil;

/**
 * Property of some result (i.e. hit, group, groupOfGroups)
 * @param <T> type of result
 */
public interface ResultProperty<T> extends Serializable, PropertySerializeUtil.SerializableProperty {

    /**
     * Strip sensitivity information.
     * 
     * When deserializing, we ignore sensitivity option for non-context properties.
     * Eventually, we will implement this for these properties.
     * 
     * @param info input string
     * @return stripped string
     */
    static String ignoreSensitivity(String info) {
        if (info.endsWith(":s") || info.endsWith(":i"))
            return info.substring(0, info.length() - 2);
        return info;
    }

    /**
     * Get the name of the property
     * @return name
     */
    String name();

    /**
     * Serialize this HitProperty so we can deserialize it later (to pass it via
     * URL, for example)
     * 
     * @return the String representation of this HitProperty
     */
    @Override
    String serialize();

    /**
     * Reverse the sort order of this hit property.
     * 
     * @return a new hit property with the sort order reversed
     */
    ResultProperty<T> reverse();

    /**
     * Is the comparison reversed?
     * 
     * @return true if it is, false if not
     */
    boolean isReverse();

    @Override
    String toString();

    /**
     * Get the names of all (sub-)properties separately.
     * 
     * @return the list
     */
    default List<String> propNames() {
        List<String> names = new ArrayList<>();
        if (isCompound()) {
            propsList().forEach(prop -> names.addAll(prop.propNames()));
        } else {
            names.add(serializeReverse() + name());
        }
        return names;
    }
    
    @Override
    int hashCode();
    
    @Override
    boolean equals(Object obj);

    default boolean isCompound() {
        return false;
    }

    /**
     * Return the list of properties.
     *
     * If this is ResultPropertyMultiple, the list will contain multiple items,
     * otherwise just 1.
     *
     * @return list of properties
     */
    default List<? extends ResultProperty<T>> propsList() {
        return List.of(this);
    }

    String serializeReverse();

    /** Prepare this property to be used for sorting/grouping. Can e.g. batch-load required context for given hits.
     *
     * This is called automatically when the context is needed. To avoid holding on to memory, code that uses the
     * property (e.g. to sort, group or filter) should call disposeContext() when it's done with the property.
     * The regular BlackLab methods should do this automatically.
     */
    default void fetchContext() {}

    /** Dispose of previously fetched context (to free up memory) */
    default void disposeContext() {}

    /** The type of value this property produces.
     *
     * Used to optimize sorting (e.g. precalculate CollationKeys).
     *
     * @return the type of value this will yield
     */
    Class<? extends PropertyValue> getValueType();
}
