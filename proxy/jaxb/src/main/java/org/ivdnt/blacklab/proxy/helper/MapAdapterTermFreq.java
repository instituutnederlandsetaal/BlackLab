package org.ivdnt.blacklab.proxy.helper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlValue;
import jakarta.xml.bind.annotation.adapters.XmlAdapter;

import org.ivdnt.blacklab.proxy.helper.MapAdapterTermFreq.WrapperTermFreq;

/**
 * Helps us to (de)serialize the map of fieldValues.
 */
public class MapAdapterTermFreq extends XmlAdapter<WrapperTermFreq, Map<String, Long>> {
    @Override
    public WrapperTermFreq marshal(Map<String, Long> m) {
        WrapperTermFreq wrapper = new WrapperTermFreq();
        List<TermFreq> elements = new ArrayList<>();
        for (Map.Entry<String, Long> property : m.entrySet()) {
            elements.add(new TermFreq(property.getKey(), property.getValue()));
        }
        wrapper.elements = elements;
        return wrapper;
    }

    @Override
    public Map<String, Long> unmarshal(WrapperTermFreq v) {
        Map<String, Long> returnval = new LinkedHashMap();
        for (TermFreq e: v.elements) {
            returnval.put(e.text, e.freq);
        }
        return returnval;
    }

    public static class TermFreq {

        @XmlAttribute
        public String text;

        @XmlValue
        public Long freq;

        public TermFreq(String text, Long freq) {
            this.text = text;
            this.freq = freq;
        }
    }

    public static class WrapperTermFreq {
        @XmlElement(name = "term")
        public List<TermFreq> elements;
    }
}
