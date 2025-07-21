package nl.inl.blacklab.search.indexmetadata;

import org.junit.Assert;
import org.junit.Test;

public class TestRelationUtil {

    @Test
    public void testInlineTagRelationType() {
        String rt = RelationUtil.fullType(RelationUtil.CLASS_INLINE_TAG, "word");
        String tagName = RelationUtil.typeFromFullType(rt);
        Assert.assertEquals("word", tagName);
    }
}
