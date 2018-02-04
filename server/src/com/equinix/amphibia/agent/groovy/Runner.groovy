package com.equinix.amphibia.agent.groovy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeMap;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;

import com.eviware.soapui.SoapUI;
import com.eviware.soapui.config.CredentialsConfig;
import com.eviware.soapui.impl.wsdl.WsdlTestSuite;
import com.eviware.soapui.impl.wsdl.submit.transports.http.HttpResponse;
import com.eviware.soapui.impl.wsdl.testcase.WsdlTestCase;
import com.eviware.soapui.impl.wsdl.testcase.WsdlTestCaseRunner;
import com.eviware.soapui.impl.wsdl.testcase.WsdlTestRunContext;
import com.eviware.soapui.impl.wsdl.teststeps.PropertyTransfer;
import com.eviware.soapui.impl.wsdl.teststeps.PropertyTransfersTestStep;
import com.eviware.soapui.impl.wsdl.teststeps.RestRequestStepResult;
import com.eviware.soapui.impl.wsdl.teststeps.RestTestRequest;
import com.eviware.soapui.impl.wsdl.teststeps.RestTestRequestStep;
import com.eviware.soapui.impl.wsdl.teststeps.WsdlPropertiesTestStep;
import com.eviware.soapui.model.support.DefaultTestStepProperty;
import com.eviware.soapui.model.testsuite.TestProperty;
import com.eviware.soapui.model.testsuite.TestRunListener;
import com.eviware.soapui.model.testsuite.TestStep;
import com.eviware.soapui.model.testsuite.TestStepResult.TestStepStatus;
import com.eviware.soapui.support.JsonPathFacade;
import com.eviware.soapui.support.types.StringToObjectMap;
import com.eviware.soapui.support.types.StringToStringsMap;

import net.sf.json.JSON;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

@groovy.transform.TypeChecked
public class Runner extends AbstractScript {

	private JSON optionsJson = null;
	private String outputFolder = null;
	private String gotoTagName = null;
	private JUnitReports jUnitReports;

	public Runner() throws Exception {
		super();
	}

