package org.ivdnt.blacklab.proxy.representation;

import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;

@XmlAccessorType(XmlAccessType.FIELD)
public class SpecialFieldInfo {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String pidField;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String titleField;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String authorField;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String dateField;

    @SuppressWarnings("unused")
    public SpecialFieldInfo() {
        // no-arg ctor required by Jersey
    }

    public SpecialFieldInfo(String pidField, String titleField) {
        this.pidField = pidField;
        this.titleField = titleField;
    }

    @Override
    public String toString() {
        return "FieldInfo{" +
                "pidField='" + pidField + '\'' +
                ", titleField='" + titleField + '\'' +
                ", authorField='" + authorField + '\'' +
                ", dateField='" + dateField + '\'' +
                '}';
    }
}
