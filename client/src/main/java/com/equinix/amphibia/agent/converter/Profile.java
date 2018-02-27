package com.equinix.amphibia.agent.converter;

import com.equinix.amphibia.agent.builder.ProjectAbstract;
import com.equinix.amphibia.agent.converter.Converter.RESOURCE_TYPE;
import com.equinix.amphibia.agent.converter.Swagger.ApiInfo;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

@SuppressWarnings({"serial", "unchecked"})
public class Profile {

    protected JSONObject definitions;
    protected Swagger swagger;
    protected final boolean isJSON;
    protected final boolean isNullTests;
    protected final boolean isTypeTests;
    protected final boolean isAddNullTests;
    protected final boolean isAddTypeTests;
    protected final boolean isNullValidation;
    protected final boolean isTypeValidation;
    protected final ArrayList<Object> resources;
    protected final ArrayList<Object> testsuites;
    protected final Map<Object, JSONObject> common;
    protected final Map<Object, Object> profile;
    protected final String responseCodes;

    public static String PROJECT_DIR = "projects";
    public static final String DATA_DIR = "data";

    public static final String RESOURCE_TYPE_FILE = "file";
    public static final String RESOURCE_TYPE_URL = "url";
    public static final String RESOURCE_TYPE_WIZARD = "wizard";

    public final Map<String, Boolean> invalidPropertyValues;

    private static final Logger LOGGER = ProjectAbstract.getLogger(Profile.class.getName());

    public static final String HTTP_STATUS_CODE = "HTTPStatusCode";
    
    public Profile() throws Exception {
        resources = new ArrayList<>();
        testsuites = new ArrayList<>();
        common = new LinkedHashMap<>();
        invalidPropertyValues = new LinkedHashMap<>();
        isJSON = "true".equals(Converter.cmd.getOptionValue(Converter.JSON));
        isNullTests = "true".equals(Converter.cmd.getOptionValue(Converter.NULL));
        isTypeTests = "true".equals(Converter.cmd.getOptionValue(Converter.TYPE));
        isAddNullTests = "true".equals(Converter.cmd.getOptionValue(Converter.ADD_NULL));
        isAddTypeTests = "true".equals(Converter.cmd.getOptionValue(Converter.ADD_TYPE));
        isNullValidation = "true".equals(Converter.cmd.getOptionValue(Converter.NULL_VALIDATION));
        isTypeValidation = "true".equals(Converter.cmd.getOptionValue(Converter.TYPE_VALIDATION));

        String codes = Converter.cmd.getOptionValue(Converter.CODES);
        if (codes == null) {
            responseCodes = "200,201";
        } else {
            responseCodes = codes;
        }

        profile = new LinkedHashMap<Object, Object>() {
            {
                put("version", "1.0.0");
                put("project", new LinkedHashMap<Object, Object>() {
                    {
                        put("id", UUID.randomUUID().toString());
                        put("name", null);
                        put("appendLogs", false);
                        put("continueOnError", true);
                        put("testCaseTimeout", 15000);
                    }
                });
                put("properties", new JSONObject());
                put("resources", resources);
                put("testsuites", testsuites);
                put("common", common);
            }
        };
    }

    public Map<Object, JSONObject> getCommon() {
        return common;
    }

    public void finalize(String projectName) {
        ((LinkedHashMap<Object, Object>) profile.get("project")).put("name", projectName);
    }

    public void setSwagger(Swagger swagger) {
        this.swagger = swagger;
    }

    public void setDefinition(JSONObject doc) {
        this.definitions = doc.getJSONObject("definitions");
    }

    public void addResource(String resourceId, String intf, String inputParam, boolean isURL, String propertiesFile) {
        resources.add(new LinkedHashMap<Object, Object>() {
            {
                put("id", resourceId);
                put("interface", intf);
                put("type", isURL ? RESOURCE_TYPE_URL : RESOURCE_TYPE_FILE);
                put("source", inputParam);
                put("properties", propertiesFile);
                put("headers", new LinkedHashMap<Object, Object>() {
                });
            }
        });
    }

