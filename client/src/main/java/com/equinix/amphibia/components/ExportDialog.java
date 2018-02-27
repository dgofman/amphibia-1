/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.equinix.amphibia.components;

import com.equinix.amphibia.Amphibia;
import com.equinix.amphibia.IO;
import com.equinix.amphibia.agent.converter.Converter;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.ResourceBundle;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.ToolTipManager;
import javax.swing.UIManager;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeCellEditor;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

/**
 *
 * @author dgofman
 */
public final class ExportDialog extends javax.swing.JPanel {

    private final DefaultTreeModel treeModel;
    private final DefaultMutableTreeNode rootNode;
    private JDialog dialog;
    private JButton okButton;
    private JButton cancelButton;
    private MainPanel mainPanel;

    /**
     * Creates new form JDialogTest
     *
     * @param mainPanel
     */
    public ExportDialog(MainPanel mainPanel) {
        this.mainPanel = mainPanel;

        initComponents();

        rootNode = new DefaultMutableTreeNode("Root");
        treeModel = new DefaultTreeModel(rootNode);

        treeCustom.setModel(treeModel);
        treeCustom.setRowHeight(20);
        treeCustom.setRootVisible(false);
        treeCustom.setShowsRootHandles(true);
        treeCustom.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        treeCustom.setEditable(true);
        treeCustom.setCellRenderer(new CheckBoxRenderer(treeCustom));
        treeCustom.setCellEditor(new CheckBoxRenderer(treeCustom));

        ToolTipManager tooltip = ToolTipManager.sharedInstance();
        tooltip.setInitialDelay(200);
        tooltip.registerComponent(treeCustom);

        okButton = new JButton(UIManager.getString("OptionPane.okButtonText"));
        okButton.addActionListener((ActionEvent evt) -> {
            JFileChooser jc = Amphibia.setFileChooserDir(new JFileChooser());
            jc.setFileFilter(new FileNameExtensionFilter("Rule-Properties JSON File", "json", "text"));
            jc.setSelectedFile(IO.getFile("rules-properties.json"));
            int rVal = jc.showSaveDialog(null);
            if (rVal != JFileChooser.CANCEL_OPTION) {
                try {
                    JSONObject json = (JSONObject) IO.getJSON(IO.getResources("rules_properties_template.json"));
                    buildProperties(json);
                    IO.write(IO.prettyJson(json.toString()), jc.getSelectedFile());
                    Desktop desktop = Desktop.getDesktop();
                    desktop.open(jc.getSelectedFile());
                } catch (Exception ex) {
                    mainPanel.addError(ex);
                }
            }
            dialog.setVisible(false);
        });
        cancelButton = new JButton(UIManager.getString("OptionPane.cancelButtonText"));
        cancelButton.addActionListener((ActionEvent evt) -> {
            dialog.setVisible(false);
        });
        dialog = Amphibia.createDialog(this, new Object[]{okButton, cancelButton}, true);
        dialog.setSize(new Dimension(500, 600));
        java.awt.EventQueue.invokeLater(() -> {
            dialog.setLocationRelativeTo(mainPanel);
        });

        rbnParentLevel.setSelected(true);
    }
    
