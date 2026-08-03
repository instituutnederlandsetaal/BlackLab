package nl.inl.util;

import java.io.StringWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class TestXmlUtil {

    @Test
    public void testXmlToPlainText() {
        // Remove tags
        Assert.assertEquals("test test test", XmlUtil.xmlToPlainText("test <bla>test</bla> test"));

        // Interpret entities
        Assert.assertEquals("test > test", XmlUtil.xmlToPlainText("test &gt; test"));

        // Interpret numerical entities
        Assert.assertEquals("test A test", XmlUtil.xmlToPlainText("test &#65; test"));

        // Interpret hex numerical entities
        Assert.assertEquals("test B test", XmlUtil.xmlToPlainText("test &#x42; test"));

        // Ignore entities inside tags
        Assert.assertEquals("test test", XmlUtil.xmlToPlainText("test <bla test=\"&quot;\" > test"));

        // Other whitespace characters normalized to space
        Assert.assertEquals("test test", XmlUtil.xmlToPlainText("test\ntest"));

        // Normalize whitespace; keep leading space
        Assert.assertEquals(" test test", XmlUtil.xmlToPlainText("\t\ttest \n\rtest"));

        // Replace with non-breaking spaces
        Assert.assertEquals("test\u00A0test", XmlUtil.xmlToPlainText("test test", true));

        // Replace with non-breaking spaces; keep trailing space
        Assert.assertEquals("test\u00A0test\u00A0", XmlUtil.xmlToPlainText("test test ", true));
    }

    @Test
    public void testYamlMultiLineWithCanonical() throws Exception {
        String []keys = new String[]{"SomeKeyString:\n\nAnotherLine", "SomeKeyInteger:\n\nAnotherLine","SomeKeyBoolean:\n\nAnotherLine", "Simple"};
        ObjectMapper jsonMapper = Json.getJsonObjectMapper();
        ObjectNode jsonRoot = jsonMapper.createObjectNode();
        jsonRoot.put(keys[0], "valstring");
        jsonRoot.put(keys[1], 20);
        jsonRoot.put(keys[2], true);
        jsonRoot.put(keys[3], "simple");

        ObjectMapper yamlObjectMapper = Json.getYamlObjectMapper();
        StringWriter swriter = new StringWriter();

        yamlObjectMapper.writeValue(swriter, jsonRoot);

        ObjectMapper readMapper =  Json.getYamlObjectMapper();
        ObjectNode readJsonRoot = (ObjectNode) readMapper.readTree(swriter.toString());
        Assert.assertEquals("valstring", Json.getString(readJsonRoot, keys[0], ""));
        Assert.assertEquals(20, Json.getInt(readJsonRoot, keys[1], 0));
        Assert.assertTrue(Json.getBoolean(readJsonRoot, keys[2], false));
        Assert.assertEquals("simple", Json.getString(readJsonRoot, keys[3], ""));
    }

    @Test
    public void testSerializeStandardCollections() throws JsonProcessingException {
        Map<String, Object> map = new HashMap<>();
        map.put("string", "Hallo");
        map.put("list", List.of("aap", "noot", "mies"));

        ObjectMapper jsonMapper = Json.getJsonObjectMapper();
        JsonNode tree = jsonMapper.valueToTree(map);

        Map<String, Object> reconstructed = jsonMapper.treeToValue(tree, Map.class);

        Assert.assertEquals(reconstructed.size(), map.size());
        Assert.assertEquals(reconstructed.get("string"), map.get("string"));
        Assert.assertEquals(reconstructed.get("list"), map.get("list"));
    }
}
