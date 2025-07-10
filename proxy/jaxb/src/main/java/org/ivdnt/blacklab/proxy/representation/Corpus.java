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
import org.ivdnt.blacklab.proxy.helper.SerializationUtil;

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
public class Corpus {

    /** Use this to serialize indices to JSON.
     *
     * Necessary because we convert a list (needed for the XML mapping) to a JSON object structure .
     */
    private static class ListAnnotatedFieldAnnotationGroupsSerializer extends JsonSerializer<List<AnnotatedFieldAnnotationGroups>> {
        @Override
        public void serialize(List<AnnotatedFieldAnnotationGroups> value, JsonGenerator jgen, SerializerProvider provider)
                throws IOException {

            if (value == null)
                return;
            jgen.writeStartObject();
            for (AnnotatedFieldAnnotationGroups field: value) {
                jgen.writeArrayFieldStart(field.name);
                for (AnnotationGroup annotationGroup: field.annotationGroups) {
                    jgen.writeStartObject();
                    {
                        jgen.writeStringField("name", annotationGroup.name);
                        jgen.writeFieldName("annotations");
                        provider.defaultSerializeValue(annotationGroup.annotations, jgen);
                    }
                    jgen.writeEndObject();
                }
                jgen.writeEndArray();
            }
            jgen.writeEndObject();
        }
    }

    /** Use this to deserialize indices from JSON.
     *
     * Necessary because we convert a JSON object structure to a list (because that's what the XML mapping uses).
     */
    private static class ListAnnotatedFieldAnnotationGroupsDeserializer extends JsonDeserializer<List<AnnotatedFieldAnnotationGroups>> {

        @Override
        public List<AnnotatedFieldAnnotationGroups> deserialize(JsonParser parser, DeserializationContext deserializationContext)
                throws IOException {

            JsonToken token = parser.currentToken();
            if (token != JsonToken.START_OBJECT)
                throw new ErrorReadingResponse("Expected START_OBJECT, found " + token);

            List<AnnotatedFieldAnnotationGroups> result = new ArrayList<>();
            while (true) {
                token = parser.nextToken();
                if (token == JsonToken.END_OBJECT)
                    break;

                if (token != JsonToken.FIELD_NAME)
                    throw new ErrorReadingResponse("Expected END_OBJECT or FIELD_NAME, found " + token);
                AnnotatedFieldAnnotationGroups groups = new AnnotatedFieldAnnotationGroups();
                groups.name = parser.getCurrentName();
                groups.annotationGroups = new ArrayList<>();

                token = parser.nextToken();
                if (token != JsonToken.START_ARRAY)
                    throw new ErrorReadingResponse("Expected END_OBJECT or START_ARRAY, found " + token);
                while (true) {
                    token = parser.nextToken();
                    if (token == JsonToken.END_ARRAY)
                        break;

                    if (token != JsonToken.START_OBJECT)
                        throw new ErrorReadingResponse("Expected END_ARRAY or START_OBJECT, found " + token);
                    AnnotationGroup group = new AnnotationGroup();
                    while (true) {
                        token = parser.nextToken();
                        if (token == JsonToken.END_OBJECT)
                            break;

                        if (token != JsonToken.FIELD_NAME)
                            throw new ErrorReadingResponse("Expected END_OBJECT or FIELD_NAME, found " + token);
                        String fieldName = parser.getCurrentName();
                        parser.nextToken();
                        switch (fieldName) {
                        case "name":
                            group.name = parser.getValueAsString();
                            break;
                        case "annotations":
                            group.annotations = SerializationUtil.readStringList(parser);
                            break;
                        default:
                            throw new ErrorReadingResponse("Unexpected field " + fieldName + " in AnnotationGroup");
                        }
                    }
                    groups.annotationGroups.add(group);
                }
                result.add(groups);
            }
            return result;
        }
    }

    /** Use this to serialize annotatedFields to JSON.
     *
     * Necessary because we convert a list (needed for the XML mapping) to a JSON object structure.
     */
    private static class ListAnnotatedFieldSerializer extends JsonSerializer<List<AnnotatedField>> {
        @Override
        public void serialize(List<AnnotatedField> value, JsonGenerator jgen, SerializerProvider provider)
                throws IOException {

            if (value == null)
                return;
            jgen.writeStartObject();
            for (AnnotatedField field: value) {
                jgen.writeFieldName(field.name);
                provider.defaultSerializeValue(field, jgen);
            }
            jgen.writeEndObject();
        }
    }

    /** Use this to deserialize annotatedFields from JSON.
     *
     * Necessary because we convert a JSON object structure to a list (because that's what the XML mapping uses).
     */
    private static class ListAnnotatedFieldDeserializer extends JsonDeserializer<List<AnnotatedField>> {

        @Override
        public List<AnnotatedField> deserialize(JsonParser parser, DeserializationContext deserializationContext)
                throws IOException {

            JsonToken token = parser.currentToken();
            if (token != JsonToken.START_OBJECT)
                throw new ErrorReadingResponse("Expected START_OBJECT, found " + token);

            List<AnnotatedField> result = new ArrayList<>();
            while (true) {
                token = parser.nextToken();
                if (token == JsonToken.END_OBJECT)
                    break;

                if (token != JsonToken.FIELD_NAME)
                    throw new ErrorReadingResponse("Expected END_OBJECT or FIELD_NAME, found " + token);
//                AnnotatedField field = new AnnotatedField(parser.getCurrentName());
                String fieldName = parser.getCurrentName();

                token = parser.nextToken();
                if (token != JsonToken.START_OBJECT)
                    throw new ErrorReadingResponse("Expected START_OBJECT, found " + token);
                AnnotatedField field = deserializationContext.readValue(parser, AnnotatedField.class);
                field.name = fieldName;
                result.add(field);
            }
            return result;
        }
    }

