package nl.inl.util;

import java.lang.reflect.Type;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.victools.jsonschema.generator.Option;
import com.github.victools.jsonschema.generator.OptionPreset;
import com.github.victools.jsonschema.generator.SchemaGenerator;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder;
import com.github.victools.jsonschema.generator.SchemaVersion;
import com.github.victools.jsonschema.module.jackson.JacksonModule;

public class JsonSchemaUtil {
    /** Generate a JSON schema from ConfigInputFormat.
     *
     * Known issues:
     * - enum values don't take @JsonValue into account (just treat them as strings?)
     * - more @JsonPropertyDescription annotations would be nice
     *
     * @return JSON schema as a string
     */
    public static String getJsonSchema(Type targetType) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            SchemaGeneratorConfigBuilder configBuilder =
                    new SchemaGeneratorConfigBuilder(mapper, SchemaVersion.DRAFT_2020_12, OptionPreset.PLAIN_JSON);
            configBuilder.with(Option.MAP_VALUES_AS_ADDITIONAL_PROPERTIES);
            configBuilder.with(new JacksonModule()); // honor Jackson annotations
            SchemaGenerator generator = new SchemaGenerator(configBuilder.build());
            JsonNode schema = generator.generateSchema(targetType);
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(schema);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
