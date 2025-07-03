package org.ivdnt.blacklab.proxy.representation;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import org.ivdnt.blacklab.proxy.helper.MapAdapter;
import org.ivdnt.blacklab.proxy.helper.MapAdapterFieldValues;
import org.ivdnt.blacklab.proxy.helper.SerializationUtil;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@XmlRootElement(name="blacklabResponse")
@XmlAccessorType(XmlAccessType.FIELD)
@JsonIgnoreProperties(value = { "name" }) // this is the key of the object
public class MetadataField implements Cloneable {

    public static class FieldValuesSerializer extends JsonSerializer<Object> {
        @Override
        public void serialize(Object value, JsonGenerator jgen, SerializerProvider provider)
                throws IOException {
            if (value instanceof MapAdapterFieldValues.WrapperFieldValues) {
                // This happens because of using MapAdapterFieldValues with Jersey
                jgen.writeStartObject();
                for (MapAdapterFieldValues.FieldValueFreq item: ((MapAdapterFieldValues.WrapperFieldValues) value).elements) {
                    jgen.writeNumberField(item.text, item.freq);
                }
                jgen.writeEndObject();
            } else if (value instanceof Map) {
                // This happens if we use Jackson directly without Jersey
                jgen.writeStartObject();
                for (Map.Entry<String, Integer> entry: ((Map<String, Integer>) value).entrySet()) {
                    jgen.writeNumberField(entry.getKey(), entry.getValue());
                }
                jgen.writeEndObject();
            }
        }
    }

    public static class FieldValuesDeserializer extends JsonDeserializer<Map<String, Integer>> {

        @Override
        public Map<String, Integer> deserialize(JsonParser parser, DeserializationContext deserializationContext)
                throws IOException {
            return SerializationUtil.readIntegerMap(parser);
        }
    }


    @XmlAttribute
    public String name;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String indexName;

    public String fieldName;

    public boolean isAnnotatedField = false;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String displayName;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String description;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String uiType = "";

    public String type = "";

    public String analyzer = "";

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String unknownCondition;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String unknownValue;

    @XmlJavaTypeAdapter(MapAdapter.class)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonSerialize(using = SerializationUtil.StringMapSerializer.class)
    @JsonDeserialize(using = SerializationUtil.StringMapDeserializer.class)
    public Map<String, String> displayValues = new LinkedHashMap<>();

    @XmlJavaTypeAdapter(MapAdapterFieldValues.class)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonSerialize(using = FieldValuesSerializer.class)
    @JsonDeserialize(using = FieldValuesDeserializer.class)
    public Map<String, Integer> fieldValues = new LinkedHashMap<>();

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Boolean valueListComplete = true;

    @Override
    public MetadataField clone() {
        try {
            return (MetadataField)super.clone();
        } catch (CloneNotSupportedException e) {
            throw new UnsupportedOperationException(e);
        }
    }

    @Override
    public String toString() {
        return "MetadataField{" +
                "name='" + name + '\'' +
                ", indexName='" + indexName + '\'' +
                ", fieldName='" + fieldName + '\'' +
                ", isAnnotatedField=" + isAnnotatedField +
                ", displayName='" + displayName + '\'' +
                ", description='" + description + '\'' +
                ", uiType='" + uiType + '\'' +
                ", type='" + type + '\'' +
                ", analyzer='" + analyzer + '\'' +
                ", unknownCondition='" + unknownCondition + '\'' +
                ", unknownValue='" + unknownValue + '\'' +
                ", displayValues=" + displayValues +
                ", fieldValues=" + fieldValues +
                ", valueListComplete=" + valueListComplete +
                '}';
    }
}
