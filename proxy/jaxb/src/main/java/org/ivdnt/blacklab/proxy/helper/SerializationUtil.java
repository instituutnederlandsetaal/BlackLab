package org.ivdnt.blacklab.proxy.helper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.xml.bind.JAXBElement;

import org.ivdnt.blacklab.proxy.representation.Annotation;
import org.ivdnt.blacklab.proxy.representation.FacetValue;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

public class SerializationUtil {

    /** Return a XML-safe attribute.
     *
     * Might want to add camel case support.
     *
     * @param attributeLabel proposed label
     * @return sanitized version
     */
    public static String getCleanLabel(String attributeLabel) {
        attributeLabel = attributeLabel.replaceAll("[()]", "").replaceAll("[^\\w\\s]", "_").replaceAll(" ", "_");
        return attributeLabel.isEmpty() ? "EMPTY_VALUE" : attributeLabel;
    }

    /** Use this to serialize a String map using MapAdapter to JSON.
     */
    public static class StringMapSerializer extends JsonSerializer<Object> {
        @Override
        public void serialize(Object value, JsonGenerator jgen, SerializerProvider provider)
                throws IOException {
            if (value instanceof MapAdapter.MapWrapper) {
                // This happens because of using MapAdapter with Jersey
                jgen.writeStartObject();
                for (Object element: ((MapAdapter.MapWrapper) value).elements) {
                    JAXBElement el = (JAXBElement) element;
                    jgen.writeStringField(el.getName().getLocalPart(), el.getValue().toString());
                }
                jgen.writeEndObject();
            } else if (value instanceof Map) {
                // This happens if we use Jackson directly without Jersey
                jgen.writeStartObject();
                for (Map.Entry<String, String> entry: ((Map<String, String>) value).entrySet()) {
                    jgen.writeStringField(entry.getKey(), entry.getValue());
                }
                jgen.writeEndObject();
            } else {
                throw new IOException("Expected Map or MapAdapter.MapWrapper, found " + value.getClass().getName());
            }
        }
    }

    /** Use this to deserialize a String map using MapAdapter from JSON.
     *
     * Necessary because we convert a JSON object structure to a list (because that's what the XML mapping uses).
     */
    public static class StringMapDeserializer extends JsonDeserializer<Map<String, String>> {

        @Override
        public Map<String, String> deserialize(JsonParser parser, DeserializationContext deserializationContext)
                throws IOException {
            return readStringMap(parser);
        }
    }

    /** Use this to serialize a String map using MapAdapter to JSON.
     */
    public static class TermFreqMapSerializer extends JsonSerializer<Object> {
        @Override
        public void serialize(Object value, JsonGenerator jgen, SerializerProvider provider)
                throws IOException {
            if (value instanceof MapAdapterTermFreq.WrapperTermFreq) {
                // This happens because of using MapAdapter with Jersey
                jgen.writeStartObject();
                for (Object element: ((MapAdapterTermFreq.WrapperTermFreq) value).elements) {
                    MapAdapterTermFreq.TermFreq el = (MapAdapterTermFreq.TermFreq) element;
                    jgen.writeNumberField(el.text, el.freq);
                }
                jgen.writeEndObject();
            } else if (value instanceof Map) {
                // This happens if we use Jackson directly without Jersey
                jgen.writeStartObject();
                for (Map.Entry<String, Long> entry: ((Map<String, Long>) value).entrySet()) {
                    jgen.writeNumberField(entry.getKey(), entry.getValue());
                }
                jgen.writeEndObject();
            } else {
                throw new IOException("Expected MapAdapterTermFreq.WrapperTermFreq or Map<String, Long>, found " + value.getClass());
            }
        }
    }

    /** Use this to deserialize a String map using MapAdapter from JSON.
     *
     * Necessary because we convert a JSON object structure to a list (because that's what the XML mapping uses).
     */
    public static class TermFreqMapDeserializer extends JsonDeserializer<Map<String, Long>> {

        @Override
        public Map<String, Long> deserialize(JsonParser parser, DeserializationContext deserializationContext)
                throws IOException {
            return readLongMap(parser);
        }
    }

    private SerializationUtil() {}

    public static List<String> readStringList(JsonParser parser) throws IOException {
        JsonToken token = parser.currentToken();
        if (token != JsonToken.START_ARRAY)
            throw new ErrorReadingResponse("Expected START_ARRAY, found " + token);

        List<String> list = new ArrayList<>();
        while (true) {
            token = parser.nextToken();
            if (token == JsonToken.END_ARRAY)
                break;
            if (token != JsonToken.VALUE_STRING)
                throw new ErrorReadingResponse("Expected END_ARRAY or VALUE_STRING, found " + token);
            list.add(parser.getValueAsString());
        }
        return list;
    }

    public static List<Annotation> readAnnotations(JsonParser parser, DeserializationContext deserializationContext) throws IOException {
        List<Annotation> result = new ArrayList<>();
        while (true) {
            JsonToken token = parser.nextToken();
            if (token == JsonToken.END_OBJECT)
                break;

            if (token != JsonToken.FIELD_NAME)
                throw new ErrorReadingResponse("Expected END_OBJECT or FIELD_NAME, found " + token);
            String annotationName = parser.getCurrentName();

            token = parser.nextToken();
            if (token != JsonToken.START_OBJECT)
                throw new ErrorReadingResponse("Expected START_OBJECT, found " + token);

            Annotation annotation = deserializationContext.readValue(parser, Annotation.class);
            annotation.name = annotationName;
            result.add(annotation);
        }
        return result;
    }

