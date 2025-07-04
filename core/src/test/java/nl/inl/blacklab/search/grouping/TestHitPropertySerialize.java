package nl.inl.blacklab.search.grouping;

import java.util.Arrays;
import java.util.List;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import nl.inl.blacklab.mocks.MockBlackLabIndex;
import nl.inl.blacklab.mocks.MockForwardIndex;
import nl.inl.blacklab.mocks.MockMetadataField;
import nl.inl.blacklab.mocks.MockTerms;
import nl.inl.blacklab.resultproperty.DocProperty;
import nl.inl.blacklab.resultproperty.DocPropertyDecade;
import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.resultproperty.HitPropertyContextPart;
import nl.inl.blacklab.resultproperty.HitPropertyDocumentDecade;
import nl.inl.blacklab.resultproperty.HitPropertyDocumentId;
import nl.inl.blacklab.resultproperty.HitPropertyHitText;
import nl.inl.blacklab.resultproperty.PropertyValue;
import nl.inl.blacklab.resultproperty.PropertyValueContextWords;
import nl.inl.blacklab.resultproperty.PropertyValueDecade;
import nl.inl.blacklab.resultproperty.PropertyValueMultiple;
import nl.inl.blacklab.resultproperty.PropertyValueString;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.indexmetadata.MetadataField;
import nl.inl.blacklab.search.results.Hits;

public class TestHitPropertySerialize {

    private static final MetadataField decadeField = new MockMetadataField("decade");

    private static final MockBlackLabIndex mockIndex = new MockBlackLabIndex();

    private static final Hits hits = Hits.empty(mockIndex.createDefaultQueryInfo());

    private static Annotation lemmaAnnotation;

    @BeforeClass
    public static void setUp() {
        lemmaAnnotation = mockIndex.mainAnnotatedField().annotation("lemma");
        mockIndex.setForwardIndex(lemmaAnnotation, new MockForwardIndex(new MockTerms("aap", "noot", "mies")));
    }

    @Test
    public void testDocPropertySerialize() {
        DocProperty prop;

        prop = new DocPropertyDecade(mockIndex, "decade").reverse();
        String exp = "-decade:decade";
        Assert.assertEquals(exp, prop.serialize());
        Assert.assertEquals(exp, DocProperty.deserialize(mockIndex, exp).serialize());
    }

    @Test
    public void testHitPropValueSerialize() {
        PropertyValue val, val1;
        String exp;

        // removed
        val1 = new PropertyValueContextWords(hits.index(), lemmaAnnotation, MatchSensitivity.SENSITIVE, new int[] { 2 }, null,
                false);
        exp = "cws:contents%lemma:s:mies";
        Assert.assertEquals(exp, val1.serialize());
        Assert.assertEquals(exp, PropertyValue.deserialize(hits, exp).serialize());

        val1 = new PropertyValueDecade(1980);
        exp = "dec:1980";
        Assert.assertEquals(exp, val1.serialize());
        Assert.assertEquals(exp, PropertyValue.deserialize(hits, exp).serialize());

        val = new PropertyValueMultiple(new PropertyValue[] { val1, new PropertyValueString("blabla") });
        exp = "dec:1980,str:blabla";
        Assert.assertEquals(exp, val.serialize());
        Assert.assertEquals(exp, PropertyValue.deserialize(hits, exp).serialize());

        val = new PropertyValueMultiple(new PropertyValue[] { val1, new PropertyValueString("$bl:ab,la") });
        exp = "dec:1980,str:$DLbl$CLab$CMla";
        Assert.assertEquals(exp, val.serialize());
        Assert.assertEquals(exp, PropertyValue.deserialize(hits, exp).serialize());
    }
}
