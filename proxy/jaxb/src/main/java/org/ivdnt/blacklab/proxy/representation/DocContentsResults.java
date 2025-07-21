package org.ivdnt.blacklab.proxy.representation;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name="blacklabResponse")
@XmlAccessorType(XmlAccessType.FIELD)
public class DocContentsResults {

    public String contents;

    // required for Jersey
    public DocContentsResults() {}

    @Override
    public String toString() {
        return "DocContentsResults{" +
                "contents='" + contents + '\'' +
                '}';
    }
}
