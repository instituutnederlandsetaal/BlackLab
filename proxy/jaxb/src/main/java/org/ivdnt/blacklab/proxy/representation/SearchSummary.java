package org.ivdnt.blacklab.proxy.representation;

import java.util.Map;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import org.apache.commons.lang3.StringUtils;
import org.ivdnt.blacklab.proxy.helper.MapAdapter;
import org.ivdnt.blacklab.proxy.helper.SerializationUtil;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@XmlAccessorType(XmlAccessType.FIELD)
public class SearchSummary {

    @JsonInclude(Include.NON_NULL)
    @XmlJavaTypeAdapter(MapAdapter.class)
    @JsonSerialize(using = SerializationUtil.StringMapSerializer.class)
    @JsonDeserialize(using = SerializationUtil.StringMapDeserializer.class)
    public Map<String, String> searchParam;

    @JsonInclude(Include.NON_NULL)
    public SummaryTextPattern pattern;

    public long searchTime;

    @JsonInclude(Include.NON_NULL)
    public Long countTime;

    @JsonInclude(Include.NON_NULL)
    public Long numberOfGroups;

    @JsonInclude(Include.NON_NULL)
    public Long largestGroupSize;

    public long windowFirstResult;

    public long requestedWindowSize;

    public long actualWindowSize;

    public boolean windowHasPrevious;

    public boolean windowHasNext;

    public boolean stillCounting;

    @JsonInclude(Include.NON_NULL)
    public Long numberOfHits;

    @JsonInclude(Include.NON_NULL)
    public Long numberOfHitsRetrieved;

    @JsonInclude(Include.NON_NULL)
    public Boolean stoppedCountingHits;

    @JsonInclude(Include.NON_NULL)
    public Boolean stoppedRetrievingHits;

    public long numberOfDocs;

    public long numberOfDocsRetrieved;

    @JsonInclude(Include.NON_NULL)
    public Long tokensInMatchingDocuments;

    @JsonInclude(Include.NON_NULL)
    public Map<String, Long> subcorpusSize;

    @JsonInclude(Include.NON_NULL)
    public SpecialFieldInfo docFields;

    @JsonInclude(Include.NON_NULL)
    @XmlJavaTypeAdapter(MapAdapter.class)
    @JsonSerialize(using = SerializationUtil.StringMapSerializer.class)
    @JsonDeserialize(using = SerializationUtil.StringMapDeserializer.class)
    public Map<String, String> metadataFieldDisplayNames;

    @Override
    public String toString() {
        return "SearchSummary{" +
                "searchParam=" + StringUtils.join(searchParam) +
                ", searchTime=" + searchTime +
                ", countTime=" + countTime +
                ", numberOfGroups=" + numberOfGroups +
                ", windowFirstResult=" + windowFirstResult +
                ", requestedWindowSize=" + requestedWindowSize +
                ", actualWindowSize=" + actualWindowSize +
                ", windowHasPrevious=" + windowHasPrevious +
                ", windowHasNext=" + windowHasNext +
                ", stillCounting=" + stillCounting +
                ", numberOfHits=" + numberOfHits +
                ", numberOfHitsRetrieved=" + numberOfHitsRetrieved +
                ", stoppedCountingHits=" + stoppedCountingHits +
                ", stoppedRetrievingHits=" + stoppedRetrievingHits +
                ", numberOfDocs=" + numberOfDocs +
                ", numberOfDocsRetrieved=" + numberOfDocsRetrieved +
                ", subcorpusSize=" + subcorpusSize +
                ", docFields=" + docFields +
                ", metadataFieldDisplayNames=" + metadataFieldDisplayNames +
                '}';
    }
}
