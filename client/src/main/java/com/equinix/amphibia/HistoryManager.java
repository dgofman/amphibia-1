/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.equinix.amphibia;

import static com.equinix.amphibia.components.TreeCollection.TYPE.*;

import com.equinix.amphibia.components.Editor;
import com.equinix.amphibia.components.MainPanel;
import com.equinix.amphibia.components.TreeCollection;
import com.equinix.amphibia.components.TreeIconNode;
import java.io.File;
import java.util.Enumeration;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

/**
 *
 * @author dgofman
 */
public class HistoryManager {

    private final MainPanel mainPanel;
    private final Editor editor;

    public HistoryManager(MainPanel mainPanel, Editor editor) {
        this.mainPanel = mainPanel;
        this.editor = editor;
    }

    public void saveEntry(Editor.Entry entry, TreeCollection collection) {
        TreeIconNode node = entry.node;
        TreeIconNode.ResourceInfo info = node.info;
        TreeCollection.TYPE type = node.getType();
        TreeIconNode profile = (TreeIconNode.ProfileNode) collection.profile;
        if ("name".equals(entry.name)) {
            if (type == PROJECT || type == PROFILE) {
                JSONObject json = profile.jsonObject().getJSONObject("project");
                json.element("name", entry.value);
            } else if (type == INTERFACE) {
                if (node.info.interfaceJSON.getString("name").equals(node.info.resource.get("name"))) {
                    node.info.resource.remove(entry.name);
                } else {
                    node.info.resource.element(entry.name, entry.value);
                }
            } else if (type == TESTSUITE) {
                if (entry.value.equals(node.info.testSuite.get("name"))) {
                    node.info.testSuite.remove("alias");
                } else {
                    node.info.testSuite.element("alias", entry.value);
                }
            } else if (type == TESTCASE) {
                info.testCase.element("name", entry.value);
            } else if (type == TEST_STEP_ITEM) {
                info.testStep.element("name", entry.value);
            }
            saveAndAddHistory(profile);
            //Save new selection name
            node.getTreeIconUserObject().setLabel(entry.value.toString());
            node.saveSelection();
            mainPanel.reloadCollection(collection);
        } else if ("disabled".equals(entry.name)) {
            JSONObject json;
            switch (type) {
                case TESTSUITE:
                    JSONArray testsuites = profile.jsonObject().getJSONArray("testsuites");
                    int index = node.jsonObject().getInt("index");
                    json = testsuites.getJSONObject(index);
                    break;
                case TESTCASE:
                    json = info.testCase;
                    break;
                case TEST_STEP_ITEM:
                    json = info.testStep;
                    break;
                default:
                    return;
            }
            if (entry.value != Boolean.TRUE) {
                json.remove("disabled");
            } else {
                json.element("disabled", true);
            }
        } else if (type == PROJECT) {
            if ("properties".equals(entry.getParent().toString())) {
                updateValues(entry, collection.project.jsonObject().getJSONObject("projectProperties"), profile.jsonObject(), "properties");
            }
        } else if (type == INTERFACE) {
            if ("headers".equals(entry.getParent().toString())) {
                updateValues(entry, node.info.interfaceJSON, node.info.resource, "headers");
            } else {
                if (node.info.interfaceJSON.getString(entry.name).equals(node.info.resource.get(entry.name))) {
                    node.info.resource.remove(entry.name);
                } else {
                    node.info.resource.element(entry.name, entry.value);
                }
            }
        } else if (type == TESTSUITE) {
            if ("properties".equals(entry.getParent().toString())) {
                updateValues(entry, info.testSuiteInfo.getJSONObject("properties"), info.testSuite, "properties");
            } else if ("endpoint".equals(entry.name)) {
                node.info.resource.element("endpoint", entry.value);
                profile = collection.project;
            }
        } else if (type == TESTCASE) {
            if ("request".equals(entry.rootName) || "response".equals(entry.rootName)) {
                info.testCase.remove("request");
                info.testCase.remove("response");
                updateProperties(entry, info.testCase);
            } else if ("summary".equals(entry.name)) {
                info.testCaseInfo.element(entry.name, entry.value);
            } else if ("operationId".equals(entry.name)) {
                info.testCaseInfo.element(entry.name, entry.value);
            } else if ("method".equals(entry.name) || "path".equals(entry.name)) {
                info.testCaseInfo.element(entry.name, entry.value);
            } else if ("properties".equals(entry.getParent().toString())) {
                if (info.testCase != null) { //update profile.json
                    updateValues(entry, info.testCaseInfo.getJSONObject("properties"), info.testCase, "properties");
                }
            } else if ("headers".equals(entry.getParent().toString())) {
                if (info.testCase != null) { //update profile.json
                    updateValues(entry, info.testCaseHeaders, info.testCase, "headers");
                }
            } else {
                return;
            }
        } else if (type == RULES || type == TEST_ITEM || type == SCHEMA_ITEM) {
            saveNode(node);
            return;
        } else if (type == TEST_STEP_ITEM) {
            if ("headers".equals(entry.getParent().toString())) {
                if (info.testStep != null) { //update profile.json
                    updateValues(entry, info.testCaseHeaders, info.testStep, "headers");
                }
            } else if ("properties".equals(entry.rootName)) {
                if (info.testCase != null) { //update profile.json
                    updateValues(entry, info.testCaseInfo.getJSONObject("properties"), info.testStep, "properties");
                }
            } else {
                JSONObject json = node.jsonObject();
                info.testStep.remove("request");
                info.testStep.remove("response");
                JSONObject request = compare(info.testStepInfo.getJSONObject("request"), json.getJSONObject("request"), "request");
                if (!request.isEmpty()) {
                    info.testStep.element("request", request);
                }
                JSONObject response = compare(info.testStepInfo.getJSONObject("response"), json.getJSONObject("response"), "response");
                if (!response.isEmpty()) {
                    info.testStep.element("response", response);
                }
            }
        }
        saveNode(profile);
    }
    
