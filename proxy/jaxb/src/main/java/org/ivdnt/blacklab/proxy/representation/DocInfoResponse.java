package org.ivdnt.blacklab.proxy.representation;

import java.util.List;
import java.util.Map;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElementWrapper;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import org.ivdnt.blacklab.proxy.helper.MapAdapter;
import org.ivdnt.blacklab.proxy.helper.SerializationUtil;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@XmlRootElement(name="blacklabResponse")
@XmlAccessorType(XmlAccessType.FIELD)
public class DocInfoResponse {

    public String docPid;

    public DocInfo docInfo;

    @XmlElementWrapper(name="metadataFieldGroups")
    @XmlElement(name = "metadataFieldGroup")
    @JsonProperty("metadataFieldGroups")
    @JsonInclude(Include.NON_NULL)
    public List<MetadataFieldGroup> metadataFieldGroups;

    @JsonInclude(Include.NON_NULL)
    public SpecialFieldInfo docFields;

    @XmlJavaTypeAdapter(MapAdapter.class)
    @JsonSerialize(using = SerializationUtil.StringMapSerializer.class)
    @JsonDeserialize(using = SerializationUtil.StringMapDeserializer.class)
    @JsonInclude(Include.NON_NULL)
    public Map<String, String> metadataFieldDisplayNames;

    public DocInfoResponse() {}

    @Override public String toString() {
        return "DocOverview{" +
                "docPid='" + docPid + '\'' +
                ", docInfo=" + docInfo +
                (metadataFieldGroups == null ? "" : ", metadataFieldGroups=" + metadataFieldGroups) +
                (docFields == null ? "" : ", docFields=" + docFields) +
                (metadataFieldDisplayNames == null ? "" : ", metadataFieldDisplayNames=" + metadataFieldDisplayNames) +
                '}';
    }
}
