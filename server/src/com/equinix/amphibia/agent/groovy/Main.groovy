package com.equinix.amphibia.agent.groovy;

import org.apache.log4j.Logger;

import com.eviware.soapui.impl.wsdl.WsdlProject;
import com.eviware.soapui.model.testsuite.TestRunner;

@groovy.transform.TypeChecked
public class Main {
	
	private Logger log;
	private String projectPath;
	private TestRunner runner;
	private WsdlProject project;

	private static Main instance = null;
	
	private Main() {
	}

	public static Main getInstance() {
		if (instance == null) {
			instance = new Main();
		}
		return instance;
	}

	public void init(TestRunner runner, WsdlProject project, Logger log, String projectPath) throws Exception {
		this.projectPath = projectPath;
		this.project = project;
		this.log = log;

		log.info("************************************** ${this.project.getName()} PROJECT **************************************");
		log.info("projectPath: " + projectPath);

		System.setProperty("PROJECT_NAME", project.getName());

		try {
			new Runner();
		} catch (Exception e) {
			log.error(e, e);
			throw e;
		}
	}
	
	public Logger getLog() {
		return log;
	}
	
	public String getProjectPath() {
		return projectPath;
	}
	
	public TestRunner getRunner() {
		return runner;
	}

	public WsdlProject getProject() {
		return project;
	}
}