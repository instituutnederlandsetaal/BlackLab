package org.ivdnt.blacklab.proxy.representation;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name="blacklabResponse")
@XmlAccessorType(XmlAccessType.FIELD)
public class DocContentsResults {

    public String contents;

    @SuppressWarnings("unused")
    public DocContentsResults() {
        // no-arg ctor required by Jersey
    }

    @Override
    public String toString() {
        return "DocContentsResults{" +
                "contents='" + contents + '\'' +
                '}';
    }
}
