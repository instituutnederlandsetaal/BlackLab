package nl.inl.blacklab.search.indexmetadata;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;

/**
 * A named, ordered list of metadata fields.
 *
 * Used to divide metadata into logical groups.
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class MetadataFieldGroupImpl implements MetadataFieldGroup {

//    static final Logger logger = LogManager.getLogger(MetadataFieldGroupImpl.class);

    private final String name;

    private final List<String> fieldNamesInGroup;

    boolean addRemainingFields;

    MetadataFieldGroupImpl(String name, List<String> fieldNames, boolean addRemainingFields) {
        this.name = name;
        this.fieldNamesInGroup = new ArrayList<>(fieldNames);
        this.addRemainingFields = addRemainingFields;
    }

    @Override
    public String name() {
        return name;
    }

    public List<String> getFields() {
        return fieldNamesInGroup;
    }

    @Override
    public boolean addRemainingFields() {
        return addRemainingFields;
    }

    @Override
    public Iterator<String> iterator() {
        return fieldNamesInGroup.iterator();
    }

    @Override
    public Stream<String> stream() {
        return fieldNamesInGroup.stream();
    }

    public Map<String, Object> toCustom() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", name);
        result.put("fieldNamesInGroup", fieldNamesInGroup);
        result.put("addRemainingFields", addRemainingFields);
        return result;
    }

    public static MetadataFieldGroupImpl fromCustom(Map<String, Object> customStruct) {
        String name = (String)customStruct.getOrDefault("name", "UNKNOWN");
        List<String> fieldNames = (List<String>)customStruct.getOrDefault("fieldNamesInGroup",
                Collections.emptyList());
        boolean addRemainingFields = (Boolean)customStruct.getOrDefault("addRemainingFields", false);
        return new MetadataFieldGroupImpl(name, fieldNames, addRemainingFields);
    }
}
