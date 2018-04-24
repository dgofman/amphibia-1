/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.equinix.amphibia.components;

import static com.equinix.amphibia.Amphibia.getUserPreferences;
import static com.equinix.amphibia.components.TreeCollection.TYPE.*;

import com.equinix.amphibia.agent.converter.Profile;
import com.equinix.amphibia.agent.builder.Properties;

import com.equinix.amphibia.Amphibia;
import com.equinix.amphibia.IO;

import java.io.File;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.MutableTreeNode;
import net.sf.json.JSON;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import net.sf.json.JSONNull;

/**
 *
 * @author dgofman
 */
@SuppressWarnings("NonPublicExported")
public class TreeIconNode extends DefaultMutableTreeNode {

    private final TreeIconUserObject nodeUserObject;

    private int nodeType; //0 - Project Node, 1 - Debug Node 

    private static final Logger logger = Amphibia.getLogger(TreeIconNode.class.getName());
    private static final Preferences userPreferences = getUserPreferences();
    private static String selectedUIDName;

    public static final Icon runIcon;
    public static final Icon runningIcon;
    public static final Icon passedIcon;
    public static final Icon failedIcon;
    public static final Icon skippedIcon;

    public ResourceInfo info;
    public TreeIconNode source;
    public TreeIconNode debugNode;

    public static final int STATE_OPEN_CLOSE = 0;
    public static final int STATE_DEBUG_EXPAND = 1;
    public static final int STATE_DEBUG_REPORT = 2;
    public static final int STATE_OPEN_PROJECT_OR_WIZARD_TAB = 3; //Project is open or close, or TestCase/TestStep open in Wizard tab
    public static final int STATE_HIDE_COMMONS = 4; //TestCase hide common
    
    public static final int REPORT_INIT_STATE = 0;
    public static final int REPORT_RUN_STATE = 1;
    public static final int REPORT_RUNNING_STATE = 2;
    public static final int REPORT_PASSED_STATE = 3;
    public static final int REPORT_SKIPPED_STATE = 4;
    public static final int REPORT_ERROR_STATE = 5;
    public static final int REPORT_FAILED_STATE = 6;
    
    private static final Map<String, Icon> ICONS = new HashMap<>();


    static {
        byte[] data = userPreferences.getByteArray(Amphibia.P_SELECTED_NODE, null);
        if (data != null) {
            setSelectedUIDName(new String(data));
        }
        runIcon = new ImageIcon(TreeIconNode.class.getResource("/com/equinix/amphibia/icons/testcase_run_16.png"));
        runningIcon = new ImageIcon(TreeIconNode.class.getResource("/com/equinix/amphibia/icons/testcase_running_16.png"));
        passedIcon = new ImageIcon(TreeIconNode.class.getResource("/com/equinix/amphibia/icons/testcase_passed_16.png"));
        failedIcon = new ImageIcon(TreeIconNode.class.getResource("/com/equinix/amphibia/icons/testcase_failed_16.png"));
        skippedIcon = new ImageIcon(TreeIconNode.class.getResource("/com/equinix/amphibia/icons/testcase_skipped_16.png"));
    }

    @SuppressWarnings("OverridableMethodCallInConstructor")
    public TreeIconNode() {
        this(null, "root", null, false);
        this.addType(TreeCollection.TYPE.ROOT);
    }

    public TreeIconNode(TreeCollection collection, String label, TreeCollection.TYPE type, boolean truncate) {
        this(collection, label, type, truncate, null);
    }

    public TreeIconNode(TreeCollection collection, String label, TreeCollection.TYPE type, boolean truncate, Object[][] properties) {
        this(new TreeIconUserObject(collection, label, type, truncate, properties));
    }

    @SuppressWarnings({"OverridableMethodCallInConstructor", "LeakingThisInConstructor"})
    public TreeIconNode(TreeIconNode source, TreeCollection.TYPE type) {
        this(source.getCollection(), source.getLabel(), type, false);
        this.nodeType = 1;
        this.source = source;
        this.info = source.info;
        this.addType(source.getType());
        this.nodeUserObject.setEnabled(source.nodeUserObject.isEnabled());
        source.debugNode = this;
    }

    public TreeIconNode(TreeIconNode source) {
        this(source, source.getType());
    }

    public TreeIconNode addProperties(Object[][] properties) {
        nodeUserObject.properties = properties;
        return this;
    }

