package org.ivdnt.blacklab.proxy.representation;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlRootElement;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

@XmlRootElement(name="blacklabResponse")
@XmlAccessorType(XmlAccessType.FIELD)
public class CorpusStatus {

    public String indexName = "";

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String displayName;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String description;

    public String status = "available";

    public String documentFormat = "";

    @JsonInclude(Include.NON_NULL)
    public String timeModified;

    public long tokenCount = 0;

    // required for Jersey
    CorpusStatus() {}

    public CorpusStatus(String name, String displayName, String documentFormat) {
        this.indexName = name;
        this.displayName = displayName;
        this.documentFormat = documentFormat;
    }

    @Override
    public String toString() {
        return "CorpusStatus{" +
                "indexName='" + indexName + '\'' +
                ", displayName='" + displayName + '\'' +
                ", description='" + description + '\'' +
                ", status='" + status + '\'' +
                ", documentFormat='" + documentFormat + '\'' +
                ", timeModified='" + timeModified + '\'' +
                ", tokenCount=" + tokenCount +
                '}';
    }
}
