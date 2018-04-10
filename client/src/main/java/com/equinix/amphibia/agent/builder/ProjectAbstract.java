package com.equinix.amphibia.agent.builder;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
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
import java.awt.Color;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import net.sf.json.JSONArray;
import net.sf.json.JSONNull;
import net.sf.json.JSONObject;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.io.IOUtils;

@SuppressWarnings({"unchecked", "StaticNonFinalUsedInInitialization"})
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

    private static final File amphibiaHome;
    private static final ConsoleHandler consoleHandler;
    private static final FileHandler logFileHandler;

    private final ClassLoader classLoader = getClass().getClassLoader();

    public static int LOG_LIMIT = 1000000; // 1 Mb
    public static int NUM_LOGS = 5;
    public static int AUTO_FLUSH = 10000; //10 seconds

    public static Color GREEN = new Color(40, 130, 10);
    public static Color BLUE = Color.BLUE;
    public static Color RED = Color.RED;

    static {
        amphibiaHome = new File(System.getProperty("user.home"), "amphibia");
        amphibiaHome.mkdirs();

        consoleHandler = new ConsoleHandler();
        FileHandler fileHandler = null;
        try {
            fileHandler = new FileHandler(new File(amphibiaHome, "amphibia.log").getAbsolutePath(), LOG_LIMIT, NUM_LOGS, false);

            final Formatter formatter = new Formatter() {
                //final DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                //System.setProperty("java.util.logging.SimpleFormatter.format", "%1$tY-%1$tm-%1$td %1$tH:%1$tM:%1$tS [%4$-7s] (%2$s)     %5$s%6$s%n");

                @Override
                public String format(LogRecord record) {
                    return String.format(Locale.ROOT,
                            "%1$tY-%1$tm-%1$td %1$tH:%1$tM:%1$tS [%4$-7s] (%2$s)     %5$s%6$s%n",
                            record.getMillis(), record.getSourceClassName() + "::" + record.getSourceMethodName(), "",
                            record.getLevel(), formatMessage(record), getStackTrace(record.getThrown()));
                }

                private String getStackTrace(Throwable thrown) {
                    if (thrown == null) {
                        return "";
                    }
                    StringWriter sw = new StringWriter();
                    try (PrintWriter printWriter = new PrintWriter(sw)) {
                        thrown.printStackTrace(printWriter);
                    }
                    return sw.toString();
                }
            };
            fileHandler.setFormatter(formatter);
            consoleHandler.setFormatter(formatter);
        } catch (IOException | SecurityException ex) {
        }
        logFileHandler = fileHandler;
        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                logFileHandler.flush();
            }
        }, AUTO_FLUSH);
    }

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

    public static File getAmphibiaHome() {
        return amphibiaHome;
    }

    public static Logger getLogger(String className) {
        return getLogger(Logger.getLogger(className));
    }

    public static Logger getLogger(Logger logger) {
        logger.setUseParentHandlers(false);
        logger.addHandler(logFileHandler);
        logger.addHandler(consoleHandler);
        return logger;
    }

    public static String getRelativePath(String projectDir, URI file) {
        return getRelativePath(new File(projectDir).toURI(), file);
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
        } else if (value instanceof JSONNull) {
            return true;
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

    public static void saveFile(File outputFile, String content) throws Exception {
        try (PrintWriter writer = new PrintWriter(new FileOutputStream(outputFile, false))) {
            writer.println(content);
        }
    }

    protected String tabs(String source, String tabs) {
        return source.replaceAll("^", tabs).replaceAll("\n", "\n" + tabs);
    }

    protected String toJson(Object value) throws Exception {
        return JSONObject.fromObject(value).toString().replaceAll("\\\"", "\\\\\"");
    }

    public static String prettyJson(Object value) throws Exception {
        return prettyJson(JSONObject.fromObject(value).toString());
    }

    public static String prettyJson(String value) throws Exception {
        return prettyJson(value, "\t", 4);
    }

    public static String prettyJson(String value, String tabs, int spaces) throws Exception {
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

    public static JSONObject getJSON(String path) throws IOException {
        return getJSON(new File(path));
    }

    public static JSONObject getJSON(File file) throws IOException {
        return getJSON(file.toURI().toURL().openStream());
    }

    public static JSONObject getJSON(InputStream is) throws IOException {
        String json = getFileContent(is);
        return JSONObject.fromObject(json);
    }

    public static String getFileContent(String file) throws IOException {
        return getFileContent(new File(file).toURI());
    }

    public static String getFileContent(URI uri) throws IOException {
        return getFileContent(uri.toURL().openStream());
    }

    public static String getFileContent(InputStream is) throws IOException {
        String str = IOUtils.toString(is);
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
