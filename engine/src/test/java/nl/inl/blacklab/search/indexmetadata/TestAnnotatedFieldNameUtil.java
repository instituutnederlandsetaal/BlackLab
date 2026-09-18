package nl.inl.blacklab.search.indexmetadata;

import org.junit.Assert;
import org.junit.Test;

import nl.inl.util.XmlUtil;

public class TestAnnotatedFieldNameUtil {

    public static void assertValidXmlElementName(String name) {
        assertRewriteXmlElementName(name, name);
    }

    public static void assertRewriteXmlElementName(String expected, String name) {
        Assert.assertEquals(expected, XmlUtil.sanitizeXmlElementName(name));
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

    @Test
    public void testSourceBookkeepingFieldsAreNotRelations() {
        Assert.assertEquals("contents#source_ranges", AnnotatedFieldNameUtil.sourceRangesField("contents"));
        Assert.assertEquals("contents#source_status", AnnotatedFieldNameUtil.sourceStatusField("contents__nl"));
        Assert.assertEquals("contents#source_units", AnnotatedFieldNameUtil.sourceUnitsField("contents"));

        String[][] sourceFields = {
                { "contents#source_ranges", "source_ranges" },
                { "contents#source_status", "source_status" },
                { "contents#source_units", "source_units" }
        };
        for (String[] field: sourceFields) {
            Assert.assertArrayEquals(new String[] { "contents", null, null, field[1] },
                    AnnotatedFieldNameUtil.getNameComponents(field[0]));
            Assert.assertTrue(AnnotatedFieldNameUtil.isSourceBookkeepingField(field[0]));
            Assert.assertFalse(AnnotatedFieldNameUtil.isRelationsField(field[0]));
        }
        for (String field: new String[] { "contents#cs", "contents#length_tokens", "_relation",
                AnnotatedFieldNameUtil.bookkeepingField("contents", "word", "fiid") }) {
            Assert.assertFalse(AnnotatedFieldNameUtil.isSourceBookkeepingField(field));
            Assert.assertFalse(AnnotatedFieldNameUtil.isRelationsField(field));
        }
        Assert.assertFalse(AnnotatedFieldNameUtil.isSourceBookkeepingField("email@domain"));
        Assert.assertTrue(AnnotatedFieldNameUtil.isSourceBookkeepingField("contents%word#source_ranges"));
        Assert.assertTrue(AnnotatedFieldNameUtil.isRelationsField(
                AnnotatedFieldNameUtil.bookkeepingField("contents",
                        AnnotatedFieldNameUtil.RELATIONS_ANNOT_NAME, "fiid")));
        Assert.assertTrue(AnnotatedFieldNameUtil.isRelationsField(
                AnnotatedFieldNameUtil.annotationField("contents", AnnotatedFieldNameUtil.RELATIONS_ANNOT_NAME,
                        MatchSensitivity.SENSITIVE.luceneFieldSuffix())));
    }

    @Test
    public void testSourceUnitsField() {
        Assert.assertTrue(AnnotatedFieldNameUtil.isSourceUnitsField("contents#source_units"));
        Assert.assertTrue(AnnotatedFieldNameUtil.isSourceUnitsField("contents__nl#source_units"));
        Assert.assertFalse(AnnotatedFieldNameUtil.isSourceUnitsField("contents#source_status"));
        Assert.assertFalse(AnnotatedFieldNameUtil.isSourceUnitsField("contents#source_units_extra"));
    }
}