    public TreeIconNode addJSON(JSON json) {
        nodeUserObject.json = json;
        return this;
    }

    public TreeIconNode addType(TreeCollection.TYPE type) {
        nodeUserObject.type = type;
        return this;
    }

    public JSONObject jsonObject() {
        return (JSONObject) nodeUserObject.json;
    }

    public JSONArray jsonArray() {
        return (JSONArray) nodeUserObject.json;
    }

    public TreeCollection.TYPE getType() {
        return nodeUserObject.type;
    }

    public TreeIconNode addTooltip(String tooltip) {
        nodeUserObject.setTooltip(tooltip);
        return this;
    }

    public String getLabel() {
        return nodeUserObject.getLabel();
    }

    public TreeIconNode(TreeIconUserObject nodeUserObject) {
        super(nodeUserObject);
        this.nodeUserObject = nodeUserObject;
        this.nodeType = 0;
    }

    public TreeCollection getCollection() {
        return getTreeIconUserObject().getCollection();
    }

    public TreeIconNode setReportState(int state) {
        if (nodeUserObject.json instanceof JSONObject && jsonObject().containsKey("states")) {
            jsonObject().getJSONArray("states").set(STATE_DEBUG_REPORT, state);
        }
        return this;
    }

    public int getReportState() {
        if (nodeUserObject.json instanceof JSONObject && jsonObject().containsKey("states")) {
            return jsonObject().getJSONArray("states").getInt(STATE_DEBUG_REPORT);
        }
        return REPORT_INIT_STATE;
    }

    public Icon getReportIcon() {
        switch (getReportState()) {
            case REPORT_RUN_STATE:
                return runIcon;
            case REPORT_RUNNING_STATE:
                return runningIcon;
            case REPORT_PASSED_STATE:
                return passedIcon;
            case REPORT_ERROR_STATE:
            case REPORT_FAILED_STATE:
                return failedIcon;
            case REPORT_SKIPPED_STATE:
                return skippedIcon;
            default:
                return null;
        }
    }

    @Override
    public void setParent(MutableTreeNode newParent) {
        super.setParent(newParent);
        if (getUID(getNodePath()).equals(selectedUIDName)) {
            MainPanel.setSelectedNode(this.source != null ? this.source : this);
        }
    }

    public String getNodePath() {
        return Arrays.toString(this.getPath());
    }

    public void saveSelection() {
        setSelectedUIDName(getUID());
        userPreferences.putByteArray(Amphibia.P_SELECTED_NODE, selectedUIDName.getBytes());
    }

    public String getUID() {
        return getUID(getNodePath());
    }

    public String getUID(String path) {
        return nodeType + "_" + getCollection().getUUID() + "_" + path;
    }

    public TreeIconUserObject getTreeIconUserObject() {
        return nodeUserObject;
    }

    public TreeIconNode cloneNode() {
        TreeIconNode node = (TreeIconNode) clone();
        node.source = this;
        return node;
    }

    public TreeIconNode getSource() {
        return source;
    }

    private static void setSelectedUIDName(String name) {
        selectedUIDName = name;
    }

    static public class ResourceInfo {

        public File file;
        public JSONObject resource; //resource item in projectResources array
        public JSONObject interfaceJSON; // (profile.json) resources headers
        public JSONObject testSuite; //(profile.json) get and change testcases order
        public JSONObject testCase; //(profile.json) get and set testcase name and disabled status
        public JSONObject testStep; //(profile.json) get diff of parent test file
        public JSONObject testSuiteInfo; //get testsuite properties
        public JSONObject testCaseInfo; //get testcase properties and configiration
        public JSONObject testCaseHeaders; //combination interface and testcase headers
        public JSONObject testStepInfo; //teststep file data
        public JSONObject common; //Common data
        public JSONArray states; //states of node, indexes: (0 - project expand state, 1 - debug expand state, 2 - run report state)
        public Properties properties;
        public int consoleLine;

        public ResourceInfo(File file) {
            this.file = file;
        }

        public ResourceInfo(JSONArray states) {
            this.states = states;
        }
        
        public ResourceInfo(File file, JSONObject resource, JSONObject interfaceJSON) {
            this(file, resource, interfaceJSON, null, null, null, null, null);
        }

