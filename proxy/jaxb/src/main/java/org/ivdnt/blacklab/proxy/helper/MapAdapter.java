package org.ivdnt.blacklab.proxy.helper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.annotation.XmlAnyElement;
import jakarta.xml.bind.annotation.adapters.XmlAdapter;
import javax.xml.namespace.QName;

import org.w3c.dom.Element;

/**
 * Helps us to (de)serialize a map where the keys become element names in XML.
 */
public class MapAdapter extends XmlAdapter<MapAdapter.MapWrapper, Map<String, Object>> {
    @Override
    public MapWrapper marshal(Map<String, Object> m) {
        MapWrapper wrapper = new MapWrapper();
        if (m != null) {
            List elements = new ArrayList();
            for (Map.Entry<String, Object> property: m.entrySet()) {

                if (property.getValue() instanceof Map) {
                    elements.add(new JAXBElement<>(new QName(SerializationUtil.getCleanLabel(property.getKey())),
                            MapWrapper.class, marshal((Map) property.getValue())));
                } else {
                    elements.add(new JAXBElement<>(new QName(SerializationUtil.getCleanLabel(property.getKey())),
                            String.class, property.getValue().toString()));
                }
            }
            wrapper.elements = elements;
        }
        return wrapper;
    }

    @Override
    public Map<String, Object> unmarshal(MapWrapper v) {
        if (v.elements == null)
            return null;
        Map<String, Object> returnval = new LinkedHashMap();
        for (Object o : v.elements) {
            Element e = (Element) o;
            if (e.getChildNodes().getLength() > 1) {
                MapWrapper mw = new MapWrapper();
                mw.elements = new ArrayList();
                for (int count = 0; count < e.getChildNodes().getLength(); count++) {
                    if (e.getChildNodes().item(count) instanceof Element) {
                        mw.elements.add(e.getChildNodes().item(count));
                    }
                }
                returnval.put(e.getTagName(), unmarshal(mw));
            } else {
                returnval.put(e.getTagName(), e.getTextContent());
            }
        }
        return returnval;
    }

    public static class MapWrapper {
        @XmlAnyElement
        public List elements;
    }
}
