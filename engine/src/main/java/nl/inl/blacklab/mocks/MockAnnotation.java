package nl.inl.blacklab.mocks;

import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;

import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.AnnotationSensitivity;
import nl.inl.blacklab.search.indexmetadata.CustomProps;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;

public class MockAnnotation implements Annotation {
    
    private AnnotatedField field;

    private final String name;

    AnnotationSensitivity i;

    public MockAnnotation(String name) {
        this.name = name;
        i = new AnnotationSensitivity() {
            @Override
            public Annotation annotation() {
                return MockAnnotation.this;
            }

            @Override
            public MatchSensitivity sensitivity() {
                return MatchSensitivity.INSENSITIVE;
            }
        };
    }
    
    public void setField(AnnotatedField field) {
        this.field = field;
    }

    @Override
    public AnnotatedField field() {
        return field;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public boolean hasForwardIndex() {
        return true; //false
    }

    @Override
    public AnnotationSensitivity offsetsSensitivity() {
        return null;
    }

    @Override
    public Collection<AnnotationSensitivity> sensitivities() {
        return null;
    }

    @Override
    public boolean hasSensitivity(MatchSensitivity sensitivity) {
        return false;
    }

    @Override
    public AnnotationSensitivity sensitivity(MatchSensitivity sensitivity) {
        if (sensitivity == MatchSensitivity.INSENSITIVE)
            return i;
        return null;
    }

    @Override
    public boolean isInternal() {
        return false;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        MockAnnotation that = (MockAnnotation) o;
        return Objects.equals(field, that.field) && Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(field, name);
    }

    @Override
    public Set<String> subannotationNames() {
        return Collections.emptySet();
    }

    @Override
    public CustomProps custom() {
        return CustomProps.NONE;
    }

    @Override
    public boolean isSubannotation() {
        return false;
    }

    @Override
    public Annotation parentAnnotation() {
        return null;
    }

    @Override
    public String toString() {
        return "MockAnnotation{" +
                "field=" + field +
                ", name='" + name + '\'' +
                '}';
    }
}
