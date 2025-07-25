package nl.inl.blacklab.search.indexmetadata;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.xml.bind.annotation.XmlTransient;
import nl.inl.blacklab.exceptions.InvalidIndex;
import nl.inl.blacklab.search.BlackLabIndex;

public abstract class FieldImpl implements Field, Freezable {
    /** Field's name */
    @XmlTransient
    protected String fieldName;

    @XmlTransient
    protected BlackLabIndex index;

    /** Does the field have an associated content store? */
    @JsonProperty("hasContentStore")
    protected boolean contentStore;

    /** Custom field properties */
    protected CustomPropsMap custom = new CustomPropsMap();

    /**
     * If true, this instance is frozen and may not be mutated anymore.
     * Doing so anyway will throw an exception.
     */
    @XmlTransient
    private final FreezeStatus frozen = new FreezeStatus();

    // For JAXB deserialization
    @SuppressWarnings("unused")
    FieldImpl() {
    }

    FieldImpl(BlackLabIndex index, String fieldName) {
        this.index = index;
        this.fieldName = fieldName;
    }

    @Override
    public BlackLabIndex index() {
        return index;
    }

    /**
     * Get this field's name
     * 
     * @return this field's name
     */
    @Override
    public String name() {
        return fieldName;
    }

    void putCustom(String name, Object value) {
        if (!value.equals(custom.get(name))) {
            ensureNotFrozen();
            custom.put(name, value);
        }
    }

    @Override
    public CustomProps custom() {
        return custom;
    }

    public void setContentStore(boolean contentStore) {
        if (contentStore != this.contentStore) {
            try {
                ensureNotFrozen();
                this.contentStore = contentStore;
            } catch (UnsupportedOperationException e) {
                throw new InvalidIndex("Tried to set contentStore to " + contentStore + ", but field is frozen: " + fieldName, e);
            }
        }
    }

    @Override
    public boolean hasContentStore() {
        return contentStore;
    }

    @Override
    public int hashCode() {
        return fieldName.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        FieldImpl other = (FieldImpl) obj;
        return fieldName.equals(other.fieldName);
    }
    
    @Override
    public String toString() {
        return fieldName;
    }

    public void fixAfterDeserialization(BlackLabIndex index, String fieldName) {
        ensureNotFrozen();
        this.fieldName = fieldName;
        this.index = index;
    }

    @Override
    public boolean freeze() {
        return frozen.freeze();
    }

    @Override
    public boolean isFrozen() {
        return frozen.isFrozen();
    }

}
