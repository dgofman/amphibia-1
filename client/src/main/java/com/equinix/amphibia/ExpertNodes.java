/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.equinix.amphibia;

import com.equinix.amphibia.components.TreeCollection;
import static com.equinix.amphibia.components.TreeCollection.EDIT_ITEM_PROPERTIES;
import static com.equinix.amphibia.components.TreeCollection.RESOURCES_PROPERTIES;
import static com.equinix.amphibia.components.TreeCollection.TEST_ITEM_PROPERTIES;
import static com.equinix.amphibia.components.TreeCollection.TEST_TESTCASE_PROPERTIES;
import static com.equinix.amphibia.components.TreeCollection.TEST_TESTSUITE_PROPERTIES;
import static com.equinix.amphibia.components.TreeCollection.TYPE.REQUEST_ITEM;
import static com.equinix.amphibia.components.TreeCollection.TYPE.RESPONSE_ITEM;
import static com.equinix.amphibia.components.TreeCollection.TYPE.SCHEMA_ITEM;
import static com.equinix.amphibia.components.TreeCollection.TYPE.TESTCASE;
import static com.equinix.amphibia.components.TreeCollection.TYPE.TESTSUITE;
import static com.equinix.amphibia.components.TreeCollection.TYPE.TEST_ITEM;
import static com.equinix.amphibia.components.TreeCollection.VIEW_ITEM_PROPERTIES;
import com.equinix.amphibia.components.TreeIconNode;
import java.io.File;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.sf.json.JSONArray;
import net.sf.json.JSONNull;
import net.sf.json.JSONObject;

/**
 *
 * @author dgofman
 */
public class ExpertNodes {
    
    public static String dirFormat = "data/%s/tests/%s";
    public static String pathFormat = ExpertNodes.dirFormat + "/%s.json";
    
    private static final Logger logger = Logger.getLogger(ExpertNodes.class.getName());
    
