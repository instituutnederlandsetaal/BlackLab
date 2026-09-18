package nl.inl.blacklab.indexers.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import nl.inl.util.Json;

public class TestConfigAnnotatedField {

    @Test
    public void testDefaultAndExplicitDocumentContainerAreEquivalent() {
        ConfigAnnotatedField omitted = new ConfigAnnotatedField("contents");
        omitted.setWordPath(".//w");
        Assert.assertEquals(".", omitted.getContainerPath());
        Assert.assertEquals(omitted, omitted.copy());
        Assert.assertEquals(".", omitted.copy().getContainerPath());

        ConfigAnnotatedField explicit = omitted.copy();
        explicit.setContainerPath(".");
        Assert.assertEquals(omitted, explicit);
        Assert.assertEquals(omitted, explicit.copy());
    }

    @Test
    public void testDocumentContainerSerializationUsesDefault() {
        ConfigAnnotatedField omitted = new ConfigAnnotatedField("contents");
        JsonNode omittedJson = Json.getJsonObjectMapper().valueToTree(omitted);
        Assert.assertEquals(".", omittedJson.path("containerPath").asText());

        ConfigAnnotatedField explicit = new ConfigAnnotatedField("contents");
        explicit.setContainerPath(".");
        JsonNode explicitJson = Json.getJsonObjectMapper().valueToTree(explicit);
        Assert.assertEquals(omittedJson, explicitJson);
    }

    @Test
    public void testExplicitPunctuationSurvivesCopy() {
        ConfigAnnotatedField field = new ConfigAnnotatedField("contents");
        field.setWordPath(".//w");
        field.setPunctBeforePath("''");
        field.setPunctAfterLastWordPath("following-sibling::text()[1]");

        ConfigAnnotatedField copy = field.copy();
        Assert.assertEquals(field, copy);
        Assert.assertEquals("''", copy.getPunctBeforePath());
        Assert.assertEquals("following-sibling::text()[1]", copy.getPunctAfterLastWordPath());
    }

    @Test
    public void testInlineTagConfigurationSurvivesCopy() {
        ConfigProcessStep replace = new ConfigProcessStep();
        replace.setAction("replace");
        replace.addParam("find", "apple");
        replace.addParam("replace", "pear");
        ConfigAttribute type = new ConfigAttribute();
        type.setName("type");
        type.setValuePath("@type");
        type.setProcess(List.of(replace));
        ConfigAttribute defaultExclude = new ConfigAttribute();
        defaultExclude.setExclude(true);
        ConfigInlineTag tag = new ConfigInlineTag(".//s", "sentence");
        tag.setTokenIdPath("@id");
        tag.setAttributes(List.of(type, defaultExclude));
        ConfigAnnotatedField field = new ConfigAnnotatedField("contents");
        field.addInlineTag(tag);

        ConfigInlineTag copy = field.copy().getInlineTags().get(0);

        Assert.assertEquals(".//s", copy.getPath());
        Assert.assertEquals("sentence", copy.getDisplayAs());
        Assert.assertEquals("@id", copy.getTokenIdPath());
        Assert.assertFalse(copy.isDefaultIndexAttributes());
        Assert.assertEquals("@type", copy.getAttributes().get("type").getValuePath());
        Assert.assertEquals("replace", copy.getAttributes().get("type").getProcess().get(0).getAction());
        Assert.assertEquals("pear", copy.getAttributes().get("type").getCompiledProcessSteps()
                .performSingle("apple", Map.of()));
        Assert.assertNotSame(type, copy.getAttributes().get("type"));
    }

    @Test
    public void testInlineFragmentMetadataSurvivesCopy() {
        ConfigInlineTag tag = new ConfigInlineTag(".//s", "sentence");
        tag.setType(AnnotationType.FRAGMENT);
        tag.setMetadataContainerPath("..");
        ConfigMetadataBlock block = new ConfigMetadataBlock();
        block.setApplyDocRules(false);
        ConfigMetadataBlock nested = new ConfigMetadataBlock();
        ConfigMetadataField metadataField = nested.getOrCreateField("speaker");
        metadataField.setValuePath("@speaker");
        metadataField.setFragments(FragmentBehaviour.SEPARATE);
        block.setBlocks(List.of(nested));
        tag.getMetadata().add(block);
        ConfigAnnotatedField field = new ConfigAnnotatedField("contents");
        field.addInlineTag(tag);

        ConfigInlineTag copy = field.copy().getInlineTags().get(0);
        Assert.assertEquals(AnnotationType.FRAGMENT, copy.getType());
        Assert.assertEquals("..", copy.getMetadataContainerPath());
        ConfigMetadataBlock copiedBlock = copy.getMetadata().get(0);
        Assert.assertFalse(copiedBlock.isApplyDocRules());
        ConfigMetadataField copiedField = copiedBlock.getBlocks().get(0).getField("speaker");
        Assert.assertEquals("@speaker", copiedField.getValuePath());
        Assert.assertEquals(FragmentBehaviour.SEPARATE, copiedField.getFragments());
        copiedField.setValuePath("@other");
        Assert.assertEquals("@speaker", metadataField.getValuePath());
    }

