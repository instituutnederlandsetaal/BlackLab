package nl.inl.blacklab.server.lib.results;

import java.util.Collection;
import java.util.Map;

import nl.inl.blacklab.search.indexmetadata.MetadataField;
import nl.inl.blacklab.search.indexmetadata.MetadataFieldGroup;

public class ResultMetadataGroupInfo {
    private final Map<String, ? extends MetadataFieldGroup> metaGroups;

    private final Collection<MetadataField> metadataFieldsNotInGroups;

    ResultMetadataGroupInfo(Map<String, ? extends MetadataFieldGroup> metaGroups,
            Collection<MetadataField> metadataFieldsNotInGroups) {
        this.metaGroups = metaGroups;
        this.metadataFieldsNotInGroups = metadataFieldsNotInGroups;
    }

    public Map<String, ? extends MetadataFieldGroup> getMetaGroups() {
        return metaGroups;
    }

    public Collection<MetadataField> getMetadataFieldsNotInGroups() {
        return metadataFieldsNotInGroups;
    }
}
