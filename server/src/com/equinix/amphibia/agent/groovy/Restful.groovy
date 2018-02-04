package com.equinix.amphibia.agent.groovy;

import com.eviware.soapui.impl.wsdl.WsdlTestSuite;
import com.eviware.soapui.impl.wsdl.submit.transports.http.HttpResponse;
import com.eviware.soapui.impl.wsdl.testcase.WsdlTestCase;
import com.eviware.soapui.impl.wsdl.teststeps.RestTestRequest;
import com.eviware.soapui.impl.wsdl.teststeps.RestTestRequestStep;

import net.sf.json.JSON;

public interface Restful {

	public void onRequest(WsdlTestSuite testSuite, WsdlTestCase testCase, RestTestRequestStep testStep, JSON stepJson, RestTestRequest request);
	
	public void onResponse(WsdlTestSuite testSuite, WsdlTestCase testCase, RestTestRequestStep testStep, JSON stepJson, HttpResponse response);
}
