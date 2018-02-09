package com.equinix.amphibia.agent.mock;

import java.io.File;
import java.util.Map;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import com.equinix.amphibia.agent.converter.Converter;
import com.equinix.amphibia.agent.converter.Converter.RESOURCE_TYPE;

public final class Server {

    public static final String INPUT = "input";
    public static final String PROJECT = "project";
    public static final String PORT = "port";

    public static final int DEFAUL_PORT = 8090;

    public static void execute(String[] args) throws Exception {
        Options options = new Options();
        options.addOption(new Option("p", PROJECT, true, "Server port number. Default: " + DEFAUL_PORT));

        Option input = new Option("a", PROJECT, true, "Amphibia Project file");
        input.setRequired(true);
        options.addOption(input);

        CommandLine cmd = validateArguments(options, args);
        new Project(cmd, cmd.getOptionValue(PROJECT));
    }

    public static void main(String[] args) throws Exception {
        Options options = new Options();
        options.addOption(new Option("a", PROJECT, true, "Amphibia Project file"));
        options.addOption(new Option("p", PORT, true, "Server port number. Default: " + DEFAUL_PORT));
        Option input = new Option("i", INPUT, true, "Comma-separated list of Swagger file(s) or URL(s)");
        input.setRequired(true);
        options.addOption(input);

        CommandLine cmd = validateArguments(options, args);

        String projectPath = cmd.getOptionValue(PROJECT);
        if (projectPath == null || !new File(projectPath).exists()) {
            String[] params = new String[] { "-i=" + cmd.getOptionValue(INPUT), "-j=true", "-d=true" };
            Map<RESOURCE_TYPE, Object> results = Converter.execute(params);
            projectPath = results.get(RESOURCE_TYPE.project).toString();
        }
        new Project(cmd, projectPath);
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
            printHelp(formatter, options);
            System.exit(1);
            throw e;
        }
        return cmd;
    }

    public static void printHelp(HelpFormatter formatter, Options options) {
        formatter.printHelp("Builder", options);
    }
}
