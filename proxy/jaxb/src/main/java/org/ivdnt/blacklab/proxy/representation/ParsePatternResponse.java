package org.ivdnt.blacklab.proxy.representation;

import java.util.Map;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;

import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name="blacklabResponse")
@XmlAccessorType(XmlAccessType.FIELD)
public class ParsePatternResponse {

    // NOTE: JSON only!

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Map<String, String> params;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    SummaryTextPattern parsed;
}
