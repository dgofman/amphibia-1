import org.apache.log4j.Logger;

import groovy.lang.Script;

import com.equinix.amphibia.agent.groovy.Main
import com.eviware.soapui.impl.wsdl.WsdlProject;
import com.eviware.soapui.model.testsuite.TestRunner;

@groovy.transform.TypeChecked
public class Bridge {
	
	public Bridge(Script script, WsdlProject project, Logger log, String projectPath) throws Exception {
		TestRunner runner = null;
		if (script.getBinding().hasVariable("runner")) {
			runner = ((TestRunner)script.getBinding().getVariable("runner"));
			runner.cancel(project.getName());
		}
		log.info("Calling Bridge");
		Main.getInstance().init(runner, project, log, projectPath);
	}
}