	@Override
	protected void init() {
		super.init();

		jUnitReports = new JUnitReports();

		JSON profileJson = getJsonObject(DATA_DIR + "/profile.json");
		JSONObject projectProperties = getProperties(profileJson);
		JSONArray testsuitesJson = (JSONArray) profileJson.getAt("testsuites");
		optionsJson = (JSON) profileJson.getAt("project");

		if (SoapUI.getCmdLineRunner() != null) {
			outputFolder = SoapUI.getCmdLineRunner().getOutputFolder();

			if (outputFolder != null && optionsJson.getAt("appendLogs") == false && new File(outputFolder).exists()) {
				FileUtils.cleanDirectory(new File(outputFolder));
			}
		} else {
			outputFolder = getFile(projectPath, "Reports").getAbsolutePath();
		}

		Map<String, WsdlTestSuite> testSuites = new HashMap<String, WsdlTestSuite>();
		for (Object testSuiteJson : testsuitesJson) {
			String testSuiteName = testSuiteJson.getAt("name").toString();
			if (testSuiteJson.getAt("disabled") == true) {
				log.info("Ignored TestSuite: " + testSuiteName);
				continue;
			}
			jUnitReports.createNewReport(outputFolder,  testSuiteName);

			WsdlTestSuite testSuite = project.getTestSuiteByName(testSuiteName);
			if (testSuite == null) {
				throw new IllegalArgumentException("TestSuite is undefined: ${testSuiteName}");
			}

			JSONArray testCasesJson = (JSONArray) testSuiteJson.getAt("testcases");
			for (Object testCaseJson : testCasesJson) {
				String testCaseName = testCaseJson.getAt("name");
				if (testCaseJson.getAt("disabled") == true) {
					log.info("Ignored TestCase: " + testCaseName);
					continue;
				}
				WsdlTestCase testCase = testSuite.getTestCaseByName(testCaseName);
				if (testCase == null) {
					throw new IllegalArgumentException("TestCase is undefined: ${testCaseName}");
				}
				log.info("[------ Initializing testcase ${testSuite.name}::${testCase.name} ------]");

				TestRunListener[] testRunListeners = testCase.getTestRunListeners();
				for (int i = 0; i < testRunListeners.length; i++) {
					if ("com.eviware.soapui.tools.SoapUITestCaseRunner".equals(testRunListeners[i].getClass().getName())) {
						Method setIgnoreError = testRunListeners[i].getClass().getMethod("setIgnoreError", boolean.class);
						if (setIgnoreError != null) {
							setIgnoreError.invoke(testRunListeners[i], true);
							break;
						}
					}
				}

				TreeMap<String, WsdlPropertiesTestStep> testCaseProperties = new TreeMap<String, WsdlPropertiesTestStep>();

				String path = testCaseJson.getAt("path");
				JSONObject testcaseProperties = (JSONObject) testCaseJson.getAt("properties");
				if (testcaseProperties == null) {
					testcaseProperties = new JSONObject();
				}
				JSONObject testsJson = (JSONObject)getJsonObject(path);
				if (testsJson == null) {
					log.info("Invalid test path: {$path}");
					continue;
				}

				JSONArray testStepsJson = (JSONArray) testCaseJson.getAt("steps");
				for (Object testStepJson : testStepsJson) {
					String testStepName = testStepJson.getAt("name").toString();
					if (testStepJson.getAt("disabled") == true) {
						log.info("Ignored testStep: " + testStepName);
						continue;
					}

					JSONObject onFailJson = new JSONObject();
					/*String tagName = (String)testJson.getAt("goto");
					 if (tagName != null && gotoTagName == null) {
					 gotoTagName = tagName;
					 log.info("GOTO Tag: " + tagName);
					 }
					 JSONObject onFailJson = (JSONObject)testJson.getAt("onfail");
					 tagName = (String)testJson.getAt("tag");
					 if (gotoTagName != null) {
					 if (gotoTagName.equals(tagName)) {
					 gotoTagName = null;
					 }
					 }*/

					RestTestRequestStep testStep = (RestTestRequestStep)testCase.getTestStepByName(testStepName);
					if (testStep == null) {
						throw new IllegalArgumentException("TestStep is undefined: ${testStepName}");
					}
					log.info("[------ Initializing teststep: ${testStep.name} ------]");

					PrintWriter writer = null;
					try {
						if (outputFolder != null) {
							File dir = new File(outputFolder, testSuiteName);
							if (!dir.exists()) {
								dir.mkdirs();
							}
							writer = new PrintWriter(new FileOutputStream(new File(dir, AbstractScript.stripName(testStepName + ".log")), true));
						}

						JSONObject testProperties = getProperties(testStepJson);
						ReportTestCase testcase = jUnitReports.addNewTestCase(testCaseName, testStepName);

						if (testStepJson.getAt("disabled") == true) {
							log.info("Ignored testStep: " + testStep.getName());
							continue;
						}

						/*if (gotoTagName != null) {
						 if (!gotoTagName.equals(tagName)) {
						 continue;
						 }
						 gotoTagName = null;
						 }
						 String gotoTag = (String)stepJson.getAt("goto");
						 if (gotoTag != null && gotoTagName == null) {
						 gotoTagName = gotoTag;
						 log.info("GOTO Tag: " + gotoTag);
						 continue;
						 }*/

						testcase.success();

						RestRequestStepResult result = null;
						TestCaseRunner testCaseRunner = new TestCaseRunner(testCase, testStep, (JSONObject)testStepJson);
						try {
							RestTestRequest request = testStep.getTestRequest();
							CredentialsConfig credentialsConfig = request.getConfig().getCredentials();
							credentialsConfig.setAuthType(CredentialsConfig.AuthType.GLOBAL_HTTP_SETTINGS);

							StringToStringsMap stsmap = new StringToStringsMap();
							request.setRequestHeaders(stsmap);
							request.setRequestContent("");

							JSON requestJson = (JSON)testStepJson.getAt("request");
							JSONObject parametersJson = new JSONObject();
							if (requestJson != null) {
								parametersJson = getProperties(requestJson);

								log.info("Global Properties:");
								for (String key : SoapUI.getGlobalProperties().getProperties().keySet()) {
									log.info("\t->" + key + " = " + SoapUI.getGlobalProperties().getPropertyValue(key));
									addProperty(testStep, key, SoapUI.getGlobalProperties().getPropertyValue(key));
								}

								log.info("JSON Runner Properties:");
								for (Object _key : projectProperties.keySet()) {
									String key = (String)_key;
									if (projectProperties.getAt(key) != null) {
										log.info("\t->" + key + " = " + projectProperties.getAt(key).toString());
										addProperty(testStep, key, projectProperties.getAt(key).toString());
									}
								}

								log.info("Project CustomProperties");
								for (String key : this.project.getProperties().keySet()) {
									log.info("\t->" + key + " = " + this.project.getPropertyValue(key));
									addProperty(testStep, key, this.project.getPropertyValue(key));
								}

								log.info("TestCase Properties:");
								for (String key : testCase.getProperties().keySet()) {
									log.info("\t->" + key + " = " + testCase.getPropertyValue(key));
									addProperty(testStep, key, testCase.getPropertyValue(key));
								}

								log.info("JSON Test Properties:");
								for (Object _key : testProperties.keySet()) {
									String key = (String)_key;
									if (testProperties.getAt(key) != null) {
										String value = testProperties.getAt(key).toString();
										if (testcaseProperties.getAt(key) != null) {
											log.info("\t->" + key + " = '" + value + "' overrride to: " + testcaseProperties.getAt(key));
											value = testcaseProperties.getAt(key);
										}
										log.info("\t->" + key + " = " + value);
										addProperty(testStep, key, value);
									}
								}

								log.info("WsdlPropertiesTestStep Properties:");
								for(TestStep stepProp : testCase.getTestStepList()) {
									if (stepProp instanceof WsdlPropertiesTestStep) {
										WsdlPropertiesTestStep propertiesTestStep = (WsdlPropertiesTestStep)stepProp;
										Map<String, TestProperty> properties = propertiesTestStep.getProperties();
										for (String key : properties.keySet()) {
											TestProperty testProperty = properties.get(key);
											log.info("\t->" + testProperty.getName() + " = " + testProperty.getValue());
											testCaseProperties.put(testProperty.getName(), propertiesTestStep);
											addProperty(testStep, testProperty.getName(), testProperty.getValue());
										}
									}
								}

								log.info("JSON Step Properties:");
								for (Object _key : parametersJson.keySet()) {
									String key = (String)_key;
									if (parametersJson.getAt(key) != null) {
										log.info("\t->" + key + " = " + parametersJson.getAt(key).toString());
										addProperty(testStep, key, parametersJson.getAt(key).toString());
									}
								}

								JSON locationJson = (JSON)requestJson.getAt("location");
								RestTestRequest testRequest = testStep.getTestRequest();
								if (locationJson != null) {
									if (locationJson.getAt("endpoint") != null) {
										testRequest.setEndpoint(locationJson.getAt("endpoint").toString());
									}
									if (locationJson.getAt("pathname") != null) {
										testRequest.setPath(locationJson.getAt("pathname").toString());
									}
								}

								String scriptName = requestJson.getAt("script");
								if (scriptName != null) {
									log.info("Request Script: ${scriptName}");
									Class<?> callerClass = getClass().getClassLoader().loadClass(scriptName);
									Restful restful = (Restful)callerClass.newInstance();
									restful.onRequest(testSuite, testCase, testStep, (JSON)testStepJson, testRequest);
								}
							}

							if (isNotNull(requestJson.getAt("body"))) {
								JSONObject body = getBody(requestJson.getAt("body").toString(), testStep.getProperties());
								if (isNotNull(requestJson.getAt("schema"))) {
									String fileName = requestJson.getAt("schema").toString();
									JSON schema = getSchema(fileName);
									new SchemaValidator(fileName, body, schema);
								}
								request.setRequestContent(body.toString());
							}
							JSONObject headersJson = getHeaders(requestJson);
							if (headersJson != null) {
								for (Object _key : headersJson.keySet()) {
									String key = (String)_key;
									if (isNotNull(headersJson.getAt(key))) {
										stsmap.add(key, headersJson.getAt(key).toString());
									}
								}
								request.setRequestHeaders(stsmap);
							}

							testCaseRunner.start(false);
							result = testCaseRunner.getResult();
							if (result == null) {
								continue;
							}

							if (result.getStatus() == TestStepStatus.CANCELED) {
								testcase.addException(ReportTestCase.State.ERROR, new Exception(String.join("\n", result.getMessages()), null));
								continue;
							}

							if (result.getStatus() == TestStepStatus.FAILED) {
								testcase.addException(ReportTestCase.State.ERROR, new Exception(String.join("\n", result.getMessages())));
								continue;
							}
							HttpResponse response = ((RestRequestStepResult)result).getResponse();

							JSON responseJson = (JSON)testStepJson.getAt("response");
							if (isNotNull(responseJson) && response != null) {
								String resultContent = response.getContentAsString();
								if (resultContent != null  && resultContent.trim().length() > 0) {
									JSONObject apiResultBody = (JSONObject)parse(resultContent);
									if (apiResultBody != null && isNotNull(responseJson.getAt("schema"))) {
										String fileName = responseJson.getAt("schema").toString();
										JSON schema = getSchema(fileName);
										new SchemaValidator(fileName, apiResultBody, schema);
									}

									JSONObject bodyJson = getBody(responseJson);
									if (bodyJson != null) {
										compareBodyValues(bodyJson, apiResultBody, (JSONObject)responseJson.getAt("properties"));
									}

									JSONObject transferJson = getTransfer(responseJson);
									if (transferJson != null) {
										JSONObject transferProperty = (JSONObject)transferJson.getAt("global");
										if (transferProperty != null) {
											for (Object _key : transferProperty.keySet()) {
												String key = (String)_key;
												SoapUI.getGlobalProperties().setPropertyValue(key, getTransferValue(resultContent, transferProperty.getString(key)));
											}
										}

										transferProperty = (JSONObject)transferJson.getAt("runner");
										if (transferProperty != null) {
											for (Object key : transferProperty.keySet()) {
												projectProperties.put(key, getTransferValue(resultContent, transferProperty.getString((String)key)));
											}
										}

										transferProperty = (JSONObject)transferJson.getAt("project");
										if (transferProperty != null) {
											for (Object _key : transferProperty.keySet()) {
												String key = (String)_key;
												if (this.project.getProperty(key) == null) {
													this.project.addProperty(key);
												}
												TestProperty property = this.project.getProperty(key);
												property.setValue(getTransferValue(resultContent, transferProperty.getString(key)));
											}
										}

										transferProperty = (JSONObject)transferJson.getAt("testcase");
										if (transferProperty != null) {
											for (Object _key : transferProperty.keySet()) {
												String key = (String)_key;
												if (testCase.getProperty(key) == null) {
													testCase.addProperty(key);
												}
												TestProperty property = testCase.getProperty(key);
												property.setValue(getTransferValue(resultContent, transferProperty.getString(key)));
											}
										}

										transferProperty = (JSONObject)transferJson.getAt("test");
										if (transferProperty != null) {
											for (Object key : transferProperty.keySet()) {
												testProperties.put(key, getTransferValue(resultContent, transferProperty.getString((String)key)));
											}
										}

										transferProperty = (JSONObject)transferJson.getAt("teststep");
										if (transferProperty != null && testCaseProperties != null) {
											for (Object _key : transferProperty.keySet()) {
												String key = (String)_key;
												WsdlPropertiesTestStep property = testCaseProperties.get(key);
												if (property == null) {
													property = (WsdlPropertiesTestStep)testCaseProperties.firstEntry().getValue();
												}
												if (property != null) {
													property.setPropertyValue(key, getTransferValue(resultContent, transferProperty.getString(key)));
												}
											}
										}
									}

									if (optionsJson.getAt("updateTransfersProperty") == true ||
									(transferJson != null && transferJson.getAt("updateTransfersProperty") == true)) {
										for(TestStep stepProp : testCase.getTestStepList()) {
											if (stepProp instanceof PropertyTransfersTestStep) {
												PropertyTransfersTestStep propertyTransfers = (PropertyTransfersTestStep)stepProp;
												for (int index = 0; index < propertyTransfers.getTransferCount(); index++) {
													PropertyTransfer propertyTransfer = propertyTransfers.getTransferAt(index);
													//resultContent = propertyTransfer.getSourceProperty().getValue();
													String transfersValue = getTransferValue(resultContent, propertyTransfer.getSourcePath());
													WsdlPropertiesTestStep propertiesTestStep = (WsdlPropertiesTestStep)testCase.getTestStepByName(propertyTransfer.getTargetStepName());
													if (propertiesTestStep != null) {
														propertiesTestStep.setPropertyValue(propertyTransfer.getTargetPropertyName(), String.valueOf(transfersValue));
														log.info("${propertyTransfer.getName()} :: ${propertyTransfer.getSourcePath()} - ${transfersValue}");
													}
												}
											}
										}
									}
								}

								headersJson = getHeaders(responseJson);
								if (headersJson != null) {
									StringToStringsMap headers = response.getResponseHeaders();
									for (Object _key : headersJson.keySet()) {
										String key = (String)_key;
										assertEquals(headersJson.getAt(key).toString(), headers.get(key, ""));
									}
								}

								JSONArray assertsJson = getAsserts(responseJson);
								if (assertsJson != null) {
									for (Object assertJson : assertsJson) {
										String status = assertJson.getAt("status");
										if (status != null && result != null) {
											assertEquals(status, String.valueOf(result.getStatus()));
										}
										Integer statusCode = Integer.valueOf(assertJson.getAt("statusCode").toString());
										if (statusCode != null) {
											assertEquals(statusCode.intValue(), response.getStatusCode());
										}
									}
								}

								String scriptName = responseJson.getAt("script");
								if (scriptName != null) {
									if (scriptName != null) {
										log.info("Response Script: ${scriptName}");
										Class<?> callerClass = getClass().getClassLoader().loadClass(scriptName);
										Restful restful = (Restful)callerClass.newInstance();
										restful.onResponse(testSuite, testCase, testStep, (JSON)testStepJson, response);
									}
								}
							}
						} catch(AssertionError a) {
							testcase.addException(ReportTestCase.State.FAIL, a);
							errorHandler(a, onFailJson);
						} catch (Throwable t) {
							testcase.addException(ReportTestCase.State.ERROR, t);
							errorHandler(t, onFailJson);
						} finally {
							testCaseRunner.afterRun();
							jUnitReports.afterRun(testcase, testCaseRunner);
							if (result != null && writer != null) {
								result.writeTo(writer);
							}
							testCaseRunner = null;
						}
					} catch (Throwable t) {
						log.error("Failed: ${testSuiteName} -> ${testCaseName} -> ${testStepName}");
						log.error(t);
						ReportTestCase testcase = jUnitReports.addNewTestCase(testCaseName, testStepName);
						testcase.addException(ReportTestCase.State.ERROR, t);
						errorHandler(t, null);
					} finally {
						if (writer != null) {
							writer.close();
						}
					}
				}
			}
		}
		jUnitReports.save();
	}

