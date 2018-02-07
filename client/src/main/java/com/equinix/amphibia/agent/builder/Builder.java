package com.equinix.amphibia.agent.builder;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
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

public class Builder {

    public static final String FORMAT = "format";
    public static final String INPUT = "input";
    public static final String RESOURCE = "resource";
    public static final String JSON = "json";

    public static final String ADD_PROJECT = "project";
    public static final String ADD_RESOURCE = "resource";
    public static final String ADD_WARNINGS = "warnings";
    public static final String ADD_NOTE = "note";
    public static final String ADD_ARGS = "args";

    private static Map<String, Object> results;

    @SuppressWarnings("ResultOfObjectAllocationIgnored")
    public static Map<String, Object> execute(String[] args) throws Exception {
        Options options = new Options();
        options.addOption(new Option("f", FORMAT, true, "Export format: POSTMAN, JUNIT, JSON, MOCHA, SOAPUI, READYAPI, SWAGGER. Default: POSTMAN"));
        options.addOption(new Option("r", RESOURCE, true, "Generate Resource files. Default: true"));
        options.addOption(new Option("j", JSON, true, "JSON output. Default: false"));

        Option input = new Option("i", INPUT, true, "JSON input project file");
        input.setRequired(true);
        options.addOption(input);

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

        String json = cmd.getOptionValue(JSON);
        if ("true".equals(json)) {
            results = new LinkedHashMap<>();
            results.put(Builder.ADD_PROJECT, null);
            results.put(Builder.ADD_WARNINGS, new ArrayList<>());
            Logger.getGlobal().setLevel(Level.SEVERE);
        }

        String inputFormat = cmd.getOptionValue(FORMAT);
        if ("SOAPUI".equalsIgnoreCase(inputFormat)) {
            new SoapUI(cmd);
        } else if ("READYAPI".equalsIgnoreCase(inputFormat)) {
            new ReadyAPI(cmd);
        } else if ("JUNIT".equalsIgnoreCase(inputFormat)) {
            new JUnit(cmd);
        } else if ("MOCHA".equalsIgnoreCase(inputFormat)) {
            new Mocha(cmd);
        } else if ("SWAGGER".equalsIgnoreCase(inputFormat)) {
            new Swagger(cmd);
        } else if ("JSON".equalsIgnoreCase(inputFormat)) {
            new JsonScript(cmd);
        } else {
            new Postman(cmd);
        }

        return results;
    }

    public static void addResult(String addType, File file) {
        addResult(addType, ProjectAbstract.getRelativePath(file.toURI()));
    }

    public static void addResult(String type, Object value) {
        if (results != null) {
            Object item = results.get(type);
            if (item == null) {
                results.put(type, value);
            } else {
                @SuppressWarnings("unchecked")
                List<String> children = (List<String>) item;
                children.add(value.toString());
                Collections.sort(children);
            }
        }
    }

    public static void printHelp(HelpFormatter formatter, Options options) {
        formatter.printHelp("Builder", options);
    }
}
