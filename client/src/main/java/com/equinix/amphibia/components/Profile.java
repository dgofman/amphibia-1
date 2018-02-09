/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.equinix.amphibia.components;

import com.equinix.amphibia.Amphibia;
import com.equinix.amphibia.HttpConnection;
import com.equinix.amphibia.IHttpConnection;
import com.equinix.amphibia.IO;
import java.awt.Color;
import java.awt.Desktop;

import java.awt.EventQueue;
import java.awt.Rectangle;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringEscapeUtils;

import org.apache.tools.ant.Project;
import org.apache.tools.ant.Target;
import org.apache.tools.ant.taskdefs.optional.junit.AggregateTransformer;
import org.apache.tools.ant.taskdefs.optional.junit.XMLResultAggregator;
import org.apache.tools.ant.types.FileSet;

/**
 *
 * @author dgofman
 */
public final class Profile extends BaseTaskPane implements IHttpConnection {

    private boolean isRunning;
    private boolean includeSkippedTests;
    private boolean autoSwitchConsole;
    private Editor editor;
    private Thread currentThread;
    private TreeIconNode selectedNode;
    private DefaultTreeModel treeModel;
    private HttpConnection connection;
    private Map<Thread, Boolean> threads;
    private boolean continueOnError;
    private int lineIndex;

    private final SimpleDateFormat reportDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
    public static Color BLUE = Color.BLUE;
    public static Color RED = Color.RED;
    public static Color GREEN = new Color(40, 130, 10);

    public Profile(MainPanel mainPanel, Editor editor) {
        super();
        this.editor = editor;
        this.mainPanel = mainPanel;
        this.tree = mainPanel.debugTreeNav;
        this.treeModel = mainPanel.debugTreeModel;
        this.threads = new HashMap<>();
        this.connection = new HttpConnection(this);
    }

    @Override
    public DefaultMutableTreeNode addWarning(String warning) {
        return editor.addToTree(editor.warnings, warning);
    }

    @Override
    public DefaultMutableTreeNode addError(String error) {
        return editor.addToTree(editor.errors, error);
    }
    
    public Profile addToConsole(String text, Color color, boolean isBold) {
        SimpleAttributeSet attr = new SimpleAttributeSet();
        StyleConstants.setForeground(attr, color);
        StyleConstants.setBold(attr, isBold);
        return addToConsole(text, attr);
    }

