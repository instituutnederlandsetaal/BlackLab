package org.ivdnt.blacklab.proxy.representation;

import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;

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
