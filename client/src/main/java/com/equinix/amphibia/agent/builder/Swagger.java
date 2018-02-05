package com.equinix.amphibia.agent.builder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.cli.CommandLine;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

public class Swagger extends ProjectAbstract {

    protected File outputFile;
    protected String swaggerJSON;
    protected Map<Integer, List<String>> interfaceMap;

    private static final Logger LOGGER = Logger.getLogger(Swagger.class.getName());

    public Swagger(CommandLine cmd) throws Exception {
        super(cmd);
    }

    @Override
    protected void init() throws Exception {
        interfaceMap = new LinkedHashMap<>();
        super.init();
    }

    @Override
    protected void readInputData() throws Exception {
        super.readInputData();
        swaggerJSON = this.getFileContent(getTemplateFile("swagger/swagger.json"));
    }

    @Override
    protected void saveFile() throws Exception {
        super.saveFile();
        List<String> paths = new ArrayList<>();
        swaggerJSON = replace(swaggerJSON, "<% PATHS %>", String.join(",\n", paths));

        PrintWriter writer = new PrintWriter(new FileOutputStream(outputFile, false));
        Builder.addResult(Builder.ADD_PROJECT, outputFile);
        writer.println(swaggerJSON);
        writer.close();
    }

    @Override
    protected void printEnd() throws Exception {
        String note = "Open http://editor2.swagger.io in browser and copy & paste JSON from: {0}";
        Object[] args = new String[]{outputFile.getName()};
        Builder.addResult(Builder.ADD_NOTE, note);
        Builder.addResult(Builder.ADD_ARGS, args);
        LOGGER.log(Level.INFO, "Saved successfully.\n\nNOTE:\n\n");
        LOGGER.log(Level.INFO, MessageFormat.format(note, args));
    }

    @Override
    protected void buildProject(String name) throws Exception {
        super.buildProject(name);
        outputFile = new File(outputDirPath, name + ".json");
        swaggerJSON = replace(swaggerJSON, "<% PROJECT_NAME %>", name);
    }

    @Override
    protected void buildGlobalParameters(JSONArray globals) throws Exception {
        super.buildGlobalParameters(globals);
    }

    @Override
    protected void buildProperties(JSONObject properties) throws Exception {
        super.buildProperties(properties);
    }

    @Override
    protected void buildInterfaces(JSONArray interfaces) throws Exception {
        super.buildInterfaces(interfaces);
        List<String> parameters = new ArrayList<>();
        String refJSON = this.getFileContent(getTemplateFile("swagger/ref.json"));
        for (int index = 0; index < interfaces.size(); index++) {
            JSONObject interfaceItem = interfaces.getJSONObject(index);
            if (interfaceItem.containsKey("headers")) {
                JSONObject headers = interfaceItem.getJSONObject("headers");
                List<String> params = new ArrayList<>();
                for (Object key : headers.keySet()) {
                    String name = "param" + (parameters.size() + 1);
                    String parameterJSON = this.getFileContent(getTemplateFile("swagger/headerParam.json"));
                    parameterJSON = replace(parameterJSON, "<% PARAM %>", name);
                    parameterJSON = replace(parameterJSON, "<% IN %>", "header");
                    parameterJSON = replace(parameterJSON, "<% NAME %>", key);
                    parameterJSON = replace(parameterJSON, "<% VALUE %>", getValue(headers.get(key)));
                    parameters.add(parameterJSON);

                    params.add(refJSON.replaceAll("<% PARAM %>", name));
                }
                interfaceMap.put(index, params);
            }
        }
        swaggerJSON = replace(swaggerJSON, "<% PARAMETERS %>", String.join(",\n", parameters));
    }

    @Override
    protected void buildResources(JSONArray resources) throws Exception {
        super.buildResources(resources);

        Properties properties = new Properties(globalsJson, projectPropertiesJSON);

        Map<Object, List<String>> paths = new LinkedHashMap<>();
        Map<Object, String> definitions = new LinkedHashMap<>();
        for (int index = 0; index < resources.size(); index++) {
            JSONObject resource = resources.getJSONObject(index);
            List<String> headerParams = interfaceMap.get(index);
            JSONObject testsuites = resource.getJSONObject("testsuites");
            for (Object name : testsuites.keySet()) {
                JSONObject testSuiteItem = testsuites.getJSONObject(name.toString());
                properties.setTestSuite(testSuiteItem.getJSONObject("properties"));
                buildTestCases(paths, definitions, name.toString(), testSuiteItem, headerParams, properties);
            }
        }
        List<String> joinPaths = new ArrayList<>();
        paths.keySet().forEach((path) -> {
            joinPaths.add("\t\t\"/" + path + "\": {\n" + String.join(",\n", paths.get(path)) + "\n\t\t}");
        });
        swaggerJSON = replace(swaggerJSON, "<% PATHS %>", String.join(",\n", joinPaths));
        swaggerJSON = replace(swaggerJSON, "<% DEFINITIONS %>", String.join(",\n", definitions.values()));
    }

