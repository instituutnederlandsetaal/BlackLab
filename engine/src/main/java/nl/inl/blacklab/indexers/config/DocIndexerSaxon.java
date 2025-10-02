package nl.inl.blacklab.indexers.config;

import nl.inl.blacklab.index.DocWriter;

/**
 * Synonym for DocIndexerXPath, for historical reasons.
 */
public class DocIndexerSaxon extends DocIndexerXPath {
    public DocIndexerSaxon(DocWriter docWriter) {
        super(docWriter);
    }
}
