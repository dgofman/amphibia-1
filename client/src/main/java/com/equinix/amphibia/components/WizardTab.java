/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.equinix.amphibia.components;

import static com.equinix.amphibia.agent.runner.HttpConnectionImpl.Result;

import com.equinix.amphibia.Amphibia;
import com.equinix.amphibia.HttpConnection;
import com.equinix.amphibia.agent.runner.IHttpConnection;
import com.equinix.amphibia.IO;

import com.equinix.amphibia.agent.builder.Properties;
import com.equinix.amphibia.agent.converter.Profile;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.Enumeration;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.CellEditor;
import javax.swing.DefaultComboBoxModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.OverlayLayout;
import javax.swing.SwingConstants;
import javax.swing.border.Border;
import javax.swing.table.DefaultTableModel;
import javax.swing.text.AbstractDocument;
import javax.swing.text.BadLocationException;
import javax.swing.text.Element;
import javax.swing.text.ParagraphView;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import javax.swing.text.StyledEditorKit;
import javax.swing.text.View;
import javax.swing.text.ViewFactory;
import javax.swing.tree.DefaultMutableTreeNode;
import net.sf.json.JSONArray;
import net.sf.json.JSONNull;
import net.sf.json.JSONObject;

/**
 *
 * @author dgofman
 */
public final class WizardTab extends javax.swing.JPanel implements IHttpConnection {

    private ResourceBundle bundle;
    private Wizard wizard;
    private TreeIconNode openedNode;
    private JDialog headersDialog;
    private JDialog saveTestCase;
    private JButton applyHeadersButton;
    private JButton applyTestCaseButton;
    private JButton headersCancelButton;
    private JButton testcaseCancelButton;
    private DefaultComboBoxModel testSuitesModel;
    private String uID;

