package org.ivdnt.blacklab.proxy.representation;

import java.util.List;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;

import com.fasterxml.jackson.annotation.JsonProperty;

@XmlAccessorType(XmlAccessType.FIELD)
public class AnnotatedFieldAnnotationGroups {

    @XmlAttribute
    public String name;

    //@XmlElementWrapper(name="annotationGroups")
    @XmlElement(name = "annotationGroup")
    @JsonProperty("annotationGroups")
    public List<AnnotationGroup> annotationGroups;

    @Override
    public String toString() {
        return "AnnotatedFieldAnnotationGroups{" +
                "name='" + name + '\'' +
                ", annotationGroups=" + annotationGroups +
                '}';
    }
}
