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

}
