package nl.inl.blacklab.search.indexmetadata;

import org.junit.Assert;
import org.junit.Test;

public class TestAnnotatedFieldNameUtil {

    public static void assertValidXmlElementName(String name) {
        assertRewriteXmlElementName(name, name);
    }

    public static void assertRewriteXmlElementName(String expected, String name) {
        Assert.assertEquals(expected, AnnotatedFieldNameUtil.sanitizeXmlElementName(name));
    }

    @Test
    public void testSanitizeXmlElementName() {
        assertValidXmlElementName("a");
        assertValidXmlElementName("a-b");
        assertValidXmlElementName("a.b");
        assertValidXmlElementName("a_b");
        assertValidXmlElementName("a1");
        assertRewriteXmlElementName("_EMPTY_", "");
        assertRewriteXmlElementName("a_b", "a/b");
    }

    @Test
    public void testInlineTagRelationType() {
        String rt = RelationUtil.fullType(RelationUtil.CLASS_INLINE_TAG, "word");
        String tagName = RelationUtil.typeFromFullType(rt);
        Assert.assertEquals("word", tagName);
    }
}
