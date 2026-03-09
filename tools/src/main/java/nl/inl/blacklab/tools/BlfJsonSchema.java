package nl.inl.blacklab.tools;

import com.fasterxml.jackson.core.JsonProcessingException;

import nl.inl.blacklab.indexers.config.ConfigInputFormat;
import nl.inl.util.JsonSchemaUtil;

public class BlfJsonSchema {

    public static void main(String[] args) throws JsonProcessingException {
        System.out.println(JsonSchemaUtil.getJsonSchema(ConfigInputFormat.class));
    }

}
