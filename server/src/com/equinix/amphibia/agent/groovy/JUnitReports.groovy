package com.equinix.amphibia.agent.groovy;

import static com.equinix.amphibia.agent.groovy.ReportTestCase.State

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.eviware.soapui.SoapUI;
import com.eviware.soapui.model.testsuite.TestCaseRunner;

public class JUnitReports {

	protected ReportTestSuite testsute;
	protected String outputFolder;
	
	protected Map<String, ReportTestSuite> testsuites;

	public JUnitReports() {
		testsuites = new TreeMap<String, ReportTestSuite>();
	}

	public void createNewReport(String outputFolder, String testSuiteName) {
		this.outputFolder = outputFolder;
		testsute = testsuites.get(testSuiteName);
		if (testsute == null) {
			testsute = new ReportTestSuite();
			testsuites.put(testSuiteName, testsute);
		}
	}

	public ReportTestCase addNewTestCase(String testCaseName, String className) {
		ReportTestCase testcase = new ReportTestCase(testCaseName, className);
		testsute.testcases.add(testcase);
		return testcase;
	}

	public void afterRun(ReportTestCase testcase, TestCaseRunner testRunner) {
		testcase.setTime(testRunner.getTimeTaken());
		testsute.totalTime += testRunner.getTimeTaken();
	}

	public void save() throws IOException {
		int totalTime = 0;
		int totalTestSteps = 0;
		int totalErrors = 0;
		int totalFailed = 0;
		int totalSkipped = 0;

		Map<String, Boolean> testCases = new TreeMap<String, Boolean>();

		for (String testSuiteName : testsuites.keySet()) {
			ReportTestSuite testsuite = testsuites.get(testSuiteName);
			try {
				String fileName = "TEST- ${testSuiteName.replaceAll("\\W+", "")}.xml";
				PrintWriter pw = new PrintWriter(new FileOutputStream(new File(outputFolder, fileName.replaceAll("[\\\\/:*?\"<>|]", "_")), true));
				pw.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");

				StringBuffer sw = new StringBuffer();
				totalTime += testsuite.totalTime;
				int noofFailures, noofErrors, noofSkipped;
				noofFailures = noofErrors = noofSkipped = 0;
	
				for (ReportTestCase testcase : testsuite.testcases) {
					totalTestSteps++;
					testCases.put(testcase.getClassName(), true);
					String testCaseAttrs = " time=\"" + testcase.getTime() + "\" name=\"" + testcase.getTestCaseName() + "\" classname=\"" + testcase.getClassName() + "\" tag=\"" + testcase.getTagName();
					switch (testcase.getState()) {
						case State.SUCCESS:
							sw.append("<testcase" + testCaseAttrs + "\" />");
							break;
						case State.ERROR:
							sw.append("<testcase" + testCaseAttrs + "\">" + 
									"<error message=\"" + testcase.getMessage() + "\" type=\"" + testcase.getType() + "\"><![CDATA[" + testcase.getStackTrace() + "]]></error></testcase>");
							noofErrors++;
							totalErrors++;
							break;
						case State.FAIL:
							sw.append("<testcase" + testCaseAttrs + "\">" + 
									"<failure message=\"" + testcase.getMessage() + "\" type=\"" + testcase.getType() + "\"><![CDATA[" + testcase.getStackTrace() + "]]></failure></testcase>");
							noofFailures++;
							totalFailed++;
							break;
						case State.SKIPPED:
							sw.append("<testcase" + testCaseAttrs + "\"><skipped message=\"\"/></testcase>");
							noofSkipped++;
							totalSkipped++;
							break;
					}
				}
	
				pw.println("<testsuite time=\"" + (testsuite.totalTime / 1000) + "\" failures=\"" + noofFailures + "\" errors=\""
					+ noofErrors + "\" " + "skipped=\"" + noofSkipped + "\" tests=\"" + testsuite.testcases.size()
					+ "\" " + "timestamp=\"" + new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").format(testsuite.timestamp) + "\" " + "name=\"" + testSuiteName + "\">");
				pw.println(sw.toString());
				pw.println("</testsuite>");
				pw.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		System.out.println();
		System.out.println("SoapUI " + SoapUI.SOAPUI_VERSION + " TestCaseRunner Summary");
		System.out.println("-----------------------------");
		System.out.println("Time Taken: " + totalTime + "ms");
		System.out.println("Total TestSuites: " + testsuites.size());
		System.out.println("Total TestCases: " + testCases.size());
		System.out.println("Total TestSteps: " + totalTestSteps);
		System.out.println("Total Errors: " + totalErrors);
		System.out.println("Total Failed: " + totalFailed);
		System.out.println("Total Skipped: " + totalSkipped);
	}


	public class ReportTestSuite {
		public double totalTime = 0;
		public long timestamp = System.currentTimeMillis();
		public List<ReportTestCase> testcases = new ArrayList<ReportTestCase>();
	}
}
