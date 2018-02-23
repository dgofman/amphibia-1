package com.equinix.amphibia.agent.runner;

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

public class Runner {
	
	public static final String PROJECT = "project";
	
	private static Map<String, Object> results;

	public static Map<String, Object> execute(String[] args) throws Exception {
        Options options = new Options();
        Option input = new Option("a", PROJECT, true, "Amphibia Project file");
        input.setRequired(true);
        options.addOption(input);

        CommandLine cmd = validateArguments(options, args);

        results = new LinkedHashMap<>();
        //new Project(cmd, cmd.getOptionValue(PROJECT));
        return results;
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
	
	public static void run() {
		
	}
}
