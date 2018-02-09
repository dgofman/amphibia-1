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

@SuppressWarnings("serial")
public class Profile {

    protected JSONObject definitions;
    protected Swagger swagger;
    protected final boolean isJSON;
    protected final boolean isNullTests;
    protected final boolean isTypeTests;
    protected final boolean isAddNullTests;
    protected final boolean isAddTypeTests;
    protected final ArrayList<Object> resources;
    protected final ArrayList<Object> testsuites;
    protected final Map<Object, JSONObject> common;
    protected final Map<Object, Object> profile;

    public static String PROJECT_DIR = "projects";
    public static final String DATA_DIR = "data";
    public static final String ASSERTS_DIR = "asserts";

    public static final String RESOURCE_TYPE_FILE = "file";
    public static final String RESOURCE_TYPE_URL = "url";
    public static final String RESOURCE_TYPE_WIZARD = "wizard";

    private static final Logger LOGGER = Logger.getLogger(Profile.class.getName());

    public Profile() throws Exception {
        resources = new ArrayList<>();
        testsuites = new ArrayList<>();
        common = new LinkedHashMap<>();
        isJSON = "true".equals(Converter.cmd.getOptionValue(Converter.JSON));
        isNullTests = "true".equals(Converter.cmd.getOptionValue(Converter.NULL));
        isTypeTests = "true".equals(Converter.cmd.getOptionValue(Converter.TYPE));
        isAddNullTests = "true".equals(Converter.cmd.getOptionValue(Converter.ADD_NULL));
        isAddTypeTests = "true".equals(Converter.cmd.getOptionValue(Converter.ADD_TYPE));

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

    @SuppressWarnings("unchecked")
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
        resources.add(
                new LinkedHashMap<Object, Object>() {
            {
                put("id", resourceId);
                put("interface", intf);
                put("type", isURL ? RESOURCE_TYPE_URL : RESOURCE_TYPE_FILE);
                put("source", inputParam);
                put("properties", propertiesFile);
                put("headers", new LinkedHashMap<Object, Object>() {});
            }
        });
    }

    public void saveFile(JSONObject output, File outputFile) throws Exception {
        for (Object httpCode : Swagger.asserts.keySet()) {
            JSONObject item = Swagger.asserts.getJSONObject(httpCode.toString());
            Schema.save(swagger.getDataDir(), item.toString(), item.getString("status"), ASSERTS_DIR);
        }
        save(new File(DATA_DIR), Swagger.getJson(profile), "profile.json", null, RESOURCE_TYPE.profile);

        PrintWriter writer = new PrintWriter(new FileOutputStream(outputFile, false));
        writer.println(Swagger.getJson(output.toString()));
        writer.close();
        LOGGER.log(Level.INFO, "The test file saved successfully.\n{0}", outputFile);
        Converter.addResult(RESOURCE_TYPE.project, outputFile.getCanonicalPath());
    }

