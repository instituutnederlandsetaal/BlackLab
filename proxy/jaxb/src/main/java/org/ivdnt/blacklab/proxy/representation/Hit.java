package org.ivdnt.blacklab.proxy.representation;

import java.util.List;
import java.util.Map;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElementWrapper;
import jakarta.xml.bind.annotation.XmlTransient;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@XmlAccessorType(XmlAccessType.FIELD)
public class Hit  {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String docPid;

    @XmlTransient
    public DocInfo docInfo;

    public long start;

    public long end;

    @XmlElementWrapper(name="captureGroups")
    @XmlElement(name = "captureGroup")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty("captureGroups")
    public List<CaptureGroup> captureGroups;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty("matchInfos")
    public Map<String, MatchInfo> matchInfo; // @@ why not matchInfos here? for XML serialization..?

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public ContextWords left;

    public ContextWords match;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public ContextWords right;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Map<String, Hit> otherFields; // @@ check XML serialization

    // required for Jersey
    public Hit() {}

    public Hit(String docPid, long start, long end) {
        this.docPid = docPid;
        this.start = start;
        this.end = end;
    }

    @Override
    public String toString() {
        return "Hit{" +
                "docPid='" + docPid + '\'' +
                ", docInfo=" + docInfo +
                ", start=" + start +
                ", end=" + end +
                ", captureGroups=" + captureGroups +
                ", matchInfo=" + matchInfo +
                ", left=" + left +
                ", match=" + match +
                ", right=" + right +
                '}';
    }
}