    @Test
    public void testStandoffFragmentConfigurationSurvivesCopy() {
        ConfigStandoffAnnotations fragment = new ConfigStandoffAnnotations(".//fragment", "@from");
        fragment.setSpanEndPath("@to");
        fragment.setSpanEndIsInclusive(false);
        fragment.setType(AnnotationType.FRAGMENT);
        fragment.setMetadataContainerPath("..");
        ConfigMetadataBlock block = new ConfigMetadataBlock();
        block.getOrCreateField("speaker").setValuePath("@speaker");
        fragment.getMetadata().add(block);
        ConfigAnnotatedField field = new ConfigAnnotatedField("contents");
        field.addStandoffAnnotation(fragment);

        ConfigStandoffAnnotations copy = field.copy().getStandoffAnnotations().get(0);
        Assert.assertEquals(Json.getJsonObjectMapper().valueToTree(fragment),
                Json.getJsonObjectMapper().valueToTree(copy));
        copy.getMetadata().get(0).getField("speaker").setValuePath("@other");
        Assert.assertEquals("@speaker", block.getField("speaker").getValuePath());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testProcessStepCopyDoesNotShareJsonContainers() {
        List<Object> values = new ArrayList<>(List.of("one"));
        Map<String, Object> table = new LinkedHashMap<>();
        table.put("key", values);
        ConfigProcessStep step = new ConfigProcessStep();
        step.setAction("map");
        step.addParam("table", table);

        ConfigProcessStep copy = step.copy();
        Map<String, Object> copiedTable = (Map<String, Object>) copy.getParam().get("table");
        List<Object> copiedValues = (List<Object>) copiedTable.get("key");

        Assert.assertNotSame(table, copiedTable);
        Assert.assertNotSame(values, copiedValues);
        copiedValues.add("two");
        Assert.assertEquals(List.of("one"), values);
    }

    @Test
    public void testPunctuationModesCannotBeCombined() {
        ConfigAnnotatedField field = new ConfigAnnotatedField("contents");
        field.setWordPath(".//w");
        field.setPunctPath(".//text()");
        field.setPunctBeforePath("''");

        InputFormatMessages messages = new InputFormatMessages();
        field.validate(messages);
        Assert.assertEquals(List.of("annotated field contents cannot combine punctPath with explicit punctuation paths"),
                messages.getErrors());
    }

    @Test
    public void testEmptyOptionalXPathIsRejectedButEmptyStringExpressionIsAllowed() {
        ConfigAnnotatedField field = new ConfigAnnotatedField("contents");
        field.setWordPath(".//w");
        field.setPunctBeforePath("");

        InputFormatMessages messages = new InputFormatMessages();
        field.validate(messages);
        Assert.assertEquals(List.of("annotated field contents has an empty punctBeforePath"), messages.getErrors());

        field.setPunctBeforePath("''");
        messages = new InputFormatMessages();
        field.validate(messages);
        Assert.assertTrue(messages.getErrors().isEmpty());
    }

    @Test
    public void testXmlOptionsRejectedForNonXmlFormats() {
        String yaml = """
                version: 2
                fileType: text
                annotatedFields:
                  contents:
                    punctBeforePath: "''"
                    annotations:
                      - name: word
                        valuePath: .
                """;
        try {
            ConfigInputFormat.read(yaml, false, "test", null);
            Assert.fail("Expected XML-specific option to be rejected");
        } catch (InvalidInputFormatConfig e) {
            Assert.assertTrue(e.getMessage().contains("uses XML-specific options with file type text"));
        }
    }

    @Test
    public void testNonXmlFormatSurvivesRoundTrip() throws Exception {
        String yaml = """
                version: 2
                fileType: text
                annotatedFields:
                  contents:
                    annotations:
                      - name: word
                        valuePath: .
                """;
        ConfigInputFormat original = ConfigInputFormat.read(yaml, false, "test", null);
        String json = Json.getJsonObjectMapper().writeValueAsString(original);

        ConfigInputFormat roundTripped = ConfigInputFormat.read(json, true, "test", null);

        Assert.assertNull(roundTripped.getAnnotatedField("contents").getWordPath());
        Assert.assertEquals(".", roundTripped.getAnnotatedField("contents").getContainerPath());
        Assert.assertFalse(roundTripped.getAnnotatedField("contents").hasXmlOnlyOptions());
    }

    @Test
    public void testNullContainerPathIsRejectedInYamlAndJson() {
        List<String> configs = List.of("""
                version: 2
                fileType: xml
                annotatedFields:
                  contents:
                    containerPath: null
                    wordPath: .//w
                """, """
                {
                  "version": 2,
                  "fileType": "xml",
                  "annotatedFields": {
                    "contents": { "containerPath": null, "wordPath": ".//w" }
                  }
                }
                """);

        for (int i = 0; i < configs.size(); i++) {
            try {
                ConfigInputFormat.read(configs.get(i), i == 1, "test", null);
                Assert.fail("Expected null containerPath to be rejected");
            } catch (InvalidInputFormatConfig e) {
                Assert.assertTrue(e.getMessage().contains("containerPath may not be null"));
            }
        }
    }

    @Test
    public void testSourceBookkeepingMetadataNamesAreReserved() {
        String template = """
                    version: 2
                    fileType: xml
                    annotatedFields:
                      contents:
                        wordPath: .//w
                    metadata:
                      containerPath: .
                      fields:
                        - name: %s
                          valuePath: .
                    %s
                    """;
        for (String yaml: List.of(
                template.formatted("contents#source_ranges", ""),
                template.formatted("title", "indexFieldAs:\n  title: contents#source_units"))) {
            try {
                ConfigInputFormat.read(yaml, false, "test", null);
                Assert.fail("Expected reserved metadata name to be rejected");
            } catch (InvalidInputFormatConfig e) {
                Assert.assertTrue(e.getMessage().contains("metadata field name is reserved by BlackLab"));
            }
        }

        ConfigInputFormat.read(template.formatted("email@domain", ""), false, "test", null);
    }
}
