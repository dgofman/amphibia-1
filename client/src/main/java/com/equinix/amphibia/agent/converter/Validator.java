package com.equinix.amphibia.agent.converter;

import static com.equinix.amphibia.agent.converter.Converter.RESOURCE_TYPE;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;

@SuppressWarnings("unchecked")
public class Validator {

    private static final Map<String, Boolean> uniqWarningKeys = new HashMap<String, Boolean>();

    public static void validatePathsParam(String path, JSONObject doc, JSONObject param) {
        if (isWarning(path, doc, param, null, new ArrayList<>(), null)) {
            String in = "";
            if (param.containsKey("in")) {
                in = StringUtils.capitalize(param.get("in").toString());
            }
            Converter.addResult(RESOURCE_TYPE.warnings, in + " [" + param.get("name") + "] doesn't have a default value. Path: " + path);
        }
    }

    public static void validateDefinitionParam(String property, JSONObject doc, JSONObject param, String definitionName, JSONObject defintion, List<String> paths) {
        if (isWarning(property, doc, param, defintion, paths, definitionName)) {
            JSONObject definitions = doc.getJSONObject("definitions");
            if (definitions.containsKey(definitionName)) {
                if (!isWarning(property, doc, param, definitions.getJSONObject(definitionName), paths, definitionName)) {
                    return;
                }
            }
            if (param.containsKey("properties")) {
                JSONObject properties = param.getJSONObject("properties");
                paths.add(property);
                properties.keySet().forEach((name) -> {
                    JSONObject props = properties.getJSONObject(name.toString());
                    validateDefinitionParam(name.toString(), doc, props, definitionName, new JSONObject(), paths);
                });
                return;
            }
            if (!uniqWarningKeys.containsKey(definitionName + ':' + property)) {
                uniqWarningKeys.put(definitionName + ':' + property, true);
                String[] path = Stream.concat(Arrays.stream(paths.toArray()), Arrays.stream(new String[]{property})).toArray(String[]::new);
                Converter.addResult(RESOURCE_TYPE.warnings, "Field [" + String.join(".", path) + "] doesn't have a default value.' Definition: " + definitionName);
            }
        }
    }

    private static boolean isWarning(String property, JSONObject doc, JSONObject param, JSONObject defintion, List<String> paths, String definitionName) {
        if (param.containsKey("$ref") || param.containsKey("schema") || param.containsKey("items")) {
            JSONObject schema;
            if (param.containsKey("$ref")) {
                schema = param;
            } else {
                schema = param.getJSONObject("schema");
                if (!schema.containsKey("$ref")) {
                    schema = param.getJSONObject("items");
                }
            }
            if (schema.containsKey("$ref")) {
                String ref = schema.getString("$ref");
                definitionName = Swagger.getDefinitionName(ref);
                JSONObject definitions = doc.getJSONObject("definitions");
                if (!definitions.containsKey(definitionName)) {
                    Converter.addResult(RESOURCE_TYPE.errors, "Definition '" + definitionName + "' is undefined");
                } else {
                    JSONObject def = definitions.getJSONObject(definitionName);
                    if (def.containsKey("properties")) {
                        JSONObject properties = def.getJSONObject("properties");
                        for (Object name : properties.keySet()) {
                            JSONObject props = properties.getJSONObject(name.toString());
                            validateDefinitionParam(name.toString(), doc, props, definitionName, def, new ArrayList<>());
                        }
                        return false;
                    }
                }
            }
        }
        if (!param.containsKey("default") && !param.containsKey("example") && !param.containsKey("enum")) {
            if ("true".equals(Converter.cmd.getOptionValue(Converter.DEFAULT))) {
                if (defintion != null && defintion.containsKey("example")) {
                    Object example = defintion.optJSONObject("example");
                    if (getExample(example, property) != null) {
                        return false;
                    }
                    for (String key : paths) {
                        example = getExample(example, key);
                    }
                    example = getExample(example, property);
                    if (example != null) {
                        return false;
                    }
                } else if (param.containsKey("type") && param.containsKey("items") && "array".equals(param.getString("type"))) {
                    paths.add(property);
                    return isWarning(property, doc, param.getJSONObject("items"), defintion, paths, definitionName);
                }
                return true;
            }
        }
        return false;
    }

