package com.equinix.amphibia.agent.builder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipOutputStream;

import org.apache.commons.cli.CommandLine;

import com.equinix.amphibia.agent.converter.Profile;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.io.IOUtils;

@SuppressWarnings("unchecked")
public class JUnit extends ProjectAbstract {

    protected File outputFile;
    protected File zipFile;
    protected File pomFile;
    protected String pomXML;
    protected String tests;
    protected List<String> testCaseOrder;

    private static final Logger LOGGER = ProjectAbstract.getLogger(JUnit.class.getName());

    public JUnit(CommandLine cmd) throws Exception {
        super(cmd);
    }

    @Override
    protected void readInputData() throws Exception {
        super.readInputData();
        testCaseOrder = new ArrayList<>();
        pomXML = this.getFileContent(getTemplateFile("junit/pom.xml"));
        tests = this.getFileContent(getTemplateFile("junit/Tests.java"));
    }

    @Override
    protected void buildProject(String name) throws Exception {
        super.buildProject(name);
        outputFile = new File(outputDirPath, name + ".java");
        zipFile = new File(outputDirPath, name + ".zip");

        pomXML = pomXML.replaceAll("<% PROJECT_NAME %>", name);
        for (String arg : cmd.getArgs()) {
            String[] pair = arg.split("=");
            if ("groupId".equals(pair[0])) {
                pomXML = pomXML.replaceAll("<% GROUP_ID %>", pair[1]);
            }
        }
        pomXML = pomXML.replaceAll("<% GROUP_ID %>", "localhost");
        tests = replace(tests, "<% PROJECT_NAME %>", name);
    }

    @Override
    protected void saveFile() throws Exception {
        super.saveFile();
        PrintWriter writer;

        pomFile = new File(outputDirPath, "pom.xml");
        writer = new PrintWriter(new FileOutputStream(pomFile, false));
        writer.println(pomXML);
        writer.close();
        LOGGER.log(Level.INFO, "The pom.xml file saved successfully.\n{0}", pomFile);

        String content = this.getFileContent(getTemplateFile("junit/project"));
        writer = new PrintWriter(new FileOutputStream(new File(outputDirPath, ".project"), false));
        writer.println(replace(content, "<% PROJECT_NAME %>", projectName));
        writer.close();

        IOUtils.copy(getTemplateFile("junit/classpath").toURL().openStream(), new FileOutputStream(new File(outputDirPath, ".classpath")));
        IOUtils.copy(getTemplateFile("junit/AmphibiaBaseTest.java").toURL().openStream(), new FileOutputStream(new File(outputDirPath, "AmphibiaBaseTest.java")));
        IOUtils.copy(getTemplateFile("junit/Profile.java").toURL().openStream(), new FileOutputStream(new File(outputDirPath, "Profile.java")));
        Builder.addResult(Builder.ADD_PROJECT, outputFile);

        writer = new PrintWriter(new FileOutputStream(outputFile, false));
        writer.println(tests.replace("<% TESTCASES %>", String.join(",\n\t\t\t", testCaseOrder)));
        writer.close();
    }

    @Override
    protected void saveResources() throws Exception {
        ZipOutputStream zout = new ZipOutputStream(new FileOutputStream(zipFile));
        addToZip(new File(outputDirPath, "AmphibiaBaseTest.java"), zout, outputDirPath);
        addToZip(new File(outputDirPath, "Profile.java"), zout, outputDirPath);
        addToZip(outputFile, zout, outputDirPath);
        addToZip(pomFile, zout, outputDirPath);
        if (!"false".equals(cmd.getOptionValue(Builder.RESOURCE))) {
            addToZip(new File(projectDirPath, Profile.DATA_DIR), zout, outputDirPath);
        }
        zout.close();
        Builder.addResult(Builder.ADD_RESOURCE, zipFile);
    }

    @Override
    protected void printEnd() throws Exception {
        LOGGER.log(Level.INFO, "Saved successfully");
    }

    @Override
    protected void buildGlobalParameters(JSONArray globals) throws Exception {
        super.buildGlobalParameters(globals);
        List<String> globalConfig = new ArrayList<>();
        globals.forEach((item) -> {
            JSONObject globalItem = (JSONObject) item;
            globalConfig.add("\t\tput(\"" + globalItem.getString("name") + "\", " + getValue(globalItem.get("value")) + ");");
        });
        tests = replace(tests, "<% GLOBALS %>", String.join("\n", globalConfig));
    }

    @Override
    protected void buildProperties(JSONObject properties) throws Exception {
        super.buildProperties(properties);
    }

