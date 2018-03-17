package com.equinix.amphibia.agent.builder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipOutputStream;

import org.apache.commons.cli.CommandLine;

import com.equinix.amphibia.agent.converter.Profile;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

@SuppressWarnings("unchecked")
public class SoapUI extends ProjectAbstract {

    public static final String VERSION = "5.3.0";

    protected String xmlContent;
    protected File outputFile;
    protected File zipFile;
    protected File projectFile;
    protected File classpathFile;
    protected File pomFile;

    private static final Logger LOGGER = ProjectAbstract.getLogger(SoapUI.class.getName());

    public SoapUI(CommandLine cmd) throws Exception {
        super(cmd);
    }

    @Override
    protected void readInputData() throws Exception {
        super.readInputData();
        xmlContent = getFileContent(getTemplateFile("soapui/soapui.xml"));
    }

    @Override
    protected void buildProject(String name) throws Exception {
        super.buildProject(name);
        outputFile = new File(outputDirPath, name + "-soap-" + VERSION + ".xml");
        xmlContent = replace(xmlContent, "<% VERSION %>", VERSION);
        xmlContent = replace(xmlContent, "<% PROJECT_NAME %>", name);
        File dir = new File(outputDirPath, "src/scripts");
        if (!dir.exists()) {
            dir.mkdirs(); //WARN  [SoapUIProGroovyScriptEngineFactory] Missing scripts folder [soapui\projects\src]
        }
        pomFile = new File(outputDirPath, "pom.xml");
        zipFile = new File(outputDirPath, name + ".zip");
        projectFile = new File(outputDirPath, ".project");
        classpathFile = new File(outputDirPath, ".classpath");

        Builder.addResult(Builder.ADD_PROJECT, outputFile);
    }

    protected void resourceFiles(String pom) throws Exception {
        String separator = "\n\t\t\t\t\t\t\t\t";
        String name = inputJsonProject.get("name").toString();
        pom = pom.replaceAll("<% PROJECT_NAME %>", name);
        for (String arg : cmd.getArgs()) {
            String[] pair = arg.split("=");
            if ("groupId".equals(pair[0])) {
                pom = pom.replaceAll("<% GROUP_ID %>", pair[1]);
            }
        }
        pom = pom.replaceAll("<% GROUP_ID %>", "localhost");

        List<String> globalProperties = new ArrayList<>();
        globalsJson.keySet().forEach((key) -> {
            globalProperties.add("<globalProperty>" + key + "=" + globalsJson.get(key) + "</globalProperty>");
        });
        pom = pom.replaceAll("<% GLOBAL_PROPERTIES %>", String.join(separator, globalProperties));

        List<String> projectProperties = new ArrayList<>();
        projectPropertiesJSON.keySet().forEach((key) -> {
            projectProperties.add("<projectProperty>" + key + "=" + projectPropertiesJSON.get(key) + "</projectProperty>");
        });
        pom = pom.replaceAll("<% PROJECT_PROPERTIES %>", String.join(separator, projectProperties));

        PrintWriter writer = new PrintWriter(new FileOutputStream(pomFile, false));
        writer.println(pom);
        writer.close();
        writer = new PrintWriter(new FileOutputStream(projectFile, false));
        writer.println(getFileContent(getTemplateFile("soapui/project")).replaceAll("<% NAME %>", name));
        writer.close();

        writer = new PrintWriter(new FileOutputStream(classpathFile, false));
        writer.println(getFileContent(getTemplateFile("soapui/classpath")));
        writer.close();
    }

    @Override
    protected void saveFile() throws Exception {
        super.saveFile();
        PrintWriter writer = new PrintWriter(new FileOutputStream(outputFile, false));
        writer.println(xmlContent);
        writer.close();
    }

    @Override
    protected void saveResources() throws Exception {
        ZipOutputStream zout = new ZipOutputStream(new FileOutputStream(zipFile));
        if (!"false".equals(cmd.getOptionValue(Builder.RESOURCE))) {
            addToZip(new File(projectDirPath, Profile.DATA_DIR), zout, outputDirPath);
        }
        addToZip(outputFile, zout, outputDirPath);
        addToZip(pomFile, zout, outputDirPath);
        addToZip(projectFile, zout, outputDirPath);
        addToZip(classpathFile, zout, outputDirPath);

        zout.close();
        Builder.addResult(Builder.ADD_RESOURCE, zipFile);
    }

