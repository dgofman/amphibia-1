package com.equinix.amphibia.agent.converter;

import com.equinix.amphibia.agent.builder.ProjectAbstract;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import net.sf.json.JSONArray;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import net.sf.json.JSONObject;

public class Converter {

    public static final String NAME = "name";
    public static final String PATH = "path";
    public static final String INPUT = "input";
    public static final String MERGE = "merge";
    public static final String PROPERTIES = "properties";
    public static final String INTERFACES = "interfaces";
    public static final String SCHEMA = "schema";
    public static final String TESTS = "tests";
    public static final String JSON = "json";
    public static final String NULL = "null";
    public static final String ADD_NULL = "null+";
    public static final String TYPE = "type";
    public static final String ADD_TYPE = "type+";
    public static final String NULL_VALIDATION = "null-";
    public static final String TYPE_VALIDATION = "type-";
    public static final String DEFAULT = "default";
    public static final String CODES = "codes";

    public static CommandLine cmd;

    private static Map<RESOURCE_TYPE, Object> results;

    private static final Logger LOGGER = ProjectAbstract.getLogger(Converter.class.getName());

    public static enum RESOURCE_TYPE {
        project,
        profile,
        schemas,
        tests,
        requests,
        responses,
        asserts,
        warnings,
        errors
    };

    public static final Object ENDPOINT = 0;
    public static final Object VARIABLE = 1;

    public static Map<RESOURCE_TYPE, Object> execute(String[] args) throws Exception {
        Options options = new Options();
        options.addOption(new Option("n", NAME, true, "Project name (Optional)"));
        options.addOption(new Option("a", PATH, true, "Absolute path (Optional)"));
        options.addOption(new Option("m", MERGE, true, "Merge properties into project file. Default: false"));
        options.addOption(new Option("p", PROPERTIES, true, "Comma-separated list of property file(s) (Optional)"));
        options.addOption(new Option("f", INTERFACES, true, "Comma-separated list of interface name(s) (Optional)"));
        options.addOption(new Option("s", SCHEMA, true, "Generate JSON schemas. Default: true"));
        options.addOption(new Option("t", TESTS, true, "Generate JSON tests. Default: true"));
        options.addOption(new Option("j", JSON, true, "JSON output. Default: false"));
        options.addOption(new Option("u", NULL, true, "Generate NULL TestSteps. Default: false"));
        options.addOption(new Option("uu", ADD_NULL, true, "Include NULL TestSteps. Default: false"));
        options.addOption(new Option("y", TYPE, true, "Generate invalid type TestSteps. Default: false"));
        options.addOption(new Option("yy", ADD_TYPE, true, "Include invalid type TestSteps. Default: false"));
        options.addOption(new Option("vn", NULL_VALIDATION, true, "Validate parameter value on NULL. Default: false"));
        options.addOption(new Option("vt", TYPE_VALIDATION, true, "Validate parameter value BOOLEAN TYPE. Default: false"));
        options.addOption(new Option("d", DEFAULT, true, "Validate that default values have been assigned (Optional)"));
        options.addOption(new Option("c", CODES, true, "Comma-separated list of HTTP response codes. Default (200,201)"));

        Option input = new Option("i", INPUT, true, "Comma-separated list of Swagger file(s) or URL(s)");
        input.setRequired(true);
        options.addOption(input);

        CommandLineParser parser = new GnuParser();
        HelpFormatter formatter = new HelpFormatter();

        try {
            cmd = parser.parse(options, args);
            for (Option o : cmd.getOptions()) {
                if (o.isRequired() && o.getValue().isEmpty()) {
                    throw new Error(o.getDescription() + " is empty");
                }
            }
        } catch (ParseException e) {
            printHelp(formatter, options);
            System.exit(1);
            throw e;
        }

        String json = cmd.getOptionValue(JSON);
        if ("true".equals(json)) {
            results = new LinkedHashMap<>();
            results.put(RESOURCE_TYPE.project, null);
            results.put(RESOURCE_TYPE.profile, null);
            results.put(RESOURCE_TYPE.tests, new ArrayList<>());
            results.put(RESOURCE_TYPE.requests, new ArrayList<>());
            results.put(RESOURCE_TYPE.responses, new ArrayList<>());
            results.put(RESOURCE_TYPE.schemas, new ArrayList<>());
            results.put(RESOURCE_TYPE.warnings, new ArrayList<>());
            results.put(RESOURCE_TYPE.asserts, new ArrayList<>());
            results.put(RESOURCE_TYPE.errors, new ArrayList<>());
            Logger.getGlobal().setLevel(Level.SEVERE);
        }

        String name = cmd.getOptionValue(Converter.NAME);
        File projectFile = null;
        String[] inputParams = cmd.getOptionValue(INPUT).split(",");
        String projectPath = cmd.getOptionValue(Converter.PATH);
        String projectDir;
        if (projectPath != null) {
            projectFile = new File(projectPath);
            projectDir = projectFile.getParentFile().getAbsolutePath();
        } else {
            projectDir = new File(Profile.PROJECT_DIR, new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss").format(new Date())).getAbsolutePath();
        }

        File outputDir = new File(new File(projectDir).getAbsolutePath());
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }

        try {
            FileUtils.deleteDirectory(new File(projectDir, Profile.DATA_DIR));
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, ex.toString(), ex);
        }
        Profile profile = new Profile(projectDir);
        JSONObject output = new JSONObject();
        Map<String, JSONObject> rulesAndPrperties = new LinkedHashMap<>();
        for (int i = 0; i < inputParams.length; i++) {
            InputStream is;
            String inputParam = inputParams[i];
            boolean isURL = inputParam.startsWith("http");
            if (isURL) {
                is = new URL(inputParam).openStream();
            } else {
                is = new FileInputStream(inputParam);
            }

            String propertiesFile = null;
            String resourceId = null;
            String param = cmd.getOptionValue(Converter.PROPERTIES);
            if (param != null) {
                String[] properties = param.split(",");
                File file;
                if (i < properties.length && (file = new File(properties[i])).exists()) {
                    propertiesFile = properties[i];
                    if (!rulesAndPrperties.containsKey(propertiesFile)) {
                        rulesAndPrperties.put(propertiesFile, getRulesAndProperties(projectDir, file));
                    }
                    JSONArray resources = rulesAndPrperties.get(propertiesFile).getJSONObject("info").getJSONArray("resources");
                    for (Object item : resources) {
                        JSONObject resource = (JSONObject) item;
                        if (resource.containsKey("source")) {
                            if ((isURL && inputParam.equals(resource.getString("source")))
                                    || inputParam.contains(resource.getString("source"))) {
                                resourceId = resource.getString("id");
                                break;
                            }
                        }
                    }
                }
            }
            Swagger swagger = new Swagger(cmd, projectDir, resourceId, rulesAndPrperties.get(propertiesFile), is, output, profile);
            profile.setSwagger(swagger, swagger.getRulesAndPrperties());
            name = swagger.init(name, i, inputParam, isURL, propertiesFile);
            IOUtils.closeQuietly(is);
        }

