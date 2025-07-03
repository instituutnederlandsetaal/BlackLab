package org.ivdnt.blacklab.proxy.representation;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElementWrapper;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlType;

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
@XmlType(propOrder={"summary", "docs", "docGroups", "facets" })
public class DocsResults implements Cloneable, EntityWithSummary {

    private static class DocListSerializer extends JsonSerializer<BigList<Doc>> {
        @Override
        public void serialize(BigList<Doc> value, JsonGenerator jgen, SerializerProvider provider)
                throws IOException {
            if (value == null)
                return;
            jgen.writeStartArray();
            for (Doc h: value) {
                provider.defaultSerializeValue(h, jgen);
            }
            jgen.writeEndArray();
        }
    }

    private static class DocListDeserializer extends JsonDeserializer<BigList<Doc>> {
        @Override
        public BigList<Doc> deserialize(JsonParser parser, DeserializationContext deserializationContext)
                throws IOException {
            JsonToken token = parser.getCurrentToken();
            if (token != JsonToken.START_ARRAY)
                throw new ErrorReadingResponse("Expected START_ARRAY, found " + token);

            BigList<Doc> hits = new ObjectBigArrayBigList<>();
            while (true) {
                token = parser.nextToken();
                if (token == JsonToken.END_ARRAY)
                    break;
                Doc h = deserializationContext.readValue(parser, Doc.class);
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

    @XmlElementWrapper(name="docs")
    @XmlElement(name = "doc")
    @JsonProperty("docs")
    @JsonSerialize(using = DocListSerializer.class)
    @JsonDeserialize(using = DocListDeserializer.class)
    @JsonInclude(Include.NON_NULL)
    public BigList<Doc> docs;

    @XmlElementWrapper(name="docGroups")
    @XmlElement(name = "docGroup")
    @JsonProperty("docGroups")
    @JsonInclude(Include.NON_NULL)
    public List<HitOrDocGroup> docGroups;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonSerialize(using = SerializationUtil.FacetSerializer.class)
    @JsonDeserialize(using = SerializationUtil.FacetDeserializer.class)
    public Map<String, ArrayList<FacetValue>> facets;

    // required for Jersey
    @SuppressWarnings("unused")
    public DocsResults() {}

    @Override
    public DocsResults clone() throws CloneNotSupportedException {
        return (DocsResults)super.clone();
    }

    @Override
    public String toString() {
        return "DocsResults{" +
                "summary=" + summary +
                ", docs=" + docs +
                ", docGroups=" + docGroups +
                ", facets=" + facets +
                '}';
    }
}