    public void saveFile(JSONObject output, File outputFile) throws Exception {
        for (Object fileName : Swagger.asserts.keySet()) {
            String body = Swagger.asserts.get(fileName);
            save(swagger.getDataDir(), body, fileName.toString(), "asserts", RESOURCE_TYPE.asserts);
        }
        save(new File(DATA_DIR), Swagger.getJson(profile), "profile.json", null, RESOURCE_TYPE.profile);

        PrintWriter writer = new PrintWriter(new FileOutputStream(outputFile, false));
        writer.println(Swagger.getJson(output.toString()));
        writer.close();
        LOGGER.log(Level.INFO, "The test file saved successfully.\n{0}", outputFile);
        Converter.addResult(RESOURCE_TYPE.project, outputFile.getCanonicalPath());
    }

    protected static String save(File dataDir, String json, String fileName, String childDir, RESOURCE_TYPE type)
            throws Exception {
        if ("false".equals(Converter.cmd.getOptionValue(Converter.TESTS))) {
            return null;
        }
        if (childDir != null) {
            dataDir = new File(dataDir, childDir);
        }
        File outputDir = new File(PROJECT_DIR, dataDir.getPath());
        File outputFile = new File(outputDir, fileName);
        if (!outputFile.getParentFile().exists()) {
            outputFile.getParentFile().mkdirs();
        }
        PrintWriter writer = new PrintWriter(new FileOutputStream(outputFile, false));
        writer.println(json);
        writer.close();
        LOGGER.log(Level.INFO, "The file saved successfully.\n{0}", outputFile);
        String filePath = ProjectAbstract.getRelativePath(outputFile.toURI());
        if (type != null) {
            Converter.addResult(type, filePath);
        }
        return filePath;
    }

    @SuppressWarnings("NonPublicExported")
    public void addTestCases(int index, String resourceId, String interfaceName,
            Map<String, List<ApiInfo>> testSuiteMap, JSONObject swaggerProperties) throws Exception {
        JSONObject testSuitesRules = (JSONObject) swaggerProperties.getOrDefault("testsuites", new JSONObject());
        JSONObject testsRules = (JSONObject) swaggerProperties.getOrDefault("tests", new JSONObject());
        for (String testSuiteName : testSuiteMap.keySet()) {
            List<Map<Object, Object>> testcases = new ArrayList<>();
            JSONObject testSuiteRules = (JSONObject) testSuitesRules.getOrDefault(testSuiteName, new JSONObject());
            JSONObject testcasesRules = (JSONObject) testSuiteRules.getOrDefault("testcases", new JSONObject());
            JSONObject testSuiteTests = (JSONObject) testsRules.getOrDefault(testSuiteName, new JSONObject());

            Map<String, Object> testsuite = new LinkedHashMap<>();
            testsuite.put("name", testSuiteName);
            testsuite.put("resource", resourceId);
            for (ApiInfo info : testSuiteMap.get(testSuiteName)) {
                String testFile = getPath(info) + ".json";
                String name = info.methodName + "_" + info.apiName;
                JSONObject testcaseRules = (JSONObject) testcasesRules.getOrDefault(name, new JSONObject());
                JSONArray steps = (JSONArray) testcaseRules.getOrDefault("steps", new JSONArray());
                testcases.add(new LinkedHashMap<Object, Object>() {
                    {
                        put("name", testcaseRules.getOrDefault("name", name));
                        put("path", swagger.getDataDirPath() + "/tests/" + testFile);
                        put("steps", steps);
                    }
                });
                testcasesRules.remove(name);
                addTestSteps(steps, info, testFile, "tests", testSuiteTests);
            }

            if (!testcasesRules.isEmpty()) {
                testcasesRules.keySet().forEach((key) -> {
                    JSONObject testcaseRules = testcasesRules.getJSONObject(key.toString());
                    testcases.add(new LinkedHashMap<Object, Object>() {
                        {
                            put("name", key);
                            put("path", swagger.getDataDirPath() + "/tests/" + key + ".json");
                            put("steps", testcaseRules.getOrDefault("steps", new JSONArray()));
                        }
                    });
                });
            }
            testsuite.put("testcases", testcases);
            testsuites.add(testsuite);
            testSuitesRules.remove(testSuiteName);
        }

        if (!testSuitesRules.isEmpty()) {
            testSuitesRules.keySet().forEach((testSuiteName) -> {
                JSONObject testSuiteRules = (JSONObject) testSuitesRules.get(testSuiteName);
                Map<String, Object> testsuite = new LinkedHashMap<>();
                testsuite.put("name", testSuiteName);
                testsuite.put("resource", resourceId);
                if (testSuiteRules.containsKey("properties")) {
                    testsuite.put("properties", testSuiteRules.get("properties"));
                }
                JSONArray testcases = new JSONArray();
                JSONObject testcasesRules = (JSONObject) testSuiteRules.getOrDefault("testcases", new JSONArray());
                testcasesRules.keySet().forEach((testcaseName) -> {
                    JSONObject testcaseRules = testcasesRules.getJSONObject(testcaseName.toString());
                    testcases.add(new LinkedHashMap<Object, Object>() {
                        {
                            put("name", testcaseName);
                            put("path", swagger.getDataDirPath() + "/tests/" + testcaseName + ".json");
                            put("steps", testcaseRules.getOrDefault("steps", new JSONArray()));
                        }
                    });
                });
                testsuite.put("testcases", testcases);
                testsuites.add(testsuite);
            });
        }
    }