    public static Object getExample(Object example, String key) {
        if (example instanceof JSONObject) {
            example = ((JSONObject) example).get(key);
        }
        if (example instanceof JSONArray) {
            example = ((JSONArray) example).get(0);
        }
        return example;
    }

    public static void validateExample(String ref, Map<Object, Object> example, Schema schema) {
        walk(ref, example, schema.getFields(), schema.getDefinitions(), new ArrayList<>());
    }

    private static void walk(String defintionPath, Map<Object, Object> node1, Map<Object, Object> node2, Map<Object, Object> defintions, ArrayList<String> keys) {
        String definitionName = Swagger.getDefinitionName(defintionPath);
        for (Object key : node1.keySet()) {
            Object value1 = node1.get(key);
            ArrayList<String> paths = (ArrayList<String>) keys.clone();
            paths.add(key.toString());
            if (node2 == null || !node2.containsKey(key)) {
                Converter.addResult(RESOURCE_TYPE.errors, "Unexpected path '" + String.join(".", paths) + "' in example property of definition [" + definitionName + "]");
            } else {
                Object value2 = node2.get(key);
                Map<String, Object> info = (Map<String, Object>) node2.get(key);
                String type = info.get("$type").toString();
                if (null == type) {
                    Converter.addResult(RESOURCE_TYPE.errors, "Unsupported data type '" + type + "' in example property of definition: " + definitionName + "::" + String.join(".", paths));
                } else {
                    switch (type) {
                        case "string":
                            if (!(value1 instanceof String)) {
                                Converter.addResult(RESOURCE_TYPE.errors, "Invalid value for datatype string '" + value1 + "' in example property of definition: " + definitionName + "::" + String.join(".", paths));
                            }
                            break;
                        case "boolean":
                            if (!(value1 instanceof Boolean)) {
                                Converter.addResult(RESOURCE_TYPE.errors, "Invalid value for datatype boolean '" + value1 + "' in example property of definition: " + definitionName + "::" + String.join(".", paths));
                            }
                            break;
                        case "integer":
                        case "number":
                            try {
                                if (!Double.isNaN(Double.parseDouble(String.valueOf(value1)))) {
                                    continue;
                                }
                            } catch (NumberFormatException e) {
                            }
                            Converter.addResult(RESOURCE_TYPE.errors, "Invalid value for datatype number '" + value1 + "' in example property of definition: " + definitionName + "::" + String.join(".", paths));
                            break;
                        case "object":
                            if (value2 instanceof Map) {
                                Object ref = ((Map<Object, Object>) value2).get("$ref");
                                if (ref != null) {
                                    walk(defintionPath, (Map<Object, Object>) value1, (Map<Object, Object>) defintions.get(ref), defintions, paths);
                                }
                            }
                            break;
                        case "array":
                            if (!(value1 instanceof List)) {
                                Converter.addResult(RESOURCE_TYPE.errors, "Invalid value for datatype array '" + value1 + "' in example property of definition: " + definitionName + "::" + String.join(".", paths));
                            } else {
                                Object ref = ((Map<Object, Object>) value2).get("$ref");
                                if (ref != null) {
                                    ((List<Object>) value1).forEach((item) -> {
                                        walk(defintionPath, (Map<Object, Object>) item, (Map<Object, Object>) defintions.get(ref), defintions, paths);
                                    });
                                }
                            }
                            break;
                        default:
                            Converter.addResult(RESOURCE_TYPE.errors, "Unsupported data type '" + type + "' in example property of definition: " + definitionName + "::" + String.join(".", paths));
                            break;
                    }
                }
            }
        }
    }
}
