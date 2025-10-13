package org.ivdnt.blacklab.proxy.representation;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;

import com.fasterxml.jackson.annotation.JsonInclude;

@XmlAccessorType(XmlAccessType.FIELD)
public class VersionInfo {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String blacklabBuildTime;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String blacklabVersion;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String blacklabScmRevision;

    // v3 inconsistent naming
    @XmlElement
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String blackLabBuildTime;

    // v3 inconsistent naming
    @XmlElement
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String blackLabVersion;

    public String indexFormat = "3.1";

    public String timeCreated = "";

    public String timeModified = "";

    @Override
    public String toString() {
        return "VersionInfo{" +
                "blacklabBuildTime='" + blacklabBuildTime + '\'' +
                ", blacklabVersion='" + blacklabVersion + '\'' +
                ", blacklabScmRevision='" + blacklabScmRevision + '\'' +
                ", blackLabBuildTime='" + blackLabBuildTime + '\'' +
                ", blackLabVersion='" + blackLabVersion + '\'' +
                ", indexFormat='" + indexFormat + '\'' +
                ", timeCreated='" + timeCreated + '\'' +
                ", timeModified='" + timeModified + '\'' +
                '}';
    }
}
