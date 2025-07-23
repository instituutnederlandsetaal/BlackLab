package nl.inl.blacklab.resultproperty;

import java.util.Comparator;

import nl.inl.blacklab.search.results.DocGroup;

/**
 * Abstract base class for a property of a hit, like document title, hit text,
 * right context, etc.
 */
public abstract class DocGroupProperty extends GroupProperty implements Comparator<DocGroup> {

    public static DocGroupProperty deserialize(String serialized) {
        if (serialized == null || serialized.isEmpty())
            return null;
        boolean reverse = false;
        if (serialized.charAt(0) == '-') {
            reverse = true;
            serialized = serialized.substring(1);
        }
        String propName = ResultProperty.ignoreSensitivity(serialized);
        DocGroupProperty result;
        if (propName.equalsIgnoreCase(DocGroupPropertyIdentity.ID))
            result = DocGroupPropertyIdentity.get();
        else
            result = DocGroupPropertySize.get();
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

    public abstract PropertyValue get(DocGroup result);

    /**
     * Compares two groups on this property
     * 
     * @param a first group
     * @param b second group
     * @return 0 if equal, negative if a < b, positive if a > b.
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

}
