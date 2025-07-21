package org.ivdnt.blacklab.proxy.helper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.xml.namespace.QName;

import org.ivdnt.blacklab.proxy.representation.DocInfo;
import org.ivdnt.blacklab.proxy.representation.FieldTokenCount;
import org.ivdnt.blacklab.proxy.representation.MetadataValues;

import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.annotation.XmlAnyElement;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlSeeAlso;
import jakarta.xml.bind.annotation.adapters.XmlAdapter;

/**
 * Helps us to (de)serialize DocInfo in XML.
 */
public class DocInfoAdapter extends XmlAdapter<DocInfoAdapter.DocInfoWrapper, DocInfo> {

    @XmlSeeAlso({MetadataValues.class})
    public static class DocInfoWrapper {
        @XmlAttribute
        public String pid;

        @XmlAnyElement
        public List<JAXBElement<?>> elements;
    }

    @Override
    public DocInfoWrapper marshal(DocInfo m) {
        DocInfoWrapper wrapper = new DocInfoWrapper();
        wrapper.pid = m.pid;
        wrapper.elements = new ArrayList<>();
        for (Map.Entry<String, MetadataValues> e: m.metadata.entrySet()) {
            QName elName = new QName(SerializationUtil.getCleanLabel(e.getKey()));
            JAXBElement<?> jaxbElement = new JAXBElement<>(elName, MetadataValues.class,
                    e.getValue());
            wrapper.elements.add(jaxbElement);
        }
        wrapper.elements.add(new JAXBElement<>(new QName("lengthInTokens"), Long.class, m.lengthInTokens));
        wrapper.elements.add(new JAXBElement<>(new QName("mayView"), Boolean.class, m.mayView));
        if (m.tokenCounts != null) {
            wrapper.elements.add(new JAXBElement<>(new QName("tokenCounts"), List.class, m.tokenCounts));
        }
        return wrapper;
    }

    @Override
    public DocInfo unmarshal(DocInfoWrapper wrapper) {
        DocInfo docInfo = new DocInfo();
        for (JAXBElement<?> element: wrapper.elements) {
            String name = element.getName().getLocalPart();
            switch (name) {
            case "lengthInTokens" -> docInfo.lengthInTokens = (Long) element.getValue();
            case "mayView" -> docInfo.mayView = (Boolean) element.getValue();
            case "tokenCounts" -> docInfo.tokenCounts = (List<FieldTokenCount>) element.getValue();
            default ->
                // Actual metadata value.
                    docInfo.metadata.put(name, (MetadataValues) element.getValue());
            }
        }
        return docInfo;
    }
}
