package nl.inl.blacklab.mocks;

import nl.inl.blacklab.search.indexmetadata.CustomProps;
import nl.inl.blacklab.search.indexmetadata.FieldType;
import nl.inl.blacklab.search.indexmetadata.MetadataField;
import nl.inl.blacklab.search.indexmetadata.MetadataFieldValues;

public record MockMetadataField(String name) implements MetadataField {

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
}
