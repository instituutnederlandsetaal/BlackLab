package org.ivdnt.blacklab.solr;

import org.apache.lucene.codecs.Codec;
import org.apache.solr.core.CodecFactory;

import nl.inl.blacklab.codec.blacklab60.BlackLab60Codec;

public class BL60CodecFactory extends CodecFactory {

    @Override
    public Codec getCodec() {
        return new BlackLab60Codec();
    }
}
