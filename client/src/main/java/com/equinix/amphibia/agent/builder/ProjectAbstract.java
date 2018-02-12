package com.equinix.amphibia.agent.builder;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;


import org.apache.commons.cli.CommandLine;

import com.equinix.amphibia.agent.converter.Profile;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.io.IOUtils;

@SuppressWarnings("unchecked")
public abstract class ProjectAbstract {

    protected CommandLine cmd;
    protected JSONObject inputJsonProject;

    protected Properties properties;
    protected File projectDir;
    protected String inputFilePath;
    protected String projectDirPath;
    protected String outputDirPath;
    protected String projectName;

    protected JSONObject globalsJson;
    protected JSONObject projectPropertiesJSON;
    protected JSONObject interfacesJson;

    private final ClassLoader classLoader = getClass().getClassLoader();
    
    @SuppressWarnings("OverridableMethodCallInConstructor")
    public ProjectAbstract(CommandLine cmd) throws Exception {
        this.cmd = cmd;
        inputFilePath = cmd.getOptionValue(Builder.INPUT);
        projectDir = new File(inputFilePath).getParentFile();
        projectDirPath = projectDir.getAbsolutePath();
        outputDirPath = new File(projectDirPath, getClass().getSimpleName().toLowerCase()).getAbsolutePath();

        File outputDir = new File(outputDirPath);
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }

        init();
    }

    public static String getRelativePath(URI file) {
        return getRelativePath(new File(Profile.PROJECT_DIR).toURI(), file);
    }

    public static String getRelativePath(URI base, URI file) {
        return base.relativize(file).getPath();
    }

    protected String stripName(String value) {
        return com.equinix.amphibia.agent.converter.Swagger.stripName(value);
    }

    public static boolean isNULL(Object value) {
        if (value == null) {
            return true;
        } else if (value instanceof JSONObject) {
            return ((JSONObject) value).isNullObject();
        }
        return false;
    }

    protected void init() throws Exception {
        readInputData();
        parseInputProjectFile();
        saveFile();
        if (!"false".equals(cmd.getOptionValue(Builder.RESOURCE))) {
            saveResources();
        }
        printEnd();
    }

    protected String tabs(String source, String tabs) {
        return source.replaceAll("^", tabs).replaceAll("\n", "\n" + tabs);
    }

    protected String toJson(Object value) throws Exception {
        return JSONObject.fromObject(value).toString().replaceAll("\\\"", "\\\\\"");
    }

    protected String prettyJson(Object value) throws Exception {
        return prettyJson(JSONObject.fromObject(value).toString());
    }

    protected String prettyJson(String value) throws Exception {
        return prettyJson(value, "\t", 4);
    }

    public String prettyJson(String value, String tabs, int spaces) throws Exception {
        ScriptEngine scriptEngine = new ScriptEngineManager().getEngineByName("JavaScript");
        scriptEngine.put("jsonString", value);
        scriptEngine.eval("result = JSON.stringify(JSON.parse(jsonString), null, " + spaces + ")");
        String json = ((String) scriptEngine.get("result")).replaceAll(" {" + spaces + "}", tabs);
        return json == null || "null".equals(json) ? "" : json;
    }

    public String typeof(Object value) throws Exception {
        ScriptEngine scriptEngine = new ScriptEngineManager().getEngineByName("JavaScript");
        scriptEngine.put("value", value);
        scriptEngine.eval("result = typeof(value)");
        return ((String) scriptEngine.get("result"));
    }

    protected JSONObject getJSON(String path) throws IOException {
        return getJSON(new File(path));
    }

    protected JSONObject getJSON(File file) throws IOException {
        String json = getFileContent(file.toURI());
        return JSONObject.fromObject(json);
    }

    protected String getFileContent(String file) throws IOException {
        return getFileContent(new File(file).toURI());
    }

    protected String getFileContent(URI uri) throws IOException {
        String str = IOUtils.toString(uri.toURL().openStream());
        return str;
    }

    protected URI getTemplateFile(String path) throws Exception {
        return getFile(path, "../resources/templates", "resources/templates/");
    }

    protected Object getValue(Object value) {
        return getValue(value, "\"");
    }

    protected Object getValue(Object value, String quotes) {
        if (value instanceof String) {
            return quotes + value + quotes;
        } else {
            return value;
        }
    }

    protected URI getFile(String path, String fileDir, String resourceDir) throws Exception {
        URL url = classLoader.getResource(resourceDir + path);
        if (url != null) {
            return url.toURI();
        } else {
            File file = new File(fileDir, path);
            if (!file.exists()) {
                throw new FileNotFoundException("File path: " + file.getAbsolutePath());
            }
            return file.toURI();
        }
    }

    protected void addToZip(final File file, final ZipOutputStream zs, final String basePath) throws Exception {
        addToZip(file.toURI(), zs, basePath);
    }

    protected void addToZip(final URI uri, final ZipOutputStream zs, final String basePath) throws Exception {
        Path pp = Paths.get(uri);
        Files.walk(pp)
                .filter(path -> !Files.isDirectory(path))
                .forEach((Path path) -> {
                    String relative = ProjectAbstract.getRelativePath(new File(basePath).toURI(), path.toUri());
                    ZipEntry zipEntry = new ZipEntry(relative);
                    try {
                        zs.putNextEntry(zipEntry);
                        zs.write(Files.readAllBytes(path));
                        zs.closeEntry();
                    } catch (IOException e) {
                    }
                });
    }

    protected String replace(String source, Object target, Object replacement) {
        return source.replace(String.valueOf(target), String.valueOf(replacement));
    }

    protected void readInputData() throws Exception {
        File inputFile = new File(inputFilePath);
        if (!inputFile.exists()) {
            throw new FileNotFoundException("The JSON input project file not found: " + inputFile.getAbsolutePath());
        }
        inputJsonProject = getJSON(inputFile);
    }

    protected void parseInputProjectFile() throws Exception {
        buildProject(inputJsonProject.getString("name"));
        buildGlobalParameters(inputJsonProject.getJSONArray("globals"));
        buildProperties(inputJsonProject.getJSONObject("projectProperties"));
        buildInterfaces(inputJsonProject.getJSONArray("interfaces"));
        buildResources(inputJsonProject.getJSONArray("projectResources"));
    }

    protected void buildProject(String name) throws Exception {
        projectName = name;
    }

    protected void buildGlobalParameters(JSONArray globals) throws Exception {
        globalsJson = new JSONObject();
        globals.forEach((item) -> {
            JSONObject globalItem = (JSONObject) item;
            globalsJson.element(globalItem.getString("name"), globalItem.get("value"));
        });
    }

    protected void buildProperties(JSONObject properties) throws Exception {
        projectPropertiesJSON = properties;
        this.properties = new Properties(globalsJson, properties);
    }

    protected void buildInterfaces(JSONArray interfaces) throws Exception {
        interfacesJson = new JSONObject();
    }

    protected void buildResources(JSONArray resources) throws Exception {
    }

    protected void saveFile() throws Exception {
    }

    protected void saveResources() throws Exception {
    }

    protected void printEnd() throws Exception {
    }
}