    private void buildProperties(JSONObject json) {
        boolean isTestSuite = rbnParentLevel.isSelected() && chbTestSuite.isSelected();
        boolean isTestCase = rbnParentLevel.isSelected() && chbTestCase.isSelected();
        boolean isTestStep = rbnParentLevel.isSelected() && chbTestStep.isSelected();
        boolean isCommon = rbnParentLevel.isSelected() && chbCommon.isSelected();
        boolean isTests = rbnParentLevel.isSelected() && chbTests.isSelected();
        
        TreeCollection collection = MainPanel.selectedNode.getCollection();
        JSONObject project = collection.project.jsonObject();
        JSONObject profile = collection.getProjectProfile();

        JSONObject infoNode = json.getJSONObject("info");
        infoNode.put("name", collection.getProjectName());
        infoNode.put("directory", collection.getProjectDir().getAbsolutePath());
        infoNode.put("environment", Amphibia.getUserPreferences().get(Amphibia.P_SELECTED_ENVIRONMENT, null));
        JSONArray resources = new JSONArray();
        JSONArray interfaces = project.getJSONArray("interfaces");
        profile.getJSONArray("resources").forEach((item1) -> {
            JSONObject resource = (JSONObject) item1;
            JSONObject headers = new JSONObject();
            
            interfaces.forEach((item2) ->  {
                JSONObject itf = (JSONObject) item2;
                if (itf.getString("id").equals(resource.get("interface"))) {
                    if (itf.containsKey("headers")) {
                        headers.putAll(itf.getJSONObject("headers"));
                    }
                }
            });
            
            if (resource.containsKey("headers")) {
                headers.putAll(resource.getJSONObject("headers"));
            }
            
            resources.add(new LinkedHashMap<Object, Object>() {{
                put("type", resource.getOrDefault("type", null));
                put("source", resource.getOrDefault("source", null));
                put("headers", headers);
            }});
        });
        infoNode.put("resources", resources);
        
        JSONObject endpoints = json.getJSONObject("endpoints");
        endpoints.clear();
        JSONObject globals = json.getJSONObject("globalProperties");
        globals.clear();
        
        Object[][] vars = GlobalVariableDialog.getGlobalVarData();
        int columnIndex = Amphibia.instance.getSelectedEnvDataIndex();
        for (Object[] data : vars) {
            Object value = IO.isNULL(data[columnIndex], null);
            if (Converter.ENDPOINT.equals(data[0])) {
                endpoints.put(data[1], value);
            } else {
                globals.put(data[1], value);
            }
        }
        
        json.getJSONObject("projectProperties").putAll(project.getJSONObject("projectProperties"));
        json.getJSONObject("projectProperties").putAll(profile.getJSONObject("properties"));
        
        JSONObject projectTestSuites = new JSONObject();
        project.getJSONArray("projectResources").forEach((item) -> {
            JSONObject resource = (JSONObject) item;
            projectTestSuites.putAll(resource.getJSONObject("testsuites"));
        });
        
        JSONObject testSuitesRules = json.getJSONObject("testsuites");
        CheckBoxNode testsuites = (CheckBoxNode) rootNode.getChildAt(0);
        CheckBoxNode commons = (CheckBoxNode) rootNode.getChildAt(1);
        Enumeration children = testsuites.children();
        while (children.hasMoreElements()) {
            CheckBoxNode testsuiteNode = (CheckBoxNode) children.nextElement();
            JSONObject testCases = new JSONObject();
            Enumeration testcases = testsuiteNode.children();
            while (testcases.hasMoreElements()) {
                CheckBoxNode testcaseNode = (CheckBoxNode) testcases.nextElement();
                JSONArray testSteps = new JSONArray();
                Enumeration teststeps = testcaseNode.children();
                while (teststeps.hasMoreElements()) {
                    CheckBoxNode teststepNode = (CheckBoxNode) teststeps.nextElement();
                    if (isTestStep || teststepNode.getActionIndex() != CheckBoxNode.UNSELECT) {
                        TreeIconNode.ResourceInfo info = teststepNode.getInfo();
                        JSONObject testStep = copyValues(info.testStep, info.testCase.getString("path").split("/"));
                        if (testStep.containsKey("common") && !testStep.getString("name").equals(testStep.getString("common"))) {
                            testSteps.add(testStep);
                        } else {
                            JSONObject clone = IO.toJSONObject(testStep);
                            clone.remove("name");
                            clone.remove("common");
                            if (!clone.isEmpty()) {
                                testSteps.add(testStep);
                            }
                        }
                    }
                }
                
                String path = testcaseNode.getInfo().testCase.getString("path");
                String[] arr = path.split("/");
                String testSuiteName = arr[3];
                String testCaseName = arr[4].split(".json")[0];
                JSONObject projectTestSuite = (JSONObject) projectTestSuites.getOrDefault(testSuiteName, new JSONObject());
                JSONObject testCase = copyValues(testcaseNode.getInfo().testCase, arr);
                if (isTestCase || testcaseNode.getActionIndex() != CheckBoxNode.UNSELECT) {
                    JSONObject headers = new JSONObject();
                    JSONObject properties = new JSONObject();

                    for (Object item : projectTestSuite.getJSONArray("testcases")) {
                        JSONObject testcase = (JSONObject) item;
                        if (testCaseName.equals(testcase.getString("name"))) {
                            headers.putAll((JSONObject) testcase.getOrDefault("headers", new JSONObject()));
                            properties.putAll((JSONObject) testcase.getOrDefault("properties", new JSONObject()));
                            properties.remove("HTTPStatusCode");
                            break;
                        }
                    }
                    
                    headers.putAll((JSONObject) testCase.getOrDefault("headers", new JSONObject()));
                    if (!headers.isEmpty()) {
                        testCase.put("headers", headers);
                    }

                    properties.putAll((JSONObject) testCase.getOrDefault("properties", new JSONObject()));
                    if (!properties.isEmpty()) {
                        testCase.put("properties", properties);
                    }
                }
                if (!testSteps.isEmpty()) {
                    testCase.put("steps", testSteps);
                }
                
                if (testCaseName.equals(testCase.getString("name"))) {
                    testCase.remove("name");
                }

                if (!testCase.isEmpty()) {
                    testCases.put(testCaseName, testCase);
                }
                
                if (isTests) {
                    JSONObject tests = json.getJSONObject("tests");
                    JSONObject suiteJSON = (JSONObject) tests.getOrDefault(testSuiteName, new JSONObject()) ;
                    try {
                        JSONObject test = copyValues((JSONObject) IO.getJSON(path), path.split("/"));
                        if (test.getJSONObject("response").getJSONArray("asserts").isEmpty()) {
                            test.getJSONObject("response").remove("asserts");
                        }
                        suiteJSON.put(testCaseName, test);
                    } catch (Exception ex) {
                        mainPanel.addError(ex);
                    }
                    tests.put(testSuiteName, suiteJSON);
                }
            }
               
            JSONObject testSuite = new JSONObject();
            JSONObject original = testsuiteNode.getInfo().testSuite;
            if (isTestSuite || testsuiteNode.getActionIndex() != CheckBoxNode.UNSELECT) {
                JSONObject properties = new JSONObject();
                JSONObject projectTestSuite = (JSONObject) projectTestSuites.getOrDefault(original.getString("name"), new JSONObject());
                properties.putAll((JSONObject) projectTestSuite.getOrDefault("properties", new JSONObject()));
                properties.putAll((JSONObject) original.getOrDefault("properties", new JSONObject()));
                if (!properties.isEmpty()) {
                    testSuite.put("properties", properties);
                }
            }
            if (!testCases.isEmpty()) {
                testSuite.put("testcases", testCases);
            }
            if (!testSuite.isEmpty()) {
                testSuitesRules.put(original.getString("name"), testSuite);
            }
        }

        JSONObject commonsNode = json.getJSONObject("commons");
        children = commons.children();
        while (children.hasMoreElements()) {
            CheckBoxNode commonNode = (CheckBoxNode) children.nextElement();
            if (isCommon || commonNode.getActionIndex() != CheckBoxNode.UNSELECT) {
                commonsNode.put(commonNode.getLabel(), commonNode.getJSON());
            }
        }
    }
    