    @SuppressWarnings("NonPublicExported")
    protected void addTestSteps(JSONArray steps, ApiInfo info, String testFile, String childDir, JSONObject testSuiteTests)
            throws Exception {
        JSONObject api = info.api;
        Map<Object, Object> body = new LinkedHashMap<>();

        Map<String, Object> teststep = new LinkedHashMap<>();
        Map<String, Object> step = addStep(info, testFile, api, body);
        Map<Object, Map<Object, Object>> request = (Map<Object, Map<Object, Object>>) step.get("request");
        if (request != null) {
            Map<Object, Object> properties = request.get("properties");
            properties.keySet().forEach((key) -> {
                if (isNullTests) {
                    String name = key.toString().replaceAll("\\.", "_") + "_NULL";
                    JSONObject json = new JSONObject();
                    if (isAddNullTests) {
                        boolean add = true;
                        for (Object item : steps) {
                            if (((JSONObject) item).getString("name").equals(name)) {
                                add = false;
                                break;
                            }
                        }
                        if (add) {
                            json.element("name", name);
                            json.element("common", name);
                            steps.add(json);
                        }
                    }

                    if (!common.containsKey(name)) {
                        json = new JSONObject();
                        json.element("request", new LinkedHashMap<Object, Object>() {
                            {
                                put("properties", new LinkedHashMap<Object, Object>() {
                                    {
                                        put(key, null);
                                    }
                                });
                            }
                        });
                        json.element("response", new LinkedHashMap<Object, Object>() {
                            {
                                put("properties", new LinkedHashMap<Object, Object>() {
                                    {
                                        put(HTTP_STATUS_CODE, 400);
                                    }
                                });
                            }
                        });
                        common.put(name, json);
                    }
                }

                if (isTypeTests) {
                    JSONObject json = new JSONObject();
                    String name = key.toString().replaceAll("\\.", "_") + "_TRUE";
                    if (isAddTypeTests) {
                        json.element("name", name);
                        json.element("common", name);
                        steps.add(json);
                    }

                    if (!common.containsKey(name)) {
                        json = new JSONObject();
                        json.element("request", new LinkedHashMap<Object, Object>() {
                            {
                                put("properties", new LinkedHashMap<Object, Object>() {
                                    {
                                        put(key, properties.get(key) instanceof Boolean ? "true" : true);
                                    }
                                });
                            }
                        });
                        json.element("response", new LinkedHashMap<Object, Object>() {
                            {
                                put("properties", new LinkedHashMap<Object, Object>() {
                                    {
                                        put(HTTP_STATUS_CODE, 400);
                                    }
                                });
                            }
                        });
                        common.put(name, json);
                    }
                }
            });
        }
        teststep.put("request", request);
        teststep.put("response", step.get("response"));
        JSONObject json = JSONObject.fromObject(teststep);
        if (testSuiteTests.containsKey(info.testCaseName)) {
            //TODO
            JSONObject testCaseJSON = testSuiteTests.getJSONObject(info.testCaseName);
            json.putAll(testCaseJSON);
        }
        String testStepFile = save(swagger.getDataDir(), Swagger.getJson(json), testFile, childDir, null);
        Converter.addResult(RESOURCE_TYPE.tests, testStepFile);
    }

