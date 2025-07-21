package org.ivdnt.blacklab.proxy.representation;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;

@XmlAccessorType(XmlAccessType.FIELD)
public class RelationType {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public long count;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Map<String, AttributeType> attributes;
}
