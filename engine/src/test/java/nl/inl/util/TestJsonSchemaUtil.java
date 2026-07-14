package nl.inl.util;

import org.junit.Assert;
import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import nl.inl.blacklab.indexers.config.ConfigInputFormat;

public class TestJsonSchemaUtil {

    @Test
    public void testMapValueSchema() throws Exception {
        JsonNode schema = new ObjectMapper().readTree(JsonSchemaUtil.getJsonSchema(ConfigInputFormat.class));
        JsonNode annotatedFieldSchema = schema.at("/properties/annotatedFields/additionalProperties");

        Assert.assertFalse("annotatedFields should declare the schema of its values", annotatedFieldSchema.isMissingNode());
        Assert.assertEquals("#/$defs/ConfigAnnotatedField", annotatedFieldSchema.path("$ref").asText());
        Assert.assertTrue(schema.at("/$defs/ConfigAnnotatedField/properties/annotations").isObject());
    }
}
