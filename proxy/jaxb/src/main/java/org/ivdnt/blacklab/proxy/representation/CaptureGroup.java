package org.ivdnt.blacklab.proxy.representation;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

@XmlAccessorType(XmlAccessType.FIELD)
public class CaptureGroup {
    public String name;

    public long start;

    public long end;

    @Override
    public String toString() {
        return "CaptureGroup{" +
                "name='" + name + '\'' +
                ", start=" + start +
                ", end=" + end +
                '}';
    }
}