    private JSONObject copyValues(JSONObject source, String[] path) {
        JSONObject target = new JSONObject();
        target.put("name", source.get("name"));
        if (source.containsKey("common")) {
            target.put("common", source.get("common"));
        }
        if (source.containsKey("headers")) {
            target.put("headers", source.get("headers"));
        }
        if (source.containsKey("properties")) {
            target.put("properties", source.get("properties"));
        }
        if (source.containsKey("transfer")) {
            target.put("transfer", source.get("transfer"));
        }
        if (source.containsKey("request")) {
            JSONObject request = source.getJSONObject("request");
            JSONObject targetRequest = new JSONObject();
            String body = String.format("data/%s/requests/%s/%s", path[1], path[3], path[4]);
            if (request.containsKey("body") && !body.equals(request.getString("body"))) {
                targetRequest.put("body", request.getString("body") == null ? null : request.getString("body").replace(String.format("data/%s/", path[1]), "data/%s/"));
            }
            if (request.containsKey("properties")) {
                targetRequest.put("properties", request.get("properties"));
            }
            if (!targetRequest.isEmpty()) {
                target.put("request", targetRequest);
            }
        }
        if (source.containsKey("response")) {
            JSONObject response = source.getJSONObject("response");
            JSONObject targetResponse = new JSONObject();
            String body = String.format("data/%s/responses/%s/%s", path[1], path[3], path[4]);
            if (response.containsKey("body") && !body.equals(response.getString("body"))) {
                targetResponse.put("body", response.getString("body") == null ? null : response.getString("body").replace(String.format("data/%s/", path[1]), "data/%s/"));
            }
            if (response.containsKey("properties")) {
                targetResponse.put("properties", response.get("properties"));
            }
            if (response.containsKey("asserts")) {
                targetResponse.put("asserts", response.get("asserts"));
            }
            if (response.containsKey("transfer")) {
                targetResponse.put("transfer", response.get("transfer"));
            }
            if (!targetResponse.isEmpty()) {
                target.put("response", targetResponse);
            }
        }
        return target;
    }

