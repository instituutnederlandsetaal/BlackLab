package org.ivdnt.blacklab.proxy.representation;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@XmlAccessorType(XmlAccessType.FIELD)
@JsonIgnoreProperties(value = { "name" })
public class InputFormat {
    @XmlAttribute
    public String name;

    public String displayName;

    public String description;

    public String helpUrl;

    public boolean configurationBased;

    public boolean isVisible;
}
