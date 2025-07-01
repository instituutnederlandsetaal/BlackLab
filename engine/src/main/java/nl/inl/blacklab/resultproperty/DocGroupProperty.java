package nl.inl.blacklab.resultproperty;

import java.util.List;

import nl.inl.blacklab.search.results.DocGroup;
import nl.inl.blacklab.search.results.DocResult;

/**
 * Abstract base class for a property of a hit, like document title, hit text,
 * right context, etc.
 */
public abstract class DocGroupProperty extends GroupProperty<DocResult, DocGroup> {

    static final DocGroupPropertyIdentity propIdentity = new DocGroupPropertyIdentity();

    static final DocGroupPropertySize propSize = new DocGroupPropertySize();

    public static DocGroupPropertyIdentity identity() {
        return propIdentity;
    }

    public static DocGroupPropertySize size() {
        return propSize;
    }
    
    public static DocGroupProperty deserialize(String serialized) {
        if (serialized == null || serialized.isEmpty())
            return null;
        boolean reverse = false;
        if (serialized.length() > 0 && serialized.charAt(0) == '-') {
            reverse = true;
            serialized = serialized.substring(1);
        }
        String propName = ResultProperty.ignoreSensitivity(serialized);
        DocGroupProperty result;
        if (propName.equalsIgnoreCase(DocGroupPropertyIdentity.ID))
            result = propIdentity;
        else
            result = propSize;
        if (reverse)
            result = result.reverse();
        return result;
    }

    protected DocGroupProperty(DocGroupProperty prop, boolean invert) {
        super(prop, invert);
    }
    
    protected DocGroupProperty() {
        super();
    }

    @Override
    public abstract PropertyValue get(DocGroup result);

    /**
     * Compares two groups on this property
     * 
     * @param a first group
     * @param b second group
     * @return 0 if equal, negative if a &lt; b, positive if a &gt; b.
     */
    @Override
    public abstract int compare(DocGroup a, DocGroup b);

    @Override
    public abstract String serialize();

    /**
     * Reverse the comparison.
     * 
     * @return doc group property with reversed comparison 
     */
    @Override
    public abstract DocGroupProperty reverse();

    @Override
    public String toString() {
        return serialize();
    }

    @Override
    public List<DocGroupProperty> props() {
        return null;
    }
}