    @SuppressWarnings("SleepWhileInLoop")
    public Profile addToConsole(String text, SimpleAttributeSet attr) {
        if (text != null && !text.isEmpty()) {
            StyledDocument doc = editor.txtConsole.getStyledDocument();
            if (autoSwitchConsole) {
                editor.activateTab(Amphibia.TAB_CONSOLE, Amphibia.instance.mnuConsole);
            }

            try {
                lineIndex = getContextHeight();
                doc.insertString(doc.getLength(), text, attr);
                if (text.split("\n").length > 1) {
                    int newLineIndex = getContextHeight();
                    do {
                        lineIndex = newLineIndex;
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException ex) {
                        }
                        newLineIndex = getContextHeight();
                    } while (lineIndex != newLineIndex);
                }
            } catch (BadLocationException ex) {
                logger.log(Level.SEVERE, ex.toString(), ex);
            }
        }
        return this;
    }

    private int getContextHeight() {
        try {
            return editor.txtConsole.getUI().getMinimumSize(this).height;
        } catch (Exception e) {
            return lineIndex;
        }
    }

    public Profile date() {
        return addToConsole("\n" + dateMediumFormat.print(new Date().getTime()) + "\n\n", Color.BLACK, true);
    }

    @Override
    public Profile info(String text) {
        return info(text, false);
    }

    @Override
    public Profile info(String text, boolean isBold) {
        return addToConsole(text, Color.BLACK, isBold);
    }
    
    @Override
    public Profile info(String text, Color Color) {
        return addToConsole(text, Color, false);
    }
    
    @Override
    public Profile info(String text, SimpleAttributeSet attr) {
        return addToConsole(text, attr);
    }

    public void runTests() {
        TreeIconNode node = MainPanel.selectedNode;
        if (node == null) {
            return;
        }
        resetConsole();
        threads.clear();
        isRunning = true;
        mainPanel.reportTreeNode.removeAllChildren();
        mainPanel.reportTreeModel.reload();

        autoSwitchConsole = userPreferences.getBoolean(Amphibia.P_SWITCH_CONSOLE, true);
        includeSkippedTests = userPreferences.getBoolean(Amphibia.P_SKIPPED_TEST, true);
        continueOnError = userPreferences.getBoolean(Amphibia.P_CONTINUE_ON_ERROR, true);

        TreeIconNode projectNode = node.getCollection().project.debugNode;

        //Reset all nodes
        Enumeration children = projectNode.preorderEnumeration();
        while (children.hasMoreElements()) {
            TreeIconNode child = (TreeIconNode) children.nextElement();
            child.setReportState(TreeIconNode.REPORT_INIT_STATE);
            if (child.getType() == TreeCollection.TYPE.TEST_STEP_ITEM || child.getType() == TreeCollection.TYPE.TESTCASE) {
                child.jsonObject().remove("error");
                child.jsonObject().remove("time");
                child.jsonObject().remove("timestamp");
            }
        }

        if (tree.getSelectionPath() != null) {
            selectedNode = (TreeIconNode) tree.getSelectionPath().getLastPathComponent();
            //Set run state
            selectedNode.setReportState(TreeIconNode.REPORT_RUN_STATE);
            children = selectedNode.preorderEnumeration();
            while (children.hasMoreElements()) {
                TreeIconNode child = (TreeIconNode) children.nextElement();
                child.setReportState(TreeIconNode.REPORT_RUN_STATE);
                tree.expandPath(new TreePath(child.getPath()));
                treeModel.nodeStructureChanged(child);
            }

            startThread();
        }
    }

    public void stopTests() {
        if (selectedNode == null) {
            return;
        }
        if (connection.urlConnection() != null) {
            connection.disconnect();
        }
        threads.clear();

        Enumeration children = selectedNode.preorderEnumeration();
        while (children.hasMoreElements()) {
            TreeIconNode child = (TreeIconNode) children.nextElement();
            if (child.getReportState() == TreeIconNode.REPORT_RUN_STATE) {
                child.setReportState(TreeIconNode.REPORT_SKIPPED_STATE);
            }
        }
        IO.write(MainPanel.selectedNode.getCollection().profile, Profile.this);

        selectedNode = null;
        isRunning = false;
        Amphibia.instance.btnPause.setSelected(false);
    }

    public void resetConsole() {
        lineIndex = 0;
        editor.txtConsole.setText("");
        if (MainPanel.selectedNode != null) {
            TreeIconNode.ProfileNode profile = MainPanel.selectedNode.getCollection().profile;
            JSONArray testsuites = profile.jsonObject().getJSONArray("testsuites");
            testsuites.forEach((testsuite) -> {
                JSONArray testcases = ((JSONObject) testsuite).getJSONArray("testcases");
                testcases.forEach((item) -> {
                    JSONObject testcase = (JSONObject) item;
                    testcase.remove("line");
                    testcase.getJSONArray("steps").forEach((step) -> {
                        ((JSONObject) step).remove("line");
                    });
                });
            });
            mainPanel.saveNodeValue(profile);
        }
    }

    public void pauseResumeTests(boolean isPaused) {
        if (selectedNode == null) {
            Amphibia.instance.btnPause.setSelected(false);
            return;
        }
        threads.put(currentThread, false);
        isRunning = !isPaused;
        if (isRunning) {
            startThread();
        }
    }

    public void generateJUnitReport() throws IOException {
        if (MainPanel.selectedNode == null) {
            return;
        }
        TreeCollection collection = MainPanel.selectedNode.getCollection();
        TreeIconNode profile = collection.profile;
        JSONArray testsuites = profile.jsonObject().getJSONArray("testsuites");
        File dir = IO.getFile(collection, "reports");
        if (!dir.exists()) {
            dir.mkdirs();
        }

        PrintWriter pw = new PrintWriter(new FileOutputStream(IO.newFile(dir, "junit-noframes.xml"), false));
        pw.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        pw.println("<testsuites>");

        for (int i = 0; i < testsuites.size(); i++) {
            final int index = i;
            final double[] data = {
                0, //0 - time
                0, //1 - tests
                0, //2 - skipped
                0, //3 - errors
                0 //4 - failures (asserts)
            };
            JSONObject testsuite = testsuites.getJSONObject(index);
            JSONArray testcases = testsuite.getJSONArray("testcases");
            StringBuilder sb = new StringBuilder();
            String testSuiteName = testsuite.getString("name");
            testcases.forEach((tc) -> {
                JSONObject testcase = (JSONObject) tc;
                String classname = testcase.getString("name");
                if (testcase.containsKey("steps") && testcase.getJSONArray("steps").size() > 0) {
                    JSONArray teststeps = testcase.getJSONArray("steps");
                    teststeps.forEach((ts) -> {
                        JSONObject step = (JSONObject) ts;
                        sb.append(addTestCaseInfo(data, testSuiteName, classname, step.getString("name"), step));
                    });
                } else {
                    sb.append(addTestCaseInfo(data, testSuiteName, classname, "", testcase));
                }
            });

            StringBuilder sb2 = new StringBuilder();
            sb2.append("\t<testsuite time=\"")
                    .append(data[0]).append("\" tests=\"")
                    .append(data[1]).append("\" skipped=\"")
                    .append(data[2]).append("\" errors=\"")
                    .append(data[3]).append("\" failures=\"")
                    .append(data[4]).append("\" id=\"")
                    .append(index).append("\" name=\"")
                    .append(testSuiteName)
                    .append("\" package=\"")
                    .append(collection.getProjectName())
                    .append("\" timestamp=\"")
                    .append(testsuite.getOrDefault("timestamp", 0))
                    .append("\">\n");
            sb2.append(sb.toString());
            sb2.append("\t</testsuite>");

            pw.println(sb2.toString());

            File xmlFile = IO.newFile(dir, "TEST-" + testsuite.get("name") + ".xml");
            PrintWriter xmlPw = new PrintWriter(new FileOutputStream(xmlFile, false));
            xmlPw.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
            xmlPw.println(sb2.toString());
            xmlPw.close();
        }
        pw.println("</testsuites>");
        pw.close();

        System.setProperty("PROJECT_NAME", collection.getProjectName());

        Project project = new Project();
        project.init();

        Target target = new Target();
        target.setName("junitreport");
        project.addTarget(target);

        FileSet fs = new FileSet();
        fs.setDir(dir);
        fs.createInclude().setName("TEST-*.xml");

        AggregateTransformer.Format format = new AggregateTransformer.Format();
        format.setValue(AggregateTransformer.NOFRAMES);

        XMLResultAggregator aggregator = new XMLResultAggregator();
        aggregator.addFileSet(fs);
        aggregator.setProject(project);
        aggregator.setTodir(dir);

        AggregateTransformer transformer = aggregator.createReport();
        transformer.setFormat(format);
        transformer.setStyledir(IO.newFile("../resources"));
        transformer.setTodir(dir);

        target.addTask(aggregator);
        project.executeTarget("junitreport");

        Desktop desktop = Desktop.getDesktop();
        desktop.open(dir);
    }

    private String addTestCaseInfo(double[] data, String testSuiteName, String className, String name, JSONObject result) {
        if (result.containsKey("states")) {
            String info = "\n\t\t\t";

            switch (result.getJSONArray("states").getInt(2)) {
                case TreeIconNode.REPORT_PASSED_STATE:
                    if (result.containsKey("error")) {
                        JSONArray err = result.getJSONArray("error");
                        info += "<system-err><![CDATA[" + err.get(2) + "\n\n" + err.get(3) + "]]></system-err>";
                    } else {
                        info = "";
                    }
                    break;
                case TreeIconNode.REPORT_SKIPPED_STATE:
                    info += "<skipped message=\"\"/>";
                    data[2]++;
                    break;
                case TreeIconNode.REPORT_ERROR_STATE:
                    JSONArray err = result.getJSONArray("error");
                    info += "<error message=\"" + StringEscapeUtils.escapeXml(err.getString(0)) + "\" type=\"" + err.get(1) + "\"><![CDATA[" + err.get(2) + "\n\n" + err.get(3) + "]]></error>";
                    data[3]++;
                    break;
                case TreeIconNode.REPORT_FAILED_STATE:
                    JSONArray fail = result.getJSONArray("error");
                    info += "<failure message=\"" + StringEscapeUtils.escapeXml(fail.getString(0)) + "\" type=\"" + fail.get(1) + "\"><![CDATA[" + fail.get(2) + "\n\n" + fail.get(3) + "]]></failure>";
                    data[4]++;
                    break;
                default:
                    return "";
            }

            double time = result.containsKey("time") ? result.getDouble("time") / 1000 : 0;
            data[0] += time;
            data[1]++;

            return "\t\t<testcase parent=\"" + testSuiteName + "\" classname=\"" + className + "\" name=\"" + name + "\" time=\"" + time + "\">" + info + "\n\t\t</testcase>\n";
        }
        return "";
    }

    public void openReport() {
        resetConsole();
        if (MainPanel.selectedNode == null) {
            return;
        }
        mainPanel.reportTreeNode.removeAllChildren();
        TreeCollection collection = MainPanel.selectedNode.getCollection();
        TreeIconNode profile = collection.profile;
        JSONArray testsuites = profile.jsonObject().getJSONArray("testsuites");
        testsuites.forEach((ts) -> {
            JSONArray testcases = ((JSONObject) ts).getJSONArray("testcases");
            testcases.forEach((tc) -> {
                JSONObject testcase = (JSONObject) tc;
                boolean addTascase = testcase.containsKey("time");
                TreeIconNode testCaseNode = new TreeIconNode(collection, testcase.getString("name"), TreeCollection.TYPE.TESTCASE, false).addJSON(testcase);
                if (testcase.containsKey("error")) {
                    List<String> err = new ArrayList<>();
                    testcase.getJSONArray("error").forEach((val) -> {
                        err.add(String.valueOf(val));
                    });
                    testCaseNode.add(new TreeIconNode(collection, String.join("\n", err), null, false).addType(TreeCollection.TYPE.ERRORS));
                }
                JSONArray steps = testcase.getJSONArray("steps");
                for (int i = 0; i < steps.size(); i++) {
                    JSONObject step = steps.getJSONObject(i);
                    if (step.containsKey("time")) {
                        addTascase = true;
                        TreeIconNode testStepNode = new TreeIconNode(collection, step.getString("name"), null, false)
                                .addType(TreeCollection.TYPE.TEST_STEP_ITEM)
                                .addJSON(step);
                        testCaseNode.add(testStepNode);
                        if (step.containsKey("error")) {
                            List<String> err = new ArrayList<>();
                            step.getJSONArray("error").forEach((val) -> {
                                err.add(String.valueOf(val));
                            });
                            testStepNode.add(new TreeIconNode(collection, String.join("\n", err), null, false).addType(TreeCollection.TYPE.ERRORS));
                        }
                    }
                }
                if (addTascase) {
                    mainPanel.reportTreeNode.add(testCaseNode);
                }
            });
        });
        mainPanel.reportTreeModel.reload();
    }

    public boolean isRunning() {
        return isRunning;
    }

    private boolean isRunning(Thread thread) {
        if (!threads.containsKey(thread)) {
            return false;
        }
        return threads.get(thread);
    }

    private void startThread() {
        currentThread = new Thread() {
            @Override
            public void run() {
                Thread thread = currentThread;
                setTimestamp(selectedNode);
                executeTest(thread, null, selectedNode, selectedNode.getReportState());
                if (isRunning(thread)) {
                    stopTests();
                    mainPanel.reloadAll(false);
                }
            }
        };
        threads.put(currentThread, true);
        currentThread.start();
    }

    private void setTimestamp(TreeIconNode node) {
        if (node.info != null && node.info.testSuiteInfo != null) {
            node.info.testSuite.element("timestamp", reportDateFormat.format(new Date()));
        }
    }

    private synchronized int executeTest(Thread thread, TreeIconNode parent, TreeIconNode node, int resultState) {
        TreeIconNode.ResourceInfo info = node.info;
        EventQueue.invokeLater(() -> {
            Rectangle rect = tree.getPathBounds(new TreePath(node.getPath()));
            if (rect != null) {
                rect.x = 0;
                try {
                    tree.scrollRectToVisible(rect);
                } catch (Exception e) {
                }
            }
        });

        if (node.getType() == TreeCollection.TYPE.PROJECT) {
            resultState = TreeIconNode.REPORT_RUN_STATE;
        }

        if (node.getType() == TreeCollection.TYPE.TESTSUITE) {
            setTimestamp(node);
        }

        if (node.source != null && node.source.getTreeIconUserObject().json != null) {
            if (node.source.jsonObject().getOrDefault("disabled", false) == Boolean.TRUE) {
                resultState = TreeIconNode.REPORT_SKIPPED_STATE;
            }
        }

        Enumeration children = node.children();
        if (isRunning(thread) && !children.hasMoreElements()
                && (node.getType() == TreeCollection.TYPE.TEST_STEP_ITEM || node.getType() == TreeCollection.TYPE.TESTCASE)) {
            TreeIconNode parentNode = parent;
            if (parentNode == null) {
                parentNode = mainPanel.reportTreeNode;
            }
            if (includeSkippedTests && resultState == TreeIconNode.REPORT_SKIPPED_STATE) {
                node.jsonObject().element("time", 0);
                parentNode.add(node.cloneNode());
            } else if (resultState == TreeIconNode.REPORT_RUN_STATE) {
                node.setReportState(TreeIconNode.REPORT_RUNNING_STATE);
                treeModel.nodeStructureChanged(node);
                JSONObject json = node.jsonObject();
                JSONObject sourceJSON = node.source.jsonObject();
                HttpConnection.Result result = new HttpConnection.Result();
                int startIndex = lineIndex;
                String name = sourceJSON.getString("name");
                try {
                    date();
                    result = connection.request(name, sourceJSON.getString("method"), node.source);
                } catch (Exception e) {
                    connection.addError(result, name, e);
                }
                info.consoleLine = Math.max(0, startIndex);
                json.element("time", result.time);
                json.element("line", info.consoleLine);

                if (result.exception == null) {
                    resultState = TreeIconNode.REPORT_PASSED_STATE;
                    parentNode.add(node.cloneNode().addJSON(json));
                } else {
                    String[] error = result.createError();
                    json.element("error", error);
                    resultState = TreeIconNode.REPORT_ERROR_STATE;
                    TreeIconNode child = node.cloneNode().addJSON(json);
                    parentNode.add(child);
                    child.add(new TreeIconNode(child.getCollection(), String.join("\n", error), null, false).addType(TreeCollection.TYPE.ERRORS));
                    if (!continueOnError) {
                        stopTests();
                    }
                }
                
                Object expected = info.getResultStatus();
                if (expected != null && result.statusCode == Integer.parseInt(expected.toString())) {
                    resultState = TreeIconNode.REPORT_PASSED_STATE;
                }
            }
            EventQueue.invokeLater(() -> {
                mainPanel.reportTreeModel.reload(mainPanel.reportTreeNode);
            });
        } else {
            final TreeIconNode parentNode = (node.getType() == TreeCollection.TYPE.TESTCASE) ? node.cloneNode() : null;
            if (parentNode != null) {
                EventQueue.invokeLater(() -> {
                    mainPanel.reportTreeNode.add(parentNode);
                });
            }
            while (isRunning(thread) && children.hasMoreElements()) {
                TreeIconNode child = (TreeIconNode) children.nextElement();
                int childResultState = resultState == TreeIconNode.REPORT_SKIPPED_STATE ? resultState : child.getReportState();
                childResultState = executeTest(thread, parentNode, child, childResultState);
                if (resultState == TreeIconNode.REPORT_RUN_STATE && childResultState == TreeIconNode.REPORT_ERROR_STATE) {
                    resultState = childResultState;
                }
                if (parentNode != null) {
                    parentNode.setReportState(resultState);
                    EventQueue.invokeLater(() -> {
                        mainPanel.reportTreeModel.nodeChanged(parentNode);
                    });
                }
            }
            if (resultState == TreeIconNode.REPORT_RUN_STATE) {
                resultState = TreeIconNode.REPORT_PASSED_STATE;
            }
            if (!isRunning(thread)) {
                return resultState;
            }
        }

        node.setReportState(resultState);
        EventQueue.invokeLater(() -> {
            treeModel.nodeStructureChanged(node);
        });
        return resultState;
    }
}