	protected void errorHandler(Throwable t, JSONObject onFailJson) {
		String trace = t.getMessage() + "\n\t" + t.getStackTrace().join("\n\t");
		log.error(trace);
		if (optionsJson != null && optionsJson.getAt("continueOnError") == false) {
			throw t;
		}
		if (onFailJson != null) {
			gotoTagName = onFailJson.getAt("goto");
			if (onFailJson.getAt("continue") == false) {
				throw t;
			}
		}
	}

	protected Object getJsonValue(Object json) throws IOException {
		return this.getJsonValue(json, null);
	}

	protected Object getJsonValue(Object json, Map<String, TestProperty> properties) throws IOException {
		if (json instanceof String) {
			return getJsonObject((String)json, properties);
		} else {
			return json;
		}
	}

	protected JSONObject getProperties(Object json) {
		JSONObject properties = (JSONObject)getJsonValue(json.getAt("properties"));
		if (properties == null) {
			properties = new JSONObject();
		}
		return properties;
	}

	protected JSONObject getHeaders(JSON json) {
		return (JSONObject)getJsonValue(json.getAt("headers"));
	}

	protected JSONObject getBody(JSON json) {
		return (JSONObject)getJsonValue(json.getAt("body"));
	}

	protected JSONObject getBody(String json, Map<String, TestProperty> properties) {
		return (JSONObject)getJsonValue(json, properties);
	}

