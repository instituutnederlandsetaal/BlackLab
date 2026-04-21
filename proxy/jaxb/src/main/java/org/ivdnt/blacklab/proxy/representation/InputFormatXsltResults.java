package org.ivdnt.blacklab.proxy.representation;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name="blacklabResponse")
@XmlAccessorType(XmlAccessType.FIELD)
public class InputFormatXsltResults {

    public String xslt;

    @SuppressWarnings("unused")
    public InputFormatXsltResults() {
        // no-arg ctor required by Jersey
    }

    @Override
    public String toString() {
        return "InputFormatXsltResults{" +
                "xslt='" + xslt + '\'' +
                '}';
    }
}
