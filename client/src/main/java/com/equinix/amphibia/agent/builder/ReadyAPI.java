package com.equinix.amphibia.agent.builder;

import java.io.File;

import org.apache.commons.cli.CommandLine;

public class ReadyAPI extends SoapUI {

    public static final String UI_VERSION = "6.0.0";

    public ReadyAPI(CommandLine cmd) throws Exception {
        super(cmd);
    }

    @Override
    protected void buildProject(String name) throws Exception {
        super.buildProject(name);
        outputFile = new File(outputDirPath, name + "-ready-" + UI_VERSION + ".xml");
        xmlContent = replace(xmlContent, "<% VERSION %>", UI_VERSION);
        xmlContent = replace(xmlContent, "<% PROJECT_NAME %>", name);
    }

    @Override
    protected void resourceFiles(String pom) throws Exception {
        super.resourceFiles(getFileContent(getTemplateFile("soapui/pom_pro.xml")));
    }
}
