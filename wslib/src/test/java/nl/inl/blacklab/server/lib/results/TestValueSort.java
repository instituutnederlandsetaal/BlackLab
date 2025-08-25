package nl.inl.blacklab.server.lib.results;

import java.util.Arrays;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import com.ibm.icu.text.Collator;

import nl.inl.blacklab.forwardindex.Collators;
import nl.inl.blacklab.search.BlackLab;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;

public class TestValueSort {

    @Test
    public void testValueSortFieldInfo() {
        List<String> list = Arrays.asList("vuur", ")vis(", "noot", "(mies)", "aap", "aa(n)", "aa(s)");
        List<String> expected = Arrays.asList("aa(n)", "aap", "aa(s)", "(mies)", "noot", ")vis(", "vuur");
        Collator coll = BlackLab.getFieldValueSortCollator();
        list.sort(coll);
        Assert.assertEquals(expected, list);
    }
    
    @Test
    public void testValueSortV2() {
        final List<String> list = Arrays.asList(
            "a-",
            "-b",
            "a",
            "AA-",
            "aa",
            "AA",
            "cool_stuff",
            "cool stuff",
            "cool-stuff",
            "help?",
            "help.",
            "help",
            "(h)elp",
            ".",
            "a-",
            "b",
            "tes(t)ed",
            "test",
            "tested",
            ""
        );
        final List<String> expected = Arrays.asList(
            "",
            "-b",
            ".",
            "(h)elp",
            "a",
            "a-",
            "a-",
            "aa",
            "AA",
            "AA-",
            "b",
            "cool stuff",
            "cool-stuff",
            "cool_stuff",
            "help",
            "help?",
            "help.",
            "tes(t)ed",
            "test",
            "tested"
        );
        Collator coll = new Collators(BlackLab.defaultCollator()).get(MatchSensitivity.INSENSITIVE);
        list.sort(coll);
        Assert.assertEquals(expected, list);
    }
}
