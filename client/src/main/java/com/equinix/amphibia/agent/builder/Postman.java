package com.equinix.amphibia.agent.builder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.cli.CommandLine;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

public class Postman extends ProjectAbstract {

    public static final String DEFAULT_ENDPOINT = "{{RestEndPoint}}";
    
    private static final Logger LOGGER = Logger.getLogger(Postman.class.getName());

    protected String jsonContent;
    protected File outputFile;
    protected Map<String, String> headerMap;
    
    protected List<String> collectionIds;

    public Postman(CommandLine cmd) throws Exception {
        super(cmd);
    }

    @Override
    protected void init() throws Exception {
        collectionIds = new ArrayList<>();
        headerMap = new LinkedHashMap<>();
        super.init();
    }

    @Override
    protected void readInputData() throws Exception {
        super.readInputData();
        jsonContent = this.getFileContent(getTemplateFile("postman/postman.json"));
    }

    @Override
    protected void buildProject(String name) throws Exception {
        super.buildProject(name);
        projectName = name;
        outputFile = new File(outputDirPath, name + ".json");
        jsonContent = replace(jsonContent, "<% PROJECT_NAME %>", name);
    }

    @Override
    protected void saveFile() throws Exception {
        super.saveFile();
        LOGGER.log(Level.INFO, "Output file: {0}", outputFile);
        PrintWriter writer = new PrintWriter(new FileOutputStream(outputFile, false));
        writer.println(jsonContent);
        writer.close();
        Builder.addResult(Builder.ADD_PROJECT, outputFile);
    }

    public String getTimestamp() {
        return String.valueOf(new Date().getTime());
    }

    private String getId() {
        String id = "\"00000000-" + String.format("%04d", collectionIds.size()) + "-0000-0000-000000000000\"";
        collectionIds.add(id);
        return id;
    }

    private void addProperty(List<String> list, Object name, Object value) throws Exception {
        String propertyJSON = this.getFileContent(getTemplateFile("postman/property.json"));
        propertyJSON = replace(propertyJSON, "<% NAME %>", name);
        propertyJSON = replace(propertyJSON, "<% VALUE %>", value);
        list.add(tabs(propertyJSON, "\t\t\t\t"));
    }

    @Override
    protected void buildGlobalParameters(JSONArray globals) throws Exception {
        super.buildGlobalParameters(globals);
        List<String> globalList = new ArrayList<>();
        for (Object item : globals) {
            JSONObject globalItem = (JSONObject) item;
            String parameterJSON = this.getFileContent(getTemplateFile("postman/property.json"));
            parameterJSON = replace(parameterJSON, "<% NAME %>", globalItem.get("name"));
            parameterJSON = replace(parameterJSON, "<% VALUE %>", getValue(globalItem.get("value")));
            globalList.add(tabs(parameterJSON, "\t\t"));
        }
        jsonContent = replace(jsonContent, "<% GLOBALS %>", String.join(",\n", globalList));
    }

    @Override
    protected void buildProperties(JSONObject properties) throws Exception {
        super.buildProperties(properties);
        List<String> environmentList = new ArrayList<>();
        for (Object name : projectPropertiesJSON.keySet()) {
            addProperty(environmentList, name, getValue(projectPropertiesJSON.get(name)));
        }
        String envJSON = this.getFileContent(getTemplateFile("postman/environment.json"));
        envJSON = replace(envJSON, "<% NAME %>", projectName);
        envJSON = replace(envJSON, "<% PARAMETERS %>", String.join(",\n", environmentList));
        jsonContent = replace(jsonContent, "<% ENVIRONMENTS %>", envJSON);
    }

    @Override
    protected void buildInterfaces(JSONArray interfaces) throws Exception {
        super.buildInterfaces(interfaces);
        List<String> headers = new ArrayList<>();
        for (int index = 0; index < interfaces.size(); index++) {
            JSONObject interfaceItem = interfaces.getJSONObject(index);
            if (interfaceItem.containsKey("headers")) {
                String headerJSON = this.getFileContent(getTemplateFile("postman/header.json"));
                headerJSON = replace(headerJSON, "<% ID %>", getId());
                headerJSON = replace(headerJSON, "<% NAME %>", interfaceItem.getString("name"));
                headerJSON = replace(headerJSON, "<% TIMESTAMP %>", getTimestamp());
                List<String> headerPropertyList = new ArrayList<>();

                JSONObject headersJSON = interfaceItem.getJSONObject("headers");
                List<String> headersList = new ArrayList<>();
                for (Object name : headersJSON.keySet()) {
                    addProperty(headerPropertyList, name, getValue(headersJSON.get(name)));
                    headersList.add(name + ": " + headersJSON.get(name));
                }
                headerMap.put(interfaceItem.getString("id"), String.join("\\n", headersList));
                interfacesJson.put(interfaceItem.getString("id"), interfaceItem);

                headerJSON = replace(headerJSON, "<% PARAMETERS %>", String.join(",\n", headerPropertyList));
                headers.add(headerJSON);
            }
        }
        jsonContent = replace(jsonContent, "<% HEADER_PRESETS %>", String.join(",\n", headers));
    }