    protected static String save(File dataDir, String json, String fileName, String childDir, RESOURCE_TYPE type) throws Exception {
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
    public void addTestCases(int index, String resourceId, String interfaceName, Map<String, List<ApiInfo>> testSuiteMap) throws Exception {
        for (String testSuiteName : testSuiteMap.keySet()) {
            List<ApiInfo> testcases = testSuiteMap.get(testSuiteName);
            List<Map<Object, Object>> tests = new ArrayList<>();

            Map<String, Object> testcase = new LinkedHashMap<>();
            testcase.put("name", testSuiteName);
            testcase.put("resource", resourceId);
            for (ApiInfo info : testcases) {
                String fileName = info.apiName;
                String testFile = Swagger.stripName(testSuiteName) + "/" + info.methodName + "_" + fileName + ".json";
                JSONArray steps = new JSONArray();
                tests.add(new LinkedHashMap<Object, Object>() {
                    {
                        put("name", info.methodName + "_" + fileName);
                        put("path", swagger.getDataDirPath() + "/tests/" + testFile);
                        put("steps", steps);
                    }
                });
                addTestSteps(steps, info, testFile, "tests", fileName);
            }
            testcase.put("testcases", tests);
            testsuites.add(testcase);
        }
    }

    @SuppressWarnings({ "NonPublicExported", "unchecked" })
    protected void addTestSteps(JSONArray steps, ApiInfo info, String testFile, String childDir, String fileName) throws Exception {
        JSONObject api = info.api;
        Map<Object, Object> body = new LinkedHashMap<>();

        Map<String, Object> teststep = new LinkedHashMap<>();
        teststep.put("defaultName", info.testCaseName);
        Map<String, Object> step = addStep(info, testFile, api, body);
		Map<Object, Map<Object, Object>> request = (Map<Object, Map<Object, Object>>) step.get("request");
        if (request != null) {
            Map<Object, Object> properties = request.get("properties");
            properties.keySet().forEach((key) -> {
                if (isNullTests) {
                    String name = key.toString().replaceAll("\\.", "_") + "_NULL";
                    JSONObject json = new JSONObject();
                    if (isAddNullTests) {
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
                                        put(key, null);
                                    }
                                });
                            }
                        });
                        json.element("response", new LinkedHashMap<Object, Object>() {
                            {
                                put("properties", new LinkedHashMap<Object, Object>() {
                                    {
                                        put("HTTPStatusCode", 400);
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
                                        put("HTTPStatusCode", 400);
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
        String testStepFile = save(swagger.getDataDir(), Swagger.getJson(teststep), testFile, childDir, null);
        Converter.addResult(RESOURCE_TYPE.tests, testStepFile);
    }

    @SuppressWarnings("NonPublicExported")
    protected Map<Object, Object> addTestStepProperties(ApiInfo info, JSONObject api, Map<Object, Object> properties, Map<Object, Object> body) {
        if (api.containsKey("parameters")) {
            JSONArray parameters = api.getJSONArray("parameters");
            for (Object obj : parameters) {
                JSONObject param = (JSONObject) obj;
                if (param.containsKey("in") && "body".equals(param.getString("in"))) {
                    if (param.containsKey("schema")) {
                        JSONObject schema = param.getJSONObject("schema");
                        if (schema.containsKey("$ref")) {
                            addBodyAndProperties(info, schema.getString("$ref"), properties, body, "");
                        }
                    }
                    break;
                }
            }
        }
        return properties;
    }

    @SuppressWarnings("NonPublicExported")
    protected void addBodyAndProperties(ApiInfo info, String ref, Map<Object, Object> properties, Map<Object, Object> body, String ids) {
        String definitionName = Swagger.getDefinitionName(ref);
        JSONObject definition = definitions.getJSONObject(definitionName);
        if (!definition.isNullObject() && definition.containsKey("properties")) {
            JSONObject props = definition.getJSONObject("properties");
            addBodyAndProperty(info, definitionName, props, properties, body, ids);
        }
    }

    @SuppressWarnings("NonPublicExported")
    protected void addBodyAndProperty(ApiInfo info, String definitionName, JSONObject props, Map<Object, Object> properties, Map<Object, Object> body, String ids) {
        for (Object key : props.keySet()) {
            String id = ids + (ids.length() == 0 ? "" : ".") + key;

            JSONObject val = (JSONObject) props.get(key);
            String param = escapeParam(val, "${#" + id + "}");

            Object suiteProperty = swagger.getTestRuleProperty(info, id);
            if (suiteProperty != null) {
                properties.put(id, suiteProperty);
                body.put(key, param);
                continue;
            }

            if (val.containsKey("default")) {
                properties.put(id, val.get("default"));
                body.put(key, param);
            } else if (val.containsKey("example")) {
                properties.put(id, val.get("example"));
                body.put(key, param);
            } else if (val.containsKey("enum")) {
                properties.put(id, val.getJSONArray("enum").get(0));
                body.put(key, param);
            } else {
                Map<Object, Object> child = new LinkedHashMap<>();
                if (val.containsKey("$ref")) {
                    addBodyAndProperties(info, val.getString("$ref"), properties, child, id);
                } else if (val.containsKey("items")) {
                    JSONObject items = val.getJSONObject("items");
                    if (items.containsKey("$ref")) {
                        addBodyAndProperties(info, items.getString("$ref"), properties, child, id);
                    } else if (items.containsKey("properties")) {
                        addBodyAndProperty(info, definitionName, items.getJSONObject("properties"), properties, child, id);
                    } else if (items.containsKey("example")) {
                        properties.put(id, items.get("example"));
                        body.put(key, new Object[]{escapeParam(items, "${#" + id + "}")});
                        continue;
                    }
                } else if (val.containsKey("properties")) {
                    addBodyAndProperty(info, definitionName, val.getJSONObject("properties"), properties, child, id);
                } else {
                    JSONObject definition = definitions.getJSONObject(definitionName);
                    if (definition.containsKey("example")) {
                        Object example = definition.getJSONObject("example");
                        Object value = Validator.getExample(example, key.toString());
                        if (value != null) {
                            properties.put(id, value);
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
                                properties.put(id, example);
                                body.put(key, param);
                                continue;
                            }
                        }
                    }
                    properties.put(id, null);
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

    protected String escapeParam(JSONObject val, String param) {
        return isJSON ? "`" + param + "`" : "" + param + "'";
    }

    @SuppressWarnings("NonPublicExported")
    protected Map<String, Object> addStep(ApiInfo info, String fileName, JSONObject api, Map<Object, Object> body) throws Exception {
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
            requestBody = save(swagger.getDataDir(), Swagger.escapeJson(body), fileName, "requests", RESOURCE_TYPE.requests);
        }

        Map<Object, Object> responseProperties = new LinkedHashMap<>();
        Object responseBody = null;
        Object responseSchema = null;
        Object responseAsserts = new ArrayList<>();
        if (api.containsKey("responses")) {
            JSONObject responses = api.getJSONObject("responses");
            for (Object httpCode : responses.keySet()) {
                JSONObject response = responses.getJSONObject(httpCode.toString());
                if (response.containsKey("schema")) {
                    JSONObject schema = response.getJSONObject("schema");
                    if (schema.containsKey("$ref")) {
                        String ref = schema.getString("$ref");
                        if (Schema.schemas.get(ref) != null) {
                            responseSchema = Schema.schemas.get(ref);
                        }
                        Map<Object, Object> resBody = new LinkedHashMap<>();
                        addBodyAndProperties(info, schema.getString("$ref"), responseProperties, resBody, "");
                        if (!resBody.isEmpty()) {
                            responseBody = save(swagger.getDataDir(), Swagger.escapeJson(resBody), fileName, "responses", RESOURCE_TYPE.responses);
                        }
                    }
                }

                if (Swagger.asserts.containsKey(httpCode)) {
                    responseAsserts = Swagger.getPath(new File(Schema.getSchemasDir(swagger.getDataDir()), ASSERTS_DIR)) + "/" + Swagger.asserts.getJSONObject(httpCode.toString()).getString("status") + ".json";
                    break;
                }
            }
        }

        final Object reqBody = requestBody == null ? Swagger.NULL : requestBody;
        final Object reqSchema = requestSchema == null ? Swagger.NULL : requestSchema;
        final Object resBody = responseBody == null ? Swagger.NULL : responseBody;
        final Object resSchema = responseSchema == null ? Swagger.NULL : responseSchema;
        final Object resAsserts = responseAsserts;
        step.put("request", new LinkedHashMap<Object, Object>() {
            {
                put("properties", requestProperties);
                put("body", reqBody);
                put("schema", reqSchema);
            }
        });
        step.put("response", new LinkedHashMap<Object, Object>() {
            {
                put("properties", responseProperties);
                put("body", resBody);
                put("schema", resSchema);
                put("asserts", resAsserts);
            }
        });
        return step;
    }
}