    /** Use this to serialize metadata fields to JSON.
     *
     * Necessary because we convert a list (needed for the XML mapping) to a JSON object structure .
     */
    private static class ListMetadataFieldSerializer extends JsonSerializer<List<MetadataField>> {
        @Override
        public void serialize(List<MetadataField> value, JsonGenerator jgen, SerializerProvider provider)
                throws IOException {

            if (value == null)
                return;
            jgen.writeStartObject();
            for (MetadataField field: value) {
                jgen.writeFieldName(field.name);
                provider.defaultSerializeValue(field, jgen);
            }
            jgen.writeEndObject();
        }
    }

    /** Use this to deserialize metadata fields from JSON.
     *
     * Necessary because we convert a JSON object structure to a list (because that's what the XML mapping uses).
     */
    private static class ListMetadataFieldDeserializer extends JsonDeserializer<List<MetadataField>> {

        @Override
        public List<MetadataField> deserialize(JsonParser parser, DeserializationContext deserializationContext)
                throws IOException {

            JsonToken token = parser.currentToken();
            if (token != JsonToken.START_OBJECT)
                throw new ErrorReadingResponse("Expected START_OBJECT, found " + token);

            List<MetadataField> result = new ArrayList<>();
            while (true) {
                token = parser.nextToken();
                if (token == JsonToken.END_OBJECT)
                    break;

                if (token != JsonToken.FIELD_NAME)
                    throw new ErrorReadingResponse("Expected END_OBJECT or FIELD_NAME, found " + token);
                //MetadataField field = new MetadataField();
                String fieldName = parser.getCurrentName();

                token = parser.nextToken();
                if (token != JsonToken.START_OBJECT)
                    throw new ErrorReadingResponse("Expected START_OBJECT, found " + token);

                MetadataField field = deserializationContext.readValue(parser, MetadataField.class);
                field.name = fieldName;
                result.add(field);
            }
            return result;
        }
    }

    public String indexName = "";

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String displayName = "";

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String description = "";

    public String status = "available";

    public boolean contentViewable = false;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String textDirection = "ltr";

    public String documentFormat = "";

    public long tokenCount = 0;

    public long documentCount = 0;

    public VersionInfo versionInfo;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String pidField;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public SpecialFieldInfo fieldInfo;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String mainAnnotatedField;

    @XmlElementWrapper(name="annotatedFields")
    @XmlElement(name = "annotatedField")
    @JsonProperty("annotatedFields")
    @JsonSerialize(using = ListAnnotatedFieldSerializer.class)
    @JsonDeserialize(using = ListAnnotatedFieldDeserializer.class)
    public List<AnnotatedField> annotatedFields;

    @XmlElementWrapper(name="metadataFields")
    @XmlElement(name = "metadataField")
    @JsonProperty("metadataFields")
    @JsonSerialize(using = ListMetadataFieldSerializer.class)
    @JsonDeserialize(using = ListMetadataFieldDeserializer.class)
    public List<MetadataField> metadataFields;

    @XmlElementWrapper(name="metadataFieldGroups")
    @XmlElement(name = "metadataFieldGroup")
    @JsonProperty("metadataFieldGroups")
    public List<MetadataFieldGroup> metadataFieldGroups;

    @XmlElementWrapper(name="annotationGroups")
    @XmlElement(name = "annotatedField")
    @JsonProperty("annotationGroups")
    @JsonSerialize(using = ListAnnotatedFieldAnnotationGroupsSerializer.class)
    @JsonDeserialize(using = ListAnnotatedFieldAnnotationGroupsDeserializer.class)
    public List<AnnotatedFieldAnnotationGroups> annotationGroups;

    // required for Jersey
    @SuppressWarnings("unused")
    private Corpus() {}

    @SuppressWarnings("unused")
    public Corpus(String name, SpecialFieldInfo fieldInfo,
            List<AnnotatedField> annotatedFields, List<MetadataField> metadataFields) {
        this.indexName = name;
        this.fieldInfo = fieldInfo;
        this.annotatedFields = annotatedFields;
        this.metadataFields = metadataFields;
    }

    @Override
    public String toString() {
        return "Corpus{" +
                "indexName='" + indexName + '\'' +
                ", displayName='" + displayName + '\'' +
                ", description='" + description + '\'' +
                ", status='" + status + '\'' +
                ", contentViewable=" + contentViewable +
                ", textDirection='" + textDirection + '\'' +
                ", documentFormat='" + documentFormat + '\'' +
                ", tokenCount=" + tokenCount +
                ", documentCount=" + documentCount +
                ", versionInfo=" + versionInfo +
                ", pidField=" + pidField +
                ", fieldInfo=" + fieldInfo +
                ", annotatedFields=" + annotatedFields +
                ", metadataFields=" + metadataFields +
                ", metadataFieldGroups=" + metadataFieldGroups +
                ", annotationGroups=" + annotationGroups +
                '}';
    }
}
