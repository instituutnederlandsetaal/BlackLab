package org.ivdnt.blacklab.solr;

import org.apache.lucene.codecs.Codec;
import org.apache.solr.core.CodecFactory;

import nl.inl.blacklab.codec.blacklab50.BlackLab50Codec;

public class BL50CodecFactory extends CodecFactory {

    @Override
    public Codec getCodec() {
        return new BlackLab50Codec();
    }
}
