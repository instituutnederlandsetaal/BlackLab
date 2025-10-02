package nl.inl.blacklab.indexers.config.process;

import java.util.List;

public interface DocMetadata {
    List<String> getMetadataField(String name);
}
