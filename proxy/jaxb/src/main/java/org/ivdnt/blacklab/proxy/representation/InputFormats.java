package org.ivdnt.blacklab.proxy.representation;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

import org.ivdnt.blacklab.proxy.helper.ErrorReadingResponse;

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

@XmlRootElement(name="blacklabResponse")
@XmlAccessorType(XmlAccessType.FIELD)
public class InputFormats {

    private static class ListInputFormatSerializer extends JsonSerializer<List<InputFormat>> {
        @Override
        public void serialize(List<InputFormat> value, JsonGenerator jgen, SerializerProvider provider)
                throws IOException {

            if (value == null)
                return;
            jgen.writeStartObject();
            for (InputFormat format: value) {
                jgen.writeFieldName(format.name);
                provider.defaultSerializeValue(format, jgen);
            }
            jgen.writeEndObject();
        }
    }

    private static class ListInputFormatDeserializer extends JsonDeserializer<List<InputFormat>> {

        @Override
        public List<InputFormat> deserialize(JsonParser parser, DeserializationContext deserializationContext)
                throws IOException {

            JsonToken token = parser.currentToken();
            if (token != JsonToken.START_OBJECT)
                throw new ErrorReadingResponse("Expected START_OBJECT, found " + token);

            List<InputFormat> result = new ArrayList<>();
            while (true) {
                token = parser.nextToken();
                if (token == JsonToken.END_OBJECT)
                    break;

                if (token != JsonToken.FIELD_NAME)
                    throw new ErrorReadingResponse("Expected END_OBJECT or FIELD_NAME, found " + token);
                String name = parser.getCurrentName();

                token = parser.nextToken();
                if (token != JsonToken.START_OBJECT)
                    throw new ErrorReadingResponse("Expected END_OBJECT or START_OBJECT, found " + token);

                InputFormat inputFormat = deserializationContext.readValue(parser, InputFormat.class);
                inputFormat.name = name;
                result.add(inputFormat);
            }
            return result;
        }
    }

    public User user;

    @XmlElementWrapper(name="supportedInputFormats")
    @XmlElement(name = "format")
    @JsonProperty("supportedInputFormats")
    @JsonSerialize(using= ListInputFormatSerializer.class)
    @JsonDeserialize(using= ListInputFormatDeserializer.class)
    public List<InputFormat> supportedInputFormats;

    public InputFormats() {
        user = new User();
    }

}
