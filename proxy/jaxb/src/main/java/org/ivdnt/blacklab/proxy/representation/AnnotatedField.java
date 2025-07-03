package org.ivdnt.blacklab.proxy.representation;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElementWrapper;
import jakarta.xml.bind.annotation.XmlRootElement;

import org.ivdnt.blacklab.proxy.helper.ErrorReadingResponse;
import org.ivdnt.blacklab.proxy.helper.SerializationUtil;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
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
@JsonIgnoreProperties(value = { "name" })
public class AnnotatedField implements Cloneable {

    /** Use this to serialize annotatedFields to JSON.
     *
     * Necessary because we convert a list (needed for the XML mapping) to a JSON object structure.
     */
    private static class ListAnnotationSerializer extends JsonSerializer<List<Annotation>> {
        @Override
        public void serialize(List<Annotation> value, JsonGenerator jgen, SerializerProvider provider)
                throws IOException {

            if (value == null)
                return;
            jgen.writeStartObject();
            for (Annotation a: value) {
                jgen.writeFieldName(a.name);
                provider.defaultSerializeValue(a, jgen);
            }
            jgen.writeEndObject();
        }
    }

    /** Use this to deserialize annotatedFields from JSON.
     *
     * Necessary because we convert a JSON object structure to a list (because that's what the XML mapping uses).
     */
    private static class ListAnnotationDeserializer extends JsonDeserializer<List<Annotation>> {
        @Override
        public List<Annotation> deserialize(JsonParser parser, DeserializationContext deserializationContext)
                throws IOException {

            JsonToken token = parser.currentToken();
            if (token != JsonToken.START_OBJECT)
                throw new ErrorReadingResponse("Expected START_OBJECT, found " + token);
            return SerializationUtil.readAnnotations(parser, deserializationContext);
        }
    }

    @XmlAttribute
    public String name;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String indexName;

    public String fieldName;

    public boolean isAnnotatedField = true;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String displayName = "";

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String description = "";

    public boolean hasContentStore;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Boolean hasXmlTags;

    public String mainAnnotation = "";

    @XmlElementWrapper(name="displayOrder")
    @XmlElement(name = "fieldName")
    @JsonProperty("displayOrder")
    public List<String> displayOrder = new ArrayList<>();

    @XmlElementWrapper(name="annotations")
    @XmlElement(name = "annotation")
    @JsonProperty("annotations")
    @JsonSerialize(using = ListAnnotationSerializer.class)
    @JsonDeserialize(using = ListAnnotationDeserializer.class)
    public List<Annotation> annotations = new ArrayList<>();

    @Override
    public String toString() {
        return "AnnotatedField{" +
                "name='" + name + '\'' +
                ", indexName='" + indexName + '\'' +
                ", fieldName='" + fieldName + '\'' +
                ", isAnnotatedField=" + isAnnotatedField +
                ", displayName='" + displayName + '\'' +
                ", description='" + description + '\'' +
                ", hasContentStore=" + hasContentStore +
                ", hasXmlTags=" + hasXmlTags +
                ", mainAnnotation='" + mainAnnotation + '\'' +
                ", displayOrder=" + displayOrder +
                ", annotations=" + annotations +
                '}';
    }

    private AnnotatedField() {}

    @Override
    public AnnotatedField clone() {
        try {
            return (AnnotatedField)super.clone();
        } catch (CloneNotSupportedException e) {
            throw new UnsupportedOperationException(e);
        }
    }

    public AnnotatedField(String name) {
        this.name = this.fieldName = name;
    }
}