	protected JSONArray getAsserts(JSON json) {
		return (JSONArray)getJsonValue(json.getAt("asserts"));
	}

	protected JSONObject getTransfer(JSON json) {
		return (JSONObject)getJsonValue(json.getAt("transfer"));
	}

	protected String getTransferValue(String content, String path) {
		return new JsonPathFacade(content).readObjectValue(path);
	}

	protected void compareBodyValues(Object source, Object target, JSONObject properties) {
		if (source instanceof JSONArray) {
			assertEquals("compare array of items in body", source.toString(), target.toString());
		} else {
			Set<Object> keys = ((JSONObject)source).keySet();
			for (Object _key : keys) {
				String key = (String)_key;
				if (source.getAt(key) instanceof JSON) {
					compareBodyValues(source.getAt(key), target.getAt(key), properties);
				} else {
					String pattern = source.getAt(key).toString();
					if (pattern.startsWith("\${#")) {
						pattern = properties.getAt(key);
					}
					Pattern p = Pattern.compile(pattern.replaceAll("\\(", "\\\\(").replaceAll("\\)", "\\\\)"), Pattern.DOTALL);
					assertTrue("'${key}' is invalid in response body: ${target}", target != null && (target.getAt(key) != null || target.getAt(key) == null && source.getAt(key) == null));
					assertTrue("invalid body key ${target} - '${key}': '${pattern}' != '${target.getAt(key)}'", p.matcher(String.valueOf(target.getAt(key))).matches());
				}
			}
		}
	}