    private void updateProperties(Editor.Entry entry, JSONObject target) {
        TreeIconNode node = entry.node;
        try {
            File file = IO.getFile(node.getCollection(), node.jsonObject().getString("file"));
            if (file != null && file.exists()) {
                JSONObject testJSON = (JSONObject) IO.getJSON(file);
                JSONObject json = node.jsonObject();
                JSONObject request = compare(testJSON.getJSONObject("request"), json.getJSONObject("request"), "request");
                if (!request.isEmpty()) {
                    target.element("request", request);
                }
                JSONObject response = compare(testJSON.getJSONObject("response"), json.getJSONObject("response"), "response");
                if (!response.isEmpty()) {
                    target.element("response", response);
                }
                return;
            }
        } catch (Exception ex) {
            mainPanel.addError(ex);
        }
        if (entry.json.isEmpty()) {
            target.remove(entry.rootName);
        } else {
            target.element(entry.rootName, entry.json);
        }
    }

    public static JSONObject compare(JSONObject source, JSONObject target, String rootName) {
        source.keySet().forEach((key) -> {
            if (rootName == null || !"properties".equals(key)) {
                if (String.valueOf(source.get(key)).equals(String.valueOf(target.get(key)))) {
                    target.remove(key);
                }
            }
        });
        if (rootName != null) {
            JSONObject properties = target.getJSONObject("properties");
            TreeIconNode node = MainPanel.selectedNode;
            if (node.info.common != null) {
                JSONObject commonItem = node.info.common;
                JSONObject commonProperties = commonItem.getJSONObject(rootName).getJSONObject("properties");
                commonProperties.keySet().forEach((key) -> {
                    if (String.valueOf(commonProperties.get(key)).equals(String.valueOf(properties.get(key)))) {
                        properties.remove(key);
                    }
                });
            }
            if (source.containsKey("properties")) {
                compare(source.getJSONObject("properties"), properties, null);
            }
            if (properties.isEmpty()) {
                target.remove("properties");
            }
        }
        return target;
    }

    public static void replace(JSONObject source, JSONObject target) {
        if (IO.isNULL(target)) {
            return;
        }
        source.keySet().forEach((k) -> {
            String key = k.toString();
            if (source.get(key) instanceof JSONObject && !source.getJSONObject(key).isNullObject()) {
                if (target.getJSONObject(key).isNullObject()) {
                    target.element(key, source.get(key));
                } else {
                    replace(source.getJSONObject(key), target.getJSONObject(key));
                }
            } else {
                target.element(key, source.get(key));
            }
        });
    }

    private void updateValues(Editor.Entry entry, JSONObject source, JSONObject target, String name) {
        JSONObject json = target.getJSONObject(name);
        if (json.isNullObject()) {
            json = new JSONObject();
        }
        if (entry.isDelete) {
            json.remove(entry.name);
        } else if (source.containsKey(entry.name) && String.valueOf(source.get(entry.name)).equals(String.valueOf(entry.value))) {
            json.remove(entry.name);
        } else {
            json.element(entry.name, entry.value);
        }
        if (json.isEmpty()) {
            target.remove(name);
        } else {
            target.element(name, json);
        }
    }

    public void saveNode(TreeIconNode node) {
        mainPanel.history.saveAndAddHistory(node);
        mainPanel.reloadCollection(node.getCollection());
    }

    public void resetHistory(boolean all) {
        editor.resetHistory();
    }

    public void renameResource() {
        int index = 0;
        String[] names = new String[MainPanel.selectedNode.getParent().getChildCount() - 1];
        Enumeration children = MainPanel.selectedNode.getParent().children();
        while (children.hasMoreElements()) {
            Object node = children.nextElement();
            if (node != MainPanel.selectedNode) {
                names[index++] = node.toString();
            }
        }
        String name = Amphibia.instance.inputDialog("renameResources", MainPanel.selectedNode.getLabel(), names);
        if (name != null && !name.isEmpty()) {
            Editor.Entry entry = new Editor.Entry(MainPanel.selectedNode, "name");
            entry.value = name;
            saveEntry(entry, MainPanel.selectedNode.getCollection());
        }
    }

    public void addHistory(String oldContent, String newContent, TreeIconNode node) {
        addHistory(node.getTreeIconUserObject().getFullPath(), oldContent, newContent);
    }

    public void addHistory(String filePath, String oldContent, String newContent) {
        editor.addHistory(null, filePath, oldContent, newContent);
    }

    public void saveAndAddHistory(TreeIconNode node) {
        String[] contents = IO.write(node, editor);
        addHistory(contents[0], contents[1], node);
    }
}
