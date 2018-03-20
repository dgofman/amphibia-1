package com.equinix.amphibia.agent.converter;

import java.io.File;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

import org.apache.commons.cli.CommandLine;
import org.yaml.snakeyaml.Yaml;

import com.equinix.amphibia.agent.builder.ProjectAbstract;
import com.equinix.amphibia.agent.converter.Converter.RESOURCE_TYPE;

import net.sf.json.JSONArray;
import net.sf.json.JSONNull;
import net.sf.json.JSONObject;

@SuppressWarnings({"serial", "unchecked"})
public final class Swagger {

    private final CommandLine cmd;
    private final String projectDir;
    private final JSONObject doc;
    private final JSONObject output;
    private final JSONObject rulesAndPrperties;
    private final Profile profile;
    private final String resourceId;
    private final boolean isDataGenerate;
    private final boolean isMerge;

    public static final Map<Object, String> asserts = new LinkedHashMap<>();
    public static final JSONNull NULL = JSONNull.getInstance();

    public Swagger(CommandLine cmd, String projectDir, String resourceId, JSONObject rulesAndPrperties, InputStream input, JSONObject output, Profile profile) throws Exception {
        String content = ProjectAbstract.getFileContent(input);
        if (content.trim().startsWith("{")) {
            this.doc = JSONObject.fromObject(content);
        } else {
            Yaml yaml = new Yaml();
            Object json = yaml.load(content);
            this.doc = JSONObject.fromObject(json);
        }
        this.cmd = cmd;
        this.projectDir = projectDir;
        this.rulesAndPrperties = rulesAndPrperties;
        this.output = output;
        this.profile = profile;
        this.isMerge = "true".equals(Converter.cmd.getOptionValue(Converter.MERGE));
        if (resourceId != null) {
            this.resourceId = resourceId;
            this.isDataGenerate = false;
        } else {
            this.resourceId = UUID.randomUUID().toString();
            this.isDataGenerate = true;
        }
    }

    public boolean isDataGenerate() {
        return isDataGenerate;
    }

    public String init(String name, int index, String inputParam, boolean isURL, String propertiesFile) throws Exception {
        output.put("version", "1.0.0");
        if (name == null) {
            JSONObject info = doc.getJSONObject("info");
            if (!info.isNullObject()) {
                name = (String) info.getString("title").replaceAll(" ", "");
            }
        }
        if (name == null || name.trim().length() == 0) {
            name = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
        }
        if (!output.containsKey("name")) {
            output.put("name", name);
        }
        profile.setDefinition(doc);
        parse(index, inputParam, isURL, propertiesFile);
        return name;
    }

    public String getResourceId() {
        return resourceId;
    }

    public String getDataDirPath() {
        return Profile.DATA_DIR + "/" + resourceId;
    }

    public File getDataDir() {
        return new File(Profile.DATA_DIR, resourceId);
    }

    public static String getJson(List<?> value) throws Exception {
        return getJson(JSONArray.fromObject(value).toString());
    }

    public static String getJson(Map<?, ?> value) throws Exception {
        return getJson(JSONObject.fromObject(value).toString());
    }

    public static String getJson(String strJson) throws Exception {
        ScriptEngine scriptEngine = new ScriptEngineManager().getEngineByName("JavaScript");
        scriptEngine.put("jsonString", strJson);
        scriptEngine.eval("result = JSON.stringify(JSON.parse(jsonString), null, 4)");
        return ((String) scriptEngine.get("result")).replaceAll(" {4}", "\t");
    }

