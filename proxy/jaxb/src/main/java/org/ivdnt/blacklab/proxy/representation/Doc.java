package org.ivdnt.blacklab.proxy.representation;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;

@XmlAccessorType(XmlAccessType.FIELD)
public class Doc {

    public String docPid;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Long numberOfHits;

    public DocInfo docInfo;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public List<DocResultsHitContext> snippets;

    @SuppressWarnings("unused")
    public Doc() {
        // no-arg ctor required by Jersey
    }

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
