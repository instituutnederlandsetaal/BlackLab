package nl.inl.blacklab.search.indexmetadata;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Groups of annotations for a single field
 */
public record AnnotationGroups(String fieldName, List<AnnotationGroup> groups) implements Iterable<AnnotationGroup> {

    public AnnotationGroups(String fieldName, List<AnnotationGroup> groups) {
        this.fieldName = fieldName;
        this.groups = new ArrayList<>(groups);
    }

    @Override
    public Iterator<AnnotationGroup> iterator() {
        return groups.iterator();
    }

    public Stream<AnnotationGroup> stream() {
        return groups.stream();
    }

    public AnnotationGroup get(String name) {
        return groups.stream().filter(g -> g.groupName().equals(name)).findFirst().orElse(null);
    }

    public List<Map<String, Object>> toCustom() {
        return groups.stream()
                .map(AnnotationGroup::toCustom)
                .toList();
    }

    public static AnnotationGroups fromCustom(String fieldName, List<Map<String, Object>> serialized) {
        List<AnnotationGroup> groups = serialized.stream()
                .map(g -> AnnotationGroup.fromCustom(fieldName, g))
                .toList();
        return new AnnotationGroups(fieldName, groups);
    }
}
