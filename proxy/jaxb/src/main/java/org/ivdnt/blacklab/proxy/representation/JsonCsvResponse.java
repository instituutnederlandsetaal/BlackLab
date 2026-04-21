package org.ivdnt.blacklab.proxy.representation;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name="blacklabResponse")
@XmlAccessorType(XmlAccessType.FIELD)
public class JsonCsvResponse {

    public String csv;

    @SuppressWarnings("unused")
    public JsonCsvResponse() {
        // no-arg ctor required by Jersey
    }

    @Override
    public String toString() {
        return "JsonCsvResponse{" +
                "csv='" + csv + '\'' +
                '}';
    }
}
