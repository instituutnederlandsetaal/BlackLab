package nl.inl.blacklab.mocks;

import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.Annotations;
import nl.inl.blacklab.search.indexmetadata.CustomProps;
import nl.inl.blacklab.search.indexmetadata.RelationsStats;

public class MockAnnotatedField implements AnnotatedField {
    
    final String name;
    
    private final List<Annotation> annotations;
    
    public MockAnnotatedField(String name, List<Annotation> annotations) {
        this.name = name;
        this.annotations = annotations;
        for (Annotation a: annotations) {
            if (a instanceof MockAnnotation) {
                ((MockAnnotation) a).setField(this);
            }
        }
    }

    @Override
    public String name() {
        return this.name;
    }

    @Override
    public boolean hasContentStore() {
        return false;
    }

    @Override
    public Annotations annotations() {
        return new Annotations() {

            @Override
            public Annotation main() {
                return annotations.get(0);
            }

            @Override
            public Iterator<Annotation> iterator() {
                return annotations.iterator();
            }

            @Override
            public Stream<Annotation> stream() {
                return annotations.stream();
            }

            @Override
            public boolean exists(String name) {
                return annotations.stream().anyMatch(a -> a.name().equals(name));
            }

            @Override
            public Annotation get(String name) {
                return annotations.stream().filter(a -> a.name().equals(name)).findFirst().orElse(null);
            }

            @Override
            public boolean isEmpty() {
                return annotations.isEmpty();
            }

            @Override
            public int size() {
                return annotations.size();
            }

        };
    }

    @Override
    public boolean hasRelationAnnotation() {
        return false;
    }

    @Override
    public String tokenLengthField() {
        return null;
    }

    @Override
    public String offsetsField() {
        return null;
    }

    @Override
    public CustomProps custom() {
        return CustomProps.NONE;
    }

    @Override
    public RelationsStats getRelationsStats(BlackLabIndex index, long limitValues) {
        throw new UnsupportedOperationException();
    }
}
