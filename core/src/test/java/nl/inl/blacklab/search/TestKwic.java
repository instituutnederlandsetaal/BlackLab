package nl.inl.blacklab.search;

import java.util.Arrays;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import nl.inl.blacklab.mocks.MockAnnotation;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldNameUtil;
import nl.inl.blacklab.search.indexmetadata.Annotation;

public class TestKwic {

    static final List<String> PROPS = Arrays.asList(AnnotatedFieldNameUtil.PUNCTUATION_ANNOT_NAME, "lemma", "pos", "word");
    
    static final List<Annotation> ANNOTS = PROPS.stream().map(name -> (Annotation)new MockAnnotation(name)).toList();

    static final List<String> TOKENS = Arrays.asList(
            " ", "de", "lw", "De",
            " ", "snel", "bn", "snelle",
            " ", "bruin", "bn", "bruine",
            " ", "vos", "zn", "vos");
    
    static final List<String> LEFT = Arrays.asList(" ", "de", "lw", "De", " ", "snel", "bn", "snelle");
    
    static final List<String> MATCH = Arrays.asList(" ", "bruin", "bn", "bruine");
    
    static final List<String> RIGHT = Arrays.asList(" ", "vos", "zn", "vos");
    
    static final String EXP_LEFT = "<w lemma=\"de\" pos=\"lw\">De</w> <w lemma=\"snel\" pos=\"bn\">snelle</w> ";
    
    static final String EXP_MATCH = "<w lemma=\"bruin\" pos=\"bn\">bruine</w>";
    
    static final String EXP_RIGHT = " <w lemma=\"vos\" pos=\"zn\">vos</w>";
    
    @Test
    public void testKwicToConcordance() {
        Kwic kwic = new Kwic(ANNOTS, TOKENS, 2, 3, 0);
        Concordance conc = kwic.toConcordance();
    
        Assert.assertEquals(EXP_LEFT, conc.left());
        Assert.assertEquals(EXP_MATCH, conc.match());
        Assert.assertEquals(EXP_RIGHT, conc.right());
    }

    @Test
    public void testKwicNewConstructor() {
        Kwic kwic = new Kwic(ANNOTS, TOKENS, 2, 3, 0);
    
        Assert.assertEquals(EXP_LEFT + EXP_MATCH + EXP_RIGHT, kwic.fragment().xml());
        Assert.assertEquals(LEFT, kwic.before());
        Assert.assertEquals(MATCH, kwic.match());
        Assert.assertEquals(RIGHT, kwic.after());
        Assert.assertEquals(Arrays.asList("De", "snelle"), kwic.before(ANNOTS.get(3)));
    }

}
