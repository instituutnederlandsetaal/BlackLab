package nl.inl.blacklab.mocks;

import org.jspecify.annotations.NonNull;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.CustomProps;
import nl.inl.blacklab.search.indexmetadata.Field;
import nl.inl.blacklab.search.indexmetadata.FieldType;
import nl.inl.blacklab.search.indexmetadata.MetadataField;
import nl.inl.blacklab.search.indexmetadata.MetadataFieldValues;

public record MockMetadataField(String name) implements MetadataField {

    private static final BlackLabIndex index = new MockBlackLabIndex();

    @Override
    public BlackLabIndex index() {
        return index;
    }

    @Override
    public boolean hasContentStore() {
        return false;
    }

    @Override
    public String offsetsField() {
        return null;
    }

    @Override
    public CustomProps custom() {
        return CustomProps.NONE;
    }

    @Override
    public FieldType type() {
        return null;
    }

    @Override
    public String analyzerName() {
        return null;
    }

    @Override
    public MetadataFieldValues values(long maxValues) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public int compareTo(@NonNull Field field) {
        if (field instanceof MockMetadataField)
            return name().compareTo(field.name());
        return getClass().getName().compareTo(field.getClass().getName());
    }

    @Override
    public boolean occursInFragments() {
        return false;
    }
}