    protected void buildTestCases(Map<Object, List<String>> paths, Map<Object, String> definitions, String testSuiteName, JSONObject testSuiteItem, List<String> headerParams, Properties properties) throws Exception {
        JSONArray testcases = testSuiteItem.getJSONArray("testcases");
        for (Object item : testcases) {
            JSONObject testcase = (JSONObject) item;
            if ("restrequest".equals(testcase.get("type"))) {
                properties.setTestCase(testcase.getJSONObject("properties"));
                Object path;
                List<String> params = new ArrayList<>();
                params.addAll(headerParams);

                String test = this.getFileContent(getTemplateFile("swagger/path.json"));
                test = replace(test, "<% TAG %>", testSuiteName);
                test = replace(test, "<% SUMMARY %>", testcase.getString("summary"));

                test = replace(test, "<% NAME %>", testcase.containsKey("operationId") ? testcase.getString("operationId") : testcase.getString("name"));

                test = replace(test, "<% METHOD %>", testcase.getString("method").toLowerCase());
                test = replace(test, "<% MEDIATYPE %>", testcase.containsKey("mediatype") ? testcase.getString("mediatype") : "application/json");

                path = testcase.get("path");
                if (path != null) {
                    String[] values = path.toString().replaceAll("&amp;", "&").split("\\?");
                    for (int i = 0; i < values.length; i++) {
                        StringBuilder sb = new StringBuilder(values[i]);
                        Matcher m = Pattern.compile("\\$\\{#(.*?)#(.*?)\\}", Pattern.DOTALL | Pattern.MULTILINE).matcher(values[i]);
                        while (m.find()) {
                            JSONObject source = properties.getProperty(m.group(1));
                            if (source == null) {
                                continue;
                            }
                            String key = m.group(2);
                            Matcher m2 = Pattern.compile("_[A-Z][A-Z0-9_]+", Pattern.DOTALL).matcher(key);
                            if (m2.find()) {
                                String param = m2.group(0).substring(1).toLowerCase();
                                String parameterJSON = this.getFileContent(getTemplateFile("swagger/parameter.json"));
                                parameterJSON = replace(parameterJSON, "<% REQUIRED %>", i == 0);
                                parameterJSON = replace(parameterJSON, "<% IN %>", i == 0 ? "path" : "query");
                                parameterJSON = replace(parameterJSON, "<% NAME %>", param);
                                parameterJSON = replace(parameterJSON, "<% VALUE %>", getValue(source.get(key)));
                                parameterJSON = replace(parameterJSON, "<% TYPE %>", typeof(source.get(key)));
                                params.add(parameterJSON);

                                int offset = values[i].length() - sb.length();
                                sb.replace(m.start(0) - offset, m.end(2) - offset + 1, "{" + param + "}");
                            }
                        }
                        values[i] = sb.toString();
                    }
                    path = values[0];
  
                    Object body = testcase.get("body");
                    if (!isNULL(body)) {
                        body = properties.replace(prettyJson(body));
                        String bodyJSON = this.getFileContent(getTemplateFile("swagger/body.json"));
                        if (testcase.containsKey("definition")) {
                            String defName = testcase.getString("definition");
                            params.add(bodyJSON.replace("<% SCHEMA %>", "\"$ref\": \"#/definitions/" + defName + "\""));
                            if (!definitions.containsKey(defName)) {
                                definitions.put(defName, this.getFileContent(getTemplateFile("swagger/definition.json"))
                                        .replace("<% NAME %>", defName)
                                        .replace("<% EXAMPLE %>", body.toString()));
                            }
                        } else {
                            params.add(bodyJSON.replace("<% SCHEMA %>", "\"example\": " + body.toString()));
                        }
                    }
                }
                test = replace(test, "<% PARAMETERS %>", String.join(",\n", params));

                List<String> assertionList = new ArrayList<>();
                if (testcase.containsKey("assertions")) {
                    for (Object assertion : testcase.getJSONArray("assertions")) {
                        JSONObject assertionItem = (JSONObject) assertion;
                        String assertionJSON = this.getFileContent(getTemplateFile("swagger/assertion.json"));
                        Object value = ((Map<?, ?>) assertionItem.get("replace")).get("value");
                        assertionJSON = replace(assertionJSON, "<% NAME %>", assertionItem.get("type"));
                        assertionJSON = replace(assertionJSON, "<% VALUE %>", properties.replace(value));
                        assertionList.add(assertionJSON);
                    }
                }
                test = replace(test, "<% ASSERTIONS %>", String.join(",\n", assertionList));

                List<String> list = paths.get(path);
                if (list == null) {
                    list = new ArrayList<>();
                    paths.put(path, list);
                }
                list.add(test);
            }
        }
    }
}