    public void openDialog() {
        rootNode.removeAllChildren();
        TreeCollection collection = MainPanel.selectedNode.getCollection();
        CheckBoxNode node = CheckBoxNode.createChildren(collection.testsuites);
        rootNode.add(node);
        rootNode.add(CheckBoxNode.createChildren(collection.common));
        treeModel.reload(rootNode);
        treeCustom.expandPath(new TreePath(node.getPath()));
        dialog.setVisible(true);
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        btgLevels = new ButtonGroup();
        pnlTop = new JPanel();
        rbnParentLevel = new JRadioButton();
        chbTestSuite = new JCheckBox();
        chbTestCase = new JCheckBox();
        chbTestStep = new JCheckBox();
        chbCommon = new JCheckBox();
        chbTests = new JCheckBox();
        rbnCustom = new JRadioButton();
        spnCustom = new JScrollPane();
        treeCustom = new JTree();

        setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        setLayout(new BorderLayout());

        ResourceBundle bundle = ResourceBundle.getBundle("com/equinix/amphibia/messages"); // NOI18N
        pnlTop.setBorder(BorderFactory.createTitledBorder(bundle.getString("exportProperties"))); // NOI18N
        pnlTop.setLayout(new BoxLayout(pnlTop, BoxLayout.Y_AXIS));

        btgLevels.add(rbnParentLevel);
        rbnParentLevel.setSelected(true);
        rbnParentLevel.setText(bundle.getString("parentLevel")); // NOI18N
        pnlTop.add(rbnParentLevel);

        chbTestSuite.setSelected(true);
        chbTestSuite.setText(bundle.getString("testsuite")); // NOI18N
        chbTestSuite.setMargin(new Insets(2, 30, 2, 2));
        pnlTop.add(chbTestSuite);

        chbTestCase.setSelected(true);
        chbTestCase.setText(bundle.getString("testcase")); // NOI18N
        chbTestCase.setMargin(new Insets(2, 30, 2, 2));
        pnlTop.add(chbTestCase);

        chbTestStep.setSelected(true);
        chbTestStep.setText(bundle.getString("teststep")); // NOI18N
        chbTestStep.setMargin(new Insets(2, 30, 2, 2));
        pnlTop.add(chbTestStep);

        chbCommon.setText(bundle.getString("common")); // NOI18N
        chbCommon.setMargin(new Insets(2, 30, 2, 2));
        pnlTop.add(chbCommon);

        chbTests.setText(bundle.getString("tests")); // NOI18N
        chbTests.setMargin(new Insets(2, 30, 2, 2));
        pnlTop.add(chbTests);

        btgLevels.add(rbnCustom);
        rbnCustom.setText("Custom Selection");
        rbnCustom.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent evt) {
                rbnCustomStateChanged(evt);
            }
        });
        pnlTop.add(rbnCustom);

        add(pnlTop, BorderLayout.NORTH);

        treeCustom.setEnabled(false);
        spnCustom.setViewportView(treeCustom);

        add(spnCustom, BorderLayout.CENTER);
    }// </editor-fold>//GEN-END:initComponents

    private void rbnCustomStateChanged(ChangeEvent evt) {//GEN-FIRST:event_rbnCustomStateChanged
        boolean b = rbnCustom.isSelected();
        treeCustom.setEnabled(b);
        chbTestCase.setEnabled(!b);
        chbTestStep.setEnabled(!b);
        chbTestSuite.setEnabled(!b);
    }//GEN-LAST:event_rbnCustomStateChanged


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private ButtonGroup btgLevels;
    private JCheckBox chbCommon;
    private JCheckBox chbTestCase;
    private JCheckBox chbTestStep;
    private JCheckBox chbTestSuite;
    private JCheckBox chbTests;
    private JPanel pnlTop;
    private JRadioButton rbnCustom;
    private JRadioButton rbnParentLevel;
    private JScrollPane spnCustom;
    private JTree treeCustom;
    // End of variables declaration//GEN-END:variables
}

