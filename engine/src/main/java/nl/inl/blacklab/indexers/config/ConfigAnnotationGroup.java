package nl.inl.blacklab.indexers.config;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Configuration for a group of annotations.
 *
 * These could e.g. be shown together on a tab in a user interface.
 */
public class ConfigAnnotationGroup {

    /** Group name (often displayed on tab or in list) */
    private String name;

    /** Annotations in this group */
    private final List<String> annotations = new ArrayList<>();

    /** Add any annotations not yet in any group to this one? */
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    private boolean addRemainingAnnotations = false;

    public ConfigAnnotationGroup() {
    }

    public ConfigAnnotationGroup(String name) {
        setName(name);
    }

    public ConfigAnnotationGroup copy() {
        ConfigAnnotationGroup cp = new ConfigAnnotationGroup(name);
        cp.annotations.addAll(annotations);
        cp.addRemainingAnnotations = addRemainingAnnotations;
        return cp;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<String> getAnnotations() {
        return annotations;
    }

    public void addAnnotations(List<String> fields) {
        this.annotations.addAll(fields);
    }

    public boolean isAddRemainingAnnotations() {
        return addRemainingAnnotations;
    }

    public void setAddRemainingAnnotations(boolean addRemainingAnnotations) {
        this.addRemainingAnnotations = addRemainingAnnotations;
    }

    @Override
    public String toString() {
        return "ConfigAnnotationGroup [name=" + name + "]";
    }

}
