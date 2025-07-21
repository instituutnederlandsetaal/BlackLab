package org.ivdnt.blacklab.proxy.representation;

import java.util.List;
import java.util.Map;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElementWrapper;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@XmlAccessorType(XmlAccessType.FIELD)
public class JsonQueryStructure {

    String type;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    String adjust;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    Integer adjustLeading;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    Integer adjustTrailing;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    String annotation;

    @XmlElementWrapper(name = "args")
    @XmlElement(name = "arg")
    @JsonProperty("args")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    List<Object> args;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    Map<String, String> attributes;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    String capture;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    List<String> captures;

    @XmlElementWrapper(name = "children")
    @XmlElement(name = "child")
    @JsonProperty("children")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    List<JsonQueryStructure> children;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    JsonQueryStructure clause;

    @XmlElementWrapper(name = "clauses")
    @XmlElement(name = "clause")
    @JsonProperty("clauses")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    List<JsonQueryStructure> clauses;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    JsonQueryStructure constraint;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    String direction;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    Integer end;

    @XmlElementWrapper(name = "excludes")
    @XmlElement(name = "exclude")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    List<JsonQueryStructure> exclude;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    JsonQueryStructure filter;

    @XmlElementWrapper(name = "includes")
    @XmlElement(name = "include")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    List<JsonQueryStructure> include;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    Boolean invert;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    Integer max;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    Integer min;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    String name;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    Boolean negate;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    String operation;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    JsonQueryStructure parent;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    JsonQueryStructure producer;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    String spanmode;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    String reltype;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    String sensitivity;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    Integer start;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    Boolean trailingEdge;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    String value;
}
