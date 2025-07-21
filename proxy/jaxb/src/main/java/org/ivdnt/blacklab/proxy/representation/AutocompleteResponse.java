package org.ivdnt.blacklab.proxy.representation;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElementWrapper;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import org.ivdnt.blacklab.proxy.helper.AutocompleteAdapter;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@XmlRootElement(name="blacklabResponse")
@XmlAccessorType()
@XmlJavaTypeAdapter(AutocompleteAdapter.class)
@JsonSerialize(using = AutocompleteResponse.Serializer.class)
@JsonDeserialize(using = AutocompleteResponse.Deserializer.class)
public class AutocompleteResponse {

    public static class Serializer extends JsonSerializer<Object> {
        @Override
        public void serialize(Object value, JsonGenerator jgen, SerializerProvider provider)
                throws IOException {
            if (value == null)
                return;
            if (value instanceof  AutocompleteResponse) {
                // Value was not marshalled.
                provider.defaultSerializeValue(((AutocompleteResponse) value).terms, jgen);
            } else if (value instanceof List) {
                // Value was marshalled to an ArrayList.
                provider.defaultSerializeValue(value, jgen);
            } else
                throw new IllegalArgumentException();
        }
    }

    public static class Deserializer extends JsonDeserializer<AutocompleteResponse> {
        @Override
        public AutocompleteResponse deserialize(JsonParser parser, DeserializationContext deserializationContext)
                throws IOException {
            AutocompleteResponse r = new AutocompleteResponse();
            r.terms = (List<String>)deserializationContext.readValue(parser, ArrayList.class);
            return r;
        }
    }

    @XmlElementWrapper(name = "terms")
    @XmlElement(name = "term")
    public List<String> terms;
}
