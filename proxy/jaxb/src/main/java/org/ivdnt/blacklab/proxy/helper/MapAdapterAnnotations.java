package org.ivdnt.blacklab.proxy.helper;

import java.util.HashMap;
import java.util.Map;

import jakarta.xml.bind.annotation.XmlAnyAttribute;
import jakarta.xml.bind.annotation.XmlValue;
import jakarta.xml.bind.annotation.adapters.XmlAdapter;
import javax.xml.namespace.QName;

import org.ivdnt.blacklab.proxy.representation.Word;

/**
 * Helps us to (de)serialize a map where the keys become element names in XML.
 */
public class MapAdapterAnnotations extends XmlAdapter<MapAdapterAnnotations.MapWrapperWord, Word> {
    @Override
    public MapWrapperWord marshal(Word input) {
        MapWrapperWord result = new MapWrapperWord();
        result.otherAnnotations = new HashMap<>();
        input.otherAnnotations.forEach( (k, v) -> result.otherAnnotations.put(new QName(SerializationUtil.getCleanLabel(k)), v));
        result.mainAnnotation = input.mainAnnotation;
        return result;
    }

    @Override
    public Word unmarshal(MapWrapperWord input) {
        Word result = new Word();
        input.otherAnnotations.forEach( (k, v) -> result.otherAnnotations.put(k.getLocalPart(), v));
        result.mainAnnotation = input.mainAnnotation;
        return result;
    }

    public static class MapWrapperWord {

        @XmlValue
        public String mainAnnotation;

        @XmlAnyAttribute
        public Map<QName, String> otherAnnotations;
    }
}
