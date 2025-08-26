package nl.inl.blacklab.resultproperty;

/**
 * Abstract base class for a property of a group op results.
 */
public abstract class GroupProperty implements ResultProperty {

    /** Reverse comparison result or not? */
    protected final boolean reverse;
    
    GroupProperty(GroupProperty prop, boolean invert) {
        this.reverse = invert ? !prop.reverse : prop.reverse;
    }

    protected GroupProperty() {
        this.reverse = sortDescendingByDefault();
    }

    /**
     * Is the default for this property to sort descending?
     * 
     * This is usually a good default for "group size" or "number of hits".
     * 
     * @return whether to sort descending by default
     */
    protected boolean sortDescendingByDefault() {
        return false;
    }

    @Override
    public abstract String serialize();

    /**
     * Used by subclasses to add a dash for reverse when serializing
     * 
     * @return either a dash or the empty string
     */
    @Override
    public String serializeReverse() {
        return reverse ? "-" : "";
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
     * @return reversed group property 
     */
    @Override
    public abstract GroupProperty reverse();

    @Override
    public String toString() {
        return serialize();
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (reverse ? 1231 : 1237);
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        GroupProperty other = (GroupProperty) obj;
        if (reverse != other.reverse)
            return false;
        return true;
    }

}