    @SuppressWarnings("NonPublicExported")
    protected Map<Object, Object> addTestStepProperties(ApiInfo info, JSONObject api, Map<Object, Object> properties,
            Map<Object, Object> body) {
        if (api.containsKey("parameters")) {
            JSONArray parameters = api.getJSONArray("parameters");
            for (Object obj : parameters) {
                JSONObject param = (JSONObject) obj;
                if (param.containsKey("in") && "body".equals(param.getString("in"))) {
                    if (param.containsKey("schema")) {
                        JSONObject schema = param.getJSONObject("schema");
                        if (schema.containsKey("$ref")) {
                            addBodyAndProperties(info, schema.getString("$ref"), properties, body);
                        }
                    }
                    break;
                }
            }
        }
        return properties;
    }

    @SuppressWarnings("NonPublicExported")
    protected void addBodyAndProperties(ApiInfo info, String ref, Map<Object, Object> properties,
            Map<Object, Object> body) {
        addBodyAndProperties(info, ref, properties, body, new JSONObject(), "");
    }

    @SuppressWarnings("NonPublicExported")
    protected void addBodyAndProperties(ApiInfo info, String ref, Map<Object, Object> properties,
            Map<Object, Object> body, JSONObject examples, String ids) {
        String definitionName = Swagger.getDefinitionName(ref);
        JSONObject definition = definitions.getJSONObject(definitionName);
        if (!definition.isNullObject() && definition.containsKey("properties")) {
            JSONObject props = definition.getJSONObject("properties");
            addBodyAndProperty(info, definitionName, props, properties, body, examples, ids);
        }
    }

    @SuppressWarnings("NonPublicExported")
    protected void addBodyAndProperty(ApiInfo info, String definitionName, JSONObject props,
            Map<Object, Object> properties, Map<Object, Object> body, JSONObject examples, String ids) {
        for (Object key : props.keySet()) {
            String id = ids + (ids.length() == 0 ? "" : ".") + key;

            JSONObject val = (JSONObject) props.get(key);
            String param = escapeParam(val, "${#" + id + "}");

            Object suiteProperty = swagger.getTestRuleProperty(info, id);
            if (suiteProperty != null) {
                addPropertyValue(info, definitionName, examples, properties, id, suiteProperty);
                body.put(key, param);
                continue;
            }

            if (val.containsKey("default")) {
                properties.put(id, val.get("default"));
                body.put(key, param);
            } else if (val.containsKey("example")) {
                addPropertyValue(info, definitionName, examples, properties, id, val.get("example"));
                body.put(key, param);
            } else if (val.containsKey("enum")) {
                addPropertyValue(info, definitionName, examples, properties, id, val.getJSONArray("enum").get(0));
                body.put(key, param);
            } else {
                Map<Object, Object> child = new LinkedHashMap<>();
                if (val.containsKey("$ref")) {
                    addBodyAndProperties(info, val.getString("$ref"), properties, child, examples, id);
                } else if (val.containsKey("items")) {
                    JSONObject items = val.getJSONObject("items");
                    if (items.containsKey("$ref")) {
                        addBodyAndProperties(info, items.getString("$ref"), properties, child, examples, id);
                    } else if (items.containsKey("properties")) {
                        addBodyAndProperty(info, definitionName, items.getJSONObject("properties"), properties, child,
                                examples, id);
                    } else if (items.containsKey("example")) {
                        addPropertyValue(info, definitionName, examples, properties, id, items.get("example"));
                        body.put(key, new Object[]{escapeParam(items, "${#" + id + "}")});
                        continue;
                    }
                } else if (val.containsKey("properties")) {
                    addBodyAndProperty(info, definitionName, val.getJSONObject("properties"), properties, child,
                            examples, id);
                } else {
                    JSONObject definition = definitions.getJSONObject(definitionName);
                    if (definition.containsKey("example")) {
                        Object example = definition.getJSONObject("example");
                        Object value = Validator.getExample(example, key.toString());
                        if (value != null) {
                            addPropertyValue(info, definitionName, examples, properties, id, value);
                            body.put(key, param);
                            continue;
                        } else {
                            String[] keys = id.split("\\.");
                            for (int i = 0; i < keys.length; i++) {
                                Object ex = Validator.getExample(example, keys[i]);
                                if (ex == null && i == 0) {
                                    continue;
                                }
                                example = ex;
                            }
                            example = Validator.getExample(example, key.toString());
                            if (example != null) {
                                addPropertyValue(info, definitionName, examples, properties, id, example);
                                body.put(key, param);
                                continue;
                            }
                        }
                    }
                    addPropertyValue(info, definitionName, examples, properties, id, null);
                    body.put(key, param);
                    continue;
                }

                if (val.containsKey("type") && "array".equals(val.getString("type"))) {
                    if (child.isEmpty()) {
                        body.put(key, new Object[]{});
                    } else {
                        body.put(key, new Object[]{child});
                    }
                } else {
                    body.put(key, child);
                }
            }
        }
    }