    @Override
    protected void buildResources(JSONArray resources) throws Exception {
        super.buildResources(resources);

        Map<String, String> folderList = new LinkedHashMap<>();
        Map<String, String> requestList = new LinkedHashMap<>();

        for (int index = 0; index < resources.size(); index++) {
            JSONObject resource = resources.getJSONObject(index);
            String iId = resource.getString("interfaceId");
            String headers = headerMap.get(iId);
            JSONObject testsuites = resource.getJSONObject("testsuites");
            for (Object name : testsuites.keySet()) {
                JSONObject testSuiteItem = testsuites.getJSONObject(name.toString());
                properties.setTestSuite(testSuiteItem.getJSONObject("properties"));

                Map<String, String> testSuiteRequests = new LinkedHashMap<>();
                buildTestCases(testSuiteRequests, resource, name.toString(), testSuiteItem, properties, interfacesJson.getJSONObject(iId).getString("basePath"), headers);
                requestList.putAll(testSuiteRequests);

                String testSuiteJSON = this.getFileContent(getTemplateFile("postman/folder.json"));
                String id = getId();
                testSuiteJSON = replace(testSuiteJSON, "<% NAME %>", name);
                testSuiteJSON = replace(testSuiteJSON, "<% ID %>", id);
                testSuiteJSON = replace(testSuiteJSON, "<% REQUEST_IDS %>", String.join(",\n\t\t\t\t\t\t", testSuiteRequests.keySet()));
                folderList.put(id, testSuiteJSON);
            }
        }

        jsonContent = replace(jsonContent, "<% TESTSUITE_IDS %>", String.join(",\n\t\t\t\t", folderList.keySet()));
        jsonContent = replace(jsonContent, "<% FOLDERS %>", String.join(",\n", folderList.values()));
        jsonContent = replace(jsonContent, "<% REQUESTS %>", String.join(",\n", requestList.values()));
    }

    protected void buildTestCases(Map<String, String> testSuiteRequests, JSONObject resource, String testSuiteName, JSONObject testSuiteItem, Properties properties, String basePath, String headers) throws Exception {
        JSONArray testcases = testSuiteItem.getJSONArray("testcases");
        String endpoint = "{{" + resource.getString("endpoint") + "}}";
        for (Object item : testcases) {
            JSONObject testCaseItem = (JSONObject) item;
            if ("restrequest".equals(testCaseItem.get("type"))) {
                properties.setTestCase(testCaseItem.getJSONObject("properties"));

                String id = getId();
                String testcase = this.getFileContent(getTemplateFile("postman/request.json"));
                testcase = replace(testcase, "<% ID %>", id);
                testcase = replace(testcase, "<% NAME %>", testCaseItem.getString("name"));
                testcase = replace(testcase, "<% SUMMARY %>", testCaseItem.getString("summary"));
                testcase = replace(testcase, "<% HEADERS %>", headers);
                testcase = replace(testcase, "<% ENDPOINT %>", endpoint);

                Object path = testCaseItem.get("path");
                if (path != null) {
                    path = properties.replace(path.toString()).replaceAll("&amp;", "&");
                    testcase = replace(testcase, "<% PATH %>", basePath + path);
                }

                Object body = Properties.getBody(projectDir, resource.getString("resourceId"), testSuiteName, testCaseItem.getString("name"), true);
                if (!isNULL(body)) {
                    body = getValue(properties.replace(prettyJson(body).replaceAll("\\n", "\\\\n").replaceAll("\\t", "\\\\t").replaceAll("\\\"", "\\\\\"")));
                }

                testcase = replace(testcase, "<% BODY %>", body);

                for (Object key : testCaseItem.keySet()) {
                    if (!"assertions".equals(key)) {
                        Object value = testCaseItem.get(key);
                        testcase = replace(testcase, "<% " + key.toString().toUpperCase() + " %>", value instanceof String ? value : toJson(value));
                    }
                }

                testSuiteRequests.put(id, testcase);
            }
        }
    }
}
