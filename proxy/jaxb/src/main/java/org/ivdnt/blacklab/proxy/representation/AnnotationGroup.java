package org.ivdnt.blacklab.proxy.representation;

import java.util.ArrayList;
import java.util.List;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElementWrapper;

import com.fasterxml.jackson.annotation.JsonProperty;

@XmlAccessorType(XmlAccessType.FIELD)
public class AnnotationGroup {

    public String name;

    @XmlElementWrapper(name="annotations")
    @XmlElement(name = "annotation")
    @JsonProperty("annotations")
    public List<String> annotations = new ArrayList<>();

    @Override
    public String toString() {
        return "AnnotationGroup{" +
                "name='" + name + '\'' +
                ", annotations=" + annotations +
                '}';
    }
}
