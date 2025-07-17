package org.ivdnt.blacklab.proxy.representation;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

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

    /** How many tokens are in the corpus?
     * If there are multiple annotated fields (such as in a parallel corpus),
     * this is the total across all fields.
     */
    public long tokenCount = 0;

    /** How many documents are in the corpus?
     * (NOTE: a document with several parallel versions counts as 1; see docVersions) */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Long documentCount;

    /** (Parallel) how many total document versions are in the corpus? */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Long docVersions;

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