    protected void parse(int index, String inputParam, boolean isURL, String propertiesFile) throws Exception {
        String host = (String) doc.getOrDefault("host", "localhost");
        if (!host.startsWith("http")) {
            JSONArray schemes = new JSONArray();
            if (doc.containsKey("schemes")) {
                schemes = doc.getJSONArray("schemes");
            } else {
                schemes.add("http");
            }
            String[] pair = host.split(":");
            host = schemes.get(0) + "://" + pair[0] + ':'
                    + ((pair.length == 2 ? pair[1] : "http".equals(schemes.get(0)) ? 80 : 443));
        } else {
            Converter.addResult(RESOURCE_TYPE.warnings, "Please fix a host value. https://swagger.io/docs/specification/2-0/api-host-and-base-path/");
        }

        JSONObject headers = new JSONObject();
        JSONArray hosts = output.containsKey("hosts") ? output.getJSONArray("hosts") : new JSONArray();
        JSONArray globals = output.containsKey("globals") ? output.getJSONArray("globals") : new JSONArray();
        if (rulesAndPrperties != null) {
            if (isMerge) {
                JSONArray resources = rulesAndPrperties.getJSONObject("info").getJSONArray("resources");
                for (Object item : resources) {
                    JSONObject resource = (JSONObject) item;
                    if (resourceId.equals(resource.getString("id"))) {
                        headers.putAll(resource.getJSONObject("headers"));
                        break;
                    }
                }
            }

            profile.getCommon().putAll(rulesAndPrperties.getJSONObject("commons"));
            JSONObject endpoints = rulesAndPrperties.getJSONObject("endpoints");
            for (Object key : endpoints.keySet()) {
                boolean newEndPoint = true;
                for (int i = 0; i < globals.size(); i++) {
                    if (key.equals(globals.getJSONObject(i).getString("name"))) {
                        globals.getJSONObject(i).put("value", endpoints.get(key));
                        newEndPoint = false;
                        break;
                    }
                }
                if (newEndPoint) {
                    if (!hosts.contains(endpoints.get(key))) {
                        hosts.add(endpoints.get(key));
                    }
                    globals.add(0, new LinkedHashMap<String, Object>() {
                        {
                            put("name", key);
                            put("value", endpoints.get(key));
                            put("type", "endpoint");
                        }
                    });
                }
            }
            JSONObject propertyGlobals = rulesAndPrperties.getJSONObject("globalProperties");
            for (Object key : propertyGlobals.keySet()) {
                boolean newProp = true;
                for (int i = 0; i < globals.size(); i++) {
                    if (key.equals(globals.getJSONObject(i).getString("name"))) {
                        globals.getJSONObject(i).put("value", propertyGlobals.get(key));
                        newProp = false;
                        break;
                    }
                }
                if (newProp) {
                    globals.add(new LinkedHashMap<String, Object>() {
                        {
                            put("name", key);
                            put("value", propertyGlobals.get(key));
                        }
                    });
                }
            }
        } else {
            final String hostVal = host;
            globals.add(new LinkedHashMap<String, Object>() {
                {
                    put("name", "RestEndPoint" + index);
                    put("value", hostVal);
                    put("type", "endpoint");
                }
            });
        }

        if (!hosts.contains(host)) {
            hosts.add(host);
        }
        output.put("hosts", hosts);

        JSONArray interfaces = output.containsKey("interfaces") ? output.getJSONArray("interfaces") : new JSONArray();
        String interfaceBasePath = (String) doc.getOrDefault("basePath", "/");
        String interfaceName = interfaceBasePath;
        String interfaceId = UUID.randomUUID().toString();
        String param = cmd.getOptionValue(Converter.INTERFACES);
        if (param != null) {
            String[] params = param.split(",");
            if (params.length > index && !params[index].isEmpty()) {
                if (params[index].contains("::")) {
                    String[] pair = params[index].split("::");
                    interfaceName = pair[0];
                    interfaceBasePath = pair[1];
                } else {
                    interfaceName = params[index];
                }
            }
        }

        final String name = "/".equals(interfaceName) ? "interface" + (index + 1) : interfaceName;
        final String basePath = interfaceBasePath;
        interfaces.add(new LinkedHashMap<String, Object>() {
            {
                put("id", interfaceId);
                put("name", name);
                put("basePath", basePath);
                put("type", "rest");
                put("headers", headers);
            }
        });
        output.put("globals", globals);
        output.put("interfaces", interfaces);

        profile.addResource(resourceId, interfaceId, inputParam, isURL, rulesAndPrperties, propertiesFile);

        JSONArray projectResources = output.containsKey("projectResources") ? output.getJSONArray("projectResources") : new JSONArray();
        JSONObject testsuites = output.containsKey("testsuites") ? output.getJSONObject("testsuites") : new JSONObject();

        addTestSuite(index, resourceId, interfaceId, interfaceBasePath, testsuites);

        JSONObject properties = output.containsKey("properties") ? output.getJSONObject("properties") : new JSONObject();
        if (isMerge && rulesAndPrperties != null) {
            JSONObject projectProperties = rulesAndPrperties.getJSONObject("projectProperties");
            projectProperties.keySet().forEach((key) -> {
                Object value = projectProperties.get(key);
                if (properties.containsKey(key)) {
                    boolean isEquials;
                    if (value instanceof String) {
                        isEquials = value.toString().equals(properties.get(key));
                    } else {
                        isEquials = value == properties.get(key);
                    }
                    if (!isEquials) {
                        Converter.addResult(RESOURCE_TYPE.warnings, "conflicting/ambiguous property name - " + key);
                    }
                }
                properties.put(key.toString(), value);
            });
        }
        output.put("projectProperties", properties);

        projectResources.add(new LinkedHashMap<String, Object>() {
            {
                put("resourceId", resourceId);
                put("interfaceId", interfaceId);
                put("endpoint", "RestEndPoint" + index);
                put("testsuites", testsuites);
            }
        });
        output.put("projectResources", projectResources);
    }

