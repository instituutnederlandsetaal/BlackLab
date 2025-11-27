package nl.inl.blacklab.indexers.config;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Configuration for a group of metadata fields.
 *
 * These could e.g. be shown together on a tab in a user interface.
 */
public class ConfigMetadataFieldGroup {

    /** Group name (often displayed on tab or in list) */
    private String name;

    /** Fields in this group */
    private final List<String> fields = new ArrayList<>();

    /** Add any fields not yet in any group to this one? */
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    private boolean addRemainingFields = false;

    public ConfigMetadataFieldGroup() {
    }

    public ConfigMetadataFieldGroup(String name) {
        setName(name);
    }

    public ConfigMetadataFieldGroup copy() {
        ConfigMetadataFieldGroup cp = new ConfigMetadataFieldGroup(name);
        cp.fields.addAll(fields);
        cp.addRemainingFields = addRemainingFields;
        return cp;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<String> getFields() {
        return fields;
    }

    public void addField(String field) {
        this.fields.add(field);
    }

    public void addFields(List<String> fields) {
        this.fields.addAll(fields);
    }

    public boolean isAddRemainingFields() {
        return addRemainingFields;
    }

    public void setAddRemainingFields(boolean addRemainingFields) {
        this.addRemainingFields = addRemainingFields;
    }

    @Override
    public String toString() {
        return "ConfigMetadataFieldGroup [name=" + name + "]";
    }

    
}
