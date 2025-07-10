package org.ivdnt.blacklab.proxy.representation;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

import org.ivdnt.blacklab.proxy.helper.ErrorReadingResponse;
import org.ivdnt.blacklab.proxy.helper.SerializationUtil;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import it.unimi.dsi.fastutil.BigList;
import it.unimi.dsi.fastutil.objects.ObjectBigArrayBigList;

@XmlRootElement(name="blacklabResponse")
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(propOrder={"summary", "hits", "docInfos", "hitGroups", "facets" })
public class HitsResults implements EntityWithSummary {

    @Override
    public SearchSummary getSummary() {
        return summary;
    }

    private static class HitListSerializer extends JsonSerializer<BigList<Hit>> {
        @Override
        public void serialize(BigList<Hit> value, JsonGenerator jgen, SerializerProvider provider)
                throws IOException {
            if (value == null)
                return;
            jgen.writeStartArray();
            for (Hit h: value) {
                provider.defaultSerializeValue(h, jgen);
            }
            jgen.writeEndArray();
        }
    }

    private static class HitListDeserializer extends JsonDeserializer<BigList<Hit>> {
        @Override
        public BigList<Hit> deserialize(JsonParser parser, DeserializationContext deserializationContext)
                throws IOException {
            JsonToken token = parser.getCurrentToken();
            if (token != JsonToken.START_ARRAY)
                throw new ErrorReadingResponse("Expected START_ARRAY, found " + token);

            BigList<Hit> hits = new ObjectBigArrayBigList<>();
            while (true) {
                token = parser.nextToken();
                if (token == JsonToken.END_ARRAY)
                    break;
                Hit h = deserializationContext.readValue(parser, Hit.class);
                hits.add(h);
            }
            return hits;
        }
    }

    /** Use this to serialize this class to JSON */
    private static class DocInfosSerializer extends JsonSerializer<List<DocInfo>> {
        @Override
        public void serialize(List<DocInfo> value, JsonGenerator jgen, SerializerProvider provider)
                throws IOException {

            if (value == null)
                return;
            jgen.writeStartObject();
            for (DocInfo el: value) {
                String pid = el.pid;
                if (pid == null)
                    pid = "UNKNOWN";
                jgen.writeObjectFieldStart(pid);
                for (Map.Entry<String, MetadataValues> v: el.metadata.entrySet()) {
                    jgen.writeArrayFieldStart(v.getKey());
                    for (String x: v.getValue().getValue()) {
                        jgen.writeString(x);
                    }
                    jgen.writeEndArray();
                }
                if (el.lengthInTokens != null)
                    jgen.writeNumberField("lengthInTokens", el.lengthInTokens);
                if (el.mayView != null)
                    jgen.writeBooleanField("mayView", el.mayView);
                jgen.writeEndObject();
            }
            jgen.writeEndObject();
        }
    }

    private static class DocInfosDeserializer extends JsonDeserializer<List<DocInfo>> {
        @Override
        public List<DocInfo> deserialize(JsonParser parser, DeserializationContext deserializationContext)
                throws IOException {
            JsonToken token = parser.getCurrentToken();
            if (token != JsonToken.START_OBJECT)
                throw new ErrorReadingResponse("Expected START_OBJECT, found " + token);

            List<DocInfo> docInfos = new ArrayList<>();
            while (true) {
                token = parser.nextToken();
                if (token == JsonToken.END_OBJECT)
                    break;

                if (token != JsonToken.FIELD_NAME)
                    throw new ErrorReadingResponse("Expected END_OBJECT or FIELD_NAME, found " + token);
                String pid = parser.getCurrentName();
                if (pid.equals("metadataFieldGroups")) {
                    // Skip this part, which doesn't really belong but ended up here unfortunately.
                    parser.nextToken(); // START_ARRAY
                    while (parser.nextToken() != JsonToken.END_ARRAY) {
                        // Skip each metadata field group object (don't contain nested objects)
                        while (parser.nextToken() != JsonToken.END_OBJECT);
                    }
                    continue;
                }
                DocInfo docInfo = new DocInfo();
                docInfo.pid = parser.getCurrentName();
                docInfo.metadata = new LinkedHashMap<>();

                token = parser.nextToken();
                if (token != JsonToken.START_OBJECT)
                    throw new ErrorReadingResponse("Expected START_OBJECT, found " + token);
                while (true) {
                    token = parser.nextToken();
                    if (token == JsonToken.END_OBJECT)
                        break;

                    if (token != JsonToken.FIELD_NAME)
                        throw new ErrorReadingResponse("Expected END_OBJECT or FIELD_NAME, found " + token);
                    String fieldName = parser.getCurrentName();
                    token = parser.nextToken();
                    if (token == JsonToken.VALUE_NUMBER_INT) {
                        // Special lengthInTokens setting?
                        if (!fieldName.equals("lengthInTokens"))
                            throw new ErrorReadingResponse("Unexpected int in metadata");
                        docInfo.lengthInTokens = parser.getValueAsInt();
                    } else if (token == JsonToken.VALUE_TRUE || token == JsonToken.VALUE_FALSE) {
                        // Special mayView setting?
                        if (!fieldName.equals("mayView"))
                            throw new ErrorReadingResponse("Unexpected boolean in metadata");
                        docInfo.mayView = parser.getValueAsBoolean();
                    } else if (token == JsonToken.START_ARRAY) {
                        // A list of metadata values
                        List<String> values = SerializationUtil.readStringList(parser);
                        docInfo.metadata.put(fieldName, new MetadataValues(values));
                    }
                }
                docInfos.add(docInfo);
            }

            return docInfos;
        }
    }

    public SearchSummary summary;

    @XmlElementWrapper(name="hits")
    @XmlElement(name = "hit")
    @JsonProperty("hits")
    @JsonSerialize(using = HitListSerializer.class)
    @JsonDeserialize(using = HitListDeserializer.class)
    @JsonInclude(Include.NON_NULL)
    public BigList<Hit> hits;

    @XmlElementWrapper(name="docInfos")
    @XmlElement(name = "docInfo")
    @JsonProperty("docInfos")
    @JsonSerialize(using = DocInfosSerializer.class)
    @JsonDeserialize(using = DocInfosDeserializer.class)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public List<DocInfo> docInfos;

    @XmlElementWrapper(name="hitGroups")
    @XmlElement(name = "hitGroup")
    @JsonProperty("hitGroups")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public List<HitOrDocGroup> hitGroups;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonSerialize(using = SerializationUtil.FacetSerializer.class)
    @JsonDeserialize(using = SerializationUtil.FacetDeserializer.class)
    public Map<String, ArrayList<FacetValue>> facets; // ArrayList because JAXB cannot handle interfaces

    // required for Jersey
    @SuppressWarnings("unused")
    public HitsResults() {}

    public HitsResults(SearchSummary summary, BigList<Hit> hits,
            List<DocInfo> docInfos) {
        this.summary = summary;
        this.hits = hits;
        this.docInfos = docInfos;
        this.hitGroups = null;
    }

    @SuppressWarnings("unused")
    public HitsResults(SearchSummary summary, List<HitOrDocGroup> groups) {
        this.summary = summary;
        this.hits = null;
        this.docInfos = null;
        this.hitGroups = groups;
    }

    @Override
    public String toString() {
        return "HitsResults{" +
                "summary=" + summary +
                ", hits=" + hits +
                ", docInfos=" + docInfos +
                ", hitGroups=" + hitGroups +
                ", facets=" + facets +
                '}';
    }
}
