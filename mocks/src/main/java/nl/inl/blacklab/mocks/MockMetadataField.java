package nl.inl.blacklab.mocks;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import nl.inl.blacklab.search.indexmetadata.CustomProps;
import nl.inl.blacklab.search.indexmetadata.FieldType;
import nl.inl.blacklab.search.indexmetadata.MetadataField;
import nl.inl.blacklab.search.indexmetadata.MetadataFieldValues;
import nl.inl.blacklab.search.indexmetadata.UnknownCondition;

public record MockMetadataField(String name) implements MetadataField {

    @Override
    public String displayName() {
        return name + "_displayName";
    }

    @Override
    public String description() {
        return name + "_desc";
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
    public String uiType() {
        return null;
    }

    @Override
    public FieldType type() {
        return null;
    }

    @Override
    public List<String> displayOrder() {
        return null;
    }

    @Override
    public String analyzerName() {
        return null;
    }

    @Override
    public String unknownValue() {
        return null;
    }

    @Override
    public UnknownCondition unknownCondition() {
        return null;
    }

    @Override
    public MetadataFieldValues values(long maxValues) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public Map<String, String> displayValues() {
        return Collections.emptyMap();
    }
}