        profile.finalize(name);

        if (projectFile == null) {
            projectFile = new File(projectDir, name + ".json");
        }
        profile.saveFile(output, projectFile);
        profile.saveFile(new File(projectDir, name + ".txt"),
            cmd.getOptionValue(INPUT) +
            "\n\nERRORS:\n" + JSONArray.fromObject(results.get(RESOURCE_TYPE.errors)).toString(4) + 
            "\n\nWARNINGS:\n" + JSONArray.fromObject(results.get(RESOURCE_TYPE.warnings)).toString(4));
        return results;
    }

    private static JSONObject getRulesAndProperties(String projectDir, File file) throws IOException {
        if (file.getName().endsWith(".json")) {
            return ProjectAbstract.getJSON(file);
        } else if (file.getName().endsWith(".zip")) {
            ZipFile zipFile = new ZipFile(file);
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            JSONObject json = null;
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.getName().equals("rules-properties.json")) {
                    json = ProjectAbstract.getJSON(zipFile.getInputStream(entry));
                } else {
                    InputStream stream = zipFile.getInputStream(entry);
                    file = new File(projectDir, entry.getName());
                    file.getParentFile().mkdirs();
                    Files.copy(stream, file.toPath());
                    stream.close();
                }
            }
            zipFile.close();
            return json;
        } else {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    public static void addResult(RESOURCE_TYPE type, Object value) {
        if (results != null) {
            Object item = results.get(type);
            if (item == null) {
                results.put(type, value);
            } else {
                List<String> children = (List<String>) item;
                children.add(value.toString());
                Collections.sort(children);
            }
        }
    }

    public static void printHelp(HelpFormatter formatter, Options options) {
        formatter.printHelp("Converter", options);
    }
}
