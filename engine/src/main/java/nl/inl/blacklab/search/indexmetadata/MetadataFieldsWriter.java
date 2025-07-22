package nl.inl.blacklab.search.indexmetadata;

import java.util.Map;

import nl.inl.blacklab.indexers.config.ConfigMetadataField;

/**
 * Interface for MetadataFields objects while indexing.
 */
public interface MetadataFieldsWriter extends MetadataFields {
    
    MetadataField register(String fieldName);
    
    void setMetadataGroups(Map<String, MetadataFieldGroupImpl> metadataGroups);

    void put(String fieldName, MetadataFieldImpl fieldDesc);

    void setDefaultAnalyzer(String name);

    /**
     * @deprecated use indexmetadata.custom().put(propName, null) instead
     */
    @Deprecated
    void clearSpecialFields();

    MetadataFieldImpl addFromConfig(ConfigMetadataField metaPidConfig);
}