	protected void addProperty(RestTestRequestStep testStep, String key, String value) {
		DefaultTestStepProperty property = (DefaultTestStepProperty)testStep.getProperty(key);
		if (property == null) {
			property = new DefaultTestStepProperty(key, testStep);
		}
		property.setValue(value);
		testStep.addProperty(property);
	}

	private final class TestCaseRunner extends WsdlTestCaseRunner {

		private RestTestRequestStep testStep;
		private RestRequestStepResult result;
		private WsdlTestRunContext testStepContext;
		private JSONObject stepJson;

		public TestCaseRunner(WsdlTestCase testCase, StringToObjectMap properties) {
			super(testCase, properties);
		}

		public TestCaseRunner(WsdlTestCase testCase, RestTestRequestStep testStep, JSONObject stepJson) {
			this(testCase, new StringToObjectMap(testStep.getProperties()));
			this.testStep = testStep;
			this.stepJson = stepJson;
			testStepContext = new WsdlTestRunContext(testStep);
		}

		public RestRequestStepResult getResult() {
			return result;
		}

		@Override
		public void run() {
			this.fillInTestRunnableListeners();
			this.notifyBeforeRun();
			Object timeout = stepJson.get("timeout");
			if (timeout == null) {
				timeout = optionsJson.getAt("testCaseTimeout");
			}
			final Timer timer = new Timer();
			boolean isTimeout = false;
			this.setStartTime();
			if (timeout != null && Integer.parseInt(timeout.toString()) > 0) {
				timer.schedule(new TimerTask() {
							@Override
							public void run() {
								isTimeout = true;
							}
						}, Long.valueOf(timeout.toString()));
			}
			this.result = (RestRequestStepResult)testStep.run(this, testStepContext);
			this.setTimeTaken();
			this.result.setTimeTaken(this.getTimeTaken());
			timer.cancel();
			if (isTimeout) {
				result.setStatus(TestStepStatus.CANCELED);
				result.addMessage("TestCase timed out");
			}
			this.getResults().add(result);
		}

		public void afterRun() {
			if (this.isRunning()) {
				try {
					this.setStatus(Status.FINISHED); //Pro
				} catch (Exception e) {
					this.cancel("FINISHED"); //Community
				}
			}
			this.setTimeTaken();
			this.internalFinally(testStepContext);
		}
	}
}