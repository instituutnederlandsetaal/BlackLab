package org.ivdnt.blacklab.proxy.representation;

import java.util.List;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElementWrapper;

import com.fasterxml.jackson.annotation.JsonProperty;

@XmlAccessorType(XmlAccessType.FIELD)
public class MetadataFieldGroup {

    public String name = "group1";

    @XmlElementWrapper(name="fields")
    @XmlElement(name = "field")
    @JsonProperty("fields")
    public List<String> fields;

    @Override
    public String toString() {
        return "MetadataFieldGroup{" +
                "name='" + name + '\'' +
                ", fields=" + fields +
                '}';
    }
}