    private final Border DEFAULT_BORDER;
    private final Border ERROR_BORDER = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Color.RED),
            BorderFactory.createEmptyBorder(2, 2, 2, 2));

    private static final Logger logger = Amphibia.getLogger(WizardTab.class.getName());

    /**
     * Creates new form WizardTab
     *
     * @param wizard the value of wizard
     */
    public WizardTab(Wizard wizard) {
        this(wizard, null);
    }

    @SuppressWarnings("LeakingThisInConstructor")
    public WizardTab(Wizard wizard, TreeIconNode node) {
        this.wizard = wizard;
        this.openedNode = node;

        bundle = Amphibia.getBundle();

        testSuitesModel = new DefaultComboBoxModel();

        initComponents();

        ExtendedStyledEditorKit editorKit = new ExtendedStyledEditorKit();
        String contentType = editorKit.getContentType();
        txtConsole.setEditorKitForContentType(contentType, editorKit);
        txtConsole.setEditorKit(editorKit);

        add(pnlWaitOverlay, 0, 0);
        pnlWaitOverlay.setVisible(false);
        lblAssertValue.setVisible(false);

        DEFAULT_BORDER = txtTestCase.getBorder();

        if (openedNode != null) {
            uID = openedNode.getUID();
            btnReload.setVisible(true);
            cmdInterface.setEnabled(false);
            cmdMethod.setEnabled(false);
            String method = node.jsonObject().getString("method");
            for (int i = 0; i < cmdMethod.getItemCount(); i++) {
                if (cmdMethod.getItemAt(i).equals(method)) {
                    cmdMethod.setSelectedIndex(i);
                    break;
                }
            }
            btnReloadActionPerformed(null);

            cmbTestSuite.setEnabled(false);
            btnAddTestSuite.setEnabled(false);
            txtTestCase.setEditable(false);
        } else {
            chbAutoReload.setVisible(false);
            btnReload.setVisible(false);
        }

        applyHeadersButton = new JButton(bundle.getString("apply"));
        applyHeadersButton.addActionListener((ActionEvent evt) -> {
            CellEditor cellEditor = tblHeadersBottom.getCellEditor();
            if (cellEditor != null) {
                cellEditor.stopCellEditing();
            }
            if (openedNode != null) {
                TreeCollection collection = MainPanel.selectedNode.getCollection();
                JSONObject json = null;
                JSONObject testCaseHeaders = IO.toJSONObject(openedNode.info.testCaseHeaders);
                if (openedNode.getType() == TreeCollection.TYPE.TESTCASE && openedNode.info.testCase != null) {
                    json = openedNode.info.testCase;
                } else if (openedNode.getType() == TreeCollection.TYPE.TEST_STEP_ITEM && openedNode.info.testStep != null) {
                    json = openedNode.info.testStep;
                    if (node.info.testCase != null && node.info.testCase.containsKey("headers")) {
                        JSONObject tcHeaders = node.info.testCase.getJSONObject("headers");
                        tcHeaders.keySet().forEach((key) -> {
                            testCaseHeaders.put(key, tcHeaders.get(key));
                        });
                    }
                }
                
                if (json != null) {
                    try {
                        JSONObject headers = new JSONObject();
                        for (int i = 0; i < tblHeadersBottom.getRowCount(); i++) {
                            Object key = tblHeadersBottom.getValueAt(i, 0);
                            if (!key.toString().isEmpty()) {
                                Object value = tblHeadersBottom.getValueAt(i, 1).toString();
                                if (!value.equals(testCaseHeaders.getOrDefault(key, null))) {
                                    headers.put(key, value.toString().isEmpty() ? JSONNull.getInstance() : value);
                                    if (headers.get(key) == JSONNull.getInstance()) {
                                        testCaseHeaders.remove(key);
                                    } else {
                                        testCaseHeaders.put(key, value);
                                    }
                                }
                            }
                        }
                        json.put("headers", headers);
                        wizard.mainPanel.saveNodeValue(collection.profile);
                        Amphibia.setText(txtReqHeaders, spnReqHeaders, IO.prettyJson(openedNode.info.properties.replace(testCaseHeaders.toString())));
                    } catch (Exception ex) {
                        addError(ex, bundle.getString("error_open_json"));
                    }
                }
            }
            headersDialog.setVisible(false);
        });

        applyTestCaseButton = new JButton(bundle.getString("apply"));
        applyTestCaseButton.addActionListener((ActionEvent evt) -> {
            String testCaseName = txtTestCase.getText().trim();
            if (testCaseName.isEmpty()) {
                txtTestCase.setBorder(ERROR_BORDER);
                return;
            }

            TreeIconNode project = MainPanel.selectedNode.getCollection().project;
            JSONArray projectResources = project.jsonObject().getJSONArray("projectResources");
            String interfaceName = "";
            JSONObject resource = null;
            boolean addResource = false;
            if (openedNode != null) {
                for (int i = 0; i < projectResources.size(); i++) {
                    JSONObject item = projectResources.getJSONObject(i);
                    if (item.getString("resourceId").equals(openedNode.info.resource.getString("resourceId"))) {
                        resource = item;
                        break;
                    }
                }
            } else if (cmdInterface.getSelectedIndex() > 0) {
                interfaceName = cmdInterface.getSelectedItem().toString();
                for (int i = 0; i < projectResources.size(); i++) {
                    JSONObject item = projectResources.getJSONObject(i);
                    if (item.getString("interface").equals(interfaceName)) {
                        resource = item;
                        break;
                    }
                }
            }
            if (resource == null) {
                resource = new JSONObject();
                resource.element("resourceId", UUID.randomUUID().toString());
                resource.element("endpoint", ((EndPoint) cmdEndpoint.getSelectedItem()).endPointName);
                resource.element("interface", interfaceName);
                resource.element("testsuites", new JSONObject());
                addResource = true;
            }

            String testSuiteName = cmbTestSuite.getSelectedItem().toString();
            JSONObject testsuites = resource.getJSONObject("testsuites");
            JSONObject testsuite;
            if (testsuites.containsKey(testSuiteName)) {
                testsuite = testsuites.getJSONObject(testSuiteName);
            } else {
                testsuite = new JSONObject();
                testsuite.element("testcases", new JSONArray());
                testsuite.element("properties", new JSONObject());
                testsuites.element(testSuiteName, testsuite);
            }

            JSONArray testcases = testsuite.getJSONArray("testcases");
            boolean addTestCase = false;
            JSONObject testcase = null;
            for (Object item : testcases) {
                if (((JSONObject) item).getString("name").equals(testCaseName)) {
                    testcase = (JSONObject) item;
                    break;
                }
            }
            if (testcase == null) {
                testcase = new JSONObject();
                testcase.put("type", "restrequest");
                testcase.put("name", testCaseName);
                testcase.put("summary", "");
                testcase.put("method", null);
                testcase.put("path", null);
                testcase.put("body", null);
                testcase.put("operationId", null);
                testcase.put("assertions", null);
                testcase.element("properties", new JSONObject());
                addTestCase = true;
            }
            testcase.element("summary", txtSummary.getText());

            if (ckbStatusAssert.isEnabled() && ckbStatusAssert.isSelected()) {
                int code;
                try {
                    code = Integer.valueOf(lblCode.getText());
                } catch (NumberFormatException e) {
                    code = 0;
                }
                testcase.getJSONObject("properties").element(Profile.HTTP_STATUS_CODE, code);
            }
            testcase.element("operationId", txtTestCaseFuncName.getText());

            testcase.element("path", txtPath.getText());
            testcase.element("method", cmdMethod.getSelectedItem().toString());
            if (ckbSaveExample.isEnabled() && ckbSaveExample.isSelected()) {
                try {
                    testcase.element("body", IO.prettyJson(txtResBody.getText()));
                } catch (Exception ex) {
                }
            }

            if (addTestCase) {
                testcases.add(testcase);
            }
            testsuites.element(testSuiteName, testsuite);
            if (addResource) {
                projectResources.add(resource);
            }

            wizard.mainPanel.saveNodeValue(project);
            saveTestCase.setVisible(false);
        });

        headersCancelButton = new JButton(bundle.getString("cancel"));
        headersCancelButton.addActionListener((ActionEvent evt) -> {
            headersDialog.setVisible(false);
        });

        testcaseCancelButton = new JButton(bundle.getString("cancel"));
        testcaseCancelButton.addActionListener((ActionEvent evt) -> {
            saveTestCase.setVisible(false);
        });

        headersDialog = Amphibia.createDialog(pnlHeaders, new Object[]{applyHeadersButton, headersCancelButton}, true);
        headersDialog.setSize(new Dimension(500, 500));

        saveTestCase = Amphibia.createDialog(pnlSaveTestCase, new Object[]{applyTestCaseButton, testcaseCancelButton}, true);
        saveTestCase.setSize(new Dimension(500, 400));

        EventQueue.invokeLater(() -> {
            headersDialog.setLocationRelativeTo(this);
            saveTestCase.setLocationRelativeTo(this);
        });
    }
    
    public void deleteTab(boolean isSave) {
        wizard.tabNav.remove(this);
        openedNode.info.states.set(TreeIconNode.STATE_OPEN_PROJECT_OR_WIZARD_TAB, 0);
        if (isSave) {
            save();
        }
    }
    
    public void save() {
        openedNode.getCollection().profile.saveState(openedNode);
    }

    public void refresh() {
        //btnHeaders.setEnabled(tabNav.getSelectedIndex() != 0);
        btnSend.setEnabled(cmdEndpoint.getSelectedItem() != null);
        if (btnSend.isEnabled()) {
            String basePath = "/";
            if (cmdInterface.getSelectedItem() != null) {
                basePath = ((Wizard.ComboItem) cmdInterface.getSelectedItem()).basePath;
            }
            lblURI.setText(((EndPoint) cmdEndpoint.getSelectedItem()).endPointValue + Properties.getURL(basePath, txtPath.getText()));
        } else {
            lblURI.setText("http://");
        }
        if (openedNode == null) {
            txtReqHeaders.setText("");
            Wizard.ComboItem item = (Wizard.ComboItem) cmdInterface.getSelectedItem();
            if (item != null) {
                JSONObject headers = (JSONObject) item.headers;
                if (!headers.isEmpty()) {
                    try {
                        if (MainPanel.selectedNode != null) {
                            TreeCollection collection = MainPanel.selectedNode.getCollection();
                            Properties properties = collection.getProjectProperties();
                            txtReqHeaders.setText(IO.prettyJson(properties.replace(headers.toString())));
                        } else {
                            txtReqHeaders.setText(IO.prettyJson(headers.toString()));
                        }
                    } catch (Exception ex) {
                        addError(ex, bundle.getString("error_open_json"));
                    }
                }
            }
        }
    }

    public void updateInterfaces() {
        String selected = String.valueOf(cmdInterface.getSelectedItem());
        DefaultComboBoxModel model = new DefaultComboBoxModel();
        cmdInterface.setModel(model);
        model.addElement(wizard.createDefaultItem());
        if (openedNode != null) {
            JSONObject resourceItem = openedNode.info.resource;
            JSONObject projectItem = null;
            if (resourceItem.containsKey("interfaceId")) {
                JSONArray interfaces = openedNode.getCollection().project.jsonObject().getJSONArray("interfaces");
                for (Object item : interfaces) {
                    JSONObject intf =  (JSONObject) item;
                    if (intf.getString("id").equals(resourceItem.getString("interfaceId"))) {
                        projectItem = intf;
                        break;
                    }
                }
            }
            model.removeAllElements();
            model.addElement(new Wizard.ComboItem(resourceItem, projectItem));
        } else {
            DefaultComboBoxModel interfaceModel = wizard.getInterfaceNameModel();
            for (int i = 0; i < interfaceModel.getSize(); i++) {
                Wizard.ComboItem item = (Wizard.ComboItem) interfaceModel.getElementAt(i);
                model.addElement(item);
                if (item.toString().equals(selected)) {
                    cmdInterface.setSelectedItem(item);
                }
            }
        }
    }

    public void updateEndPoints(Map<Object, Object> endpoints) {
        int index = -1;
        Object endPoint = null;
        if (cmdEndpoint.getSelectedItem() != null) {
            endPoint = ((EndPoint) cmdEndpoint.getSelectedItem()).endPointName;
        } else if (openedNode != null) {
            endPoint = openedNode.info.resource.getString("endpoint");
        }
        DefaultComboBoxModel model = new DefaultComboBoxModel<>();
        for (Object name : endpoints.keySet()) {
            if (index == -1 && name.equals(endPoint)) {
                index = model.getSize();
            }
            model.addElement(new EndPoint(name, endpoints.get(name)));
        }

        cmdEndpoint.setModel(model);
        if (model.getSize() > 0) {
            cmdEndpoint.setSelectedIndex(Math.max(0, index));
        }
        refresh();
    }

    public void openTestCaseDialog() {
        if (MainPanel.selectedNode == null) {
            return;
        }
        txtTestCase.setBorder(DEFAULT_BORDER);
        if (openedNode == null) {
            TreeIconNode testsuites = MainPanel.selectedNode.getCollection().testsuites;
            testSuitesModel.removeAllElements();
            Enumeration children = testsuites.children();
            while (children.hasMoreElements()) {
                testSuitesModel.addElement(children.nextElement().toString());
            }
            txtTestCase.setEnabled(testSuitesModel.getSize() > 0);
            txtTestCase.setText("");
            txtTestCaseFuncName.setText("");
            txtSummary.setText("");
        }
        try {
            IO.prettyJson(txtReqBody.getText());
        } catch (Exception ex) {
            ckbReqSchema.setEnabled(false);
        }
        try {
            IO.prettyJson(txtResBody.getText());
        } catch (Exception ex) {
            ckbResSchema.setEnabled(false);
            ckbSaveExample.setEnabled(false);
        }

        saveTestCase.setVisible(true);
    }
    
    public TreeIconNode getNode() {
        return openedNode;
    }
    
    public void replaceNode(TreeIconNode node) {
        if (node.getUID().equals(uID)) {
            openedNode = node;
            if (chbAutoReload.isSelected()) {
                btnReloadActionPerformed(null);
            }
        }        
    }
    
    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {
        GridBagConstraints gridBagConstraints;

        pnlHeaders = new JPanel();
        sptHeaders = new JSplitPane();
        pnlHeadersTop = new JPanel();
        lblHeadersTop = new JLabel();
        spnHeadersTop = new JScrollPane();
        tblHeadersTop = new JTable();
        pnlHeadersBottom = new JPanel();
        pnlHeaderBottomHeader = new JPanel();
        lblHeadersBottom = new JLabel();
        lblHeaderBottomHint = new JLabel();
        spnHeadersBottom = new JScrollPane();
        tblHeadersBottom = new JTable();
        pnlHeadersBottomFooter = new JPanel();
        btnHeadersBottomAdd = new JButton();
        btnHeadersBottomDelete = new JButton();
        lblHeadersBottomError = new JLabel();
        pnlSaveTestCase = new JPanel();
        pnlTestsuite = new JPanel();
        lblTestSuite = new JLabel();
        cmbTestSuite = new JComboBox<>();
        btnAddTestSuite = new JButton();
        pnlTestCase = new JPanel();
        lblTestCase = new JLabel();
        txtTestCase = new JTextField();
        pnlOptions = new JPanel();
        ckbReqSchema = new JCheckBox();
        ckbResSchema = new JCheckBox();
        ckbSaveExample = new JCheckBox();
        ckbStatusAssert = new JCheckBox();
        pnlTestCaseFooter = new JPanel();
        lblTestCaseFuncName = new JLabel();
        txtTestCaseFuncName = new JTextField();
        lblSummary = new JLabel();
        txtSummary = new JTextField();
        pnlWaitOverlay = new JPanel();
        lblAnimation = new JLabel();
        pnlComponents = new JPanel();
        pnlTop = new JPanel();
        lblEndpoint = new JLabel();
        pnlEndpoint = new JPanel();
        cmdEndpoint = new JComboBox<>();
        btnEndpointInfo = new JButton();
        lblInterface = new JLabel();
        pnlInterface = new JPanel();
        cmdInterface = new JComboBox<>();
        btnInterfaceInfo = new JButton();
        lblMethod = new JLabel();
        pnlMethodURI = new JPanel();
        cmdMethod = new JComboBox<>();
        lblURI = new JLabel();
        lblPath = new JLabel();
        txtPath = new JTextField();
        btnHeaders = new JButton();
        tabNav = new JTabbedPane();
        spnReqBody = new JScrollPane();
        txtReqBody = new JTextArea();
        spnReqHeaders = new JScrollPane();
        txtReqHeaders = new JTextArea();
        spnResBody = new JScrollPane();
        txtResBody = new JTextArea();
        spnResHeaders = new JScrollPane();
        txtResHeaders = new JTextArea();
        spnConsole = new JScrollPane();
        txtConsole = new JTextPane();
        pnlFooter = new JPanel();
        lblStatusCode = new JLabel();
        lblCode = new JLabel();
        lblTime = new JLabel();
        lblTimeValue = new JLabel();
        lblAssertValue = new JLabel();
        chbAutoReload = new JCheckBox();
        btnReload = new JButton();
        btnSend = new JButton();
        btnSave = new JButton();

        pnlHeaders.setLayout(new BorderLayout());

        sptHeaders.setBorder(BorderFactory.createEmptyBorder(1, 1, 1, 1));
        sptHeaders.setDividerLocation(150);
        sptHeaders.setDividerSize(3);
        sptHeaders.setOrientation(JSplitPane.VERTICAL_SPLIT);

        pnlHeadersTop.setLayout(new BorderLayout());

        lblHeadersTop.setFont(new Font("Tahoma", 1, 11)); // NOI18N
        ResourceBundle bundle = ResourceBundle.getBundle("com/equinix/amphibia/messages"); // NOI18N
        lblHeadersTop.setText(bundle.getString("sharedHeaders")); // NOI18N
        lblHeadersTop.setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 0));
        pnlHeadersTop.add(lblHeadersTop, BorderLayout.PAGE_START);

        tblHeadersTop.setEnabled(false);
        spnHeadersTop.setViewportView(tblHeadersTop);

        pnlHeadersTop.add(spnHeadersTop, BorderLayout.CENTER);

        sptHeaders.setLeftComponent(pnlHeadersTop);

        pnlHeadersBottom.setLayout(new BorderLayout());

        pnlHeaderBottomHeader.setLayout(new BorderLayout());

        lblHeadersBottom.setFont(new Font("Tahoma", 1, 11)); // NOI18N
        lblHeadersBottom.setText(bundle.getString("headers")); // NOI18N
        lblHeadersBottom.setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 0));
        pnlHeaderBottomHeader.add(lblHeadersBottom, BorderLayout.WEST);

        lblHeaderBottomHint.setForeground(Color.blue);
        lblHeaderBottomHint.setText(bundle.getString("tip_edit_column")); // NOI18N
        pnlHeaderBottomHeader.add(lblHeaderBottomHint, BorderLayout.EAST);

        pnlHeadersBottom.add(pnlHeaderBottomHeader, BorderLayout.NORTH);

        spnHeadersBottom.setViewportView(tblHeadersBottom);

        pnlHeadersBottom.add(spnHeadersBottom, BorderLayout.CENTER);

        pnlHeadersBottomFooter.setLayout(new BoxLayout(pnlHeadersBottomFooter, BoxLayout.LINE_AXIS));

        btnHeadersBottomAdd.setIcon(new ImageIcon(getClass().getResource("/com/equinix/amphibia/icons/plus-icon.png"))); // NOI18N
        btnHeadersBottomAdd.setToolTipText(bundle.getString("addRow")); // NOI18N
        btnHeadersBottomAdd.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                btnHeadersBottomAddActionPerformed(evt);
            }
        });
        pnlHeadersBottomFooter.add(btnHeadersBottomAdd);

        btnHeadersBottomDelete.setIcon(new ImageIcon(getClass().getResource("/com/equinix/amphibia/icons/remove_16.png"))); // NOI18N
        btnHeadersBottomDelete.setToolTipText(bundle.getString("deleteRow")); // NOI18N
        btnHeadersBottomDelete.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                btnHeadersBottomDeleteActionPerformed(evt);
            }
        });
        pnlHeadersBottomFooter.add(btnHeadersBottomDelete);

        lblHeadersBottomError.setForeground(Color.red);
        lblHeadersBottomError.setText(bundle.getString("tip_key_exists")); // NOI18N
        lblHeadersBottomError.setBorder(BorderFactory.createEmptyBorder(1, 10, 1, 10));
        pnlHeadersBottomFooter.add(lblHeadersBottomError);

        pnlHeadersBottom.add(pnlHeadersBottomFooter, BorderLayout.PAGE_END);

        sptHeaders.setRightComponent(pnlHeadersBottom);

        pnlHeaders.add(sptHeaders, BorderLayout.CENTER);

        pnlSaveTestCase.setLayout(new BorderLayout());

        pnlTestsuite.setLayout(new BorderLayout(5, 15));

        lblTestSuite.setFont(new Font("Tahoma", 1, 11)); // NOI18N
        lblTestSuite.setText(bundle.getString("testsuite")); // NOI18N
        pnlTestsuite.add(lblTestSuite, BorderLayout.WEST);

        cmbTestSuite.setModel(testSuitesModel);
        pnlTestsuite.add(cmbTestSuite, BorderLayout.CENTER);

        btnAddTestSuite.setIcon(new ImageIcon(getClass().getResource("/com/equinix/amphibia/icons/plus-icon.png"))); // NOI18N
        btnAddTestSuite.setMargin(new Insets(2, 4, 2, 4));
        btnAddTestSuite.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                btnAddTestSuiteActionPerformed(evt);
            }
        });
        pnlTestsuite.add(btnAddTestSuite, BorderLayout.EAST);

        pnlTestCase.setLayout(new BorderLayout(0, 5));

        lblTestCase.setFont(new Font("Tahoma", 1, 11)); // NOI18N
        lblTestCase.setText(bundle.getString("testcaseName")); // NOI18N
        pnlTestCase.add(lblTestCase, BorderLayout.NORTH);
        pnlTestCase.add(txtTestCase, BorderLayout.CENTER);

        pnlTestsuite.add(pnlTestCase, BorderLayout.SOUTH);

        pnlSaveTestCase.add(pnlTestsuite, BorderLayout.NORTH);

        pnlOptions.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 0));
        pnlOptions.setLayout(new BoxLayout(pnlOptions, BoxLayout.Y_AXIS));

        ckbReqSchema.setSelected(true);
        ckbReqSchema.setText(bundle.getString("requestBodySchema")); // NOI18N
        pnlOptions.add(ckbReqSchema);

        ckbResSchema.setSelected(true);
        ckbResSchema.setText(bundle.getString("resultBodySchema")); // NOI18N
        pnlOptions.add(ckbResSchema);

        ckbSaveExample.setSelected(true);
        ckbSaveExample.setText(bundle.getString("saveResultExample")); // NOI18N
        pnlOptions.add(ckbSaveExample);

        ckbStatusAssert.setSelected(true);
        ckbStatusAssert.setText(bundle.getString("assertionStatus")); // NOI18N
        pnlOptions.add(ckbStatusAssert);

        pnlSaveTestCase.add(pnlOptions, BorderLayout.CENTER);

        GridBagLayout jPanel2Layout = new GridBagLayout();
        jPanel2Layout.columnWidths = new int[] {0, 5, 0};
        jPanel2Layout.rowHeights = new int[] {0, 2, 0, 2, 0};
        pnlTestCaseFooter.setLayout(jPanel2Layout);

        lblTestCaseFuncName.setText(bundle.getString("functionName")); // NOI18N
        gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = GridBagConstraints.WEST;
        pnlTestCaseFooter.add(lblTestCaseFuncName, gridBagConstraints);
        gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        pnlTestCaseFooter.add(txtTestCaseFuncName, gridBagConstraints);

        lblSummary.setText(bundle.getString("summary")); // NOI18N
        gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.anchor = GridBagConstraints.WEST;
        pnlTestCaseFooter.add(lblSummary, gridBagConstraints);
        gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.fill = GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        pnlTestCaseFooter.add(txtSummary, gridBagConstraints);

        pnlSaveTestCase.add(pnlTestCaseFooter, BorderLayout.PAGE_END);

        pnlWaitOverlay.setOpaque(false);
        pnlWaitOverlay.setLayout(new GridBagLayout());

        lblAnimation.setHorizontalAlignment(SwingConstants.CENTER);
        lblAnimation.setIcon(new ImageIcon(getClass().getResource("/com/equinix/amphibia/icons/ajax-loader.gif"))); // NOI18N
        lblAnimation.setOpaque(true);
        pnlWaitOverlay.add(lblAnimation, new GridBagConstraints());

        setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        setLayout(new OverlayLayout(this));

        pnlComponents.setLayout(new BorderLayout());

        pnlTop.setLayout(new GridBagLayout());

        lblEndpoint.setText(bundle.getString("endpoint")); // NOI18N
        gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.anchor = GridBagConstraints.WEST;
        pnlTop.add(lblEndpoint, gridBagConstraints);

        pnlEndpoint.setLayout(new FlowLayout(FlowLayout.LEFT));

        cmdEndpoint.setPreferredSize(new Dimension(450, 20));
        cmdEndpoint.addItemListener(new ItemListener() {
            public void itemStateChanged(ItemEvent evt) {
                cmdEndpointItemStateChanged(evt);
            }
        });
        pnlEndpoint.add(cmdEndpoint);

        btnEndpointInfo.setIcon(new ImageIcon(getClass().getResource("/com/equinix/amphibia/icons/info_16.png"))); // NOI18N
        btnEndpointInfo.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnEndpointInfo.setFocusPainted(false);
        btnEndpointInfo.setPreferredSize(new Dimension(30, 22));
        btnEndpointInfo.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                btnEndpointInfoActionPerformed(evt);
            }
        });
        pnlEndpoint.add(btnEndpointInfo);

        gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.fill = GridBagConstraints.HORIZONTAL;
        pnlTop.add(pnlEndpoint, gridBagConstraints);

        lblInterface.setText(bundle.getString("interface")); // NOI18N
        gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.gridy = 2;
        gridBagConstraints.anchor = GridBagConstraints.WEST;
        pnlTop.add(lblInterface, gridBagConstraints);

        pnlInterface.setLayout(new FlowLayout(FlowLayout.LEFT));

        cmdInterface.setPreferredSize(new Dimension(450, 20));
        cmdInterface.addItemListener(new ItemListener() {
            public void itemStateChanged(ItemEvent evt) {
                cmdInterfaceItemStateChanged(evt);
            }
        });
        pnlInterface.add(cmdInterface);

        btnInterfaceInfo.setIcon(new ImageIcon(getClass().getResource("/com/equinix/amphibia/icons/info_16.png"))); // NOI18N
        btnInterfaceInfo.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnInterfaceInfo.setFocusPainted(false);
        btnInterfaceInfo.setPreferredSize(new Dimension(30, 22));
        btnInterfaceInfo.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                btnInterfaceInfoActionPerformed(evt);
            }
        });
        pnlInterface.add(btnInterfaceInfo);

        gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.gridy = 2;
        gridBagConstraints.fill = GridBagConstraints.HORIZONTAL;
        pnlTop.add(pnlInterface, gridBagConstraints);

        lblMethod.setText(bundle.getString("method")); // NOI18N
        gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.gridy = 3;
        gridBagConstraints.anchor = GridBagConstraints.WEST;
        pnlTop.add(lblMethod, gridBagConstraints);

        pnlMethodURI.setLayout(new BorderLayout(15, 0));

        cmdMethod.setModel(new DefaultComboBoxModel<>(new String[] { "GET", "POST", "DELETE", "PUT", "PATCH", "OPTIONS" }));
        pnlMethodURI.add(cmdMethod, BorderLayout.WEST);

        lblURI.setText("http://");
        pnlMethodURI.add(lblURI, BorderLayout.CENTER);

        gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.gridy = 3;
        gridBagConstraints.fill = GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new Insets(5, 5, 5, 5);
        pnlTop.add(pnlMethodURI, gridBagConstraints);

        lblPath.setText(bundle.getString("path")); // NOI18N
        gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.gridy = 4;
        gridBagConstraints.anchor = GridBagConstraints.WEST;
        pnlTop.add(lblPath, gridBagConstraints);

        txtPath.addKeyListener(new KeyAdapter() {
            public void keyReleased(KeyEvent evt) {
                txtPathKeyReleased(evt);
            }
        });
        gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.gridy = 4;
        gridBagConstraints.fill = GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new Insets(5, 5, 5, 5);
        pnlTop.add(txtPath, gridBagConstraints);

        btnHeaders.setText(bundle.getString("headers")); // NOI18N
        btnHeaders.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                btnHeadersActionPerformed(evt);
            }
        });
        gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.gridy = 4;
        pnlTop.add(btnHeaders, gridBagConstraints);

        pnlComponents.add(pnlTop, BorderLayout.NORTH);

        txtReqBody.setColumns(20);
        txtReqBody.setRows(5);
        spnReqBody.setViewportView(txtReqBody);

        tabNav.addTab(bundle.getString("requestBody"), spnReqBody); // NOI18N

        txtReqHeaders.setColumns(20);
        txtReqHeaders.setRows(5);
        spnReqHeaders.setViewportView(txtReqHeaders);

        tabNav.addTab(bundle.getString("requestHeaders"), spnReqHeaders); // NOI18N

        txtResBody.setEditable(false);
        txtResBody.setColumns(20);
        txtResBody.setRows(5);
        spnResBody.setViewportView(txtResBody);

        tabNav.addTab(bundle.getString("responseBody"), spnResBody); // NOI18N

        txtResHeaders.setEditable(false);
        txtResHeaders.setColumns(20);
        txtResHeaders.setRows(5);
        spnResHeaders.setViewportView(txtResHeaders);

        tabNav.addTab(bundle.getString("responseHeaders"), spnResHeaders); // NOI18N

        txtConsole.setEditable(false);
        spnConsole.setViewportView(txtConsole);

        tabNav.addTab(bundle.getString("console"), spnConsole); // NOI18N

        pnlComponents.add(tabNav, BorderLayout.CENTER);

        pnlFooter.setLayout(new GridBagLayout());

        lblStatusCode.setText(bundle.getString("statusCode")); // NOI18N
        pnlFooter.add(lblStatusCode, new GridBagConstraints());

        lblCode.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 100));
        gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.fill = GridBagConstraints.HORIZONTAL;
        pnlFooter.add(lblCode, gridBagConstraints);

        lblTime.setText(bundle.getString("time")); // NOI18N
        pnlFooter.add(lblTime, new GridBagConstraints());

        lblTimeValue.setBorder(BorderFactory.createEmptyBorder(1, 5, 1, 5));
        gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.anchor = GridBagConstraints.WEST;
        gridBagConstraints.weightx = 1.0;
        pnlFooter.add(lblTimeValue, gridBagConstraints);

        lblAssertValue.setForeground(Color.red);
        lblAssertValue.setText(bundle.getString("assertFailed")); // NOI18N
        lblAssertValue.setBorder(BorderFactory.createEmptyBorder(1, 5, 1, 5));
        gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.anchor = GridBagConstraints.WEST;
        gridBagConstraints.weightx = 1.0;
        pnlFooter.add(lblAssertValue, gridBagConstraints);

        chbAutoReload.setText(bundle.getString("autoReload")); // NOI18N
        pnlFooter.add(chbAutoReload, new GridBagConstraints());

        btnReload.setText(bundle.getString("mnuReload")); // NOI18N
        btnReload.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                btnReloadActionPerformed(evt);
            }
        });
        pnlFooter.add(btnReload, new GridBagConstraints());

        btnSend.setText(bundle.getString("send")); // NOI18N
        btnSend.setEnabled(false);
        btnSend.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                btnSendActionPerformed(evt);
            }
        });
        pnlFooter.add(btnSend, new GridBagConstraints());

        btnSave.setText(bundle.getString("save")); // NOI18N
        btnSave.setEnabled(false);
        btnSave.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                btnSaveActionPerformed(evt);
            }
        });
        gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.insets = new Insets(5, 5, 5, 5);
        pnlFooter.add(btnSave, gridBagConstraints);

        pnlComponents.add(pnlFooter, BorderLayout.SOUTH);

        add(pnlComponents);
    }// </editor-fold>//GEN-END:initComponents

    private void btnEndpointInfoActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnEndpointInfoActionPerformed
        wizard.mainPanel.globalVarsDialog.openDialog();
    }//GEN-LAST:event_btnEndpointInfoActionPerformed

    private void btnHeadersActionPerformed(ActionEvent evt) {//GEN-FIRST:event_btnHeadersActionPerformed
        lblHeadersBottomError.setVisible(false);
        updateHeaders();
        headersDialog.setVisible(true);
    }
    
    private void updateHeaders() {
        Wizard.ComboItem item = (Wizard.ComboItem) cmdInterface.getSelectedItem();
        if (item != null) {
            JSONObject headers = (JSONObject) item.headers;
            Object[][] sharedHeaders = new Object[headers.size()][2];
            int row = 0;
            for (Object key : headers.keySet()) {
                sharedHeaders[row][0] = key;
                sharedHeaders[row][1] = headers.get(key);
                row++;
            }
            tblHeadersTop.setModel(new DefaultTableModel(sharedHeaders, wizard.headerColumns));
        }
        if (openedNode != null) {
            JSONObject headers = openedNode.info.getRequestHeader(openedNode, new JSONObject());
            Object[][] testcaseHeaders = new Object[][] {};
            if (openedNode != null) {
                testcaseHeaders = new Object[headers.size()][2];
                int row = 0;
                for (Object key : headers.keySet()) {
                    testcaseHeaders[row][0] = key;
                    testcaseHeaders[row][1] = headers.get(key);
                    row++;
                }
            }
            tblHeadersBottom.setModel(new DefaultTableModel(testcaseHeaders, wizard.headerColumns));
        }
    }//GEN-LAST:event_btnHeadersActionPerformed

    private void btnSendActionPerformed(ActionEvent evt) {//GEN-FIRST:event_btnSendActionPerformed
        pnlWaitOverlay.setVisible(true);
        new Thread() {
            @Override
            public void run() {
                Result result = new Result();
                txtConsole.setText("");
                txtConsole.setAlignmentX(0);
                txtResBody.setText("");
                lblCode.setText("");
                lblTimeValue.setText("");
                lblAssertValue.setVisible(false);

                try {
                    HttpConnection con = new HttpConnection(WizardTab.this);
                    Properties properties = new Properties(new JSONObject(), new JSONObject());
                    if (MainPanel.selectedNode != null) {
                        properties = MainPanel.selectedNode.getCollection().getProjectProperties();
                    }
                    String name = openedNode != null ? openedNode.jsonObject().getString("name") : bundle.getString("wizard");
                    result = con.request(openedNode, properties, name, cmdMethod.getSelectedItem().toString(), lblURI.getText(), IO.toJSONObject(txtReqHeaders.getText()), txtReqBody.getText());
                    Amphibia.setText(txtResBody, spnResBody, result.content);
                    tabNav.setSelectedIndex(2);
                    if (openedNode != null) {
                        Properties props = openedNode.info.properties.cloneProperties();
                        con.propertyTransfer(openedNode, props, result);
                        if(!con.assertionValidation(openedNode, props, result)) {
                            lblAssertValue.setVisible(true);
                            tabNav.setSelectedIndex(4);
                        }
                    }
                } catch (Exception ex) {
                    logger.log(Level.SEVERE, ex.toString(), ex);
                    Amphibia.setText(txtResBody, spnResBody, result.content + "\n\n");
                    tabNav.setSelectedIndex(4);
                }
                Amphibia.setText(txtResHeaders, spnResHeaders, result.headers.toString(4));
                lblTimeValue.setText(String.valueOf(result.time) + " ms");
                String statusCode = String.valueOf(result.statusCode);
                lblCode.setText(statusCode);
                pnlWaitOverlay.setVisible(false);
            }
        }.start();
    }//GEN-LAST:event_btnSendActionPerformed

    private void btnSaveActionPerformed(ActionEvent evt) {//GEN-FIRST:event_btnSaveActionPerformed
        openTestCaseDialog();
    }//GEN-LAST:event_btnSaveActionPerformed

    private void btnInterfaceInfoActionPerformed(ActionEvent evt) {//GEN-FIRST:event_btnInterfaceInfoActionPerformed
        wizard.openInterfacePanel(((Wizard.ComboItem) cmdInterface.getSelectedItem()).id);
    }//GEN-LAST:event_btnInterfaceInfoActionPerformed

    private void cmdEndpointItemStateChanged(ItemEvent evt) {//GEN-FIRST:event_cmdEndpointItemStateChanged
        refresh();
    }//GEN-LAST:event_cmdEndpointItemStateChanged

    private void cmdInterfaceItemStateChanged(ItemEvent evt) {//GEN-FIRST:event_cmdInterfaceItemStateChanged
        refresh();
    }//GEN-LAST:event_cmdInterfaceItemStateChanged

    private void btnHeadersBottomAddActionPerformed(ActionEvent evt) {//GEN-FIRST:event_btnHeadersBottomAddActionPerformed
        ((DefaultTableModel)tblHeadersBottom.getModel()).addRow(new Object[][]{null, null});
    }//GEN-LAST:event_btnHeadersBottomAddActionPerformed

    private void btnHeadersBottomDeleteActionPerformed(ActionEvent evt) {//GEN-FIRST:event_btnHeadersBottomDeleteActionPerformed
        if (tblHeadersBottom.getRowCount() > 0) {
            int index = tblHeadersBottom.getSelectedRow();
            DefaultTableModel model = (DefaultTableModel) tblHeadersBottom.getModel();
            model.removeRow(index == -1 ? model.getRowCount() - 1 : index);
        }
    }//GEN-LAST:event_btnHeadersBottomDeleteActionPerformed

    private void txtPathKeyReleased(KeyEvent evt) {//GEN-FIRST:event_txtPathKeyReleased
        refresh();
    }//GEN-LAST:event_txtPathKeyReleased

    private void btnAddTestSuiteActionPerformed(ActionEvent evt) {//GEN-FIRST:event_btnAddTestSuiteActionPerformed
        int length = testSuitesModel.getSize();
        String[] names = new String[length];
        for (int i = 0; i < length; i++) {
            names[i] = testSuitesModel.getElementAt(i).toString();
        }
        String name = Amphibia.instance.inputDialog("testsuite", "", names, saveTestCase.getParent());
        if (name != null && !name.isEmpty()) {
            testSuitesModel.addElement(name);
            cmbTestSuite.setSelectedIndex(length);
            txtTestCase.setEnabled(true);
        }
    }//GEN-LAST:event_btnAddTestSuiteActionPerformed

    private void btnReloadActionPerformed(ActionEvent evt) {//GEN-FIRST:event_btnReloadActionPerformed
        if (openedNode.getParent() == null) {
            return;
        }
        JSONObject headers = openedNode.info.getRequestHeader(openedNode);
        JSONObject json = openedNode.jsonObject();
        try {
            Amphibia.setText(txtReqHeaders, spnReqHeaders, IO.prettyJson(openedNode.info.properties.replace(headers.toString())));
        } catch (Exception ex) {
            addError(ex, bundle.getString("error_open_json"));
        }

        if (json != null) {
            txtPath.setText(json.getString("reqPath"));
            txtTestCase.setText(json.getString("name"));
        }
        
        try {
            Amphibia.setText(txtReqBody, spnReqBody, openedNode.info.getRequestBody());
        } catch (Exception ex) {
            addError(ex, bundle.getString("error_open_json"));
        }
        testSuitesModel.addElement(openedNode.info.testSuite.getString("name"));
        txtSummary.setText(openedNode.info.testCaseInfo.getString("summary"));
        txtTestCaseFuncName.setText(openedNode.info.testCaseInfo.getString("operationId"));
        txtConsole.setText("");
        txtResBody.setText("");
        txtResHeaders.setText("");
        updateHeaders();
    }//GEN-LAST:event_btnReloadActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    JButton btnAddTestSuite;
    JButton btnEndpointInfo;
    JButton btnHeaders;
    JButton btnHeadersBottomAdd;
    JButton btnHeadersBottomDelete;
    JButton btnInterfaceInfo;
    JButton btnReload;
    JButton btnSave;
    JButton btnSend;
    JCheckBox chbAutoReload;
    JCheckBox ckbReqSchema;
    JCheckBox ckbResSchema;
    JCheckBox ckbSaveExample;
    JCheckBox ckbStatusAssert;
    JComboBox<String> cmbTestSuite;
    JComboBox<String> cmdEndpoint;
    JComboBox<String> cmdInterface;
    JComboBox<String> cmdMethod;
    JLabel lblAnimation;
    JLabel lblAssertValue;
    JLabel lblCode;
    JLabel lblEndpoint;
    JLabel lblHeaderBottomHint;
    JLabel lblHeadersBottom;
    JLabel lblHeadersBottomError;
    JLabel lblHeadersTop;
    JLabel lblInterface;
    JLabel lblMethod;
    JLabel lblPath;
    JLabel lblStatusCode;
    JLabel lblSummary;
    JLabel lblTestCase;
    JLabel lblTestCaseFuncName;
    JLabel lblTestSuite;
    JLabel lblTime;
    JLabel lblTimeValue;
    JLabel lblURI;
    JPanel pnlComponents;
    JPanel pnlEndpoint;
    JPanel pnlFooter;
    JPanel pnlHeaderBottomHeader;
    JPanel pnlHeaders;
    JPanel pnlHeadersBottom;
    JPanel pnlHeadersBottomFooter;
    JPanel pnlHeadersTop;
    JPanel pnlInterface;
    JPanel pnlMethodURI;
    JPanel pnlOptions;
    JPanel pnlSaveTestCase;
    JPanel pnlTestCase;
    JPanel pnlTestCaseFooter;
    JPanel pnlTestsuite;
    JPanel pnlTop;
    JPanel pnlWaitOverlay;
    JScrollPane spnConsole;
    JScrollPane spnHeadersBottom;
    JScrollPane spnHeadersTop;
    JScrollPane spnReqBody;
    JScrollPane spnReqHeaders;
    JScrollPane spnResBody;
    JScrollPane spnResHeaders;
    JSplitPane sptHeaders;
    JTabbedPane tabNav;
    JTable tblHeadersBottom;
    JTable tblHeadersTop;
    JTextPane txtConsole;
    JTextField txtPath;
    JTextArea txtReqBody;
    JTextArea txtReqHeaders;
    JTextArea txtResBody;
    JTextArea txtResHeaders;
    JTextField txtSummary;
    JTextField txtTestCase;
    JTextField txtTestCaseFuncName;
    // End of variables declaration//GEN-END:variables

    @Override
    public IHttpConnection info(String text) {
        return info(text, false);
    }

    @Override
    public IHttpConnection info(String text, boolean isBold) {
        SimpleAttributeSet attr = new SimpleAttributeSet();
        StyleConstants.setBold(attr, isBold);
        return info(text, attr);
    }
    
    @Override
    public IHttpConnection info(String text, Color color) {
        SimpleAttributeSet attr = new SimpleAttributeSet();
        StyleConstants.setForeground(attr, color);
        return info(text, attr);
    }

    @Override
    public IHttpConnection info(String text, SimpleAttributeSet attr) {
        StyledDocument doc = txtConsole.getStyledDocument();
        try {
            doc.insertString(doc.getLength(), text, attr);
        } catch (BadLocationException ex) {
            logger.log(Level.SEVERE, ex.toString(), ex);
        }
        return this;
    }

    @Override
    public DefaultMutableTreeNode addError(String error) {
        return wizard.mainPanel.editor.addError(error);
    }

    @Override
    public DefaultMutableTreeNode addError(Throwable t, String error) {
        return wizard.mainPanel.editor.addError(t, error);
    }

    class EndPoint {

        Object endPointName;
        Object endPointValue;

        public EndPoint(Object name, Object value) {
            endPointName = name;
            endPointValue = value;
        }

        @Override
        public String toString() {
            return endPointName + ": " + endPointValue;
        }
    }

    public static class ExtendedStyledEditorKit extends StyledEditorKit {
        private static final ViewFactory defaultFactory = new ViewFactory() {
            final ViewFactory styledEditorKitFactory = new StyledEditorKit().getViewFactory();
            
            @Override
            public View create(Element elem) {
                String elementName = elem.getName();
                if (elementName != null) {
                    if (elementName.equals(AbstractDocument.ParagraphElementName)) {
                        return new ExtendedParagraphView(elem);
                    }
                }
                return styledEditorKitFactory.create(elem);
            }
        };
        
        @Override
        public ViewFactory getViewFactory() {
            return defaultFactory;
        }
    }

    static class ExtendedParagraphView extends ParagraphView {
        public ExtendedParagraphView(Element elem) {
            super(elem);
        }

        @Override
        public float getMinimumSpan(int axis) {
            return super.getPreferredSpan(axis);
        }
    }
}