        public ResourceInfo(File file, JSONObject resource, JSONObject interfaceJSON, JSONObject testSuite, JSONObject testSuiteInfo, JSONObject testCaseInfo, JSONObject testCaseHeaders, JSONObject testStepInfo) {
            this.file = file;
            this.resource = resource;
            this.interfaceJSON = interfaceJSON;
            this.testSuite = testSuite;
            this.testSuiteInfo = testSuiteInfo;
            this.testCaseInfo = testCaseInfo;
            this.testCaseHeaders = testCaseHeaders;
            this.testStepInfo = testStepInfo;
            this.testStep = null;
        }

        public ResourceInfo clone(JSONObject testCase) {
            ResourceInfo clone = new ResourceInfo(file, resource, interfaceJSON, testSuite, testSuiteInfo, testCaseInfo, testCaseHeaders, testStepInfo);
            clone.properties = properties.cloneProperties();
            clone.testCase = testCase;
            return clone;
        }

        public ResourceInfo clone(JSONObject testCase, JSONObject testStep) {
            ResourceInfo clone = clone(testCase);
            clone.properties = properties.cloneProperties();
            clone.testStep = testStep;
            return clone;
        }

        public JSONObject getRequestHeader(TreeIconNode node) {
            JSONObject mergeHeaders = new JSONObject();
            mergeHeaders.putAll(interfaceJSON.getJSONObject("headers"));
            mergeHeaders.putAll(node.info.testCaseHeaders);
            JSONObject headers = getRequestHeader(node, mergeHeaders);
            JSONObject newHeaders = new JSONObject();
            headers.keySet().forEach((key) -> {
                Object header = headers.get(key);
                if (header != null && header != JSONNull.getInstance()) {
                    newHeaders.put(key, header);
                }
            });
            return newHeaders;
        }
        
        public JSONObject getRequestHeader(TreeIconNode node, JSONObject headers) {
            if (node.info.testCase != null && node.info.testCase.containsKey("headers")) {
                JSONObject tcHeaders = node.info.testCase.getJSONObject("headers");
                tcHeaders.keySet().forEach((key) -> {
                    headers.put(key, tcHeaders.get(key));
                });
            }
            if (node.getType() == TEST_STEP_ITEM && node.info.testStep != null && node.info.testStep.containsKey("headers")) {
                JSONObject tsHeaders = node.info.testStep.getJSONObject("headers");
                tsHeaders.keySet().forEach((key) -> {
                    headers.put(key, tsHeaders.get(key));
                });
            }
            return headers;
        }

        public Object getResultStatus() {
            Properties props = properties.cloneProperties();
            if (testStep != null) {
                if (testStep.containsKey("request")) {
                    props.setTestStep(testStep.getJSONObject("request").getJSONObject("properties"));
                }
                if (testStep.containsKey("response")) {
                    props.setTestStep(testStep.getJSONObject("response").getJSONObject("properties"));
                }
                if (testStep.containsKey("properties")) {
                    props.setTestStep(testStep.getJSONObject("properties"));
                }
            }
            if (common != null) {
                props.setTestStep(common.getJSONObject("response").getJSONObject("properties"));
            }
            return props.replace("${#" + Profile.HTTP_STATUS_CODE + "}", null, false);
        }

        public String getRequestBody() throws Exception {
            JSONObject step = !IO.isNULL(testStep) ? testStep : new JSONObject();
            JSONObject request1 = (JSONObject) step.getOrDefault("request", new JSONObject());
            JSONObject request2 = testStepInfo.getJSONObject("request");
            JSONObject request = request1.containsKey("body") ? request1 : request2;
            String json = null;
            if (request.get("body") instanceof String) {
                String path = request.getString("body");
                File jsonFile = IO.getFile(path);
                if (jsonFile.exists()) {
                    Properties props = properties.cloneProperties();
                    if (testStepInfo.containsKey("request")) {
                        props.setTestStep((JSONObject) testStepInfo.getJSONObject("request").getOrDefault("properties", null));
                    }
                    if (testCase.containsKey("request")) {
                        props.setTestStep((JSONObject) testCase.getJSONObject("request").getOrDefault("properties", null));
                    }
                    if (step.containsKey("request")) {
                        props.setTestStep(step.getJSONObject("request").getJSONObject("properties"));
                    }
                    if (common != null) {
                        props.setTestStep(common.getJSONObject("request").getJSONObject("properties"));
                    }
                    json = IO.readFile(jsonFile);
                    json = IO.prettyJson(json);
                    json = props.replace(json);
                }
            }
            return json;
        }

