package com.equinix.amphibia.agent.builder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.text.MessageFormat;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.cli.CommandLine;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

public class JsonScript extends ProjectAbstract {

    protected File outputFile;
    protected JSONObject jsonOutput;

    private static final Logger LOGGER = Logger.getLogger(Mocha.class.getName());

    public JsonScript(CommandLine cmd) throws Exception {
        super(cmd);
        outputFile = null;
        jsonOutput = null;
    }

    @Override
    protected void readInputData() throws Exception {
        super.readInputData();
        jsonOutput = new JSONObject();
    }

    @Override
    protected void buildProject(String name) throws Exception {
        super.buildProject(name);
        outputFile = new File(outputDirPath, name + ".json");
    }

    @Override
    protected void saveFile() throws Exception {
        super.saveFile();
        PrintWriter writer;
        writer = new PrintWriter(new FileOutputStream(outputFile, false));
        writer.println(prettyJson(jsonOutput));
        writer.close();
        Builder.addResult(Builder.ADD_PROJECT, outputFile);
    }

    @Override
    protected void printEnd() throws Exception {
        Object[] args = new String[]{outputFile.getName()};
        LOGGER.log(Level.INFO, "Saved successfully.\n\nNOTE:\n\n");
        LOGGER.log(Level.INFO, MessageFormat.format("File saved \"{0}\"", args));
    }

    @Override
    protected void buildGlobalParameters(JSONArray globals) throws Exception {
        super.buildGlobalParameters(globals);
        jsonOutput.element("globals", globalsJson);
    }

    @Override
    protected void buildProperties(JSONObject properties) throws Exception {
        super.buildProperties(properties);
    }

    @Override
    protected void buildInterfaces(JSONArray interfaces) throws Exception {
        super.buildInterfaces(interfaces);
        interfaces.forEach((item) -> {
            JSONObject interfaceItem = (JSONObject) item;
            if (interfaceItem.containsKey("headers")) {
                interfacesJson.element(interfaceItem.getString("name"), interfaceItem.getJSONObject("headers"));
            }
        });
    }

    @Override
    protected void buildResources(JSONArray resources) throws Exception {
        super.buildResources(resources);

        Properties properties = new Properties(globalsJson, projectPropertiesJSON);

        JSONArray resourcesJSON = new JSONArray();
        for (Object item : resources) {
            JSONObject resource = (JSONObject) item;
            JSONObject testSuitesJSON = new JSONObject();
            JSONObject resourceJSON = new JSONObject();
            resourceJSON.element("endpoint", resource.getString("endpoint"));
            resourceJSON.element("headers", interfacesJson.getJSONObject(resource.getString("interface")));
            JSONObject testsuites = resource.getJSONObject("testsuites");
            for (Object name : testsuites.keySet()) {
                JSONObject testSuiteItem = testsuites.getJSONObject(name.toString());
                JSONObject testCasesJSON = new JSONObject();
                properties.setTestSuite(testSuiteItem.getJSONObject("properties"));
                buildTestCases(testCasesJSON, testSuiteItem.getJSONArray("testcases"), properties);
                testSuitesJSON.element(name.toString(), testCasesJSON);
            }
            resourceJSON.element("testsuites", testSuitesJSON);
            resourcesJSON.add(resourceJSON);
        }
        jsonOutput.element("resources", resourcesJSON);
    }

    protected void buildTestCases(JSONObject testCasesJSON, JSONArray testcases, Properties properties) throws Exception {
        for (Object item : testcases) {
            JSONObject testcase = (JSONObject) item;
            JSONObject testCaseJson = new JSONObject();
            if ("restrequest".equals(testcase.get("type"))) {
                properties.setTestCase(testcase.getJSONObject("properties"));

                testcase.keySet().forEach((key) -> {
                    testCaseJson.element(key.toString(), testcase.get(key));
                });
                Object path = testcase.get("path");
                if (path != null) {
                    path = properties.replace(path.toString()).replaceAll("&amp;", "&");
                    testCaseJson.element("path", path);
                }

                Object body = testcase.get("body");
                if (!isNULL(body)) {
                    body = properties.replace(prettyJson(body));
                }
                testCaseJson.element("body", body);

                List<?> assertions = (List<?>) testcase.get("assertions");
                if (assertions != null) {
                    JSONObject assertionsJSON = new JSONObject();
                    assertions.forEach((assertion) -> {
                        JSONObject assertionItem = (JSONObject) assertion;
                        JSONObject assertionJSON = new JSONObject();
                        JSONObject replace = assertionItem.getJSONObject("replace");
                        replace.keySet().forEach((key) -> {
                            assertionJSON.element(key.toString(), replace.get(key));
                        });
                        assertionsJSON.element(assertionItem.get("type").toString(), assertionJSON);
                    });
                    testCaseJson.element("assertions", assertionsJSON);
                }
                testCasesJSON.element(testcase.getString("name"), testCaseJson);
            }
        }
    }
}
