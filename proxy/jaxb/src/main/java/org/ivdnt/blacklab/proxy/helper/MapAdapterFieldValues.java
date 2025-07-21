package org.ivdnt.blacklab.proxy.helper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlValue;
import jakarta.xml.bind.annotation.adapters.XmlAdapter;

/**
 * Helps us to (de)serialize the map of fieldValues.
 */
public class MapAdapterFieldValues extends XmlAdapter<MapAdapterFieldValues.WrapperFieldValues, Map<String, Integer>> {
    @Override
    public WrapperFieldValues marshal(Map<String, Integer> m) {
        WrapperFieldValues wrapper = new WrapperFieldValues();
        List<FieldValueFreq> elements = new ArrayList<>();
        for (Map.Entry<String, Integer> property : m.entrySet()) {
            elements.add(new FieldValueFreq(property.getKey(), property.getValue()));
        }
        wrapper.elements = elements;
        return wrapper;
    }

    @Override
    public Map<String, Integer> unmarshal(WrapperFieldValues v) {
        Map<String, Integer> returnval = new LinkedHashMap();
        for (FieldValueFreq e: v.elements) {
            returnval.put(e.text, e.freq);
        }
        return returnval;
    }

    public static class FieldValueFreq {

        @XmlAttribute
        public String text;

        @XmlValue
        public Integer freq;

        public FieldValueFreq(String text, Integer freq) {
            this.text = text;
            this.freq = freq;
        }
    }

    public static class WrapperFieldValues {
        @XmlElement(name = "value")
        public List<FieldValueFreq> elements;
    }
}
