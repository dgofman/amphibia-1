package com.equinix.amphibia.agent.groovy;

import org.apache.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.regex.Matcher;

import com.eviware.soapui.impl.wsdl.WsdlProject;
import com.eviware.soapui.model.testsuite.ProjectRunListener;
import com.eviware.soapui.model.testsuite.TestProperty;
import com.google.common.io.Files;
import com.google.common.base.Charsets;

import net.sf.json.JSON;
import net.sf.json.JSONNull
import net.sf.json.groovy.JsonSlurper;

@groovy.transform.TypeChecked
public abstract class AbstractScript {

	protected Logger log;
	protected String projectPath;
	protected ProjectRunListener[] listeners;
	protected WsdlProject project;
	protected String environment;
	protected File projectDir;
	
	public static final String PROJECT_DIR = "projects";
	public static final String DATA_DIR = "data";

	public AbstractScript() throws Exception {
		this.init();
	}

	protected void init() throws Exception {
		Main main = Main.getInstance();
		this.log = main.getLog();
		this.projectPath = main.getProjectPath();
		this.project = main.getProject();
		this.listeners = project.getProjectRunListeners();
		this.environment = project.getActiveEnvironment().getName();
		this.projectDir = new File(projectPath, PROJECT_DIR);
	}

	public JSON getSchema(String file) throws IOException {
		return new JsonSlurper().parse(getFile(projectDir, file));
	}
	
	public JSON getJsonObject(String file) throws IOException {
		return new JsonSlurper().parse(getFile(projectDir, file));
	}

	public JSON getJsonObject(String file, Map<String, TestProperty> properties) throws IOException {
		String content = Files.toString(getFile(projectDir, file), Charsets.UTF_8);
		if (properties != null) {
			for (TestProperty property : properties.values()) {
				if (content.indexOf("\${#" + property.getName() + "}") != -1) {
					if (!isNotNull(property.getValue())) {
						content = content.replaceAll("\"\\\$\\{#" + property.getName() + "\\}\"", String.valueOf(property.getValue()));
					}
					content = content.replaceAll("\\\$\\{#" + property.getName() + "\\}", String.valueOf(property.getValue()));
				}
			}
		}
		content = content.replaceAll("(\"{0,1})\\\$\\{.*\\}(\"{0,1})", "null");
		log.info("getJsonObject: " + content);
		return parse(content);
	}

	public JSON parse(String body) throws IOException {
		return new JsonSlurper().parseText(body.trim());
	}
	
	public File getFile(String parent, String filePath) {
		return getFile(new File(parent, filePath));
	}
	
	public File getFile(File parent, String filePath) {
		return getFile(new File(parent, filePath));
	}
		
	public File getFile(File file) {
		log.info("Open file: " + file.getAbsoluteFile());
		return file;
	}

	public static String stripName(String file) {
		if (file.charAt(0) == '/') {
			file = file.substring(1);
		}
		return file.replaceAll("/", "_").replaceAll("\\{", "").replaceAll("\\}", "").replaceAll(" ", "_");
	}

	public static boolean isNotNull(Object value) {
		return value != null && !"null".equals(String.valueOf(value));
	}
}