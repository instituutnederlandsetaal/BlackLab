package nl.inl.blacklab.indexers.config.process;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.Assert;
import org.junit.Test;

import nl.inl.blacklab.exceptions.MalformedInputFile;
import nl.inl.blacklab.exceptions.PluginException;
import nl.inl.blacklab.index.DocIndexer;
import nl.inl.blacklab.index.DocIndexerAbstract;
import nl.inl.util.FileReference;

/**
 * Test our most-used processing steps.
 */
public class TestProcessingSteps {

    @Test
    public void testMap() {
        Map<String, String> mapping = new HashMap<>();
        mapping.put("monkey", "animal");
        mapping.put("oak", "plant");
        ProcessingStep step = new ProcessingStepMapValues(mapping);
        test("monkey", step, "animal");
        test("oak", step, "plant");
        test("bla", step, "bla");
    }

    @Test
    public void testUnique() {
        ProcessingStep step = new ProcessingStepUnique();
        test(Collections.emptyList(), step, Collections.emptyList());
        test(List.of("NOU-C", "NOU-C"), step, List.of("NOU-C"));
        test(List.of("NOU-C", "bla","NOU-C"), step, List.of("NOU-C", "bla"));
        test("NOU-C", step, "NOU-C");
    }

    @Test
    public void testMultiple() {
        ProcessingStep step = new ProcessingStepMultiple(List.of(
                new ProcessingStepStrip(" "),
                new ProcessingStepReplace("c", "x", "i", "replaced"),
                new ProcessingStepAppend("_", "|", "test", null)
        ));
        test("  NOU-C  ", step, "NOU-x|testValue");
        test("NOU-P", step, "NOU-P|testValue");
        test("  ", step, "testValue");
        test("bla", new ProcessingStepMultiple(Collections.emptyList()), "bla");
    }

    @Test
    public void testStrip() {
        testStrip(" NOU-C  ", " ", "NOU-C");
        testStrip("*NOU-C*", "*", "NOU-C");
        testStrip("  NOU-C", " ", "NOU-C");
        testStrip("NOU-C  ", " ", "NOU-C");
    }

    private void testStrip(String input, String chars, String expected) {
        test(input, new ProcessingStepStrip(chars), expected);
    }

    @Test
    public void testSplit() {
        testSplit("NOU-C", "-", "all", List.of("NOU", "C"));
        testSplit("NOU-C", "_", "all", List.of("NOU-C"));
        testSplit("NOU-C", "-", "both", List.of("NOU-C", "NOU", "C"));
        //testSplit(" NOU C ", "\\s+", "all", List.of("NOU", "C"));
    }

    private void testSplit(String input, String separator, String keep, List<String> expected) {
        test(input, new ProcessingStepSplit(separator, "", keep), expected);
    }

    @Test
    public void testAppend() {
        testAppend("NOU-C", "|", "_", null, "suffix", "NOU-C_suffix");
        testAppend("NOU-C", "|", "_", "test", null, "NOU-C_testValue");
        testAppend("NOU-C", "|", "", "test", null, "NOU-CtestValue");
        testAppend("NOU-C", "|", "$", "test", null, "NOU-C$testValue");
        testAppend("", "|", "$", "test", null, "testValue");
        testAppend("NOU", "|", "$", "testMulti", null, "NOU$testValue1|testValue2");
    }

    private void testAppend(String input, String separator, String prefix, String field, String fixedValue, String expected) {
        test(input, new ProcessingStepAppend(separator, prefix, field, fixedValue), expected);
    }

    @Test
    public void testConcatDate() {
        testConcatDate("test", "start", "test20251101");
        testConcatDate("test", "end", "test20251130");
    }

    private void testConcatDate(String input, String autofill, String expected) {
        test(input, new ProcessingStepConcatDate("year", "month", "day", autofill), expected);
    }

    @Test
    public void testReplace() {
        testReplace("NOU-C()", "\\(", "{", "", "NOU-C{)");
        testReplace("NOU-C()", "c", "x", "i", "NOU-x()");
        testReplace("NOU-C()", "c", "x", "", "NOU-C()");
        testReplace("NOU-C()", "N(.)U", "H$1P", "", "HOP-C()");
    }

    private void testReplace(String input, String regex, String replacement, String flags, String expected) {
        test(input, new ProcessingStepReplace(regex, replacement, flags, "replaced"), expected);
    }
    
    @Test
    public void testParsePos() {
        testParsePos("NOU-C()", "_", "NOU-C");
        testParsePos("NOU-C(gender=f,number=pl)", "_", "NOU-C");
        testParsePos("NOU-C(gender=f,number=pl)", "gender", "f");
        testParsePos("NOU-C(gender=f,number=pl)", "number", "pl");
        testParsePos("NOU-C(gender=f,number=pl)", "type", "");
    }

    public void testParsePos(String input, String feature, String expected) {
        test(input, ProcessingStepParsePos.feature(feature), expected);
    }

    public void test(String input, ProcessingStep step, String expected) {
        Assert.assertEquals(expected, step.performSingle(input, docIndexer));
        Assert.assertEquals(List.of(expected), step.perform(Stream.of(input), docIndexer).collect(
                Collectors.toList()));
    }

    public void test(String input, ProcessingStep step, List<String> expected) {
        Assert.assertEquals(expected.get(0), step.performSingle(input, docIndexer));
        Assert.assertEquals(expected, step.perform(Stream.of(input), docIndexer).collect(
                Collectors.toList()));
    }

    public void test(List<String> input, ProcessingStep step, List<String> expected) {
        Assert.assertEquals(expected, step.perform(input.stream(), docIndexer).collect(
                Collectors.toList()));
    }

    DocIndexer docIndexer = new DocIndexerAbstract() {

        @Override
        public List<String> getMetadataField(String name) {
            switch (name) {
            case "test":
                return List.of("testValue");
            case "testMulti":
                return List.of("testValue1", "testValue2");
            case "year":
                return List.of("2025");
            case "month":
                return List.of("11");
            case "day":
                return null;
            }
            return null;
        }

        @Override
        public void close() {

        }

        @Override
        public void setDocument(FileReference file) {

        }

        @Override
        public void index() throws IOException, MalformedInputFile, PluginException {

        }

        @Override
        public void reportCharsProcessed() {

        }

        @Override
        public void reportTokensProcessed() {

        }

        @Override
        protected int getCharacterPosition() {
            return 0;
        }
    };
    
}