        public String getResponseBody() throws Exception{
            JSONObject step = !IO.isNULL(testStep) ? testStep : new JSONObject();
            JSONObject response1 = (JSONObject) step.getOrDefault("response", new JSONObject());
            JSONObject response2 = testStepInfo.getJSONObject("response");
            JSONObject response = response1.containsKey("body") ? response1 : response2;
            String json = null;
            if (response.get("body") instanceof String) {
                String path = response.getString("body");
                File jsonFile = IO.getFile(path);
                if (jsonFile.exists()) {
                    Properties props = properties.cloneProperties();
                    if (testStepInfo.containsKey("response")) {
                        props.setTestStep(testStepInfo.getJSONObject("response").getJSONObject("properties"));
                    }
                    if (step.containsKey("response")) {
                        props.setTestStep(step.getJSONObject("response").getJSONObject("properties"));
                    }
                    json = IO.readFile(jsonFile);
                    json = IO.prettyJson(json);
                    json = props.replace(json);
                }
            }
            return json;
        }
    }

    static public class TreeIconUserObject {

        protected TreeCollection collection;
        protected String label;
        protected String fullPath;
        protected TreeCollection.TYPE type;
        protected Icon icon;
        protected boolean enabled;
        protected Object[][] properties;

        public JSON json;

        @SuppressWarnings("OverridableMethodCallInConstructor")
        public TreeIconUserObject(TreeCollection collection, String label, TreeCollection.TYPE type, boolean truncate, Object[][] properties) {
            this.collection = collection;
            this.type = type;
            this.enabled = true;
            this.type = type;
            this.properties = properties;
            this.setIcon(type);
            update(label, null, truncate);
        }

        public void setLabel(String value) {
            label = value;
        }

        public String getLabel() {
            return label;
        }

        public String getFullPath() {
            return fullPath;
        }

        public String getTooltip() {
            return fullPath;
        }

        public void setTooltip(String value) {
            fullPath = value;
        }

        public TreeCollection.TYPE getType() {
            return type;
        }

        public void setType(TreeCollection.TYPE value) {
            type = value;
        }

        public Icon getIcon() {
            return icon;
        }

        public void setIcon(Object iconType) {
            if (iconType != null) {
                loadIcon(iconType.toString().toLowerCase() + "_16.png");
            } else {
                icon = null;
            }
        }

        private void loadIcon(String iconName) {
            String path = "/com/equinix/amphibia/icons/" + iconName;
            if (ICONS.containsKey(path)) {
                icon = ICONS.get(path);
            } else {
                try {
                    icon = new ImageIcon(TreeIconNode.class.getResource(path));
                    ICONS.put(path, icon);
                } catch (Exception e) {
                    logger.log(Level.SEVERE, path, e);
                }
            }
        }

