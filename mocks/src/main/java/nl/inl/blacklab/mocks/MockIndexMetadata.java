package nl.inl.blacklab.mocks;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import nl.inl.blacklab.indexers.config.ConfigAnnotatedField;
import nl.inl.blacklab.indexers.config.TextDirection;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFields;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.AnnotationGroups;
import nl.inl.blacklab.search.indexmetadata.CustomProps;
import nl.inl.blacklab.search.indexmetadata.IndexMetadata;
import nl.inl.blacklab.search.indexmetadata.MetadataFields;
import nl.inl.blacklab.search.results.CorpusSize;

public class MockIndexMetadata implements IndexMetadata {
    
    private final List<AnnotatedField> fields;
    
    private FreezeStatus frozen = new FreezeStatus();

    public MockIndexMetadata() {
        List<Annotation> annot = Arrays.asList(new MockAnnotation("word"), new MockAnnotation("lemma"), new MockAnnotation("pos"));
        MockAnnotatedField contents = new MockAnnotatedField("contents", annot);
        fields = List.of(contents);
    }

    @Override
    public AnnotatedFields annotatedFields() {
        return new AnnotatedFields() {
            
            @Override
            public Stream<AnnotatedField> stream() {
                return fields.stream();
            }
            
            @Override
            public AnnotatedField main() {
                return fields.get(0);
            }
            
            @Override
            public Iterator<AnnotatedField> iterator() {
                return fields.iterator();
            }
            
            @Override
            public AnnotatedField get(String fieldName) {
                return fields.stream().filter(f -> f.name().equals(fieldName)).findFirst().orElse(null);
            }
            
            @Override
            public boolean exists(String fieldName) {
                return fields.stream().anyMatch(f -> f.name().equals(fieldName));
            }

            @Override
            public AnnotationGroups annotationGroups(String fieldName) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void addFromConfig(ConfigAnnotatedField config) {
                throw new UnsupportedOperationException();
            }
        };
    }

    @Override
    public MetadataFields metadataFields() {
        return null;
    }

    @Override
    public CustomProps custom() {
        return CustomProps.NONE;
    }

    @Override
    public String displayName() {
        return null;
    }

    @Override
    public String description() {
        return null;
    }

    @Override
    public boolean contentViewable() {
        return false;
    }

    @Override
    public TextDirection textDirection() {
        return null;
    }

    @Override
    public String documentFormat() {
        return null;
    }

    @Override
    public String indexFormat() {
        return null;
    }

    @Override
    public String timeCreated() {
        return null;
    }

    @Override
    public String timeModified() {
        return null;
    }

    @Override
    public String indexBlackLabBuildTime() {
        return null;
    }

    @Override
    public String indexBlackLabVersion() {
        return null;
    }

    @Override
    public String indexBlackLabScmRevision() {
        return null;
    }

    @Override
    public long tokenCount() {
        return 0;
    }

    @Override
    public Map<String, CorpusSize.Count> countPerField() {
        return Map.of(mainAnnotatedField().name(), CorpusSize.Count.get(documentCount(), tokenCount()));
    }

    @Override
    public int documentCount() {
        return 0;
    }

    @Override
    public boolean isNewIndex() {
        return false;
    }

    @Override
    public boolean freeze() {
        return frozen.freeze();
    }

    @Override
    public boolean isFrozen() {
        return frozen.isFrozen();
    }

    @Override
    public String indexFlag(String name) {
        return "";
    }
}
