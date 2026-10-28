package nl.inl.blacklab.mocks;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import nl.inl.blacklab.search.indexmetadata.MetadataField;
import nl.inl.blacklab.search.indexmetadata.MetadataFieldGroup;
import nl.inl.blacklab.search.indexmetadata.MetadataFields;

public class MockMetadataFields implements MetadataFields {
    @Override
    public String defaultAnalyzerName() {
        return "";
    }

    @Override
    public Stream<MetadataField> stream() {
        return Stream.empty();
    }

    @Override
    public MetadataField get(String fieldName) {
        return null;
    }

    @Override
    public Map<String, ? extends MetadataFieldGroup> groups() {
        return Map.of();
    }

    @Override
    public MetadataField pidField() {
        return null;
    }

    @Override
    public boolean exists(String name) {
        return false;
    }

    @Override
    public List<String> names() {
        return List.of();
    }

    @Override
    public List<MetadataField> toList() {
        return List.of();
    }

    @Override
    public boolean anyOccurInFragments() {
        return false;
    }

    @Override
    public Iterator<MetadataField> iterator() {
        return null;
    }
}
