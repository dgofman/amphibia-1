/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.equinix.amphibia.components;

import com.equinix.amphibia.Amphibia;
import com.equinix.amphibia.IO;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.ResourceBundle;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.SwingConstants;
import javax.swing.ToolTipManager;
import javax.swing.UIManager;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.io.FilenameUtils;

/**
 *
 * @author dgofman
 */
public final class ResourceAddDialog extends javax.swing.JPanel {

    private final DefaultTreeModel testCaseModel;
    private final TreeIconNode testCaseNode;
    private final DefaultTreeModel testStepModel;
    private final TreeIconNode testStepNode;
    private final JButton cancelTestStepButton;
    private TreeIconNode treeSelectedNode;
    private JDialog testCaseDialog;
    private JDialog testStepDialog;
    private JButton okTestCaseButton;
    private JButton cancelTestCaseButton;
    private JButton okTestStepButton;

    private ResourceBundle bundle;

    /**
     * Creates new form JDialogTest
     *
     * @param mainPanel
     */
    @SuppressWarnings("LeakingThisInConstructor")
    public ResourceAddDialog(MainPanel mainPanel) {

        bundle = Amphibia.getBundle();
        
        testCaseNode = new TreeIconNode(null, bundle.getString("tests"), TreeCollection.TYPE.TESTS, false);
        testCaseModel = new DefaultTreeModel(testCaseNode);
        
        testStepNode = new TreeIconNode(null, bundle.getString("common"), TreeCollection.TYPE.COMMON, false);
        testStepModel = new DefaultTreeModel(testStepNode);

        initComponents();

        treeTests.setModel(testCaseModel);
        treeTests.setRowHeight(20);
        treeTests.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        treeTests.setCellRenderer(new DefaultTreeCellRenderer() {
            @Override
            public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean expanded, boolean isLeaf, int row, boolean hasFocus) {
                Object userObject = ((DefaultMutableTreeNode) value).getUserObject();
                Component c = super.getTreeCellRendererComponent(tree, value, selected, expanded, isLeaf, row, hasFocus);
                if (userObject instanceof TreeIconNode.TreeIconUserObject) {
                    TreeIconNode.TreeIconUserObject node = (TreeIconNode.TreeIconUserObject) userObject;
                    setToolTipText(node.getTooltip());
                    setIcon(node.getIcon());
                }
                return c;
            }
        });
        treeTests.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent e) {
                TreePath path = treeTests.getPathForLocation(e.getX(), e.getY());
                if (path == null) {
                    return;
                }
                TreeIconNode node = (TreeIconNode) treeTests.getLastSelectedPathComponent();
                if (node != null && node.getType() == TreeCollection.TYPE.TESTCASE) {
                    treeSelectedNode = node;
                    txtName.setText(FilenameUtils.removeExtension(node.getLabel()));
                }
            }
        });
        
        treeCommon.setModel(testStepModel);
        treeCommon.setRowHeight(20);
        treeCommon.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        treeCommon.setCellRenderer(new DefaultTreeCellRenderer() {
            @Override
            public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean expanded, boolean isLeaf, int row, boolean hasFocus) {
                Object userObject = ((DefaultMutableTreeNode) value).getUserObject();
                Component c = super.getTreeCellRendererComponent(tree, value, selected, expanded, isLeaf, row, hasFocus);
                if (userObject instanceof TreeIconNode.TreeIconUserObject) {
                    TreeIconNode.TreeIconUserObject node = (TreeIconNode.TreeIconUserObject) userObject;
                    if (selected && treeCommon.isEnabled()) {
                        txtTestStepName.setText(node.getTooltip());
                    }
                    setToolTipText(node.getTooltip());
                    setIcon(node.getIcon());
                }
                return c;
            }
        });
        treeCommon.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent e) {
                TreePath path = treeCommon.getPathForLocation(e.getX(), e.getY());
                if (path == null) {
                    return;
                }
                treeSelectedNode = (TreeIconNode) treeCommon.getLastSelectedPathComponent();
            }
        });
        ToolTipManager tooltip = ToolTipManager.sharedInstance();
        tooltip.setInitialDelay(200);
        tooltip.registerComponent(treeTests);
        tooltip.registerComponent(treeCommon);

        okTestCaseButton = new JButton(UIManager.getString("OptionPane.okButtonText"));
        okTestCaseButton.addActionListener((ActionEvent evt) -> {
            TreeCollection collection = MainPanel.selectedNode.getCollection();
            if (MainPanel.selectedNode.getType() == TreeCollection.TYPE.TESTSUITE) {
                if (treeSelectedNode == null) {
                    return;
                }
                JSONArray testcases = MainPanel.selectedNode.info.testSuite.getJSONArray("testcases");
                for (Object testcase : testcases) {
                    if (txtName.getText().equals(((JSONObject) testcase).getString("name"))) {
                        lblError.setText(bundle.getString("tip_name_exists"));
                        lblError.setVisible(true);
                        return;
                    }
                }
                String path = treeSelectedNode.getTreeIconUserObject().getTooltip();
                if (IO.getFile(collection, path).exists()) { 
                    JSONObject testcase = new JSONObject();
                    testcase.put("name", txtName.getText());
                    testcase.put("path", path);
                    testcase.put("steps", new JSONArray());
                    testcases.add(testcase);
                } else {
                    lblError.setText(String.format(bundle.getString("error_missing_file"), path));
                    lblError.setToolTipText(lblError.getText());
                    lblError.setVisible(true);
                    return;
                }
            }
            mainPanel.saveNodeValue(collection.profile);
            testCaseDialog.setVisible(false);
        });
        cancelTestCaseButton = new JButton(UIManager.getString("OptionPane.cancelButtonText"));
        cancelTestCaseButton.addActionListener((ActionEvent evt) -> {
            testCaseDialog.setVisible(false);
        });
        
        testCaseDialog = Amphibia.createDialog(this, new Object[]{okTestCaseButton, cancelTestCaseButton}, true);
        testCaseDialog.setSize(new Dimension(400, 400));
        java.awt.EventQueue.invokeLater(() -> {
            testCaseDialog.setLocationRelativeTo(mainPanel);
        });
        
        okTestStepButton = new JButton(UIManager.getString("OptionPane.okButtonText"));
        okTestStepButton.addActionListener((ActionEvent evt) -> {
            if (rbnAddCommon.isSelected() && (treeSelectedNode == null || treeSelectedNode.getType() == TreeCollection.TYPE.COMMON)) {
                return;
            }
            String commonName = rbnAddCommon.isSelected() ? treeSelectedNode.getLabel() : null;
            TreeIconNode node = MainPanel.selectedNode;
            TreeIconNode.ProfileNode profile = node.getCollection().profile;
            JSONObject common = profile.jsonObject().getJSONObject("common");
            String name = txtTestStepName.getText().trim();
            if (!name.isEmpty()) {
                JSONArray steps = node.info.testCase.getJSONArray("steps");
                for (Object step : steps) {
                    if (name.equals(((JSONObject) step).getString("name"))) {
                        lblTestStepError.setVisible(true);
                        return;
                    }
                }
                
                if(rbnCreateCommon.isSelected()) {
                    commonName = name;
                    common.put(commonName, new LinkedHashMap<Object,Object>() {{
                        put("request", new LinkedHashMap<Object,Object>() {{
                            put("properties", new LinkedHashMap<>());
                        }});
                        put("response", new LinkedHashMap<Object,Object>() {{
                            put("properties", new LinkedHashMap<>());
                        }});
                    }});
                }

                TreeIconNode testStep = node.getCollection().insertTreeNode(node, name, TreeCollection.TYPE.TEST_STEP_ITEM);
                testStep.saveSelection();
                JSONObject json = new JSONObject();
                json.element("name", name);
                if (commonName != null) {
                    json.element("common", commonName);
                }
                steps.add(json);
                mainPanel.saveNodeValue(profile);
                testStepDialog.setVisible(false);
            }
        });
        cancelTestStepButton = new JButton(UIManager.getString("OptionPane.cancelButtonText"));
        cancelTestStepButton.addActionListener((ActionEvent evt) -> {
            testStepDialog.setVisible(false);
        });
        
        testStepDialog = Amphibia.createDialog(pnlTestStep, new Object[]{okTestStepButton, cancelTestStepButton}, true);
        testStepDialog.setSize(new Dimension(500, 600));
        java.awt.EventQueue.invokeLater(() -> {
            testStepDialog.setLocationRelativeTo(mainPanel);
        });
    }
    
    public void showTestCaseDialog(TreeCollection collection) {
        txtName.setText("");
        lblError.setVisible(false);
        testCaseNode.removeAllChildren();
        File dataDir = IO.getFile(collection, "data");
        for (String resourceId : dataDir.list()) {
            JSONObject json = new JSONObject();
            json.element("resourceId", resourceId);
            File resourceDir = IO.newFile(dataDir, resourceId);
            if (resourceDir.isFile()) { //profile.json
                continue;
            }
            File testsDir = IO.newFile(resourceDir, "tests");
            if (testsDir.exists()) {
                for (String testSuiteName : testsDir.list()) {
                    TreeIconNode testsuite = new TreeIconNode(collection, testSuiteName, TreeCollection.TYPE.TESTSUITE, false);
                    File subdir = IO.newFile(testsDir, testSuiteName);
                    for (String testCaseName : subdir.list()) {
                        testsuite.add(new TreeIconNode(collection, testCaseName, TreeCollection.TYPE.TESTCASE, false)
                                .addTooltip(String.format("data/%s/tests/%s/%s", resourceId, testSuiteName, testCaseName)));
                    }
                    testCaseNode.add(testsuite);
                }
            }
        }
        testCaseModel.setRoot(testCaseNode);
        treeSelectedNode = null;
        
        testCaseDialog.setVisible(true);
    }
    
    @SuppressWarnings("NonPublicExported")
    public void showTestStepDialog(TreeIconNode selectedNode) {
        lblTestStepError.setVisible(false);
        testStepNode.removeAllChildren();
        Enumeration children = selectedNode.getCollection().common.children();
        while (children.hasMoreElements()) {
            testStepNode.add(((TreeIconNode) children.nextElement()).cloneNode());
        }
        testStepModel.setRoot(testStepNode);        
        treeSelectedNode = null;
 
        if (testStepNode.getChildCount() > 0) {
            treeSelectedNode = (TreeIconNode) testStepNode.getChildAt(0);
            treeCommon.setSelectionPath(new TreePath(treeSelectedNode.getPath()));
        }

        rbnCreateTestStep.setSelected(true);
        txtTestStepName.setText("");
        testStepDialog.setVisible(true);
    }
    
    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        pnlTestStep = new JPanel();
        pnlTestStepTop = new JPanel();
        rbnCreateTestStep = new JRadioButton();
        rbnCreateCommon = new JRadioButton();
        rbnAddCommon = new JRadioButton();
        spnCommon = new JScrollPane();
        treeCommon = new JTree();
        pnlTestStepBottom = new JPanel();
        lblTestStepName = new JLabel();
        txtTestStepName = new JTextField();
        lblTestStepError = new JLabel();
        btgTestStep = new ButtonGroup();
        slpTests = new JScrollPane();
        treeTests = new JTree();
        pnlBottom = new JPanel();
        lblName = new JLabel();
        txtName = new JTextField();
        lblError = new JLabel();

        pnlTestStep.setBorder(BorderFactory.createEmptyBorder(0, 5, 5, 5));
        pnlTestStep.setLayout(new BorderLayout());

        pnlTestStepTop.setLayout(new BoxLayout(pnlTestStepTop, BoxLayout.Y_AXIS));

        btgTestStep.add(rbnCreateTestStep);
        rbnCreateTestStep.setSelected(true);
        ResourceBundle bundle = ResourceBundle.getBundle("com/equinix/amphibia/messages"); // NOI18N
        rbnCreateTestStep.setText(bundle.getString("createTestStep")); // NOI18N
        pnlTestStepTop.add(rbnCreateTestStep);

        btgTestStep.add(rbnCreateCommon);
        rbnCreateCommon.setText(bundle.getString("createCommon")); // NOI18N
        pnlTestStepTop.add(rbnCreateCommon);

        btgTestStep.add(rbnAddCommon);
        rbnAddCommon.setText(bundle.getString("addFromCommon")); // NOI18N
        rbnAddCommon.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent evt) {
                rbnAddCommonStateChanged(evt);
            }
        });
        pnlTestStepTop.add(rbnAddCommon);

        pnlTestStep.add(pnlTestStepTop, BorderLayout.NORTH);

        treeCommon.setEnabled(false);
        spnCommon.setViewportView(treeCommon);

        pnlTestStep.add(spnCommon, BorderLayout.CENTER);

        pnlTestStepBottom.setBorder(BorderFactory.createEmptyBorder(5, 1, 1, 1));
        pnlTestStepBottom.setLayout(new BorderLayout(5, 0));

        lblTestStepName.setText(bundle.getString("name")); // NOI18N
        pnlTestStepBottom.add(lblTestStepName, BorderLayout.WEST);
        pnlTestStepBottom.add(txtTestStepName, BorderLayout.CENTER);

        lblTestStepError.setForeground(Color.red);
        lblTestStepError.setHorizontalAlignment(SwingConstants.CENTER);
        lblTestStepError.setText(bundle.getString("tip_name_exists")); // NOI18N
        lblTestStepError.setInheritsPopupMenu(false);
        pnlTestStepBottom.add(lblTestStepError, BorderLayout.PAGE_END);

        pnlTestStep.add(pnlTestStepBottom, BorderLayout.PAGE_END);

        setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        setLayout(new BorderLayout());

        treeTests.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        slpTests.setViewportView(treeTests);

        add(slpTests, BorderLayout.CENTER);

        pnlBottom.setBorder(BorderFactory.createEmptyBorder(5, 1, 1, 1));
        pnlBottom.setLayout(new BorderLayout(5, 0));

        lblName.setText(bundle.getString("name")); // NOI18N
        pnlBottom.add(lblName, BorderLayout.WEST);
        pnlBottom.add(txtName, BorderLayout.CENTER);

        lblError.setForeground(Color.red);
        lblError.setHorizontalAlignment(SwingConstants.CENTER);
        lblError.setText(bundle.getString("tip_name_exists")); // NOI18N
        lblError.setInheritsPopupMenu(false);
        pnlBottom.add(lblError, BorderLayout.PAGE_END);

        add(pnlBottom, BorderLayout.PAGE_END);
    }// </editor-fold>//GEN-END:initComponents

    private void rbnAddCommonStateChanged(ChangeEvent evt) {//GEN-FIRST:event_rbnAddCommonStateChanged
        treeCommon.setEnabled(rbnAddCommon.isSelected());
    }//GEN-LAST:event_rbnAddCommonStateChanged


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private ButtonGroup btgTestStep;
    private JLabel lblError;
    private JLabel lblName;
    private JLabel lblTestStepError;
    private JLabel lblTestStepName;
    private JPanel pnlBottom;
    private JPanel pnlTestStep;
    private JPanel pnlTestStepBottom;
    private JPanel pnlTestStepTop;
    private JRadioButton rbnAddCommon;
    private JRadioButton rbnCreateCommon;
    private JRadioButton rbnCreateTestStep;
    private JScrollPane slpTests;
    private JScrollPane spnCommon;
    private JTree treeCommon;
    private JTree treeTests;
    private JTextField txtName;
    private JTextField txtTestStepName;
    // End of variables declaration//GEN-END:variables
}
