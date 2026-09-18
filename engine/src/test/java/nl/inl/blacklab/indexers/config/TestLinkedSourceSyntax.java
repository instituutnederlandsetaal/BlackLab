package nl.inl.blacklab.indexers.config;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.Test;

import nl.inl.blacklab.exceptions.InvalidInputFormatConfig;
import nl.inl.blacklab.index.DocWriter;
import nl.inl.blacklab.index.IndexerStats;
import nl.inl.blacklab.search.indexmetadata.IndexMetadataWriter;
import nl.inl.blacklab.search.indexmetadata.RelationsStrategy;
import nl.inl.util.fileprocessor.FileReference;

public class TestLinkedSourceSyntax {

    @Test
    public void testSourceRangeDocumentRejectsMixedLinkedSyntax() {
        assertMixedLinkRejectedBeforeIndexing(true, false);
        assertMixedLinkRejectedBeforeIndexing(false, true);
    }

    @Test
    public void testSameSyntaxAndLegacyLinksRemainAllowed() {
        TestFormat sameSyntax = new TestFormat(true);
        sameSyntax.indexSpecificDocument(writer(true), source(), null, sameSyntax.newDoc(true), null);
        assertTrue(sameSyntax.indexCalled);

        TestFormat legacyMixed = new TestFormat(false);
        legacyMixed.indexSpecificDocument(writer(false), source(), null, legacyMixed.newDoc(true), null);
        assertTrue(legacyMixed.indexCalled);
    }

    private static void assertMixedLinkRejectedBeforeIndexing(boolean outerXml, boolean linkedXml) {
        TestFormat format = new TestFormat(linkedXml);
        DocWriter writer = writer(true);
        assertThrows(InvalidInputFormatConfig.class,
                () -> format.indexSpecificDocument(writer, source(), null, format.newDoc(outerXml), null));
        assertFalse(format.indexCalled);
    }

    private static FileReference source() {
        return FileReference.fromBytes("test", new byte[0], null);
    }

    private static DocWriter writer(boolean sourceRangeVectors) {
        DocWriter writer = mock(DocWriter.class);
        IndexMetadataWriter metadata = mock(IndexMetadataWriter.class);
        when(metadata.usesSourceRangeVectors()).thenReturn(sourceRangeVectors);
        when(writer.metadata()).thenReturn(metadata);
        when(writer.getRelationsStrategy()).thenReturn(RelationsStrategy.forNewIndex());
        return writer;
    }

    private static class TestFormat extends InputFormatTypeBase.InputFormatBase {
        private final boolean linkedXml;
        private boolean indexCalled;

        TestFormat(boolean linkedXml) {
            this.linkedXml = linkedXml;
        }

        TestDoc newDoc(boolean xml) {
            return new TestDoc(null, source(), xml);
        }

        @Override
        protected InputFormatTypeBase.Doc createDoc(DocWriter writer, FileReference file) {
            return new TestDoc(writer, file, linkedXml);
        }

        private class TestDoc extends DocBase {
            private final boolean xml;

            TestDoc(DocWriter writer, FileReference file, boolean xml) {
                super(writer, file);
                this.xml = xml;
            }

            @Override
            public IndexerStats index() {
                indexCalled = true;
                return null;
            }

            @Override
            protected boolean sourceIsXml() {
                return xml;
            }

            @Override
            protected int getCharacterPosition() {
                return 0;
            }

            @Override
            public void storeDocument() {
            }

            @Override
            public void close() {
            }
        }
    }
}