    protected void addTestSuite(int index, String resourceId, String interfaceName, String interfaceBasePath, JSONObject testsuites) throws Exception {
        JSONObject paths = doc.getJSONObject("paths");
        Map<String, List<ApiInfo>> testSuiteMap = new TreeMap<>();
        paths.keySet().forEach((path) -> {
            String originalURL = path.toString();
            JSONObject apis = paths.getJSONObject(originalURL);
            apis.keySet().forEach((methodName) -> {
                if (!"parameters".equals(methodName)) {
                    JSONObject api = apis.getJSONObject(methodName.toString());
                    String apiName = stripName(path.toString());
                    String testSuiteName = "TestSuite-" + testSuiteMap.size();
                    if (api.containsKey("tags")) {
                        JSONArray tags = api.getJSONArray("tags");
                        if (tags.size() > 0) {
                            testSuiteName = (String) tags.get(0);
                        }
                    }
                    testSuiteName = Swagger.stripName(testSuiteName);
                    List<ApiInfo> apiList = testSuiteMap.get(testSuiteName);
                    if (apiList == null) {
                        apiList = new ArrayList<>();
                        testSuiteMap.put(testSuiteName, apiList);
                    }
                    apiList.add(new ApiInfo(interfaceName, interfaceBasePath, testSuiteName, methodName.toString(), apiName, originalURL, api, apis));
                }
            });
        });

        JSONObject testSuitesRules = new JSONObject();
        if (rulesAndPrperties != null) {
            testSuitesRules = rulesAndPrperties.getJSONObject("testsuites");
        }
        for (String testSuiteName : testSuiteMap.keySet()) {
            JSONArray testcases = new JSONArray();
            JSONObject testSuiteRule = new JSONObject();
            if (isMerge && testSuitesRules.containsKey(testSuiteName)) {
                testSuiteRule.accumulateAll(testSuitesRules.getJSONObject(testSuiteName));
            }
            addTestCases(index, testcases, testSuiteMap.get(testSuiteName), testSuiteRule);
            testsuites.put(testSuiteName, new LinkedHashMap<String, Object>() {
                {
                    put("properties", testSuiteRule.getOrDefault("properties", new JSONObject()));
                    put("testcases", testcases);
                }
            });
        }

        this.profile.addTestCases(index, resourceId, interfaceName, testSuiteMap, rulesAndPrperties == null ? new JSONObject() : rulesAndPrperties);
    }

    protected void addTestCases(int index, JSONArray testcases, List<ApiInfo> apiList, JSONObject testSuiteRule) throws Exception {
        JSONObject testcasesRule = (JSONObject) testSuiteRule.getOrDefault("testcases", new JSONObject());
        for (ApiInfo info : apiList) {
            String summaryInfo = info.apiName;
            if (info.api.containsKey("summary")) {
                summaryInfo = info.api.getString("summary");
            } else if (info.api.containsKey("summary")) {
                summaryInfo = info.api.getString("summary");
            }
            info.testCaseName = info.methodName + "_" + info.apiName;
            JSONObject testcaseRule = (JSONObject) testcasesRule.getOrDefault(info.testCaseName, new JSONObject());
            Map<String, Object> properties = new LinkedHashMap<>();
            if (testcaseRule.containsKey("properties")) {
                properties = testcaseRule.getJSONObject("properties");
            }
            String summary = summaryInfo;
            JSONObject config = new JSONObject();
            config.put("type", "restrequest");
            config.put("name", info.testCaseName);
            config.put("summary", summary);
            parseConfig(config, index, properties, info);
            config.put("properties", properties);
            testcases.add(config);
        }
    }

    @SuppressWarnings("ResultOfObjectAllocationIgnored")
    protected JSONObject parseConfig(JSONObject config, int index, Map<String, Object> properties, ApiInfo info) throws Exception {
        JSONObject api = info.api;

        JSONObject responses = api.getJSONObject("responses");
        for (Object httpCode : responses.keySet()) {
            properties.put(Profile.HTTP_STATUS_CODE, "default".equals(httpCode) ? 200 : Integer.parseInt(httpCode.toString()));
            break;
        }

        Definition definition = new Definition(doc, this);
        parseDefinition(info, definition, info.apis, properties);
        parseDefinition(info, definition, api, properties);

        String path = info.path;
        for (String name : definition.getParameters().keySet()) {
            String paramValue = definition.getParameters().get(name);
            if (paramValue != null) {
                path = path.replaceAll("\\{" + name + "\\}", paramValue);
            }
        }

        final String replacePath = path + definition.getQueries();
        config.put("method", info.methodName);
        config.put("path", replacePath);
        config.put("operationId", api.getString("operationId"));
        if (definition.ref != null) {
            config.put("definition", definition.ref.split("#/definitions/")[1]);
        }

        for (Object httpCode : responses.keySet()) {
            JSONObject response = responses.getJSONObject(httpCode.toString());
            if (response != null) {
                if (response.containsKey("schema") && response.getJSONObject("schema").containsKey("$ref")) {
                    new Schema(this, projectDir, response.getJSONObject("schema").getString("$ref"), "responses");
                }
            }
        }

        return config;
    }