    @SuppressWarnings("NonPublicExported")
    protected void addBodyAndProperty(Map<Object, Object> resBody, Map<Object, Object> properties, String ids) {
        resBody.keySet().forEach((key) -> {
            String id = ids + (ids.length() == 0 ? "" : ".") + key;

            Object val = resBody.get(key);
            String param = escapeParam(val, "${#" + id + "}");
            properties.put(id, val);
            resBody.put(key, param);
            if (val instanceof JSONObject) {
                addBodyAndProperty((JSONObject) val, properties, id);
            } else if (val instanceof JSONArray) {
                ((JSONArray) val).forEach((item) -> {
                    addBodyAndProperty((JSONObject) item, properties, id);
                });
            }
        });
    }

    protected String escapeParam(Object val, String param) {
        return "`" + param + "`";
    }

    protected Object findPropertyValueFromExamples(JSONObject examples, String key) {
        String[] path = key.split("\\.");
        Object example = examples;
        if (examples.containsKey("application/json")) {
            example = examples.getJSONObject("application/json");
        } else if (examples.containsKey("application/javascript")) {
            example = examples.getJSONArray("application/javascript");
        }
        return new Object() {
            Object walk(Object example, String[] path, int index) {
                if (path.length > index) {
                    if (example instanceof JSONObject) {
                        if (((JSONObject) example).containsKey(path[index])) {
                            return walk(((JSONObject) example).get(path[index]), path, ++index);
                        }
                    } else if (example instanceof JSONArray && ((JSONArray) example).size() > 0) {
                        return walk(((JSONArray) example).get(0), path, index);
                    }
                }
                return example;
            }
        }.walk(example, path, 0);
    }

    @SuppressWarnings("NonPublicExported")
    public void addPropertyValue(ApiInfo info, String definitionName, JSONObject examples, Map<Object, Object> properties, Object key, Object value) {
        if (isNullValidation && value == null) {
            if (!examples.isEmpty()) {
                value = findPropertyValueFromExamples(examples, key.toString());
                if (value != null) {
                    properties.put(key, value);
                    return;
                }
            }
            String errorKey = definitionName + "|" + key + "|" + value;
            if (!invalidPropertyValues.containsKey(errorKey)) {
                invalidPropertyValues.put(errorKey, true);
                Converter.addResult(RESOURCE_TYPE.warnings,
                        "'" + key + "' parameter is set to NULL. (" + (definitionName != null ? "Definition: " + definitionName : "Path: " + info.path) + ")");
            }
        } else if (isTypeValidation && (value instanceof String && ("true".equals(value) || "false".equals(value)))) {
            String errorKey = definitionName + "|" + key + "|" + value;
            if (!invalidPropertyValues.containsKey(errorKey)) {
                invalidPropertyValues.put(errorKey, true);
                Converter.addResult(RESOURCE_TYPE.warnings,
                        "'" + key + "' parameter is set as BOOLEAN STRING. (" + (definitionName != null ? "Definition: " + definitionName : "Path: " + info.path) + ")");
            }
        }
        properties.put(key, value);
    }

