package org.ivdnt.blacklab.proxy.representation;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

import org.ivdnt.blacklab.proxy.helper.ErrorReadingResponse;

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

/** /hits results with aggregator=true (minimal hit info) */
@XmlRootElement(name="blacklabResponse")
@XmlAccessorType(XmlAccessType.FIELD)
public class HitsResultsMinimal implements Cloneable, EntityWithSummary {

    private static class BigListSerializer extends JsonSerializer<BigList<List<Object>>> {
        @Override
        public void serialize(BigList<List<Object>> value, JsonGenerator jgen, SerializerProvider provider)
                throws IOException {
            if (value == null)
                return;
            jgen.writeStartArray();
            for (List<Object> h: value) {
                provider.defaultSerializeValue(h, jgen);
            }
            jgen.writeEndArray();
        }
    }

    private static class BigListDeserializer extends JsonDeserializer<BigList<List<Object>>> {
        @Override
        public BigList<List<Object>> deserialize(JsonParser parser, DeserializationContext deserializationContext)
                throws IOException {
            JsonToken token = parser.getCurrentToken();
            if (token != JsonToken.START_ARRAY)
                throw new ErrorReadingResponse("Expected START_ARRAY, found " + token);

            BigList<List<Object>> hits = new ObjectBigArrayBigList<>();
            while (true) {
                token = parser.nextToken();
                if (token == JsonToken.END_ARRAY)
                    break;
                List<Object> h = deserializationContext.readValue(parser, List.class);
                hits.add(h);
            }
            return hits;
        }
    }

    @Override
    public SearchSummary getSummary() {
        return summary;
    }

    public SearchSummary summary;

    @XmlElementWrapper(name="hits")
    @XmlElement(name = "h")
    @JsonProperty("hits")
    @JsonSerialize(using = BigListSerializer.class)
    @JsonDeserialize(using = BigListDeserializer.class)
    @JsonInclude(Include.NON_NULL)
    public BigList<List<Object>> hits;

    // required for Jersey
    @SuppressWarnings("unused")
    public HitsResultsMinimal() {}

    public HitsResultsMinimal(SearchSummary summary, BigList<HitMin> hits) {
        this.summary = summary;
        this.hits = new ObjectBigArrayBigList<>();
        for (HitMin h: hits) {
            List<Object> l = new ArrayList<>();
            l.add(h.uniqueDocId());
            Collections.addAll(l, h.sortValues);
            this.hits.add(l);
        }
    }

    @Override
    public HitsResultsMinimal clone() throws CloneNotSupportedException {
        return (HitsResultsMinimal)super.clone();
    }

    @Override
    public String toString() {
        return "HitsResults{" +
                "summary=" + summary +
                ", hits=" + hits +
                '}';
    }
}
