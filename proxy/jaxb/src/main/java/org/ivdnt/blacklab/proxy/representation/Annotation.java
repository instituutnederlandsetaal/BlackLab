package org.ivdnt.blacklab.proxy.representation;

import java.util.List;
import java.util.Map;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import org.ivdnt.blacklab.proxy.helper.MapAdapter;
import org.ivdnt.blacklab.proxy.helper.SerializationUtil;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@XmlAccessorType(XmlAccessType.FIELD)
@JsonIgnoreProperties(value = { "name" }) // don't serialize name, it is used as the key
public class Annotation {

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

    @XmlJavaTypeAdapter(MapAdapter.class)
    @JsonSerialize(using = SerializationUtil.TermFreqMapSerializer.class)
    @JsonDeserialize(using = SerializationUtil.TermFreqMapDeserializer.class)
    @JsonInclude(Include.NON_NULL)
    public Map<String, Long> terms;

    @XmlElementWrapper(name="values")
    @XmlElement(name = "value")
    @JsonInclude(Include.NON_NULL)
    @JsonProperty("values")
    public List<String> values;

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
