/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.equinix.amphibia.components;

import static com.equinix.amphibia.components.JTreeTable.EditValueRenderer.TYPE.*;

import com.equinix.amphibia.Amphibia;
import com.equinix.amphibia.IO;

import java.awt.BorderLayout;
import java.awt.Color;
import java.text.NumberFormat;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.File;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.border.Border;
import net.sf.json.JSON;
import net.sf.json.JSONArray;
import net.sf.json.JSONNull;
import net.sf.json.JSONObject;

/**
 *
 * @author dgofman
 */
public final class ResourceEditDialog extends javax.swing.JPanel {

    private MainPanel mainPanel;
    private JOptionPane optionPane;
    private JDialog dialog;
    private JButton applyButton;
    private JButton deleteButton;
    private JButton resetButton;
    private JButton cancelButton;
    private JButton okButton;
    private ResourceBundle bundle;
    private Editor.Entry entry;
    private Border defaultBorder;
    private boolean isTestProperties;

    private final Border ERROR_BORDER = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Color.RED),
            BorderFactory.createEmptyBorder(2, 2, 2, 2));

    private static final Logger logger = Amphibia.getLogger(ResourceEditDialog.class.getName());
    private static final NumberFormat NUMBER = NumberFormat.getInstance();

    /**
     * Creates new form TableEditDialog
     *
     * @param mainPanel
     */
    public ResourceEditDialog(MainPanel mainPanel) {
        this.mainPanel = mainPanel;

        initComponents();

        bundle = Amphibia.getBundle();

        defaultBorder = txtName.getBorder();
        applyButton = new JButton(bundle.getString("apply"));
        applyButton.addActionListener((ActionEvent evt) -> {
            if (txtName.getText().isEmpty()) {
                txtName.setBorder(ERROR_BORDER);
                return;
            }

            TreeIconNode node = MainPanel.selectedNode;
            TreeCollection collection = node.getCollection();
            TreeCollection.TYPE type = node.getType();
            String dataType = cmbDataType.getSelectedItem().toString();
            if ("Properties".equals(dataType) && ckbPropertyCreate.isEnabled() && ckbPropertyCreate.isSelected()) {
                Matcher m = Pattern.compile("\\$\\{#(.*?)#(.*?)\\}", Pattern.DOTALL | Pattern.MULTILINE).matcher(txtEditor.getText());
                JSONObject properties = null;
                if (m.groupCount() == 2 && m.find()) {
                    Object value = ckbPropertyCopy.isSelected() ? entry.value : null;
                    switch (this.cmbPropertyTypes.getSelectedItem().toString()) {
                        case "Global":
                            JSONArray globals = new JSONArray();
                            globals.add(IO.toJSONObject(new HashMap<String, Object>() {
                                {
                                    put("name", m.group(2));
                                    put("value", value);
                                }
                            }));
                            mainPanel.globalVarsDialog.mergeVariables(globals);
                            break;
                        case "Project":
                            properties = collection.project.jsonObject().getJSONObject("properties");
                            break;
                        case "TestSuite":
                            properties = node.info.testSuiteInfo.getJSONObject("properties");
                            break;
                        case "TestCase":
                            properties = node.info.testCaseInfo.getJSONObject("properties");
                            break;
                    }
                    if (properties != null) {
                        properties.put(m.group(2), value);
                    }
                    mainPanel.history.saveAndAddHistory(collection.project);
                }
            }
            try {
                Object value = getValue(dataType, txtEditor.getText().trim());
                if (value == null) {
                    value = JSONNull.getInstance();
                }
                if ("name".equals(entry.name)) {
                    value = value.toString().trim();
                    Enumeration children = node.getParent().children();
                    while (children.hasMoreElements()) {
                        TreeIconNode child = (TreeIconNode) children.nextElement();
                        if (child != node && child.getLabel().equals(value)) {
                            lblError.setText(String.format(bundle.getString("tip_name_exists")));
                            lblError.setVisible(true);
                            return;
                        }
                    }
                }
                if (isTestProperties && !chbOnlyForTeststep.isSelected() && node.jsonObject().containsKey("file")) {
                    File file = IO.getFile(collection, node.jsonObject().getString("file"));
                    if (file.exists()) {
                        JSONObject json = (JSONObject) IO.getJSON(file);
                        json.getJSONObject(entry.rootName).getJSONObject("properties").element(txtName.getText(), value);
                        String[] contents = IO.write(json.toString(), file, true);
                        mainPanel.history.addHistory(file.getAbsolutePath(), contents[0], contents[1]);
                        mainPanel.reloadCollection(collection);
                    }
                } else if (entry.getType() == JTreeTable.EditValueRenderer.TYPE.ADD) {
                    JSONObject json = ((JSONObject) entry.json).getJSONObject(entry.name);
                    if (json.isNullObject()) {
                        json = new JSONObject();
                        ((JSONObject) entry.json).element(entry.name, json);
                    }
                    json.element(txtName.getText(), value);
                    Editor.Entry child = entry.add(node, json, txtName.getText(), value, EDIT, null, txtName.getText());
                    child.isDynamic = true;
                    saveSelectedNode(child);
                } else {
                    if (entry.json instanceof JSONObject) {
                        ((JSONObject) entry.json).element(entry.name, value);
                    } else {
                        ((JSONArray) entry.json).add(value);
                    }
                    entry.value = value;
                    saveSelectedNode(entry);
                }
                dialog.setVisible(false);
            } catch (Exception ex) {
                lblError.setText(String.format(bundle.getString("error_convert"), dataType));
                lblError.setVisible(true);
                logger.log(Level.SEVERE, ex.toString(), ex);
            }
        });
        deleteButton = new JButton(bundle.getString("delete"));
        deleteButton.addActionListener((ActionEvent evt) -> {
            deleteOrReset();
        });
        resetButton = new JButton(bundle.getString("reset"));
        resetButton.addActionListener((ActionEvent evt) -> {
            deleteOrReset();
        });
        cancelButton = new JButton(bundle.getString("cancel"));
        cancelButton.addActionListener((ActionEvent evt) -> {
            dialog.setVisible(false);
        });
        okButton = new JButton(bundle.getString("ok"));
        okButton.addActionListener((ActionEvent evt) -> {
            dialog.setVisible(false);
        });
        cmbDataTypeItemStateChanged(null);

        optionPane = new JOptionPane(this);
        dialog = Amphibia.createDialog(optionPane, true);
        dialog.setSize(new Dimension(700, 400));
        java.awt.EventQueue.invokeLater(() -> {
            dialog.setLocationRelativeTo(mainPanel);
        });
    }

    private void deleteOrReset() {
        entry.isDelete = true;
        TreeIconNode node = MainPanel.selectedNode;
        TreeCollection collection = node.getCollection();
        if (isTestProperties && !chbOnlyForTeststep.isSelected()) {
            try {
                File file = IO.getFile(collection, node.jsonObject().getString("file"));
                if (file.exists()) {
                    JSONObject json = (JSONObject) IO.getJSON(file);
                    json.getJSONObject(entry.rootName).getJSONObject("properties").remove(entry.name);
                    IO.write(IO.prettyJson(json.toString()), file);
                    mainPanel.reloadCollection(collection);
                }
            } catch (Exception ex) {
                logger.log(Level.SEVERE, ex.toString(), ex);
            }
        } else {
            if (entry.json instanceof JSONObject) {
                ((JSONObject) entry.json).remove(entry.name);
            } else {
                ((JSONArray) entry.json).remove(Integer.parseInt(entry.name));
            }
            saveSelectedNode(entry);
        }
        dialog.setVisible(false);
    }

    private void saveSelectedNode(Editor.Entry entry) {
        mainPanel.history.saveEntry(entry, MainPanel.selectedNode.getCollection());
    }

    @SuppressWarnings("NonPublicExported")
    public void openCreateDialog(Editor.Entry entry) {
        this.entry = entry;
        openEditDialog(entry, null, null, true);
    }

    @SuppressWarnings("NonPublicExported")
    public void openEditDialog(Editor.Entry entry, Object value, boolean isEdit) {
        this.entry = entry;
        openEditDialog(entry, entry.name, value, isEdit);
    }

    @SuppressWarnings("NonPublicExported")
    public void openEditDialog(String fileName, String value) {
        optionPane.setOptions(new Object[]{applyButton, cancelButton});
        txtName.setText(fileName);
        txtName.setEditable(false);
        chbOnlyForTeststep.setVisible(false);
        lblError.setVisible(false);
        lblDataType.setVisible(false);
        cmbDataType.setVisible(false);
        txtEditor.setEditable(false);
        txtEditor.setBackground(UIManager.getColor("TextArea.background"));
        txtEditor.setText(value);
        Amphibia.setText(txtEditor, splEditor, null);
        dialog.setVisible(true);
    }

    @SuppressWarnings("NonPublicExported")
    public void openEditDialog(Editor.Entry entry, String name, Object value, boolean isEdit) {
        Object[] options = new Object[]{okButton};
        if (isEdit) {
            options = new Object[]{applyButton, cancelButton};
            if (entry.isDynamic && entry != null) {
                if (entry.editMode == JTreeTable.EDIT_DELETE) {
                    options = new Object[]{applyButton, deleteButton, cancelButton};
                } else if (entry.editMode == JTreeTable.EDIT_RESET) {
                    options = new Object[]{applyButton, resetButton, cancelButton};
                }
            }
        }
        optionPane.setOptions(options);
        isTestProperties = ("request".equals(entry.rootName) || "response".equals(entry.rootName));
        chbOnlyForTeststep.setVisible(isTestProperties && MainPanel.selectedNode.getType() == TreeCollection.TYPE.TEST_STEP_ITEM);
        chbOnlyForTeststep.setSelected(chbOnlyForTeststep.isVisible());
        ckbPropertyCreate.setSelected(false);
        ckbPropertyCopy.setSelected(false);
        ckbPropertyCopy.setEnabled(false);
        txtName.setText(name);
        txtName.setEditable(name == null);
        txtName.setBorder(defaultBorder);
        txtEditor.setEditable(isEdit);
        txtEditor.setText(value == null ? "" : String.valueOf(value));
        lblDataType.setVisible(true);
        cmbDataType.setVisible(true);
        cmbDataType.setSelectedItem(getType(value));
        cmbDataType.setEnabled(isEdit && entry.getType() != EDIT_LIMIT);
        cmbDataTypeItemStateChanged(null);
        if (value instanceof JSON) {
            try {
                txtEditor.setText(IO.prettyJson(((JSON) value).toString()));
            } catch (Exception ex) {
            }
        } else if (isAny(value)) {
            txtEditor.setText(txtEditor.getText().substring(1, txtEditor.getText().length() - 1));
            lblDataType.setVisible(false);
            cmbDataType.setVisible(false);
        }
        Amphibia.setText(txtEditor, splEditor, null);
        lblError.setVisible(false);
        dialog.setVisible(true);
    }

    public static Object getValue(String dataType, String value) throws Exception {
        switch (dataType) {
            case "NULL":
                return null;
            case "Number":
                return NUMBER.parse(value);
            case "Boolean":
                return "true".equals(value);
            case "JSON":
                return IO.prettyJson(value);
            default:
                return value;
        }
    }

    public static String getType(Object value) {
        if (value == null || value == JSONNull.getInstance()) {
            return "NULL";
        }
        if (value instanceof Integer || value instanceof Long || value instanceof Double) {
            return "Number";
        }
        if (value instanceof Boolean) {
            return "Boolean";
        }
        if (value instanceof JSON) {
            return "JSON";
        }
        if (value instanceof String && ((String) value).startsWith("${#") && ((String) value).endsWith("}")) {
            return "Properties";
        }
        return "String";
    }

    public static boolean isAny(Object value) {
        if (value instanceof String) {
            String str = value.toString();
            if (str.startsWith("`") && str.endsWith("`")) {
                return true;
            }
        }
        return false;
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        pnlNewProperty = new JPanel();
        lblTitle = new JLabel();
        cmbPropertyTypes = new JComboBox<>();
        ckbPropertyCreate = new JCheckBox();
        ckbPropertyCopy = new JCheckBox();
        pnlHeader = new JPanel();
        lblName = new JLabel();
        txtName = new JTextField();
        splEditor = new JScrollPane();
        txtEditor = new JTextArea();
        pnlFooter = new JPanel();
        chbOnlyForTeststep = new JCheckBox();
        pnlDataType = new JPanel();
        lblDataType = new JLabel();
        cmbDataType = new JComboBox<>();
        lblError = new JLabel();

        pnlNewProperty.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        pnlNewProperty.setLayout(new GridLayout(4, 0, 0, 5));

        ResourceBundle bundle = ResourceBundle.getBundle("com/equinix/amphibia/messages"); // NOI18N
        lblTitle.setText(bundle.getString("properties_msg")); // NOI18N
        pnlNewProperty.add(lblTitle);

        cmbPropertyTypes.setModel(new DefaultComboBoxModel<>(new String[] { "Global", "Project", "TestSuite", "TestCase", "TestStep" }));
        cmbPropertyTypes.setMaximumSize(new Dimension(32767, 22));
        cmbPropertyTypes.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                cmbPropertyTypesActionPerformed(evt);
            }
        });
        pnlNewProperty.add(cmbPropertyTypes);

        ckbPropertyCreate.setText(bundle.getString("properties_create")); // NOI18N
        ckbPropertyCreate.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                ckbPropertyCreateActionPerformed(evt);
            }
        });
        pnlNewProperty.add(ckbPropertyCreate);

        ckbPropertyCopy.setText(bundle.getString("properties_copy")); // NOI18N
        pnlNewProperty.add(ckbPropertyCopy);

        setBorder(BorderFactory.createEmptyBorder(5, 10, 0, 10));
        setLayout(new BorderLayout());

        pnlHeader.setBorder(BorderFactory.createEmptyBorder(1, 1, 5, 1));
        pnlHeader.setLayout(new BoxLayout(pnlHeader, BoxLayout.LINE_AXIS));

        lblName.setFont(new Font("Tahoma", 1, 11)); // NOI18N
        lblName.setText(bundle.getString("name")); // NOI18N
        lblName.setBorder(BorderFactory.createEmptyBorder(1, 1, 1, 10));
        pnlHeader.add(lblName);
        pnlHeader.add(txtName);

        add(pnlHeader, BorderLayout.PAGE_START);

        txtEditor.setColumns(20);
        txtEditor.setRows(5);
        splEditor.setViewportView(txtEditor);

        add(splEditor, BorderLayout.CENTER);

        pnlFooter.setPreferredSize(new Dimension(603, 60));
        pnlFooter.setLayout(new GridLayout(3, 0));

        chbOnlyForTeststep.setText(bundle.getString("onlyForTeststep")); // NOI18N
        pnlFooter.add(chbOnlyForTeststep);

        pnlDataType.setLayout(new FlowLayout(FlowLayout.CENTER, 5, 0));

        lblDataType.setText(bundle.getString("dataType")); // NOI18N
        pnlDataType.add(lblDataType);

        cmbDataType.setModel(new DefaultComboBoxModel<>(new String[] { "NULL", "String", "Boolean", "Number", "Properties", "JSON" }));
        cmbDataType.addItemListener(new ItemListener() {
            public void itemStateChanged(ItemEvent evt) {
                cmbDataTypeItemStateChanged(evt);
            }
        });
        cmbDataType.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                cmbDataTypeActionPerformed(evt);
            }
        });
        pnlDataType.add(cmbDataType);

        pnlFooter.add(pnlDataType);

        lblError.setForeground(new Color(255, 0, 0));
        lblError.setHorizontalAlignment(SwingConstants.CENTER);
        lblError.setText(bundle.getString("error_convert")); // NOI18N
        pnlFooter.add(lblError);

        add(pnlFooter, BorderLayout.PAGE_END);
    }// </editor-fold>//GEN-END:initComponents

    private void cmbDataTypeItemStateChanged(ItemEvent evt) {//GEN-FIRST:event_cmbDataTypeItemStateChanged
        txtEditor.setEnabled(!"NULL".equals(cmbDataType.getSelectedItem()));
        txtEditor.setBackground(UIManager.getColor(txtEditor.isEnabled() ? "TextArea.background" : "TextArea.disabledBackground"));
    }//GEN-LAST:event_cmbDataTypeItemStateChanged

    private void cmbDataTypeActionPerformed(ActionEvent evt) {//GEN-FIRST:event_cmbDataTypeActionPerformed
        if (dialog.isVisible() && "Properties".equals(cmbDataType.getSelectedItem())) {
            java.awt.EventQueue.invokeLater(() -> {
                JButton btnOk = new JButton(UIManager.getString("OptionPane.okButtonText"));
                JButton btnCancel = new JButton(UIManager.getString("OptionPane.cancelButtonText"));
                JDialog propDialog = Amphibia.createDialog(pnlNewProperty, new Object[]{btnOk, btnCancel}, bundle.getString("properties_title"), false);
                propDialog.setLocationRelativeTo(mainPanel);
                btnCancel.addActionListener((ActionEvent e) -> {
                    propDialog.setVisible(false);
                });
                btnOk.addActionListener((ActionEvent e) -> {
                    entry.value = txtEditor.getText();
                    txtEditor.setText("${#" + cmbPropertyTypes.getSelectedItem() + "#" + txtName.getText() + "}");
                    propDialog.setVisible(false);
                });
                propDialog.setVisible(true);
            });
        }
    }//GEN-LAST:event_cmbDataTypeActionPerformed

    private void cmbPropertyTypesActionPerformed(ActionEvent evt) {//GEN-FIRST:event_cmbPropertyTypesActionPerformed
        ckbPropertyCreate.setEnabled(!"TestStep".equals(cmbPropertyTypes.getSelectedItem()));
        ckbPropertyCopy.setEnabled(!"TestStep".equals(cmbPropertyTypes.getSelectedItem()));
    }//GEN-LAST:event_cmbPropertyTypesActionPerformed

    private void ckbPropertyCreateActionPerformed(ActionEvent evt) {//GEN-FIRST:event_ckbPropertyCreateActionPerformed
        ckbPropertyCopy.setEnabled(ckbPropertyCreate.isSelected());
    }//GEN-LAST:event_ckbPropertyCreateActionPerformed


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private JCheckBox chbOnlyForTeststep;
    private JCheckBox ckbPropertyCopy;
    private JCheckBox ckbPropertyCreate;
    private JComboBox<String> cmbDataType;
    private JComboBox<String> cmbPropertyTypes;
    private JLabel lblDataType;
    private JLabel lblError;
    private JLabel lblName;
    private JLabel lblTitle;
    private JPanel pnlDataType;
    private JPanel pnlFooter;
    private JPanel pnlHeader;
    private JPanel pnlNewProperty;
    private JScrollPane splEditor;
    private JTextArea txtEditor;
    private JTextField txtName;
    // End of variables declaration//GEN-END:variables

}
