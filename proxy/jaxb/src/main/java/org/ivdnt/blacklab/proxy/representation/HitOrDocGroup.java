package org.ivdnt.blacklab.proxy.representation;


import java.util.List;
import java.util.Map;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

@XmlAccessorType(XmlAccessType.FIELD)
public class HitOrDocGroup {
    public String identity;

    public String identityDisplay;

    public long size;

    public List<Property> properties;

    @JsonInclude(Include.NON_NULL)
    public Long numberOfDocs;

    @JsonInclude(Include.NON_NULL)
    public Long numberOfTokens;

    @JsonInclude(Include.NON_NULL)
    public Map<String, Long> subcorpusSize;

    public HitOrDocGroup() {}

    public HitOrDocGroup(String identity, String identityDisplay, long size, List<Property> properties, long numberOfDocs,
            Map<String, Long> subcorpusSize) {
        this.identity = identity;
        this.identityDisplay = identityDisplay;
        this.size = size;
        this.properties = properties;
        this.numberOfDocs = numberOfDocs;
        this.subcorpusSize = subcorpusSize;
    }

    @Override
    public String toString() {
        return "HitGroup{" +
                "identity='" + identity + '\'' +
                ", identityDisplay='" + identityDisplay + '\'' +
                ", size=" + size +
                ", properties=" + properties +
                ", numberOfDocs=" + numberOfDocs +
                ", subcorpusSize=" + subcorpusSize +
                '}';
    }

    public String getIdentity() {
        return identity;
    }

    public void setIdentity(String identity) {
        this.identity = identity;
    }

    public String getIdentityDisplay() {
        return identityDisplay;
    }

    public void setIdentityDisplay(String identityDisplay) {
        this.identityDisplay = identityDisplay;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public List<Property> getProperties() {
        return properties;
    }

    public void setProperties(List<Property> properties) {
        this.properties = properties;
    }

    public long getNumberOfDocs() {
        return numberOfDocs;
    }

    public void setNumberOfDocs(long numberOfDocs) {
        this.numberOfDocs = numberOfDocs;
    }
}
