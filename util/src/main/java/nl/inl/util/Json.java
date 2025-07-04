package nl.inl.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.module.jaxb.JaxbAnnotationModule;

/**
 * Supports reading/writing JSON and YAML files.
 *
 * NOTE: C++ style // comments are allowed in Json files, which is a
 * non-standard extension.
 *
 */
public class Json {

    private static ObjectMapper jsonObjectMapper;

    private static ObjectMapper jsonWithJaxbAnnotation;

    private static ObjectMapper yamlObjectMapper;

    private static ObjectReader jaxbReader;

    private static ObjectWriter jaxbWriter;

    private static ObjectWriter jaxbWriterPretty;

    static {
        initObjectMappers();
    }

    private Json() {
    }

    private static void initObjectMappers() {
        JsonFactory jsonFactory = new JsonFactory();
        jsonFactory.enable(JsonParser.Feature.ALLOW_COMMENTS);
        jsonObjectMapper = new ObjectMapper(jsonFactory);
        jsonObjectMapper.configure(JsonParser.Feature.STRICT_DUPLICATE_DETECTION, true);

        jsonWithJaxbAnnotation = jsonObjectMapper.copy();
        jsonWithJaxbAnnotation.registerModule(new JaxbAnnotationModule());
        jaxbReader = jsonWithJaxbAnnotation.reader();
        jaxbWriterPretty = jsonWithJaxbAnnotation.writerWithDefaultPrettyPrinter();
        jaxbWriter = jsonWithJaxbAnnotation.writer();

        YAMLFactory yamlFactory = new YAMLFactory();
        yamlObjectMapper = new ObjectMapper(yamlFactory);
        yamlObjectMapper.configure(JsonParser.Feature.STRICT_DUPLICATE_DETECTION, true);
    }

    public static ObjectMapper getJsonObjectMapper() {
        return jsonObjectMapper;
    }

    public static ObjectWriter getJaxbWriter() {
        return getJaxbWriter(true);
    }

    public static ObjectWriter getJaxbWriter(boolean prettyPrint) {
        return prettyPrint ? jaxbWriterPretty : jaxbWriter;
    }

    public static ObjectReader getJaxbReader() {
        return jaxbReader;
    }

    public static ObjectMapper getYamlObjectMapper() {
        return yamlObjectMapper;
    }

    /**
     * Get or create a JsonNode child of the specified parent.
     *
     * @param parent parent node to get the object from
     * @param name name of the JsonNode to get
     * @return the object
     * @throws RuntimeException if a non-JsonNode child with this name exists
     */
    public static ObjectNode getObject(ObjectNode parent, String name) {
        ObjectNode object = null;
        if (parent.has(name))
            object = (ObjectNode) parent.get(name);
        else
            object = parent.putObject(name);
        return object;
    }

    /**
     * Get a string value from a JSON Object, or substitute a default value if the
     * key doesn't exist.
     *
     * @param parent parent node to get the object from
     * @param name name of the value to get
     * @param defVal value to use if key doesn't exist
     * @return the string value
     */
    public static String getString(JsonNode parent, String name, String defVal) {
        if (parent.has(name))
            return parent.get(name).asText(defVal);
        return defVal;
    }

    /**
     * Get a boolean value from a JSON Object, or substitute a default value if the
     * key doesn't exist.
     *
     * @param parent parent node to get the object from
     * @param name name of the value to get
     * @param defVal value to use if key doesn't exist
     * @return the boolean value
     */
    public static boolean getBoolean(JsonNode parent, String name, boolean defVal) {
        if (parent.has(name)) {
            return parent.get(name).asBoolean(defVal);
        }
        return defVal;
    }

    /**
     * Get a long value from a JSON Object, or substitute a default value if the key
     * doesn't exist.
     *
     * @param parent parent node to get the object from
     * @param name name of the value to get
     * @param defVal value to use if key doesn't exist
     * @return the long value
     */
    public static long getLong(ObjectNode parent, String name, long defVal) {
        if (parent.has(name))
            return parent.get(name).asLong(defVal);
        return defVal;
    }

    /**
     * Get an int value from a JSON Object, or substitute a default value if the key
     * doesn't exist.
     *
     * @param parent parent node to get the object from
     * @param name name of the value to get
     * @param defVal value to use if key doesn't exist
     * @return the int value
     */
    public static int getInt(ObjectNode parent, String name, int defVal) {
        if (parent.has(name))
            return parent.get(name).asInt(defVal);
        return defVal;
    }

    public static ArrayNode arrayOfStrings(ArrayNode arr, List<String> fields) {
        for (String str : fields) {
            arr.add(str);
        }
        return arr;
    }

    public static List<String> getListOfStrings(JsonNode group, String name) {
        if (!group.has(name))
            return Collections.emptyList();
        ArrayNode arr = (ArrayNode) group.get(name);
        List<String> result = new ArrayList<>();
        if (arr != null) {
            for (int i = 0; i < arr.size(); i++) {
                result.add(arr.get(i).asText());
            }
        }
        return result;
    }
}
