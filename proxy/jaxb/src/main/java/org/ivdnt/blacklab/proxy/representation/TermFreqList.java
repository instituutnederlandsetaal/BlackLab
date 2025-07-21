package org.ivdnt.blacklab.proxy.representation;

import java.util.Map;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import org.ivdnt.blacklab.proxy.helper.MapAdapterTermFreq;
import org.ivdnt.blacklab.proxy.helper.SerializationUtil;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@XmlRootElement(name="blacklabResponse")
@XmlAccessorType(XmlAccessType.FIELD)
public class TermFreqList {

    @XmlJavaTypeAdapter(MapAdapterTermFreq.class)
    @JsonSerialize(using = SerializationUtil.TermFreqMapSerializer.class)
    @JsonDeserialize(using = SerializationUtil.TermFreqMapDeserializer.class)
    public Map<String, Long> termFreq;
}