    @SuppressWarnings("NonPublicExported")
    protected Map<String, Object> addStep(ApiInfo info, String fileName, JSONObject api, Map<Object, Object> body)
            throws Exception {
        Map<String, Object> step = new LinkedHashMap<>();

        Object requestBody = null;
        Object requestSchema = null;
        if (api.containsKey("parameters")) {
            JSONArray parameters = api.getJSONArray("parameters");
            for (Object obj : parameters) {
                JSONObject param = (JSONObject) obj;
                if (param.containsKey("in") && "body".equals(param.getString("in"))) {
                    if (param.containsKey("schema")) {
                        JSONObject schema = param.getJSONObject("schema");
                        if (schema.containsKey("$ref")) {
                            String ref = schema.getString("$ref");
                            if (Schema.schemas.get(ref) != null) {
                                requestSchema = Schema.schemas.get(ref);
                                break;
                            }
                        }
                    }
                }
            }
        }

        Map<Object, Object> requestProperties = new LinkedHashMap<>();
        addTestStepProperties(info, api, requestProperties, body);

        if (!body.isEmpty()) {
            requestBody = save(swagger.getDataDir(), Swagger.getJson(body), fileName, "requests",
                    RESOURCE_TYPE.requests);
        }

        Map<Object, Object> responseProperties = new LinkedHashMap<>();
        String responseJSON = null;
        String responseBody = null;
        String responseSchema = null;
        if (api.containsKey("responses")) {
            JSONObject responses = api.getJSONObject("responses");
            for (Object httpCode : responses.keySet()) {
                boolean isResponseCode = responseCodes.contains(httpCode.toString());
                JSONObject response = responses.getJSONObject(httpCode.toString());
                if (response.containsKey("schema")) {
                    JSONObject schema = response.getJSONObject("schema");
                    Map<Object, Object> resBody = new LinkedHashMap<>();
                    if (schema.containsKey("$ref")) {
                        String ref = schema.getString("$ref");
                        if (Schema.schemas.get(ref) != null) {
                            if (isResponseCode) {
                                responseSchema = Schema.schemas.get(ref);
                            } else {
                                Schema.schemas.get(ref);
                            }
                        }
                        addBodyAndProperties(info, schema.getString("$ref"), responseProperties, resBody);
                    }
                    if (!resBody.isEmpty()) {
                        responseJSON = Swagger.getJson(resBody);
                    } else {
                        JSONObject examples = new JSONObject();
                        if (response.containsKey("examples")) {
                            examples = response.getJSONObject("examples");
                            if (schema.containsKey("properties")) {
                                addBodyAndProperty(info, null, schema.getJSONObject("properties"), responseProperties, resBody, examples, "");
                            } else if (schema.containsKey("items")) {
                                JSONObject items = schema.getJSONObject("items");
                                if (items.containsKey("$ref")) {
                                    addBodyAndProperties(info, items.getString("$ref"), responseProperties, resBody, examples, "");
                                }
                            }
                            if (schema.containsKey("$ref")) {
                                addBodyAndProperties(info, schema.getString("$ref"), responseProperties, resBody, examples, "");
                            }
                        }
                        if (!resBody.isEmpty()) {
                            responseJSON = Swagger.getJson(resBody);
                        } else {
                            String example;
                            if (examples.containsKey("application/json")) {
                                example = examples.getString("application/json");
                            } else if (examples.containsKey("application/javascript")) {
                                example = examples.getString("application/javascript");
                            } else {
                                continue;
                            }
                            if (example.startsWith("[")) {
                                JSONArray arr = JSONArray.fromObject(example);
                                if (arr.size() > 0) {
                                    resBody = arr.getJSONObject(0);
                                }
                            } else {
                                resBody = JSONObject.fromObject(example);
                            }
                            addBodyAndProperty(resBody, responseProperties, "assert");
                            if (!resBody.isEmpty()) {
                                responseJSON = Swagger.getJson(resBody);
                            }
                        }
                    }
                }

                if (isResponseCode) {
                    responseBody = responseJSON;
                } else {
                    String httpFile = httpCode + ".json";
                    if (Swagger.asserts.containsKey(httpFile)) {
                        if (!Swagger.asserts.get(httpFile).equals(responseJSON)) {
                            Swagger.asserts.put(getPath(info) + "_" + httpCode + ".json", responseJSON);
                        }
                    } else {
                        Swagger.asserts.put(httpFile, responseJSON);
                    }
                }
            }
        }

        Object responseFile;
        if (responseBody != null) {
            responseFile = save(swagger.getDataDir(), responseBody, fileName, "responses", RESOURCE_TYPE.responses);
        } else {
            responseFile = Swagger.NULL;
        }

        final Object reqBody = requestBody == null ? Swagger.NULL : requestBody;
        final Object reqSchema = requestSchema == null ? Swagger.NULL : requestSchema;
        final Object resSchema = responseSchema == null ? Swagger.NULL : responseSchema;
        step.put("request", new LinkedHashMap<Object, Object>() {
            {
                put("properties", requestProperties);
                put("body", reqBody);
            }
        });
        step.put("response", new LinkedHashMap<Object, Object>() {
            {
                put("properties", responseProperties);
                put("body", responseFile);
                put("asserts", new ArrayList<>());
            }
        });
        return step;
    }

    private String getPath(ApiInfo info) {
        return Swagger.stripName(info.testSuiteName) + "/" + info.methodName + "_" + info.apiName;
    }
}
