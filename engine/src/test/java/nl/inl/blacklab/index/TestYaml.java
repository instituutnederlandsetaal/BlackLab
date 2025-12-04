/*
 * Copyright 2018 Instituut voor Nederlandse Taal (INT).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nl.inl.blacklab.index;

import java.io.File;
import java.io.IOException;

import org.junit.Assert;
import org.junit.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import nl.inl.blacklab.exceptions.InvalidInputFormatConfig;
import nl.inl.blacklab.indexers.config.ConfigInputFormat;
import nl.inl.blacklab.search.BlackLab;
import nl.inl.util.Json;

/**
 *
 * @author eduard
 */
public class TestYaml {

    @Test
    public void testDuplicatObjects() {
        try {
            ClassLoader classLoader = this.getClass().getClassLoader();
            File file = new File(classLoader.getResource("yaml/nodups.blf.yaml").getFile());
            InputFormatInfoWithConfig inputFormat = new InputFormatInfoWithConfig("nodups", file);
            inputFormat.getConfig();
            Assert.fail("expected duplicates error");
        } catch (InvalidInputFormatConfig ex) {
            Assert.assertTrue(ex.getMessage().contains("Duplicate"));
        }
    }

    public static void main(String[] args) throws IOException {
        BlackLab.implicitInstance(); // init plugins
        //ClassLoader classLoader = this.getClass().getClassLoader();
        //File file = new File(classLoader.getResource("formats/tei-p5.blf.yaml").getFile());
        //File file = new File("/home/jan/int-projects/corpus-frontend-config/chn-intern/chn-intern-ngrams.blf.yaml");
        File file = new File("/home/jan/int-projects/corpus-frontend-config/OFR/OFR.blf.yaml");
        //File file = new File("/home/jan/int-projects/corpus-frontend-config/opensonar/CgnFolia.blf.yaml");
        ObjectMapper mapper = Json.getYamlObjectMapper();
        ConfigInputFormat config = ConfigInputFormat.read(file);
        mapper.writeValue(System.out, config);
        System.out.flush();
    }

//    public static void main(String[] args) throws JsonProcessingException {
//        ObjectMapper mapper = new ObjectMapper();
//
//        SchemaGeneratorConfigBuilder configBuilder =
//                new SchemaGeneratorConfigBuilder(mapper, SchemaVersion.DRAFT_2020_12, OptionPreset.PLAIN_JSON);
//
//        configBuilder.with(new JacksonModule()); // honor Jackson annotations
//
//        SchemaGenerator generator = new SchemaGenerator(configBuilder.build());
//        JsonNode schema = generator.generateSchema(ConfigInputFormat.class);
//
//        System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(schema));
//    }

}