    public static void createNodes(TreeCollection collection, JSONArray projectResources, Map<String, 
            TreeIconNode.ResourceInfo> resourceInfoMap, Map<Object, JSONObject> interfacesMap) {
        JSONObject testSuites = new JSONObject();
        projectResources.forEach((resource) -> {
            JSONObject resourseJSON = (JSONObject) resource;
            JSONObject testsuiteList = resourseJSON.getJSONObject("testsuites");
            String resourceId = resourseJSON.getString("resourceId");
            JSONObject interfaceJSON = interfacesMap.get(resourseJSON.getOrDefault("interface", ""));
            testsuiteList.keySet().forEach((name) -> {
                JSONObject testSuiteItem = testsuiteList.getJSONObject(name.toString());
                JSONObject testSuiteJSON = new JSONObject();
                String dir = String.format(dirFormat, resourceId, name);
                File file = IO.getFile(collection, dir);
                testSuites.put(name, file.getAbsolutePath());
                testSuiteJSON.element("name", name);
                testSuiteJSON.element("path", dir);
                testSuiteJSON.element("endpoint", resourseJSON.getString("endpoint"));
                testSuiteJSON.element("interface", interfaceJSON == null ? "" : interfaceJSON.getString("name"));
                testSuiteJSON.element("properties", testSuiteItem.getJSONObject("properties"));

                TreeIconNode node = collection.addTreeNode(collection.tests, name.toString(), TESTSUITE, false)
                        .addProperties(TEST_TESTSUITE_PROPERTIES)
                        .addJSON(testSuiteJSON);
                node.info = resourceInfoMap.get(file.getAbsolutePath());

                if (node.info.testSuite.containsKey("properties")) {
                    IO.replaceValues(node.info.testSuite.getJSONObject("properties"), testSuiteJSON.getJSONObject("properties"));
                }

                JSONArray testcases = new JSONArray();
                testSuiteItem.getJSONArray("testcases").forEach((testcase) -> {
                    JSONObject testCase = (JSONObject) testcase;
                    testcases.add(testCase.getString("name"));

                    JSONObject testcaseJSON = new JSONObject();
                    JSONObject config = testCase.getJSONObject("config");
                    JSONObject replace = config.getJSONObject("replace");
                    testcaseJSON.element("name", testCase.getString("name"));
                    testcaseJSON.element("type", testCase.getString("type"));
                    testcaseJSON.element("summary", testCase.getString("summary"));
                    testcaseJSON.element("operationId", config.getString("operationId"));
                    testcaseJSON.element("method", replace.getString("method"));
                    testcaseJSON.element("url path", replace.getString("path"));
                    testcaseJSON.element("example", replace.get("body"));
                    testcaseJSON.element("headers", JSONNull.getInstance());
                    testcaseJSON.element("properties", new JSONObject());

                    IO.replaceValues(testCase.getJSONObject("properties"), testcaseJSON.getJSONObject("properties"));
                    IO.replaceValue(testcaseJSON, "headers", testCase.getJSONObject("headers"));

                    String path = String.format(pathFormat, resourceId, name, testCase.getString("name"));
                    TreeIconNode.ResourceInfo info = resourceInfoMap.get(path);

                    TreeIconNode testcaseNode = collection.addTreeNode(node, testCase.getString("name"), TESTCASE, false)
                            .addProperties(TEST_TESTCASE_PROPERTIES)
                            .addTooltip(replace.getString("path"))
                            .addJSON(testcaseJSON);
                    testcaseNode.info = info;

                    if (info != null) {
                        JSONObject testStepJSON = IO.toJSONObject(testcaseNode.info.testStepInfo);
                        testStepJSON.element("file", path);
                        testcaseNode.info.testStepInfo.keySet().forEach((key) -> {
                            testStepJSON.put(key, testcaseNode.info.testStepInfo.get(key));
                        });
                        TreeIconNode childNode = collection.addTreeNode(testcaseNode, info.file.getName(), null, false)
                                .addTooltip(info.file.getAbsolutePath())
                                .addProperties((Object[][]) TEST_ITEM_PROPERTIES)
                                .addJSON(testStepJSON)
                                .addType(TEST_ITEM);
                        childNode.info = testcaseNode.info;
                    }
                });
                testSuiteJSON.element("testcases", testcases);
            });
        });
        collection.tests.addProperties((Object[][]) VIEW_ITEM_PROPERTIES).addJSON(testSuites);
        collection.project.add(collection.tests);

        Object[][] loadNodes = new Object[][]{
            {"requests", collection.requests, REQUEST_ITEM, VIEW_ITEM_PROPERTIES, VIEW_ITEM_PROPERTIES},
            {"responses", collection.responses, RESPONSE_ITEM, VIEW_ITEM_PROPERTIES, VIEW_ITEM_PROPERTIES},
            {"schemas", collection.schemas, SCHEMA_ITEM, VIEW_ITEM_PROPERTIES, EDIT_ITEM_PROPERTIES}
        };

        for (Object[] item : loadNodes) {
            File dataDir = IO.getFile(collection, "data");
            JSONObject itemJSON = new JSONObject();
            for (String resourceId : dataDir.list()) {
                File resourceDir = IO.newFile(dataDir, resourceId);
                if (resourceDir.isFile()) { //profile.json
                    continue;
                }
                File suiteDir = IO.newFile(resourceDir, item[0].toString());
                TreeIconNode parentNode = (TreeIconNode) item[1];
                TreeCollection.TYPE type = (TreeCollection.TYPE) item[2];
                collection.project.add(parentNode);

                if (suiteDir.exists()) {
                    for (String dir : suiteDir.list()) {
                        File subdir = IO.newFile(suiteDir, dir);
                        String subDirPath = String.format("%s/%s/%s/%s", "data", resourceId, item[0], dir);
                        TreeIconNode node;
                        if (parentNode != collection.schemas) {
                            node = collection.addTreeNode(parentNode, dir, TESTSUITE, false);
                        } else {
                            node = collection.addTreeNode(parentNode, dir, null, false).addType(type);
                        }
                        node.info = new TreeIconNode.ResourceInfo(subdir);
                        node.addProperties(RESOURCES_PROPERTIES)
                                .addTooltip(subdir.getAbsolutePath())
                                .addJSON(new JSONObject().element("path", subDirPath).element("files", subdir.list()));
                        itemJSON.element(dir, subDirPath);
                        for (String name : subdir.list()) {
                            File file = IO.newFile(suiteDir, dir + "/" + name);
                            if (file.isFile()) {
                                try {
                                    TreeIconNode childNode = collection.addTreeNode(node, name, null, false)
                                            .addTooltip(file.getAbsolutePath())
                                            .addProperties((Object[][]) item[4])
                                            .addJSON(IO.getJSON(file))
                                            .addType(type);
                                    childNode.info = new TreeIconNode.ResourceInfo(file);
                                } catch (Exception ex) {
                                    logger.log(Level.SEVERE, ex.toString(), ex);
                                }
                            }
                        }
                    }
                }
                parentNode.addProperties((Object[][]) item[3]).addJSON(itemJSON);
            }
        }
    }
}
