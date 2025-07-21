package nl.inl.blacklab.search.lucene;

import java.io.IOException;
import java.util.Collection;
import java.util.List;

import org.apache.lucene.queries.spans.Spans;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import nl.inl.blacklab.TestUtil;
import nl.inl.blacklab.mocks.MockSpans;

@RunWith(Parameterized.class)
public class TestSpansRepetition {

    @Parameterized.Parameters(name = "with overlap: {0}")
    public static Collection<Boolean> withOverlap() {
        return List.of(false, true);
    }

    @Parameterized.Parameter
    public boolean hitsCanOverlap;

    private BLSpans getSpans() {
        int[] aDoc   = { 1, 1, 1, 1, 1,  1 };
        int[] aStart = { 1, 2, 3, 5, 6, 10 };
        int[] aEnd   = { 2, 3, 4, 6, 7, 11 };
        BLSpans a = new MockSpans(aDoc, aStart, aEnd);
        return hitsCanOverlap ?
                new SpansRepetition(a, 2, 4) :
                new SpansRepetitionSimple(a, 2, 4);
    }

    @Test
    public void testRepetition() throws IOException {
        Spans spans = getSpans();
        int[] expDoc =   { 1, 1, 1, 1 };
        int[] expStart = { 1, 1, 2, 5 };
        int[] expEnd =   { 3, 4, 4, 7 };
        BLSpans exp = new MockSpans(expDoc, expStart, expEnd);
        TestUtil.assertEquals(exp, spans);
    }
}
