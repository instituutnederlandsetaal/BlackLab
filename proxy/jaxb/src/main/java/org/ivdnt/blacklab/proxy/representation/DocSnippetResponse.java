package org.ivdnt.blacklab.proxy.representation;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlRootElement;

import com.fasterxml.jackson.annotation.JsonInclude;

@XmlRootElement(name="blacklabResponse")
@XmlAccessorType(XmlAccessType.FIELD)
public class DocSnippetResponse {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public ContextWords snippet;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public ContextWords left;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public ContextWords match;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public ContextWords right;

    public DocSnippetResponse() {}

    @Override
    public String toString() {
        return "DocSnippetResponse{" +
                "snippet=" + snippet +
                '}';
    }
}
