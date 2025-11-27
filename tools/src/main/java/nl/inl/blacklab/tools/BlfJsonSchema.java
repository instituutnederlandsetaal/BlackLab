package nl.inl.blacklab.tools;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.victools.jsonschema.generator.OptionPreset;
import com.github.victools.jsonschema.generator.SchemaGenerator;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder;
import com.github.victools.jsonschema.generator.SchemaVersion;
import com.github.victools.jsonschema.module.jackson.JacksonModule;

import nl.inl.blacklab.indexers.config.ConfigInputFormat;

/** Generate a JSON schema from ConfigInputFormat.
 *
 * Known issues:
 * - enum values don't take @JsonValue into account (just treat them as strings?)
 * - more @JsonPropertyDescription annotations would be nice
 */
public class BlfJsonSchema {

    public static void main(String[] args) throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();

        SchemaGeneratorConfigBuilder configBuilder =
                new SchemaGeneratorConfigBuilder(mapper, SchemaVersion.DRAFT_2020_12, OptionPreset.PLAIN_JSON);

        configBuilder.with(new JacksonModule()); // honor Jackson annotations

        SchemaGenerator generator = new SchemaGenerator(configBuilder.build());
        JsonNode schema = generator.generateSchema(ConfigInputFormat.class);

        System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(schema));
    }

}
