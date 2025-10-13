package org.ivdnt.blacklab.proxy.representation;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

@XmlAccessorType(XmlAccessType.FIELD)
public class FieldTokenCount {

    public String fieldName;

    public long tokenCount;

    public FieldTokenCount(String fieldName, long tokenCount) {
        this.fieldName = fieldName;
        this.tokenCount = tokenCount;
    }

    @Override
    public String toString() {
        return "FieldTokenCount{" +
                "fieldName='" + fieldName + '\'' +
                ", tokenCount=" + tokenCount +
                '}';
    }
}
