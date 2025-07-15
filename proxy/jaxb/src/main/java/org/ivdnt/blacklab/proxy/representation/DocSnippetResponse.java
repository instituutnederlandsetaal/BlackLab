package org.ivdnt.blacklab.proxy.representation;

import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name="blacklabResponse")
@XmlAccessorType(XmlAccessType.FIELD)
public class DocSnippetResponse {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String docPid;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public long start;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public long end;

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
