/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.equinix.amphibia.components;

import static com.equinix.amphibia.Amphibia.TYPE;
import static com.equinix.amphibia.Amphibia.NAME;
import static com.equinix.amphibia.Amphibia.VALUE;

import com.equinix.amphibia.agent.converter.Converter;

import com.equinix.amphibia.Amphibia;
import com.equinix.amphibia.IO;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
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
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.UUID;
import java.util.prefs.Preferences;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.CellEditor;
import javax.swing.DefaultComboBoxModel;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

/**
 *
 * @author dgofman
 */
public class Wizard extends javax.swing.JPanel {
    
    private ResourceBundle bundle;
    private JDialog interfaceDialog;
    private JButton applyInterfaceButton;
    private JButton addInterfaceButton;
    private JButton cancelInterfaceButton;
    private WizardTab wizardTab;
    private int headerSaveIndex;
    private TreeCollection selectedCollection;
    private Map<Object, Object> endpoints;
    private JSONArray globalVariables;
    
    MainPanel mainPanel;
   
    public final Object[] headerColumns;
    
    private final DefaultTableModel headersModel;
    private final DefaultComboBoxModel interfaceNameModel;

    private final Preferences userPreferences = Amphibia.getUserPreferences();
    
    /**
     * Creates new form Wizard
     */
    public Wizard() {
        bundle = Amphibia.getBundle();
        
        endpoints = new HashMap<>();

        headerColumns = new String [] {
            bundle.getString("key"),
            bundle.getString("value")
        };
        
        headersModel = new DefaultTableModel();
        interfaceNameModel = new DefaultComboBoxModel();
        
        initComponents();
        tabNav.setComponentAt(0, wizardTab);

        applyInterfaceButton = new JButton(bundle.getString("apply"));
        applyInterfaceButton.addActionListener((ActionEvent evt) -> {
            saveSelectedModel(headerSaveIndex);
            TreeIconNode node = MainPanel.selectedNode.getCollection().project;
            JSONArray interfaces = new JSONArray();
            for (int i = 0; i < interfaceNameModel.getSize(); i++) {
                interfaces.add(((ComboItem)interfaceNameModel.getElementAt(i)).json);
            }
            node.jsonObject().element("interfaces", interfaces);
            
            if (!globalVariables.isEmpty()) {
                mainPanel.globalVarsDialog.mergeVariables(globalVariables);
                JSONArray globals = node.jsonObject().getJSONArray("globals");
                globalVariables.forEach((newItem) -> {
                    for (int i = globals.size() - 1; i >= 0; i--) {
                        if (globals.getJSONObject(i).getString("name").equals(((JSONObject)newItem).getString("name"))) {
                            globals.remove(i);
                            break;
                        }
                    }
                    globals.add(newItem);
                });
                globalVariables = new JSONArray();
            }
            
            mainPanel.saveNodeValue(node);
            interfaceDialog.setVisible(false);
            updateInterfaces();
        });
        addInterfaceButton = new JButton(bundle.getString("newInterface"));
        addInterfaceButton.addActionListener((ActionEvent evt) -> {
            JSONObject json = new JSONObject();
            json.element("type", "rest");
            if(newInterface(json)) {
                reset();
            }
        });
        cancelInterfaceButton = new JButton(bundle.getString("cancel"));
        cancelInterfaceButton.addActionListener((ActionEvent evt) -> {
            interfaceDialog.setVisible(false);
        });

        interfaceDialog = Amphibia.createDialog(pnlInterface, new Object[]{addInterfaceButton, applyInterfaceButton, cancelInterfaceButton}, true);
        interfaceDialog.setSize(new Dimension(600, 500));
    }
    
    public void setMainPanel(MainPanel mainPanel) {
        this.mainPanel = mainPanel;
        EventQueue.invokeLater(() -> {
            interfaceDialog.setLocationRelativeTo(mainPanel);
        });
    }
    
    public DefaultComboBoxModel getInterfaceNameModel() {
        return interfaceNameModel;
    }
    
    public void selectNode(TreeIconNode node) {
        if (node != null && node.getCollection() != selectedCollection) {
            selectedCollection = node.getCollection();
            updateInterfaces();
        }
    }
    
