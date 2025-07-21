package org.ivdnt.blacklab.proxy.representation;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;

@XmlAccessorType(XmlAccessType.FIELD)
public class SummaryTextPattern {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String bcql;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String fieldName;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Object json;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String error;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Map<String, MatchInfoDef> matchInfos;

}
