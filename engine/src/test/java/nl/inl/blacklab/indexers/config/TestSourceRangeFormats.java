package nl.inl.blacklab.indexers.config;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.lucene.index.Term;
import org.junit.Assert;
import org.junit.Test;

import nl.inl.blacklab.index.DocumentFormats;
import nl.inl.blacklab.index.Indexer;
import nl.inl.blacklab.plugins.FileConverter;
import nl.inl.blacklab.search.BlackLab;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.DocUtil;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.indexmetadata.SourceRangeEncoding;
import nl.inl.blacklab.search.lucene.BLSpanTermQuery;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.util.UtilsForTesting;
import nl.inl.util.fileprocessor.FileReference;

public class TestSourceRangeFormats {

    @Test
    public void testDocumentStatusUsesIndexMainFieldAcrossFormatChanges() throws Exception {
        BlackLab.implicitInstance();
        try (var testDir = UtilsForTesting.createBlackLabTestDir("source-status-field-order")) {
            for (int pass = 0; pass < 2; pass++) {
                ConfigInputFormat config = new ConfigInputFormat("source-status-order-" + pass);
                config.setFileType(ConfigInputFormat.FileType.XML);
                config.setStore(false);
                config.setDocumentPath("/doc");
                ConfigAnnotatedField contents = format(true, false).getAnnotatedField("contents");
                ConfigAnnotatedField other = contents.copy();
                other.setName("other");
                for (var field: pass == 0 ? List.of(contents, other) : List.of(other, contents))
                    config.addAnnotatedField(field);
                DocumentFormats.add(config);
                try (var writer = BlackLab.openForWriting(testDir.file(), pass == 0, config)) {
                    if (pass == 0)
                        writer.metadata().setIndexFlag(SourceRangeEncoding.INDEX_FLAG, SourceRangeEncoding.VERSION);
                    Assert.assertEquals("contents", writer.mainAnnotatedField().name());
                    Indexer indexer = Indexer.create(writer, config.getName());
                    try {
                        String xml = pass == 0 ? "<doc><w>A</w><w>B</w></doc>" : "<doc><w>C</w><w>D</w></doc>";
                        indexer.index(FileReference.fromBytes("source", xml.getBytes(StandardCharsets.UTF_8), null),
                                null, FileConverter.ExtraConverters.NONE);
                    } finally {
                        indexer.close();
                    }
                }
            }
            try (BlackLabIndex index = BlackLab.open(testDir.file())) {
                assertRanges(index, "a", true, new int[] {5, 13}, new int[] {13, 21});
                assertRanges(index, "c", true, new int[] {5, 13}, new int[] {13, 21});
            }
        }
    }

    @Test
    public void testMixedSyntaxAndExistingTextRangesSurviveAppend() throws Exception {
        BlackLab.implicitInstance();
        try (var testDir = UtilsForTesting.createBlackLabTestDir("source-range-formats")) {
            for (boolean legacy: List.of(false, true)) {
                for (boolean store: List.of(false, true)) {
                    for (boolean xmlFirst: List.of(false, true)) {
                        File directory = new File(testDir.file(), legacy + "-" + store + "-" + xmlFirst);
                        for (boolean xml: List.of(xmlFirst, !xmlFirst)) {
                            ConfigInputFormat config = format(xml, store);
                            DocumentFormats.add(config);
                            try (var writer = BlackLab.openForWriting(directory, xml == xmlFirst, config)) {
                                if (xml == xmlFirst)
                                    writer.metadata().setIndexFlag(SourceRangeEncoding.INDEX_FLAG,
                                            legacy ? "" : SourceRangeEncoding.VERSION);
                                Indexer indexer = Indexer.create(writer, config.getName());
                                try {
                                    String source = xml ? "<doc><w>A</w><w>B</w></doc>" : "one two";
                                    indexer.index(FileReference.fromBytes("source", source.getBytes(StandardCharsets.UTF_8), null),
                                            null, FileConverter.ExtraConverters.NONE);
                                } finally {
                                    indexer.close();
                                }
                            }
                        }
                        try (BlackLabIndex index = BlackLab.open(directory)) {
                            Assert.assertEquals(2, index.metadata().documentCount());
                            Assert.assertEquals(4, index.metadata().tokenCount());
                            Assert.assertEquals(!legacy, index.metadata().usesSourceRangeVectors());
                            assertRanges(index, "a", true, new int[] {5, 13}, new int[] {13, 21});
                            // Preserve the plain-text indexer's native semantics, not idealized word offsets.
                            int position = store ? 7 : 0;
                            assertRanges(index, "one", false, new int[] {position, position},
                                    new int[] {position, position});
                        }
                    }
                }
            }
        }
    }

    private static ConfigInputFormat format(boolean xml, boolean store) {
        ConfigInputFormat config = new ConfigInputFormat("source-range-" + xml + "-" + store);
        config.setFileType(xml ? ConfigInputFormat.FileType.XML : ConfigInputFormat.FileType.TEXT);
        config.setStore(store);
        config.setDocumentPath("/doc");
        ConfigAnnotatedField contents = new ConfigAnnotatedField("contents");
        contents.setWordPath(".//w");
        ConfigAnnotation word = new ConfigAnnotation();
        word.setName("word");
        word.setValuePath(".");
        contents.addAnnotation(word);
        config.addAnnotatedField(contents);
        return config;
    }

    private static void assertRanges(BlackLabIndex index, String word, boolean xml, int[] expectedStarts,
            int[] expectedEnds) {
        var field = index.mainAnnotatedField();
        var hits = index.find(new BLSpanTermQuery(QueryInfo.create(index), new Term(
                field.mainAnnotation().sensitivity(MatchSensitivity.INSENSITIVE).luceneField(), word)), null).getHits();
        Assert.assertEquals(1, hits.size());
        int docId = hits.get(0).doc();
        int[] starts = {0, 1}, ends = {0, 1};
        DocUtil.characterOffsets(index, docId, field, starts, ends, false);
        Assert.assertArrayEquals(expectedStarts, starts);
        Assert.assertArrayEquals(expectedEnds, ends);
        if (index.metadata().usesSourceRangeVectors())
            Assert.assertEquals(xml, DocUtil.hasStructuralSource(index, docId, field));
    }
}
