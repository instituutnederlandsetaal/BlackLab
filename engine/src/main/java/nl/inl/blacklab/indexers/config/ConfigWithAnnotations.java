package nl.inl.blacklab.indexers.config;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

/** Shared superclass of some Config (blf.yaml/blf.json) related classes. */
public interface ConfigWithAnnotations {

    void addAnnotation(ConfigAnnotation annotation);

    @JsonIgnore
    List<ConfigAnnotation> getAnnotationsFlattened();

}
