package com.equinix.amphibia.agent.converter;

import java.util.HashMap;
import java.util.Map;

import com.equinix.amphibia.agent.converter.Swagger.ApiInfo;

import net.sf.json.JSONObject;

public final class Definition {

    private JSONObject doc;
    private JSONObject example;
    private StringBuilder queries;
    private Map<String, String> parameters;
    private Swagger swagger;

    public String ref;

    public Definition(JSONObject doc, Swagger swagger) {
        this.doc = doc;
        this.swagger = swagger;
        queries = new StringBuilder();
        parameters = new HashMap<>();
    }

    public JSONObject getRef(String id) {
        String[] paths = id.split("/");
        JSONObject node = doc;
        for (int i = 1; i < paths.length; i++) {
            node = node.getJSONObject(paths[i]);
            if (node.isNullObject()) {
                break;
            }
        }
        if (node.containsKey("example")) {
            example = node.getJSONObject("example");
        }
        return node;
    }

    @SuppressWarnings("NonPublicExported")
    public Object addQueryParam(ApiInfo info, String key, Map<String, Object> properties, String type) throws Exception {
        return addQueryParam(info, key, Swagger.NULL, properties, type);
    }

    @SuppressWarnings("NonPublicExported")
    public Object addQueryParam(ApiInfo info, String key, Object value, Map<String, Object> properties, String type) throws Exception {
        String apiKey = info.methodName.toUpperCase() + '_' + info.apiName + '_' + key.toUpperCase();
        if (queries.length() > 0) {
            queries.append("&amp;");
        }
        queries.append(String.format("%s=${#%s}", key, apiKey));
        properties.put(apiKey, getValue(info, apiKey, type, value));
        return value;
    }

    @SuppressWarnings("NonPublicExported")
    public void addParameter(ApiInfo info, String key, Object value, Map<String, Object> properties, String type) {
        String apiKey = info.methodName.toUpperCase() + '_' + info.apiName + '_' + key.toUpperCase();
        properties.put(apiKey, getValue(info, apiKey, type, value));
        parameters.put(key, String.format("\\${#%s}", apiKey));
    }

    private Object getValue(ApiInfo info, String apiKey, String type, Object value) {
        Object suiteProperty = swagger.getTestRuleProperty(info, apiKey);
        if (suiteProperty != null) {
            value = suiteProperty;
        } else if ("boolean".equals(type)) {
            return false;
        }
        return value;
    }

    public JSONObject getExample() {
        return example;
    }

    public JSONObject getDoc() {
        return doc;
    }

    public String getQueries() {
        return queries.length() == 0 ? "" : "?" + queries.toString();
    }

    public Map<String, String> getParameters() {
        return parameters;
    }

    public static String getEnumOrDefault(JSONObject value) {
        if (value.containsKey("default")) {
            return value.getString("default");
        } else if (value.containsKey("enum")) {
            return value.getJSONArray("enum").get(0).toString();
        } else {
            return "";
        }
    }
}