final class CheckBoxRenderer extends JTreeTable.AbstractCellEditor implements TreeCellRenderer, TreeCellEditor {

    CheckBox checkBox;
    JLabel label;
    JPanel panel;

    public CheckBoxRenderer(JTree tree) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.ipadx = 2;
        checkBox = new CheckBox(tree, this);
        checkBox.setOpaque(false);
        label = new JLabel();
        label.setFont(UIManager.getFont("Tree.font"));
        panel = new JPanel();
        panel.setLayout(new GridBagLayout());
        panel.setOpaque(false);
        panel.add(checkBox, gbc);
        panel.add(label, gbc);
    }
    
    public void setEnabled(boolean b) {
        checkBox.setEnabled(b);
        label.setEnabled(b);
    }

    @Override
    public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
        return getTreeCellEditorComponent(tree, value, selected, expanded, leaf, row);
    }

    @Override
    public Component getTreeCellEditorComponent(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row) {
        if (value instanceof CheckBoxNode) {
            CheckBoxNode node = (CheckBoxNode) value;
            checkBox.setNode(node);
            label.setIcon(node.getIcon());
            label.setText(node.getLabel());
            setEnabled(tree.isEnabled());
        }
        return panel;
    }

    @Override
    public Object getCellEditorValue() {
        return checkBox.getNode();
    }
}

final class CheckBoxNode extends DefaultMutableTreeNode {

    private TreeIconNode source;
    private int actionIndex;
    
    public static final int UNSELECT = 1;

    public CheckBoxNode(TreeIconNode source) {
        this.source = source;
        this.actionIndex = UNSELECT;
    }

    public String getLabel() {
        return source.getTreeIconUserObject().getLabel();
    }

    public Icon getIcon() {
        return source.getTreeIconUserObject().getIcon();
    }
    
    public TreeCollection.TYPE getType() {
        return source.getType();
    }
    
    public TreeIconNode.ResourceInfo getInfo() {
        return source.info;
    }
    
    public JSONObject getJSON() {
        return source.jsonObject();
    }

    public void setActionIndex(int index) {
        this.actionIndex = index;
    }

    public int getActionIndex() {
        return this.actionIndex;
    }

    public static CheckBoxNode createChildren(TreeIconNode source) {
        CheckBoxNode node = new CheckBoxNode(source);
        Enumeration children = source.children();
        while (children.hasMoreElements()) {
            node.add(createChildren((TreeIconNode) children.nextElement()));
        }
        return node;
    }
}

final class CheckBox extends JCheckBox implements Icon, ActionListener {

    private JTree tree;
    private CheckBoxNode node;
    private CheckBoxRenderer renderer;
    private boolean allowPartial;
    private final static Icon icon = UIManager.getIcon("CheckBox.icon");

    public CheckBox(JTree tree, CheckBoxRenderer renderer) {
        super();
        this.tree = tree;
        this.renderer = renderer;
        setIcon(this);
        addActionListener(this);
    }

    public CheckBoxNode getNode() {
        return node;
    }

    public void setNode(CheckBoxNode node) {
        this.node = node;
        allowPartial = !node.isLeaf();
        setActionIndex(node.getActionIndex());
    }

    public void setActionIndex(int index) {
        if (index > (allowPartial ? 2 : 1)) {
            index = 0;
        }
        node.setActionIndex(index);
        setSelected(index == 0);
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
        renderer.setEnabled(tree.isEnabled());
        icon.paintIcon(c, g, x, y);
        if (node.getActionIndex() == 2) {
            if (c.isEnabled()) {
                g.setColor(UIManager.getColor("CheckBox.foreground"));
            } else {
                g.setColor(UIManager.getColor("CheckBox.shadow"));
            }
            g.fillRect(x + 4, y + 4, getIconWidth() - 8, getIconHeight() - 8);
        }
    }

    @Override
    public int getIconWidth() {
        return icon.getIconWidth();
    }

    @Override
    public int getIconHeight() {
        return icon.getIconHeight();
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        setActionIndex(node.getActionIndex() + 1);
        tree.expandPath(new TreePath(node.getPath()));
        if (allowPartial && node.getActionIndex() != 2) {
            Enumeration children = node.preorderEnumeration();
            while (children.hasMoreElements()) {
                ((CheckBoxNode) children.nextElement()).setActionIndex(node.getActionIndex());
            }
            tree.updateUI();
        }
    }
}