    public void updateInterfaces() {
        interfaceNameModel.removeAllElements();
        JSONObject json = selectedCollection.project.jsonObject();
        JSONArray projectResources = selectedCollection.project.jsonObject().getJSONArray("projectResources");
        Map<String,Boolean> interfaceIds = new HashMap<>();
        projectResources.forEach((item) -> {
            interfaceIds.put(((JSONObject) item).getString("interfaceId"), true);
        });
        json.getJSONArray("interfaces").forEach((item) -> {
            JSONObject iJson = IO.toJSONObject(item.toString());
            ComboItem comboItem = new ComboItem(iJson, !interfaceIds.containsKey(iJson.getString("id")));
            interfaceNameModel.addElement(comboItem);
        }); 

        for (int i = 0; i < tabNav.getTabCount(); i++) {
            if (tabNav.getComponent(i) instanceof WizardTab) {
                WizardTab tab = (WizardTab) tabNav.getComponent(i);
                tab.updateInterfaces();
                tab.refresh();
            }
        }
    }
    
    public void updateEndPoints() {
        Amphibia.SelectedEnvironment env = Amphibia.instance.getSelectedEnvironment();
        if (env != null) {
            endpoints = new HashMap<>();
            for (Object[] item : env.data) {
                if (Converter.ENDPOINT.equals(item[TYPE]) &&
                        item[NAME] != null && item[VALUE] != null &&
                        !item[NAME].toString().trim().isEmpty() && !item[VALUE].toString().trim().isEmpty()) {
                    endpoints.put(item[NAME], item[VALUE]);
                }
            }
            for (int i = 0; i < tabNav.getTabCount(); i++) {
                Component tab = tabNav.getComponentAt(i);
                if (tab instanceof WizardTab) {
                    ((WizardTab)tab).updateEndPoints(endpoints);
                }
            }
        }
    }
    
    public void replaceNode(TreeIconNode node) {
        for (int i = 0; i < tabNav.getTabCount(); i++) {
            Component tab = tabNav.getComponentAt(i);
            if (tab instanceof WizardTab) {
                ((WizardTab)tab).replaceNode(node);
            }
        }       
    }
    
    public void removeDeadNodes() {
        for (int i = tabNav.getTabCount() - 1; i > 0; i--) { //till first tab
            if (tabNav.getComponentAt(i) instanceof WizardTab) {
                WizardTab tab = (WizardTab) tabNav.getComponentAt(i);
                if (tab.getNode() != null && tab.getNode().getParent() == null) {
                    tabNav.remove(i);
                }
            }
        }       
    }

    private boolean newInterface(JSONObject json) {
        boolean b = saveSelectedModel(cmdName.getSelectedIndex());
        if (b) {
            String[] names = new String[interfaceNameModel.getSize()];
            for (int i = 0; i < interfaceNameModel.getSize(); i++) {
                names[i] = interfaceNameModel.getElementAt(i).toString();
            }
            String name = Amphibia.instance.inputDialog("newInterfaceName", "", names, interfaceDialog.getParent());
            b = name != null && !name.isEmpty();
            if (b) {
                json.element("id", UUID.randomUUID().toString());
                json.element("name", name);
                json.element("basePath", "");
                interfaceNameModel.addElement(new ComboItem(json, true));
            }
            headerSaveIndex = interfaceNameModel.getSize() - 1;
            cmdName.setSelectedIndex(headerSaveIndex);
        }
        return b;
    }
    
    private boolean saveSelectedModel(int index) {
        if (index != -1) {
            CellEditor cellEditor = tblEnvHeaders.getCellEditor();
            if (cellEditor != null) {
                cellEditor.stopCellEditing();
            }
            ComboItem item = (ComboItem) interfaceNameModel.getElementAt(index);
            JSONObject headers = (JSONObject) item.json.getOrDefault("headers", new JSONObject());
            item.json.element("basePath", txtBasePath.getText());
            headers.clear();
            for (int r = 0; r < headersModel.getRowCount(); r++) {
                Object key = headersModel.getValueAt(r, 0);
                if (key != null && !key.toString().isEmpty()) {
                    if (headers.containsKey(key)) {
                        lblError.setVisible(true);
                        return false;
                    }
                    headers.put(key, headersModel.getValueAt(r, 1));
                }
            }
            item.json.element("headers", headers);
            headerSaveIndex = index;
        }
        return true;
    }

    private void reset() {
        lblError.setVisible(false);
        txtBasePath.setText("/");
        globalVariables = new JSONArray();
        headersModel.setDataVector(new Object [][] {
            {null, null},
            {null, null},
            {null, null},
            {null, null},
            {null, null}
        }, headerColumns);
    }
    