    public static Map<String, String> readStringMap(JsonParser parser) throws IOException {
        JsonToken token = parser.currentToken();
        if (token != JsonToken.START_OBJECT)
            throw new ErrorReadingResponse("Expected START_OBJECT, found " + token);

        Map<String, String> result = new LinkedHashMap<>();
        while (true) {
            token = parser.nextToken();
            if (token == JsonToken.END_OBJECT)
                break;

            if (token != JsonToken.FIELD_NAME)
                throw new ErrorReadingResponse("Expected END_OBJECT or FIELD_NAME, found " + token);
            String key = parser.getCurrentName();

            token = parser.nextToken();
            if (token != JsonToken.VALUE_STRING)
                throw new ErrorReadingResponse("Expected VALUE_STRING, found " + token);
            String value = parser.getValueAsString();

            result.put(key, value);
        }
        return result;
    }

    public static Map<String, Integer> readIntegerMap(JsonParser parser) throws IOException {
        JsonToken token = parser.currentToken();
        if (token != JsonToken.START_OBJECT)
            throw new ErrorReadingResponse("Expected START_OBJECT, found " + token);

        Map<String, Integer> result = new LinkedHashMap<>();
        while (true) {
            token = parser.nextToken();
            if (token == JsonToken.END_OBJECT)
                break;

            if (token != JsonToken.FIELD_NAME)
                throw new ErrorReadingResponse("Expected END_OBJECT or FIELD_NAME, found " + token);
            String key = parser.getCurrentName();

            token = parser.nextToken();
            int value;
            switch (token) {
            case VALUE_NUMBER_INT:
                value = parser.getValueAsInt();
                break;
            case VALUE_STRING: // fieldValues accidentally sends numbers as string, compensate...
                String strValue = parser.getValueAsString();
                value = Integer.parseInt(strValue);
                break;
            default:
                throw new ErrorReadingResponse("Expected VALUE_NUMBER_INT or VALUE_STRING, found " + token);
            }

            result.put(key, value);
        }
        return result;
    }

    public static Map<String, Long> readLongMap(JsonParser parser) throws IOException {
        JsonToken token = parser.currentToken();
        if (token != JsonToken.START_OBJECT)
            throw new ErrorReadingResponse("Expected START_OBJECT, found " + token);

        Map<String, Long> result = new LinkedHashMap<>();
        while (true) {
            token = parser.nextToken();
            if (token == JsonToken.END_OBJECT)
                break;

            if (token != JsonToken.FIELD_NAME)
                throw new ErrorReadingResponse("Expected END_OBJECT or FIELD_NAME, found " + token);
            String key = parser.getCurrentName();

            token = parser.nextToken();
            long value;
            switch (token) {
            case VALUE_NUMBER_INT:
                value = parser.getValueAsLong();
                break;
            default:
                throw new ErrorReadingResponse("Expected VALUE_NUMBER_INT, found " + token);
            }

            result.put(key, value);
        }
        return result;
    }

    public static class FacetSerializer extends JsonSerializer<Map<String, List<FacetValue>>> {
        @Override
        public void serialize(Map<String, List<FacetValue>> value, JsonGenerator jgen, SerializerProvider provider)
                throws IOException {
            if (value == null)
                return;
            jgen.writeStartObject();
            for (Map.Entry<String, List<FacetValue>> e: value.entrySet()) {
                jgen.writeArrayFieldStart(e.getKey());
                for (FacetValue v: e.getValue()) {
                    provider.defaultSerializeValue(v, jgen);
                }
                jgen.writeEndArray();
            }
            jgen.writeEndObject();
        }
    }

    public static class FacetDeserializer extends JsonDeserializer<Map<String, List<FacetValue>>> {
        @Override
        public Map<String, List<FacetValue>> deserialize(JsonParser parser, DeserializationContext deserializationContext)
                throws IOException {
            JsonToken token = parser.getCurrentToken();
            if (token != JsonToken.START_OBJECT)
                throw new ErrorReadingResponse("Expected START_OBJECT, found " + token);

            Map<String, List<FacetValue>> facets = new LinkedHashMap<>();
            while (true) {
                token = parser.nextToken();
                if (token == JsonToken.END_OBJECT)
                    break;
                if (token != JsonToken.FIELD_NAME)
                    throw new ErrorReadingResponse("Expected FIELD_NAME or END_OBJECT, found " + token);
                String fieldName = parser.getCurrentName();
                token = parser.nextToken();
                if (token != JsonToken.START_ARRAY)
                    throw new ErrorReadingResponse("Expected START_ARRAY, found " + token);
                List<FacetValue> values = new ArrayList<>();
                while (true) {
                    token = parser.nextToken();
                    if (token == JsonToken.END_ARRAY)
                        break;
                    if (token != JsonToken.START_OBJECT)
                        throw new ErrorReadingResponse("Expected START_OBJECT, found " + token);
                    values.add(deserializationContext.readValue(parser, FacetValue.class));
                }
                facets.put(fieldName, values);
            }
            return facets;
        }
    }
}
