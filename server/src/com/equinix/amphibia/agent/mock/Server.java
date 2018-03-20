package com.equinix.amphibia.agent.mock;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import com.equinix.amphibia.agent.builder.ProjectAbstract;
import com.equinix.amphibia.agent.converter.Converter;
import com.equinix.amphibia.agent.converter.Profile;
import com.equinix.amphibia.agent.converter.Converter.RESOURCE_TYPE;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

public final class Server {

    public static final String BUILD = "build";
    public static final String INPUT = "input";
    public static final String PORT = "port";

    public static final int DEFAUL_PORT = 8090;

    private static final Logger LOGGER = Logger.getLogger(Server.class.getName());

    public static void execute(String[] args) throws Exception {
        //TODO call in UI Mock Server
        Options options = new Options();
        options.addOption(new Option("i", INPUT, true, "Amphibia Project file(s)"));
        options.addOption(new Option("p", PORT, true, "Server port number. Default: " + DEFAUL_PORT));

        CommandLine cmd = validateArguments(options, args);
        new Project(cmd, cmd.getOptionValue(INPUT));
    }

    public static void main(String[] args) throws Exception {
        Options options = new Options();
        options.addOption(new Option("b", BUILD, true, "JSON file for building Amphibia projects"));
        options.addOption(new Option("i", INPUT, true, "Amphibia Project file(s)"));
        options.addOption(new Option("p", PORT, true, "Server port number. Default: " + DEFAUL_PORT));

        CommandLine cmd = validateArguments(options, args);

        String projectPath = cmd.getOptionValue(INPUT);
        if (projectPath == null) {
            projectPath = "";
        }

        String buildFile = cmd.getOptionValue(BUILD);
        if (buildFile != null) {
            List<String> projects = new ArrayList<>();
            List<String> paths = new ArrayList<>();
            JSONArray json = JSONArray.fromObject(IOUtils.toString(new FileInputStream(new File(buildFile))));
            String[] files = new File(Profile.PROJECT_DIR).list();
            for (String file : files) {
                File dir = new File(Profile.PROJECT_DIR, file);
                if (dir.isDirectory()) {
                    FileUtils.deleteDirectory(dir);
                }
            }
            for (int i = 0; i < json.size(); i++) {
                try {
                    JSONObject item = json.getJSONObject(i);
                    paths.add(item.getString("path"));
                    List<String> params = new ArrayList<>(Arrays.asList("-i=" + item.getString("path"), "-j=true", "-d=true"));
                    if (!ProjectAbstract.isNULL(item.get("name"))) {
                        params.add("-n=" + item.get("name"));
                    }
                    if (!ProjectAbstract.isNULL(item.get("base"))) {
                        params.add("-f=" + item.get("base"));
                    }
                    Map<RESOURCE_TYPE, Object> results = Converter.execute(params.toArray(new String[params.size()]));
                    projects.add(results.get(RESOURCE_TYPE.project).toString());
                } catch (Exception  ex) {
                    LOGGER.log(Level.SEVERE, ex.toString(), ex);
                }
            }
            projectPath = String.join(",", projects);
            ProjectAbstract.saveFile(new File(Profile.PROJECT_DIR, "readme.txt"), String.join("\n", paths) + "\n\n" + "java -jar amphibia-server.jar -i=" + projectPath);
        }

        String[] projects = projectPath.split(",");
        Project project = new Project();
        for (String path : projects) {
            project.init(new File(path));
        }
        project.startServer(cmd.getOptionValue(Server.PORT));
    }

    private static CommandLine validateArguments(final Options options, String[] args) throws ParseException {
        CommandLineParser parser = new GnuParser();
        HelpFormatter formatter = new HelpFormatter();
        CommandLine cmd;

        try {
            cmd = parser.parse(options, args);
            for (Option o : cmd.getOptions()) {
                if (o.isRequired() && o.getValue().isEmpty()) {
                    throw new Error(o.getDescription() + " is empty");
                }
            }
        } catch (ParseException e) {
            formatter.printHelp("Builder", options);
            System.exit(1);
            throw e;
        }
        return cmd;
    }
}
