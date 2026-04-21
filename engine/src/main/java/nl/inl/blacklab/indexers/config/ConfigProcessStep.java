package nl.inl.blacklab.indexers.config;

import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

/** Configuration for a processing step on a value. */
@JsonSerialize(using = ConfigProcessStep.ConfigProcessStepSerializer.class)
@JsonDeserialize(using = ConfigProcessStep.ConfigProcessStepDeserializer.class)
public class ConfigProcessStep {

    public static final String KEY_ACTION = "action";

    // Custom Jackson serializer
    static class ConfigProcessStepSerializer extends StdSerializer<ConfigProcessStep> {

        public ConfigProcessStepSerializer() {
            this(null);
        }

        public ConfigProcessStepSerializer(Class<ConfigProcessStep> t) {
            super(t);
        }

        @Override
        public void serialize(ConfigProcessStep value, JsonGenerator gen, SerializerProvider provider) throws java.io.IOException {
            gen.writeStartObject();
            gen.writeStringField(KEY_ACTION, value.getAction());
            for (Map.Entry<String, Object> entry : value.getParam().entrySet()) {
                gen.writeObjectField(entry.getKey(), entry.getValue());
            }
            gen.writeEndObject();
        }
    }

    // Custom Jackson deserializer
    static class ConfigProcessStepDeserializer extends StdDeserializer<ConfigProcessStep> {
        public ConfigProcessStepDeserializer() {
            this(null);
        }
        public ConfigProcessStepDeserializer(Class<?> vc) {
            super(vc);
        }
        @Override
        public ConfigProcessStep deserialize(com.fasterxml.jackson.core.JsonParser jp, DeserializationContext ctxt) throws java.io.IOException {
            JsonNode node = jp.getCodec().readTree(jp);
            ConfigProcessStep step = new ConfigProcessStep();
            JsonNode actionNode = node.get(KEY_ACTION);
            if (actionNode != null && !actionNode.isNull()) {
                step.setAction(actionNode.asText());
            }
            // Add all other fields to param, using default deserialization for values
            node.fields().forEachRemaining(entry -> {
                if (!KEY_ACTION.equals(entry.getKey())) {
                    try {
                        Object value = jp.getCodec().treeToValue(entry.getValue(), Object.class);
                        step.addParam(entry.getKey(), value);
                    } catch (Exception e) {
                        throw new RuntimeException("Error deserializing field '" + entry.getKey() + "'", e);
                    }
                }
            });
            return step;
        }
    }

    /** Method to call */
    private String action;

    /** Extra parameters to pass */
    private final Map<String, Object> param = new LinkedHashMap<>();

    void validate(InputFormatMessages messages) {
        String t = "processing step";
        messages.mustHave(t, action, "method");
    }

    public ConfigProcessStep copy() {
        ConfigProcessStep cp = new ConfigProcessStep();
        cp.setAction(action);
        cp.param.putAll(param);
        return cp;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public Map<String, Object> getParam() {
        return param;
    }

    public void addParam(String name, Object value) {
        this.param.put(name, value);
    }

    @Override
    public String toString() {
        return "ConfigProcessStep [method=" + action + ", param=" + param + "]";
    }
    
}
