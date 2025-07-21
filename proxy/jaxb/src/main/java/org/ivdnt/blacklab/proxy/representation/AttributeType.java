package org.ivdnt.blacklab.proxy.representation;

import java.util.Map;


import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;

@XmlAccessorType(XmlAccessType.FIELD)
public class AttributeType {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Boolean valueListComplete;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Map<String, Long> values;
}