    protected void parseDefinition(ApiInfo info, Definition definition, JSONObject api, Map<String, Object> properties) throws Exception {
        if (api.containsKey("parameters")) {
            String methodName = info.methodName;
            for (Object item : api.getJSONArray("parameters")) {
                JSONObject param = (JSONObject) item;
                try {
                    String in = null;
                    if (param.containsKey("in")) {
                        in = param.getString("in");
                    }
                    String type = null;
                    if (param.containsKey("type")) {
                        type = param.getString("type");
                    }
                    if (null != in) {
                        switch (in) {
                            case "body":
                                JSONObject schema = param.getJSONObject("schema");
                                Schema newSchema = null;
                                if (schema.containsKey("$ref")) {
                                    definition.ref = schema.getString("$ref");
                                    definition.getRef(definition.ref);
                                    newSchema = new Schema(this, projectDir, definition.ref, "requests");
                                }
                                JSONObject example = definition.getExample();
                                if (newSchema != null && example != null) {
                                    Validator.validateExample(schema.getString("$ref"), (Map<Object, Object>) example, newSchema);
                                }
                                break;
                            case "query":
                                if (param.containsKey("default") || param.containsKey("enum")) {
                                    definition.addQueryParam(info, param.getString("name"), Definition.getEnumOrDefault(param), properties, type);
                                } else {
                                    definition.addQueryParam(info, param.getString("name"), properties, type);
                                }
                                break;
                            case "path":
                                definition.addParameter(info, param.getString("name"), Definition.getEnumOrDefault(param), properties, param.getString("type"));
                                break;
                            default:
                                break;
                        }
                    }
                    Validator.validatePathsParam(methodName + " - " + info.path, doc, param);
                } catch (Exception e) {
                    Converter.addResult(RESOURCE_TYPE.errors, methodName + "::" + info.path + " - "+ e.toString() + " (" + param + ")");
                }
            }
        }
    }

    public static String getPath(File path) {
        return path.getPath().replaceAll("\\\\", "/");
    }

    public static String getDefinitionName(String ref) {
        return ref.split("/")[2];
    }

    public static String stripName(String name) {
        if (name.charAt(0) == '/') {
            name = name.substring(1);
        }
        return name.replaceAll("[ -\\/\\.]", "_").replaceAll("\\{", "").replaceAll("\\}", "").replaceAll("_+", "_");
    }

    public JSONObject getDoc() {
        return doc;
    }

    public JSONObject getRulesAndPrperties() {
        return rulesAndPrperties;
    }

    @SuppressWarnings("NonPublicExported")
    public Object getTestRuleProperty(ApiInfo info, String id) {
        if (rulesAndPrperties != null) {
            JSONObject testsuites;
            if (rulesAndPrperties.getJSONObject("testsuites").containsKey(info.testSuiteName)) {
                testsuites = rulesAndPrperties.getJSONObject("testsuites").getJSONObject(info.testSuiteName);
                if (testsuites.containsKey(id)) {
                    return "${#TestSuite#" + id + "}";
                }
            }
            if (rulesAndPrperties.getJSONObject("properties").containsKey(id)) {
                return "${#Project#" + id + "}";
            }
        }
        return null;
    }

    final class ApiInfo {

        String interfaceName;
        String interfaceBasePath;
        String testSuiteName;
        String methodName;
        String apiName;
        String path;
        JSONObject api;
        JSONObject apis;

        String testCaseName;

        public ApiInfo(String interfaceName, String interfaceBasePath, String testSuiteName, String methodName, String apiName, String path, JSONObject api, JSONObject apis) {
            this.interfaceName = interfaceName;
            this.interfaceBasePath = interfaceBasePath;
            this.testSuiteName = testSuiteName;
            this.methodName = methodName.toUpperCase();
            this.apiName = apiName;
            this.path = path;
            this.api = api;
            this.apis = apis;
        }

        @Override
        public String toString() {
            return "ApiInfo [interfaceName=" + interfaceName + ", interfaceBasePath=" + interfaceBasePath
                    + ", testSuiteName=" + testSuiteName + ", methodName=" + methodName + ", apiName=" + apiName
                    + ", path=" + path + ", api=" + api + ", apis=" + apis + ", testCaseName=" + testCaseName + "]";
        }

    }
}
