package org.ivdnt.blacklab.proxy.representation;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

@XmlRootElement(name="blacklabResponse")
@XmlAccessorType(XmlAccessType.FIELD)
@JsonIgnoreProperties(value = { "name" })
public class CorpusSummary {

    @XmlAttribute
    @JsonInclude(Include.NON_NULL)
    public String name;

    @JsonInclude(Include.NON_NULL)
    public String displayName;

    @JsonInclude(Include.NON_NULL)
    public String description;

    public String status = "available";

    public String documentFormat = "";

    @JsonInclude(Include.NON_EMPTY)
    public String timeModified = "";

    public long tokenCount = 0;

    // required for Jersey
    CorpusSummary() {}

    public CorpusSummary(String name, String displayName, String documentFormat) {
        this.name = name;
        this.displayName = displayName;
        this.documentFormat = documentFormat;
    }

    @Override
    public String toString() {
        return "CorpusSummary{" +
                "name='" + name + '\'' +
                ", displayName='" + displayName + '\'' +
                ", description='" + description + '\'' +
                ", status='" + status + '\'' +
                ", documentFormat='" + documentFormat + '\'' +
                ", timeModified='" + timeModified + '\'' +
                ", tokenCount=" + tokenCount +
                '}';
    }
}