        public TreeCollection getCollection() {
            return collection;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public void update(String label, String fullPath, JSONObject json) {
            this.label = label;
            this.fullPath = fullPath;
            this.json = json;
        }

        public void update(String label, JSONObject json, boolean truncate) {
            this.update(truncate ? IO.newFile(label).getName() : label, label, json);
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public static class ProfileNode extends TreeIconNode {

        private JSONObject profileJSON;
        private boolean initialized;
        private Timer timer;

        ProfileNode(TreeCollection collection, String label, TreeCollection.TYPE type, boolean truncate, Object[][] properties) {
            super(collection, label, type, truncate, properties);
            initialized = false;
        }
        
        public void load(File dir) throws Exception {
            TreeCollection collection = getCollection();
            collection.loadProjectProfile();
            
            profileJSON = collection.getProjectProfile();

            if (!profileJSON.containsKey("states")) {
                JSONObject states = new JSONObject();
                states.element("project", getDefaultProjectStates());
                states.element("resources", closeState());
                states.element("interfaces", closeState());
                states.element("testsuites", openState());
                states.element("common", closeState());
                states.element("tests", closeState());
                states.element("schemas", closeState());
                states.element("requests", closeState());
                states.element("responses", closeState());
                profileJSON.element("states", states);
            }

            if (!Amphibia.isExpertView() || !profileJSON.containsKey("expandResources")) {
                profileJSON.element("expandResources", new JSONObject());
            }

            IO.write(IO.prettyJson(profileJSON.toString()), collection.getProfile());
            IO.copy(collection.getProfile(), collection.getBackupProfile());
            
            profileJSON = IO.toJSONObject(profileJSON.toString()); //clone
        }

        public void saveState(TreeIconNode node) {
            if (!Amphibia.instance.isVisible()) {
                return;
            }
            if (timer != null) {
                timer.cancel();
            }
            timer = new Timer();
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    try {
                        TreeCollection collection = node.getCollection();

                        JSONObject json = collection.profile.jsonObject();
                        IO.write(IO.prettyJson(json.toString()), collection.getBackupProfile());

                        //update states
                        TreeCollection.TYPE type = node.getType();

                        if (!initialized || type == TESTSUITE || type == TESTCASE || type == TEST_STEP_ITEM) {
                            initialized = true;
                            for (Object item1 : json.getJSONArray("testsuites")) {
                                JSONObject testsuite1 = (JSONObject) item1;
                                for (Object item2 : profileJSON.getJSONArray("testsuites")) {
                                    JSONObject testsuite2 = (JSONObject) item2;
                                    if (testsuite1.getString("name").equals(testsuite2.getString("name"))) {
                                        testsuite2.element("states", testsuite1.getOrDefault("states", getDefaultTestSuiteStates()));
                                        for (Object item3 : testsuite1.getJSONArray("testcases")) {
                                            JSONObject testcase1 = (JSONObject) item3;
                                            for (Object item4 : testsuite2.getJSONArray("testcases")) {
                                                JSONObject testcase2 = (JSONObject) item4;
                                                if (testcase1.getString("name").equals(testcase2.getString("name"))) {
                                                    testcase2.element("states", testcase1.getOrDefault("states", getDefaultTestCaseStates()));
                                                    for (Object item5 : testcase1.getJSONArray("steps")) {
                                                        JSONObject step1 = (JSONObject) item5;
                                                        for (Object item6 : testcase2.getJSONArray("steps")) {
                                                            JSONObject step2 = (JSONObject) item6;
                                                            if (step1.getString("name").equals(step2.getString("name"))) {
                                                                step2.element("states", step1.getOrDefault("states", getDefaultTestStepStates()));
                                                                break;
                                                            }
                                                        }
                                                    }
                                                    break;
                                                }
                                            }
                                        }
                                        break;
                                    }
                                }
                            }
                        }

                        for (Object item1 : json.getJSONArray("resources")) {
                            JSONObject resource1 = (JSONObject) item1;
                            for (Object item2 : profileJSON.getJSONArray("resources")) {
                                JSONObject resource2 = (JSONObject) item2;
                                if (resource1.getString("id").equals(resource2.getString("id"))) {
                                    resource2.element("states", resource1.getJSONArray("states"));
                                    break;
                                }
                            }
                        }

                        profileJSON.element("states", json.getJSONObject("states"));
                        profileJSON.element("expandResources", json.getJSONObject("expandResources"));
                        IO.write(IO.prettyJson(profileJSON.toString()), collection.getProfile());
                    } catch (Exception ex) {
                        logger.log(Level.SEVERE, ex.toString(), ex);
                    }
                }
            }, 100);
        }
    }
    
    public static int[] openState() {
        return new int[] {1};
    }
    
    public static int[] closeState() {
        return new int[] {0};
    }
    
    public static int[] getDefaultProjectStates() {
        return new int[] {1, 1, 0, 1}; //STATE_OPEN_CLOSE, STATE_DEBUG_EXPAND, STATE_DEBUG_REPORT, STATE_OPEN_PROJECT_OR_WIZARD_TAB
    }
    
    public static int[] getDefaultTestSuiteStates() {
        return new int[] {0, 0, 0}; //STATE_OPEN_CLOSE, STATE_DEBUG_EXPAND, STATE_DEBUG_REPORT
    }
    
    public static int[] getDefaultTestCaseStates() {
        return new int[] {0, 0, 0, 0, 0}; //STATE_OPEN_CLOSE, STATE_DEBUG_EXPAND, STATE_DEBUG_REPORT, STATE_OPEN_PROJECT_OR_WIZARD_TAB, STATE_HIDE_COMMONS
    }
    
    public static int[] getDefaultTestStepStates() {
        return new int[] {-1, -1, 0, 0}; //STATE_OPEN_CLOSE, STATE_DEBUG_EXPAND, STATE_DEBUG_REPORT, STATE_OPEN_PROJECT_OR_WIZARD_TAB
    }
}