    @Override
    protected void printEnd() {
        String note = "1) Open IDE and import a project file \"{0}\".\n"
                + "2) To add or update Global Properties, go to File -> Preferences -> Global Properties\n";
        List<String> args = new ArrayList<>();
        args.add(outputFile.getName());

        if (globalsJson != null && globalsJson.size() > 0) {
            note += "\nGlobal Properties:\n";
            for (Object key : globalsJson.keySet()) {
                note += "\t\t{" + args.size() + "}\n";
                args.add(key + "=" + globalsJson.get(key));
            }
        }
        Builder.addResult(Builder.ADD_NOTE, note);
        Builder.addResult(Builder.ADD_ARGS, args);
        LOGGER.log(Level.INFO, "Saved successfully.\n\nNOTE:\n\n");
        LOGGER.log(Level.INFO, MessageFormat.format(note, args.toArray()));
    }

    @Override
    protected void parseInputProjectFile() throws Exception {
        super.parseInputProjectFile();
        if (!"false".equals(cmd.getOptionValue(Builder.RESOURCE))) {
            resourceFiles(getFileContent(getTemplateFile("soapui/pom.xml")));
        }
    }

    @Override
    protected void buildGlobalParameters(JSONArray globals) throws Exception {
        super.buildGlobalParameters(globals);
    }

    @Override
    protected void buildProperties(JSONObject properties) throws Exception {
        super.buildProperties(properties);
        List<String> propertyList = new ArrayList<>();
        for (Object key : properties.keySet()) {
            String propertyXML = getFileContent(getTemplateFile("soapui/property.xml"));
            propertyXML = replace(propertyXML, "<% NAME %>", key);
            propertyXML = replace(propertyXML, "<% VALUE %>", properties.get(key));
            propertyList.add(tabs(propertyXML, "\t\t"));
        }
        xmlContent = replace(xmlContent, "<% PROPERTIES %>", addProperties(propertyList, "\t"));
    }

    @Override
    protected void buildInterfaces(JSONArray interfaces) throws Exception {
        super.buildInterfaces(interfaces);
        List<String> interfaceList = new ArrayList<>();
        for (int index = 0; index < interfaces.size(); index++) {
            JSONObject interfaceItem = interfaces.getJSONObject(index);
            interfacesJson.put(index, interfaceItem);
            List<String> resourceList = new ArrayList<>();
            String interfaceXML = getFileContent(getTemplateFile("soapui/interface.xml"));
            xmlContent = replace(xmlContent, "<% INTERFACE_NAME %>", interfaceItem.get("basePath"));
            interfaceXML = replace(interfaceXML, "<% INTERFACE_NAME %>", interfaceItem.get("basePath"));
            interfaceXML = replace(interfaceXML, "<% INTERFACE_TYPE %>", interfaceItem.get("type"));
            interfaceXML = replace(interfaceXML, "<% ENDPOINT %>", "${#Global#RestEndPoint" + index + "}");

            JSONObject headers = interfaceItem.getJSONObject("headers");
            String resourcesXML = getFileContent(getTemplateFile("soapui/resource.xml"));
            List<String> parameterList = new ArrayList<>();
            for (Object key : headers.keySet()) {
                String parameterXML = getFileContent(getTemplateFile("soapui/resource_parameters.xml"));
                parameterXML = replace(parameterXML, "<% STYLE %>", "HEADER");
                parameterXML = replace(parameterXML, "<% NAME %>", key);
                parameterXML = replace(parameterXML, "<% VALUE %>", headers.get(key));
                parameterList.add(parameterXML);
            }
            resourcesXML = replace(resourcesXML, "<% PARAMETERS %>", String.join("\n", parameterList));
            resourceList.add(resourcesXML);

            interfaceXML = replace(interfaceXML, "<% RESOURCES %>", String.join("\n", resourceList));
            interfaceList.add(interfaceXML);
        }
        xmlContent = replace(xmlContent, "<% INTERFACES %>", String.join("\n", interfaceList));
    }

    @Override
    protected void buildResources(JSONArray resources) throws Exception {
        super.buildResources(resources);

        List<String> testSuiteList = new ArrayList<>();
        for (int index = 0; index < resources.size(); index++) {
            JSONObject resource = resources.getJSONObject(index);
            JSONObject headers = interfacesJson.getJSONObject(String.valueOf(index));
            JSONObject testsuites = resource.getJSONObject("testsuites");
            for (Object name : testsuites.keySet()) {
                JSONObject testSuiteItem = testsuites.getJSONObject(name.toString());

                List<String> testCaseList = new ArrayList<>();
                String testSuiteXML = getFileContent(getTemplateFile("soapui/testSuite.xml"));
                testSuiteXML = replace(testSuiteXML, "<% TESTSUITE_NAME %>", name.toString());
                buildTestCases(name.toString(), testCaseList, resource, headers, testSuiteItem);
                testSuiteXML = replace(testSuiteXML, "<% TESTCASES %>", String.join("\n", testCaseList));
                JSONObject props = testSuiteItem.getJSONObject("properties");
                List<String> propertyList = new ArrayList<>();
                for (Object key : props.keySet()) {
                    String propertyXML = getFileContent(getTemplateFile("soapui/property.xml"));
                    propertyXML = replace(propertyXML, "<% NAME %>", key);
                    propertyXML = replace(propertyXML, "<% VALUE %>", props.get(key));
                    propertyList.add(tabs(propertyXML, "\t\t\t"));
                }
                testSuiteXML = replace(testSuiteXML, "<% PROPERTIES %>", addProperties(propertyList, "\t\t"));
                testSuiteList.add(testSuiteXML);
            }
        }
        xmlContent = replace(xmlContent, "<% TESTSUITES %>", String.join("\n", testSuiteList));
    }

