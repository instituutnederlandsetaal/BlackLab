package org.ivdnt.blacklab.proxy.representation;

import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@XmlAccessorType(XmlAccessType.FIELD)
@JsonIgnoreProperties(value = { "name" }) // don't serialize name, it is used as the key
public class Annotation implements Cloneable {

    @XmlAttribute
    public String name;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String displayName = "";

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String description = "";

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String uiType = "";

    public boolean hasForwardIndex;

    public String sensitivity;

    public String offsetsAlternative;

    public boolean isInternal;

    @XmlElementWrapper(name="values")
    @XmlElement(name = "value")
    @JsonInclude(Include.NON_NULL)
    @JsonProperty("values")
    public List<String> values;   ///@@@@ UPDATE: new API has "terms", including frequencies

    @JsonInclude(Include.NON_NULL)
    public Boolean valueListComplete;

    @XmlElementWrapper(name="subannotations")
    @XmlElement(name = "subannotation")
    @JsonProperty("subannotations")
    @JsonInclude(Include.NON_NULL)
    public List<String> subannotations;

    @JsonInclude(Include.NON_NULL)
    public String parentAnnotation;

    @Override
    public Annotation clone() {
        try {
            return (Annotation)super.clone();
        } catch (CloneNotSupportedException e) {
            throw new UnsupportedOperationException(e);
        }
    }

    @Override
    public String toString() {
        return "Annotation{" +
                "name='" + name + '\'' +
                ", displayName='" + displayName + '\'' +
                ", description='" + description + '\'' +
                ", uiType='" + uiType + '\'' +
                ", hasForwardIndex=" + hasForwardIndex +
                ", sensitivity='" + sensitivity + '\'' +
                ", offsetsAlternative='" + offsetsAlternative + '\'' +
                ", isInternal=" + isInternal +
                ", subannotations=" + subannotations +
                ", parentAnnotation=" + parentAnnotation +
                '}';
    }
}
