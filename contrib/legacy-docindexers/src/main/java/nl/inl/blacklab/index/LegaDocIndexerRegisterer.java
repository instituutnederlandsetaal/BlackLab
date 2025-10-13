package nl.inl.blacklab.index;

import nl.inl.blacklab.indexers.DocIndexerAlto;
import nl.inl.blacklab.indexers.DocIndexerFolia;
import nl.inl.blacklab.indexers.DocIndexerPageXml;
import nl.inl.blacklab.indexers.DocIndexerTei;
import nl.inl.blacklab.indexers.DocIndexerTeiPosInFunctionAttr;
import nl.inl.blacklab.indexers.DocIndexerTeiText;
import nl.inl.blacklab.indexers.DocIndexerXmlSketch;

public class LegaDocIndexerRegisterer {
    private LegaDocIndexerRegisterer() {
    }

    public static void register() {
        // Note that these names should not collide with the builtin config-based formats, or those will be used instead.

        // Some abbreviations for commonly used builtin DocIndexers.
        // You can also specify the classname for builtin DocIndexers,
        // or a fully-qualified name for your custom DocIndexer (must
        // be on the classpath)
        DocumentFormats.add("alto", DocIndexerAlto.class);
        DocumentFormats.add("di-folia", DocIndexerFolia.class);
        DocumentFormats.add("pagexml", DocIndexerPageXml.class);
        DocumentFormats.add("sketchxml", DocIndexerXmlSketch.class);

        // TEI has a number of variants
        // By default, the contents of the "body" element are indexed, but alternatively you can index the contents of "text".
        // By default, the "type" attribute is assumed to contain PoS, but alternatively you can use the "function" attribute.
        DocumentFormats.add("di-tei", DocIndexerTei.class);
        DocumentFormats.add("di-tei-element-text", DocIndexerTeiText.class);
        DocumentFormats.add("di-tei-pos-function", DocIndexerTeiPosInFunctionAttr.class);
    }
}