    protected void buildTestCases(String testSuiteName, List<String> testCaseList, JSONObject resourceItem, JSONObject headers, JSONObject testSuiteItem) throws Exception {
        JSONArray testcases = testSuiteItem.getJSONArray("testcases");
        for (Object item : testcases) {
            JSONObject testCaseItem = (JSONObject) item;
            List<String> testStepList = new ArrayList<>();
            String testCaseXML = getFileContent(getTemplateFile("soapui/testCase.xml"));
            testCaseXML = replace(testCaseXML, "<% TESTCASE_NAME %>", testCaseItem.get("name"));
            buildTestSteps(testSuiteName, testStepList, resourceItem, headers, testCaseItem);
            testCaseXML = replace(testCaseXML, "<% TEST_STEPS %>", String.join("\n", testStepList));
            JSONObject props = testCaseItem.getJSONObject("properties");
            List<String> propertyList = new ArrayList<>();
            for (Object key : props.keySet()) {
                String propertyXML = getFileContent(getTemplateFile("soapui/property.xml"));
                propertyXML = replace(propertyXML, "<% NAME %>", key);
                propertyXML = replace(propertyXML, "<% VALUE %>", props.get(key));
                propertyList.add(tabs(propertyXML, "\t\t\t\t"));
            }
            testCaseXML = replace(testCaseXML, "<% PROPERTIES %>", addProperties(propertyList, "\t\t\t"));
            testCaseList.add(testCaseXML);
        }
    }

    protected void buildTestSteps(String testSuiteName, List<String> testStepList, JSONObject resourceItem, JSONObject headers, JSONObject testCaseItem) throws Exception {
        String type = (String) testCaseItem.get("type");
        String testStepXML = getFileContent(getTemplateFile("soapui/teststeps/" + type + ".xml"));

        testStepXML = replace(testStepXML, "<% TESTSTEP_NAME %>", testCaseItem.containsKey("operationId") ? testCaseItem.get("operationId") : testCaseItem.get("name"));

        for (Object key : testCaseItem.keySet()) {
            if (!"assertions".equals(key)) {
                Object value = testCaseItem.get(key);
                testStepXML = replace(testStepXML, "<% " + key.toString().toUpperCase() + " %>", value instanceof String ? value : prettyJson(value));
            }
        }
        testStepXML = replace(testStepXML, "<% ENDPOINT %>", "${#Global#" + (testCaseItem.containsKey("endpoint") ? testCaseItem.get("endpoint") : resourceItem.get("endpoint")) + "}");
        testStepXML = replace(testStepXML, "<% INTERFACE %>", headers.getString("basePath"));
        testStepXML = replace(testStepXML, "<% MEDIATYPE %>", "application/json");

        JSONArray assertions = testCaseItem.getJSONArray("assertions");
        List<String> assertionList = new ArrayList<>();
        for (Object assertion : assertions) {
            JSONObject assertionItem = (JSONObject) assertion;
            String assertionXML = getFileContent(getTemplateFile("soapui/assertions/" + assertionItem.get("type") + ".xml"));
            JSONObject replace = assertionItem.getJSONObject("replace");
            for (Object key : replace.keySet()) {
                assertionXML = replace(assertionXML, "<% " + key.toString().toUpperCase() + " %>", replace.get(key));
            }
            assertionList.add(assertionXML);
        }
        Object body = Properties.getBody(projectDir, resourceItem.getString("resourceId"), testSuiteName, testCaseItem.getString("name"), true);
        testStepXML = replace(testStepXML, "<% BODY %>", body != null ? body : "");
        testStepXML = replace(testStepXML, "<% ASSERTIONS %>", String.join("\n", assertionList));

        testStepList.add(testStepXML);
    }

    private String addProperties(List<String> propertyList, String tabs) {
        if (propertyList.isEmpty()) {
            return tabs + "<con:properties/>";
        } else {
            return tabs + "<con:properties>\n" + String.join("\n", propertyList) + "\n" + tabs + "</con:properties>";
        }
    }
}