    @SuppressWarnings("NonPublicExported")
    public ComboItem createDefaultItem() {
        JSONObject json = new JSONObject();
        json.element("id", "");
        json.element("name", Amphibia.getBundle().getString("none"));
        json.element("basePath", "");
        return new ComboItem(json, false);
    }
    
    public void openInterfacePanel() {
        TreeIconNode node = MainPanel.selectedNode;
        if (node != null && node.info != null && node.info.resource != null) {
            openInterfacePanel(node.info.resource.getString("interfaceId"));
        } else {
            openInterfacePanel(null);
        }
    }
    
    public void openInterfacePanel(String interfaceId) {
        if (MainPanel.selectedNode != null) {
            reset();
            chbShowInterfaces.setSelected(userPreferences.getBoolean(Amphibia.P_INTERFACE, true));
            cmdNameItemStateChanged(null);
            if (interfaceId != null) {
                for (int i = 0; i < interfaceNameModel.getSize(); i++) {
                    ComboItem comboItem = (ComboItem) interfaceNameModel.getElementAt(i);
                    if (comboItem.id.equals(interfaceId)) {
                        cmdName.setSelectedIndex(i);
                        break;
                    }
                }
            }
            interfaceDialog.setVisible(true);
            updateInterfaces();
        }
    }
    
    public void addWizardTab() {
        addWizardTab(MainPanel.selectedNode);
        mainPanel.tabRight.setSelectedIndex(1);
    }

    private void addWizardTab(TreeIconNode node) {
        int index = tabNav.getTabCount();
        WizardTab newTab = new WizardTab(this, node);
        node.info.states.set(TreeIconNode.STATE_OPEN_PROJECT_OR_WIZARD_TAB,  1);
        node.getCollection().profile.saveState(node);
        tabNav.add(newTab);
        tabNav.setTabComponentAt(index, getTitlePanel(newTab, node.getTreeIconUserObject().getIcon(), node.getLabel()));
        tabNav.setSelectedIndex(index);
        newTab.updateEndPoints(endpoints);
        newTab.refresh();
    }
    