    @Override
    protected void buildInterfaces(JSONArray interfaces) throws Exception {
        super.buildInterfaces(interfaces);
        List<String> resourceList = new ArrayList<>();
        interfaces.forEach((item) -> {
            JSONObject interfaceItem = (JSONObject) item;
            interfacesJson.element(interfaceItem.getString("id"), interfaceItem);
            String header = "\t\tput(\"" + interfaceItem.getString("name") + "\", new String[][] {\n"
                    + "\t\t\t<% PARAMETERS %>\n"
                    + "\t\t});";
            List<String> headerList = new ArrayList<>();
            if (interfaceItem.containsKey("headers")) {
                JSONObject headers = interfaceItem.getJSONObject("headers");
                headers.keySet().forEach((key) -> {
                    headerList.add("{\"" + key + "\", " + getValue(headers.get(key)) + "}");
                });
            }
            resourceList.add(header.replace("<% PARAMETERS %>", String.join(",\n\t\t\t", headerList)));
        });
        tests = replace(tests, "<% HEADERS %>", String.join("\n", resourceList));
    }

    @Override
    protected void buildResources(JSONArray resources) throws Exception {
        super.buildResources(resources);

        List<String> testLists = new ArrayList<>();
        for (Object item : resources) {
            JSONObject resource = (JSONObject) item;
            JSONObject testsuites = resource.getJSONObject("testsuites");
            JSONObject interfaceItem = (JSONObject) interfacesJson.getOrDefault(resource.getString("interfaceId"), null);
            for (Object name : testsuites.keySet()) {
                JSONObject testSuiteItem = testsuites.getJSONObject(name.toString());
                properties.setTestSuite(testSuiteItem.getJSONObject("properties"));

                String test = this.getFileContent(getTemplateFile("junit/Test.java"));
                String testSuiteName = stripName(name.toString());
                testCaseOrder.add(testSuiteName + ".class");
                test = replace(test, "<% TESTSUITE_CLASS_NAME %>", testSuiteName);
                test = replace(test, "<% ENDPOINT %>", resource.getString("endpoint"));
                test = replace(test, "<% INTERFACE %>", interfaceItem != null ? interfaceItem.getString("name") : "");

                List<String> testCaseList = new ArrayList<>();
                buildTestCases(resource.getString("resourceId"), testSuiteName, testCaseList, testSuiteItem.getJSONArray("testcases"), properties);
                test = replace(test, "<% TESTCASES %>", String.join("\n\n", testCaseList));
                testLists.add(test);
            }
        }
        tests = replace(tests, "<% TESTS %>", String.join("\n\n", testLists));
    }

    protected void buildTestCases(String resourceId, String testSuiteName, List<String> testCaseList, JSONArray testcases, Properties properties) throws Exception {
        for (Object item : testcases) {
            JSONObject testCaseItem = (JSONObject) item;
            if ("restrequest".equals(testCaseItem.get("type"))) {
                properties.setTestCase(testCaseItem.getJSONObject("properties"));

                String testcase = this.getFileContent(getTemplateFile("junit/TestCase.java"));
                testcase = replace(testcase, "<% SUMMARY %>", testCaseItem.getString("summary"));
                testcase = replace(testcase, "<% TESTCASE_CLASS_NAME %>", testCaseItem.getString("name"));

                testcase = replace(testcase, "<% ENDPOINT %>", testCaseItem.containsKey("endpoint") ? "GLOBALS.get(\"" + testCaseItem.get("endpoint") + "\")" : "endpoint");
                testcase = replace(testcase, "<% METHOD %>", testCaseItem.getString("method"));

                Object path = testCaseItem.get("path");
                if (path != null) {
                    path = properties.replace(path.toString()).replaceAll("&amp;", "&");
                    testcase = replace(testcase, "<% PATH %>", path);
                }

                Object body = Properties.getBody(projectDir, resourceId, testSuiteName, testCaseItem.getString("name"), true);
                if (!isNULL(body)) {
                    body = getValue(properties.replace(toJson(body)));
                }

                testcase = replace(testcase, "<% BODY %>", body);

                for (Object key : testCaseItem.keySet()) {
                    if (!"assertions".equals(key)) {
                        Object value = testCaseItem.get(key);
                        testcase = replace(testcase, "<% " + key.toString().toUpperCase() + " %>", value instanceof String ? value : toJson(value));
                    }
                }

                if (testCaseItem.containsKey("assertions")) {
                    List<String> assertionList = new ArrayList<>();
                    for (Object assertion : testCaseItem.getJSONArray("assertions")) {
                        JSONObject assertionItem = (JSONObject) assertion;
                        String type = assertionItem.getString("type");
                        String line = "";
                        JSONObject replace = assertionItem.getJSONObject("replace");

                        switch (type) {
                            case "HTTPHeaderEquals":
                                line = "assertEquals(response.header.get(\"<% NAME %>\"), " + getValue("<% VALUE %>") + ");";
                                break;
                            case "InvalidHTTPStatusCodes":
                                line = "assertTrue(response.statusCode != <% VALUE %>);";
                                break;
                            case "ValidHTTPStatusCodes":
                                line = "assertTrue(response.statusCode == <% VALUE %>);";
                                break;
                        }
                        for (Object key : replace.keySet()) {
                            line = replace(line, "<% " + key.toString().toUpperCase() + " %>", replace.get(key).toString());
                        }
                        assertionList.add(line);
                    }

                    testcase = replace(testcase, "<% ASSERTIONS %>", String.join(",\n", assertionList));
                }
                testCaseList.add(testcase);
            }
        }
    }
}
