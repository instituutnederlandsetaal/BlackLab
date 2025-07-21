package nl.inl.blacklab.search.indexmetadata;

import jakarta.xml.bind.annotation.XmlTransient;

import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.annotation.JsonProperty;

import nl.inl.blacklab.exceptions.InvalidIndex;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.util.StringUtil;

public abstract class FieldImpl implements Field, Freezable {
    /** Field's name */
    @XmlTransient
    protected String fieldName;

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

    FieldImpl(String fieldName) {
        this.fieldName = fieldName;
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

    /**
     * @deprecated use {@link #custom()} and .put("displayName", "...") instead
     */
    @Deprecated
    public void setDisplayName(String displayName) {
        putCustom("displayName", displayName);
    }

    void putCustom(String name, Object value) {
        if (!value.equals(custom.get(name))) {
            ensureNotFrozen();
            custom.put(name, value);
        }
    }

    /**
     * @deprecated use {@link #custom()} and .get("displayName", "") instead
     */
    @Deprecated
    @Override
    public String displayName() {
        String displayName = custom.get("displayName", "");
        if (StringUtils.isEmpty(displayName)) {
            displayName = StringUtil.camelCaseToDisplayable(fieldName, true);
        }
        return displayName;
    }

    /**
     * @deprecated use {@link #custom()} and .put("description", "...") instead
     */
    @Deprecated
    public void setDescription(String description) {
        putCustom("description", description);
    }

    /**
     * @deprecated use {@link #custom()} and .get("description", "") instead
     */
    @Deprecated
    @Override
    public String description() {
        return custom.get("description", "");
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
