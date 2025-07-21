package org.ivdnt.blacklab.proxy.representation;

import java.util.Map;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlRootElement;

import com.fasterxml.jackson.annotation.JsonInclude;

@XmlRootElement(name="blacklabResponse")
@XmlAccessorType(XmlAccessType.FIELD)
public class RelationsResponse {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Map<String, RelationType> spans;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Map<String, Map<String, RelationType>> relations;
}
