package nl.inl.blacklab.search.results;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import nl.inl.blacklab.resultproperty.PropertyValue;
import nl.inl.blacklab.resultproperty.ResultProperty;

/**
 * A generic group of results, with its group identity and the results themselves.
 */
public abstract class Group {
    
    protected final PropertyValue groupIdentity;

    private final Results storedResults;
    
    private final long totalSize;

    protected Group(PropertyValue groupIdentity, Results storedResults, long totalSize) {
        this.groupIdentity = groupIdentity;
        this.storedResults = storedResults;
        this.totalSize = totalSize;
    }

    public Map<ResultProperty, PropertyValue> getGroupProperties(List<? extends ResultProperty> prop) {
        List<PropertyValue> valuesForGroup = identity().valuesList();
        Map<ResultProperty, PropertyValue> properties = new LinkedHashMap<>(prop.size());
        for (int j = 0; j < prop.size(); ++j) {
            properties.put(prop.get(j), valuesForGroup.get(j));
        }
        return properties;
    }

    public PropertyValue identity() {
        return groupIdentity;
    }
    
    public Results storedResults() {
        return storedResults;
    }
    
    public long numberOfStoredResults() {
        return storedResults != null ? storedResults.size() : 0;
    }

    public long size() {
        return totalSize;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "(id=" + identity() + ", size=" + size() + ")";
    }

    public int compareTo(Group o) {
        return identity().compareTo(o.identity());
    }

}
