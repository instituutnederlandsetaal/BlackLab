package org.ivdnt.blacklab.proxy.representation;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;

@XmlAccessorType(XmlAccessType.FIELD)
public class FacetValue {

    public String value;

    public long size;

    public FacetValue() {}

    @Override
    public String toString() {
        return "FacetValue{" +
                "value='" + value + '\'' +
                ", size=" + size +
                '}';
    }
}
