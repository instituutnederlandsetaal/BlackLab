package org.ivdnt.blacklab.proxy.representation;


import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;

@XmlAccessorType(XmlAccessType.FIELD)
public class MatchInfoDef {

    public String type;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String fieldName;

}
