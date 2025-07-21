package org.ivdnt.blacklab.proxy.representation;

import java.util.List;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;

import com.fasterxml.jackson.annotation.JsonInclude;

@XmlAccessorType(XmlAccessType.FIELD)
public class Doc {

    public String docPid;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Long numberOfHits;

    public DocInfo docInfo;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public List<DocResultsHitContext> snippets;

    // required for Jersey
    public Doc() {}

    @Override
    public String toString() {
        return "Doc{" +
                "docPid='" + docPid + '\'' +
                ", numberOfHits=" + numberOfHits +
                ", docInfo=" + docInfo +
                ", snippets=" + snippets +
                '}';
    }
}
