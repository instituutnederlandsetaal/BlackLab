package nl.inl.blacklab.server.lib;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import nl.inl.blacklab.server.search.SearchManager;
import nl.inl.blacklab.util.PropertySerializeUtil;
import nl.inl.blacklab.webservice.WebserviceOperation;
import nl.inl.blacklab.webservice.WebserviceParameter;
import nl.inl.util.Json;

/**
 * Query parameters, parsed from a JSON structure
 */
public class QueryParamsJson extends QueryParamsAbstract {

    /** Our parameters, "re-serialized" from the JSON structure */
    final Map<WebserviceParameter, String> params;

    /** Our parameters, "re-serialized" from the JSON structure */
    final Map<WebserviceParameter, Object> typedParams;

    public QueryParamsJson(String corpusName, SearchManager searchManager, User user, String json, WebserviceOperation operation) throws JsonProcessingException {
        super(corpusName, searchManager, user);
        JsonNode jsonNode = Json.getJsonObjectMapper().readTree(json);
        if (!jsonNode.isObject())
            throw new IllegalArgumentException("Expected JSON object node");
        ObjectNode jsonObject = (ObjectNode) jsonNode;
        params = new EnumMap<>(WebserviceParameter.class);
        typedParams = new EnumMap<>(WebserviceParameter.class);
        Iterator<Map.Entry<String, JsonNode>> it = jsonObject.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> entry = it.next();
            Optional<WebserviceParameter> optPar = WebserviceParameter.fromValue(entry.getKey());
            optPar.ifPresent(webserviceParameter -> {
                params.put(webserviceParameter,
                        jsonValueToString(entry.getKey(), entry.getValue()));
                typedParams.put(webserviceParameter,
                        jsonValueToObject(entry.getValue()));
            });
        }
        if (operation != null)
            params.put(WebserviceParameter.OPERATION, operation.value());
    }

    @Override
    public Map<WebserviceParameter, String> getParameters() {
        return Collections.unmodifiableMap(params);
    }

    @Override
    public Map<WebserviceParameter, Object> getTypedParameters() {
        return Collections.unmodifiableMap(typedParams);
    }

    @Override
    protected boolean has(WebserviceParameter par) {
        return params.containsKey(par);
    }

    @Override
    protected String get(WebserviceParameter par) {
        return params.getOrDefault(par, par.getDefaultValue());
    }

    private String jsonValueToString(String name, JsonNode jsonNode) {
        if (jsonNode.isArray()) {
            // group or viewgroup with a list of properties
            if (name.equals(WebserviceParameter.GROUP_BY.value()) || name.equals(WebserviceParameter.VIEW_GROUP.value()))
                return arrayOfArraysToString((ArrayNode)jsonNode);
            throw new IllegalArgumentException("Didn't expect array for key: " + name);
        } else if (jsonNode.isValueNode()) {
            return jsonNode.asText();
        } else {
            throw new IllegalArgumentException("Unexpected JSON type (not array or value) for key: " + name);
        }
    }

    private String arrayOfArraysToString(ArrayNode array) {
        List<String> properties = new ArrayList<>();
        for (int index = 0; index < array.size(); index++) {
            JsonNode value = array.get(index);
            if (!value.isArray())
                throw new IllegalArgumentException("Expected array items to be arrays");
            properties.add(arrayToString((ArrayNode)value));
        }
        return PropertySerializeUtil.combineMultiple(properties.toArray(new String[0]));
    }

    private String arrayToString(ArrayNode array) {
        List<String> properties = new ArrayList<>();
        for (int index = 0; index < array.size(); index++) {
            JsonNode value = array.get(index);
            if (!value.isValueNode())
                throw new IllegalArgumentException("Expected array items to be value nodes");
            properties.add(value.asText());
        }
        return PropertySerializeUtil.combineParts(properties.toArray(new String[0]));
    }

    private Object jsonValueToObject(JsonNode jsonNode) {
        if (jsonNode instanceof ArrayNode arrayNode) {
            return arrayNodeToArray(arrayNode);
        } else if (jsonNode instanceof ObjectNode objectNode) {
            return objectNodeToMap(objectNode);
        } else if (jsonNode.isValueNode()) {
            if (jsonNode.isTextual())
                return jsonNode.asText();
            else if (jsonNode.isInt())
                return jsonNode.asInt();
            else if (jsonNode.isNumber())
                return jsonNode.asDouble();
            else if (jsonNode.isBoolean())
                return jsonNode.asBoolean();
            throw new IllegalArgumentException("Unexpected JSON value type: " + jsonNode.getNodeType());
        } else {
            throw new IllegalArgumentException("Unexpected JSON type (not array or value): " + jsonNode.getNodeType());
        }
    }

    private Map<String, Object> objectNodeToMap(ObjectNode objectNode) {
        Map<String, Object> map = new HashMap<>();
        Iterator<Map.Entry<String, JsonNode>> it = objectNode.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> entry = it.next();
            map.put(entry.getKey(), jsonValueToObject(entry.getValue()));
        }
        return map;
    }

    private Object[] arrayNodeToArray(ArrayNode array) {
        List<Object> properties = new ArrayList<>();
        for (int index = 0; index < array.size(); index++) {
            JsonNode value = array.get(index);
            if (!value.isValueNode())
                throw new IllegalArgumentException("Expected array items to be value nodes");
            properties.add(value.asText());
        }
        return properties.toArray(new Object[0]);
    }
}
