package nl.inl.blacklab.indexers.config.saxon;

import net.sf.saxon.Configuration;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.XPathCompiler;

/**
 * A helper for indexing using Saxon.
 */
public class SaxonHelper {
    static Processor saxonProcessor = new Processor(false);
    static {
        // Enable line numbering for all documents built with this configuration
        Configuration config = saxonProcessor.getUnderlyingConfiguration();
        config.setLineNumbering(true);
    }

    private SaxonHelper() {}

    public static XPathCompiler newXPathFactory() {
        return saxonProcessor.newXPathCompiler();
    }

    public static Processor getProcessor() {
        return saxonProcessor;
    }
}