    private JPanel getTitlePanel(final JPanel panel, Icon icon, String title) {
        JPanel titlePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        titlePanel.setOpaque(false);
        JLabel label = new JLabel(title);
        label.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 15));
        label.setIcon(icon);
        titlePanel.add(label);
        JLabel closeButton = new JLabel("x");
        closeButton.setBorder(BorderFactory.createEmptyBorder(2, 2, 10, 2));
        closeButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        closeButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                tabNav.remove(panel);
            }
        });
        titlePanel.add(closeButton);
        return titlePanel;
    }
    
    public void openTabs() {
        Enumeration projects = mainPanel.treeNode.children();
        while (projects.hasMoreElements()) {
            TreeIconNode project = (TreeIconNode) projects.nextElement();
            TreeCollection collection = project.getCollection();
            if (collection.isOpen()) {
                Enumeration testsuites = collection.testsuites.children();
                while (testsuites.hasMoreElements()) {
                    TreeIconNode testsuite = (TreeIconNode) testsuites.nextElement();
                    Enumeration testcases = testsuite.children();
                    while (testcases.hasMoreElements()) {
                        TreeIconNode testcase = (TreeIconNode) testcases.nextElement();
                        if (testcase.info.states.getInt(TreeIconNode.STATE_OPEN_PROJECT_OR_WIZARD_TAB) == 1) {
                            addWizardTab(testcase);
                        }
                        Enumeration teststeps = testcase.children();
                        while (teststeps.hasMoreElements()) {
                            TreeIconNode teststep = (TreeIconNode) teststeps.nextElement();
                                if (teststep.info.states.getInt(TreeIconNode.STATE_OPEN_PROJECT_OR_WIZARD_TAB) == 1) {
                                addWizardTab(teststep);
                            }
                        }
                    }
                }
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

        pnlInterface = new JPanel();
        pnlEnvTop = new JPanel();
        lblName = new JLabel();
        cmdName = new JComboBox<>();
        btnClone = new JButton();
        btnDelete = new JButton();
        lblBasePath = new JLabel();
        txtBasePath = new JTextField();
        pnlEnvCenter = new JPanel();
        lblEnvHeaders = new JLabel();
        spnEnvHeaders = new JScrollPane();
        tblEnvHeaders = new JTable();
        pnlEnvFooter = new JPanel();
        btnAddRow = new JButton();
        btnDeleteRow = new JButton();
        lblError = new JLabel();
        chbShowInterfaces = new JCheckBox();
        pnlAddHeader = new JPanel();
        lblHeaderName = new JLabel();
        txtHeaderName = new JTextField();
        lblHeaderValue = new JLabel();
        txtHeaderValue = new JTextField();
        ckbAsGlobal = new JCheckBox();
        tabNav = new JTabbedPane();

        pnlInterface.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        pnlInterface.setLayout(new BorderLayout());

        pnlEnvTop.setLayout(new GridBagLayout());

        ResourceBundle bundle = ResourceBundle.getBundle("com/equinix/amphibia/messages"); // NOI18N
        lblName.setText(bundle.getString("interfaceName")); // NOI18N
        gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.anchor = GridBagConstraints.WEST;
        pnlEnvTop.add(lblName, gridBagConstraints);

        cmdName.setModel(interfaceNameModel);
        cmdName.addItemListener(new ItemListener() {
            public void itemStateChanged(ItemEvent evt) {
                cmdNameItemStateChanged(evt);
            }
        });
        gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.fill = GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new Insets(0, 5, 0, 5);
        pnlEnvTop.add(cmdName, gridBagConstraints);

        btnClone.setIcon(new ImageIcon(getClass().getResource("/com/equinix/amphibia/icons/clone_16.png"))); // NOI18N
        btnClone.setToolTipText(bundle.getString("clone")); // NOI18N
        btnClone.setMargin(new Insets(2, 2, 2, 2));
        btnClone.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                btnCloneActionPerformed(evt);
            }
        });
        pnlEnvTop.add(btnClone, new GridBagConstraints());

        btnDelete.setIcon(new ImageIcon(getClass().getResource("/com/equinix/amphibia/icons/close_16.png"))); // NOI18N
        btnDelete.setToolTipText(bundle.getString("delete")); // NOI18N
        btnDelete.setMargin(new Insets(2, 2, 2, 2));
        btnDelete.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                btnDeleteActionPerformed(evt);
            }
        });
        pnlEnvTop.add(btnDelete, new GridBagConstraints());

        lblBasePath.setText(bundle.getString("basePath")); // NOI18N
        gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.gridy = 2;
        gridBagConstraints.anchor = GridBagConstraints.WEST;
        pnlEnvTop.add(lblBasePath, gridBagConstraints);

        txtBasePath.setText("/");
        txtBasePath.setToolTipText("");
        gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.gridy = 2;
        gridBagConstraints.fill = GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new Insets(5, 5, 5, 5);
        pnlEnvTop.add(txtBasePath, gridBagConstraints);

        pnlInterface.add(pnlEnvTop, BorderLayout.NORTH);

        pnlEnvCenter.setLayout(new BorderLayout());

        lblEnvHeaders.setFont(new Font("Tahoma", 1, 11)); // NOI18N
        lblEnvHeaders.setText(bundle.getString("headers")); // NOI18N
        lblEnvHeaders.setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 0));
        pnlEnvCenter.add(lblEnvHeaders, BorderLayout.PAGE_START);

        tblEnvHeaders.setModel(this.headersModel);
        spnEnvHeaders.setViewportView(tblEnvHeaders);

        pnlEnvCenter.add(spnEnvHeaders, BorderLayout.CENTER);

        pnlEnvFooter.setBorder(BorderFactory.createEmptyBorder(5, 0, 10, 0));
        pnlEnvFooter.setLayout(new BoxLayout(pnlEnvFooter, BoxLayout.LINE_AXIS));

        btnAddRow.setIcon(new ImageIcon(getClass().getResource("/com/equinix/amphibia/icons/plus-icon.png"))); // NOI18N
        btnAddRow.setToolTipText(bundle.getString("addRow")); // NOI18N
        btnAddRow.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                btnAddRowActionPerformed(evt);
            }
        });
        pnlEnvFooter.add(btnAddRow);

        btnDeleteRow.setIcon(new ImageIcon(getClass().getResource("/com/equinix/amphibia/icons/remove_16.png"))); // NOI18N
        btnDeleteRow.setToolTipText(bundle.getString("deleteRow")); // NOI18N
        btnDeleteRow.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                btnDeleteRowActionPerformed(evt);
            }
        });
        pnlEnvFooter.add(btnDeleteRow);

        lblError.setForeground(Color.red);
        lblError.setText(bundle.getString("tip_key_exists")); // NOI18N
        lblError.setBorder(BorderFactory.createEmptyBorder(1, 10, 1, 10));
        pnlEnvFooter.add(lblError);

        pnlEnvCenter.add(pnlEnvFooter, BorderLayout.PAGE_END);

        pnlInterface.add(pnlEnvCenter, BorderLayout.CENTER);

        chbShowInterfaces.setText(bundle.getString("tip_open_interface")); // NOI18N
        chbShowInterfaces.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                chbShowInterfacesActionPerformed(evt);
            }
        });
        pnlInterface.add(chbShowInterfaces, BorderLayout.PAGE_END);

        pnlAddHeader.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        GridBagLayout pnlAddHeaderLayout = new GridBagLayout();
        pnlAddHeaderLayout.columnWidths = new int[] {0, 5, 0};
        pnlAddHeaderLayout.rowHeights = new int[] {0, 5, 0, 5, 0, 5, 0};
        pnlAddHeader.setLayout(pnlAddHeaderLayout);

        lblHeaderName.setText(bundle.getString("name")); // NOI18N
        gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = GridBagConstraints.WEST;
        pnlAddHeader.add(lblHeaderName, gridBagConstraints);
        gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        pnlAddHeader.add(txtHeaderName, gridBagConstraints);

        lblHeaderValue.setText(bundle.getString("value")); // NOI18N
        gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.anchor = GridBagConstraints.WEST;
        pnlAddHeader.add(lblHeaderValue, gridBagConstraints);
        gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.fill = GridBagConstraints.HORIZONTAL;
        pnlAddHeader.add(txtHeaderValue, gridBagConstraints);

        ckbAsGlobal.setText(bundle.getString("addToGlobal")); // NOI18N
        gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 6;
        gridBagConstraints.anchor = GridBagConstraints.WEST;
        pnlAddHeader.add(ckbAsGlobal, gridBagConstraints);

        setBorder(BorderFactory.createLineBorder(new Color(0, 0, 0)));
        setLayout(new BorderLayout());

        wizardTab = new WizardTab(this);
        tabNav.addTab("",
            new ImageIcon(getClass().getResource("/com/equinix/amphibia/icons/wizard_16.png")),
            wizardTab);
        add(tabNav, BorderLayout.CENTER);
    }// </editor-fold>//GEN-END:initComponents

    private void btnCloneActionPerformed(ActionEvent evt) {//GEN-FIRST:event_btnCloneActionPerformed
        if (cmdName.getSelectedIndex() != -1) {
            if (saveSelectedModel(headerSaveIndex)) {
                ComboItem item = (ComboItem) interfaceNameModel.getElementAt(cmdName.getSelectedIndex());
                newInterface(IO.toJSONObject(item.json.toString()));
            }
        }
    }//GEN-LAST:event_btnCloneActionPerformed

    private void btnAddRowActionPerformed(ActionEvent evt) {//GEN-FIRST:event_btnAddRowActionPerformed
        JButton applyButton = new JButton(bundle.getString("apply"));
        JButton cancelButton = new JButton(bundle.getString("cancel"));
        txtHeaderName.setText("");
        txtHeaderValue.setText("");
        JDialog dialog = Amphibia.createDialog(pnlAddHeader, new Object[]{applyButton, cancelButton}, true);
        dialog.setSize(new Dimension(400, 200));
        java.awt.EventQueue.invokeLater(() -> {
            dialog.setLocationRelativeTo(mainPanel);
        });
        cancelButton.addActionListener((ActionEvent e) -> {
            dialog.setVisible(false);
        });
        applyButton.addActionListener((ActionEvent e) -> {
            String value = ckbAsGlobal.isSelected() ? "${#Global#" + txtHeaderName.getText() + "}" : txtHeaderValue.getText();
            if (ckbAsGlobal.isSelected()) {
                globalVariables.add(new HashMap() {
                    {
                        put("name", txtHeaderName.getText());
                        put("value", txtHeaderValue.getText());
                    } 
                });
            }
            int row = tblEnvHeaders.getSelectedRow();
            if (row == -1) {
                headersModel.addRow(new Object[]{txtHeaderName.getText(), value});
            } else {
                headersModel.insertRow(row + 1, new Object[]{txtHeaderName.getText(), value});
            }
            dialog.setVisible(false);
        });
        dialog.setVisible(true);
    }//GEN-LAST:event_btnAddRowActionPerformed

    private void btnDeleteRowActionPerformed(ActionEvent evt) {//GEN-FIRST:event_btnDeleteRowActionPerformed
        if (headersModel.getRowCount() > 0) {
            int row = tblEnvHeaders.getSelectedRow();
            if (row == -1) {
                row = headersModel.getRowCount() - 1;
            }
            Object key = headersModel.getValueAt(row, 0);
            for (int i = globalVariables.size() - 1; i >= 0; i--) {
                if (globalVariables.getJSONObject(i).getString("name").equals(key)) {
                    globalVariables.remove(i);
                    break;
                }
            }
            headersModel.removeRow(row);
        }
    }//GEN-LAST:event_btnDeleteRowActionPerformed

    private void btnDeleteActionPerformed(ActionEvent evt) {//GEN-FIRST:event_btnDeleteActionPerformed
        int n = JOptionPane.showConfirmDialog(interfaceDialog,
                    String.format(bundle.getString("tip_delete_interface"), cmdName.getSelectedItem()), bundle.getString("title"),
                    JOptionPane.YES_NO_OPTION);
            if (n == JOptionPane.YES_OPTION) {
                interfaceNameModel.removeElementAt(cmdName.getSelectedIndex());
                reset();
            }
            headerSaveIndex = -1;
            cmdNameItemStateChanged(null);
    }//GEN-LAST:event_btnDeleteActionPerformed

    private void cmdNameItemStateChanged(ItemEvent evt) {//GEN-FIRST:event_cmdNameItemStateChanged
        Object item = interfaceNameModel.getSelectedItem();
        btnClone.setEnabled(item != null);
        btnDelete.setEnabled(item != null && ((ComboItem)item).canDelete);
        if (item != null) {
            if (evt != null && evt.getStateChange() == ItemEvent.DESELECTED) {
                if (saveSelectedModel(headerSaveIndex)) {
                    reset();
                }
            } else {
                ComboItem comboItem = (ComboItem) interfaceNameModel.getElementAt(cmdName.getSelectedIndex());
                txtBasePath.setText(comboItem.json.getOrDefault("basePath", "/").toString());
                if (comboItem.json.containsKey("headers")) {
                    JSONObject headers = comboItem.json.getJSONObject("headers");
                    if (!headers.isEmpty()) {
                        headersModel.setRowCount(0);
                        headers.keySet().forEach((key) -> {
                            headersModel.addRow(new Object[] {key, headers.get(key)});
                        });
                    }
                }
                headerSaveIndex = cmdName.getSelectedIndex();
            }
        }
    }//GEN-LAST:event_cmdNameItemStateChanged

    private void chbShowInterfacesActionPerformed(ActionEvent evt) {//GEN-FIRST:event_chbShowInterfacesActionPerformed
        userPreferences.putBoolean(Amphibia.P_INTERFACE, chbShowInterfaces.isSelected());
    }//GEN-LAST:event_chbShowInterfacesActionPerformed


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private JButton btnAddRow;
    private JButton btnClone;
    private JButton btnDelete;
    private JButton btnDeleteRow;
    private JCheckBox chbShowInterfaces;
    private JCheckBox ckbAsGlobal;
    private JComboBox<String> cmdName;
    private JLabel lblBasePath;
    private JLabel lblEnvHeaders;
    private JLabel lblError;
    private JLabel lblHeaderName;
    private JLabel lblHeaderValue;
    private JLabel lblName;
    private JPanel pnlAddHeader;
    private JPanel pnlEnvCenter;
    private JPanel pnlEnvFooter;
    private JPanel pnlEnvTop;
    private JPanel pnlInterface;
    private JScrollPane spnEnvHeaders;
    JTabbedPane tabNav;
    private JTable tblEnvHeaders;
    private JTextField txtBasePath;
    private JTextField txtHeaderName;
    private JTextField txtHeaderValue;
    // End of variables declaration//GEN-END:variables


    static public class ComboItem {
        String id;
        String label;
        JSONObject json;
        boolean canDelete;
        
        ComboItem(JSONObject item, boolean canDelete) {
            this.json = item;
            this.id = item.getString("id");
            this.label = item.getString("name");
            this.canDelete = canDelete;
        }

        @Override
        public String toString() {
            return label;
        }
    }
}
