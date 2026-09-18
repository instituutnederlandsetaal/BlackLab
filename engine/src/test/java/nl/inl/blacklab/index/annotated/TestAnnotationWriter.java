package nl.inl.blacklab.index.annotated;

import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import nl.inl.blacklab.index.BLIndexObjectFactoryLucene;
import nl.inl.blacklab.index.DocWriter;

public class TestAnnotationWriter {

    @Test
    public void testMainEmptyPositionPreservedWithoutOffsets() {
        DocWriter docWriter = Mockito.mock(DocWriter.class);
        Mockito.when(docWriter.indexObjectFactory()).thenReturn(BLIndexObjectFactoryLucene.INSTANCE);
        AnnotatedFieldWriter field = new AnnotatedFieldWriter(docWriter, "contents", "word",
                AnnotationSensitivities.ONLY_INSENSITIVE,
                AnnotatedFieldWriter.TokenOffsetStorage.SOURCE_RANGE_VECTORS, false, false, null);
        AnnotationWriter main = field.mainAnnotation();
        main.addValue("");
        main.addValueAtPosition("late", 0, null);

        Assert.assertEquals(List.of("", "late"), main.values());
        Assert.assertEquals(List.of(1, 0), main.positionIncrements());

        AnnotationWriter secondary = field.addAnnotation("lemma", AnnotationSensitivities.ONLY_INSENSITIVE, false, true);
        secondary.addValue("");
        secondary.addValueAtPosition("late", 0, null);
        Assert.assertEquals(List.of("late"), secondary.values());
        Assert.assertEquals(List.of(1), secondary.positionIncrements());
    }
}
