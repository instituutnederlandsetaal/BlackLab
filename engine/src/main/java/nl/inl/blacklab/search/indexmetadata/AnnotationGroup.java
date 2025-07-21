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

/** Logical grouping of annotations */
@XmlAccessorType(XmlAccessType.FIELD)
public class AnnotationGroup implements Iterable<String> {
    
    private final String fieldName;
    
    private final String groupName;
    
    private final List<String> annotations;
    
    private final boolean addRemainingAnnotations;

    public AnnotationGroup(String fieldName, String groupName, List<String> annotations,
            boolean addRemainingAnnotations) {
        this.fieldName = fieldName;
        this.groupName = groupName;
        this.annotations = new ArrayList<>(annotations);
        this.addRemainingAnnotations = addRemainingAnnotations; 
    }

    public String fieldName() {
        return fieldName;
    }

    public String groupName() {
        return groupName;
    }

    public List<String> annotations() {
        return Collections.unmodifiableList(annotations);
    }

    public boolean addRemainingAnnotations() {
        return addRemainingAnnotations;
    }
    
    @Override
    public Iterator<String> iterator() {
        return annotations.iterator();
    }

    public Stream<String> stream() {
        return annotations.stream();
    }

    public Map<String, Object> toCustom() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("groupName", groupName);
        result.put("annotations", annotations);
        result.put("addRemainingAnnotations", addRemainingAnnotations);
        return result;
    }

    public static AnnotationGroup fromCustom(String fieldName, Map<String, Object> serialized) {
        String groupName = (String)serialized.getOrDefault("groupName", "UNKNOWN");
        List<String> annotations = (List<String>)serialized.getOrDefault("annotations", Collections.emptyList());
        boolean addRemainingAnnotations = (Boolean)serialized.getOrDefault("addRemainingAnnotations", false);
        return new AnnotationGroup(fieldName, groupName, annotations, addRemainingAnnotations);
    }

}
