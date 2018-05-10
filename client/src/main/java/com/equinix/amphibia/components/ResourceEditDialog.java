/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.equinix.amphibia.components;

import static com.equinix.amphibia.components.JTreeTable.EditValueRenderer.TYPE.*;

import com.equinix.amphibia.agent.builder.Properties;
import com.equinix.amphibia.Amphibia;
import com.equinix.amphibia.IO;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Desktop;
import java.text.NumberFormat;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.OverlayLayout;
import javax.swing.SpinnerDateModel;
import javax.swing.SpinnerNumberModel;
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
    private JDialog dialog;
    private JButton applyButton;
    private ResourceBundle bundle;
    private Editor.Entry entry;
    private Border defaultBorder;
    private boolean isTestProperties;
    private final JOptionPane optionPane;
    private final JButton deleteButton;
    private final JButton resetButton;
    private final JButton cancelButton;
    private final JButton okButton;

    private final Border ERROR_BORDER = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Color.RED),
            BorderFactory.createEmptyBorder(2, 2, 2, 2));

    private static final Logger logger = Amphibia.getLogger(ResourceEditDialog.class.getName());
    private static final NumberFormat NUMBER = NumberFormat.getInstance();

    public static enum Types {
        NULL, String, Boolean, Number,
        Timestamp, Properties, JSON
    };

    /**
     * Creates new form TableEditDialog
     *
     * @param mainPanel
     */
    public ResourceEditDialog(MainPanel mainPanel) {
        this.mainPanel = mainPanel;

        initComponents();

        JSpinner.DateEditor de = (JSpinner.DateEditor) spnDate.getEditor();
        de.getFormat().applyPattern("yyyy-MM-dd HH:mm:ss");
        JFormattedTextField tf = de.getTextField();
        tf.setEditable(false);
        tf.setBackground(Color.white);
        spnDate.setValue(new Date());
        pnlTimestamp.setVisible(false);
        pnlMiddle.add(pnlTimestamp);

        Amphibia.addUndoManager(txtEditor);

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
                            properties = collection.profile.jsonObject().getJSONObject("properties");
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
                    mainPanel.history.saveAndAddHistory(collection.profile);
                }
            }
            try {
                Object value = getValue();
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
                if (isTestProperties) {
                    JSONObject reqRes = node.jsonObject().getJSONObject(entry.rootName);
                    if (reqRes.isNullObject()) {
                        reqRes = new JSONObject();
                    }
                    JSONObject json = reqRes.getJSONObject("properties");
                    if (json.isNullObject()) {
                        json = new JSONObject();
                    }
                    json.element(txtName.getText(), value);
                    reqRes.element("properties", json);
                    Editor.Entry child = entry.add(node, reqRes, txtName.getText(), value, EDIT, null, entry.rootName);
                    child.isDynamic = true;
                    saveSelectedNode(child);
                } else if (entry.getType() == JTreeTable.EditValueRenderer.TYPE.ADD) {
                    JSONObject json = ((JSONObject) entry.json).getJSONObject(entry.name);
                    if (json.isNullObject()) {
                        json = new JSONObject();
                        ((JSONObject) entry.json).element(entry.name, json);
                    }
                    json.element(txtName.getText(), value);
                    Editor.Entry child = entry.add(node, json, txtName.getText(), value, EDIT, null, entry.rootName);
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
                lblError.setText(ex.getMessage());
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
        dialog.setSize(new Dimension(700, 500));
        java.awt.EventQueue.invokeLater(() -> {
            dialog.setLocationRelativeTo(mainPanel);
        });
    }

    public JDialog getDialog() {
        return dialog;
    }

    public Object getValue() throws Exception {
        String dataType = cmbDataType.getSelectedItem().toString();
        try {
            if (Types.Timestamp.name().equals(dataType)) {
                StringBuilder out = new StringBuilder();
                if (rbSetDate.isSelected()) {
                    out.append(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").format(spnDate.getValue())).append("0");
                } else {
                    out.append("0000-00-00T00:00:00Z");
                    if (ckbAddSub.isSelected()) {
                        if ((int) spnDays.getValue() == 0 && (int) spnSeconds.getValue() < 0) {
                            out.append("-");
                        }
                        out.append(spnDays.getValue()).append(".");
                        out.append(Math.abs((int) spnSeconds.getValue()));
                    } else {
                        out.append("0");
                    }
                }
                out.append("-");
                if (rbCustom.isSelected()) {
                    out.append(txtFormat.getText());
                }
                return out.toString();
            } else {
                return getValue(dataType, txtEditor.getText().trim());
            }
        } catch (Exception ex) {
            throw new Exception(String.format(bundle.getString("error_convert"), dataType));
        }
    }
    
    public void setDataTypes() {
        setDataTypes(new Types[]{Types.NULL, Types.String, Types.Boolean, Types.Number, Types.Timestamp, Types.Properties, Types.JSON});
    }

    public void setDataTypes(Types[] types) {
        String[] st = new String[types.length];
        for (int i = 0; i < types.length; i++) {
            st[i] = types[i].name();
        }
        cmbDataType.setModel(new DefaultComboBoxModel<>(st));
    }

    private void deleteOrReset() {
        entry.isDelete = true;
        if (entry.json instanceof JSONObject) {
            ((JSONObject) entry.json).remove(entry.name);
        } else {
            ((JSONArray) entry.json).remove(Integer.parseInt(entry.name));
        }
        saveSelectedNode(entry);
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
    public void openEditDialog(int row, String fileName, String value) {
        JButton delButton = new JButton(bundle.getString("delete"));
        delButton.addActionListener((ActionEvent evt) -> {
            try {
                mainPanel.editor.deleteHistory(row);
                dialog.setVisible(false);
            } catch (IOException ex) {
                mainPanel.addError(ex);
            }
        });
        optionPane.setOptions(new Object[]{applyButton, delButton, cancelButton});
        txtName.setText(fileName);
        txtName.setEditable(false);
        lblError.setVisible(false);
        lblDataType.setVisible(false);
        cmbDataType.setVisible(false);
        pnlTimestamp.setVisible(false);
        splEditor.setVisible(true);
        txtEditor.setEditable(false);
        txtEditor.setEnabled(true);
        txtEditor.setBackground(UIManager.getColor("TextArea.background"));
        txtEditor.setText(value);
        Amphibia.setText(txtEditor, splEditor, null);
        Amphibia.resetUndoManager(txtEditor);
        dialog.setVisible(true);
    }

    @SuppressWarnings("NonPublicExported")
    public void openEditDialog(Editor.Entry entry, String name, Object value, boolean isEdit) {
        setDataTypes();
        Object[] options = new Object[]{okButton};
        if (isEdit) {
            options = new Object[]{applyButton, cancelButton};
            if (entry.isDynamic) {
                if (entry.editMode == JTreeTable.EDIT_DELETE) {
                    options = new Object[]{applyButton, deleteButton, cancelButton};
                } else if (entry.editMode == JTreeTable.EDIT_RESET) {
                    options = new Object[]{applyButton, resetButton, cancelButton};
                }
            }
        }
        openEditDialog(name, value, isEdit, options);
        isTestProperties = ("request".equals(entry.rootName) || "response".equals(entry.rootName));
        cmbDataType.setEnabled(isEdit && entry.getType() != EDIT_LIMIT);
        dialog.setVisible(true);
    }

    @SuppressWarnings("NonPublicExported")
    public JDialog openEditDialog(String name, Object value, boolean isEdit, Object[] options) {
        optionPane.setOptions(options);
        ckbPropertyCreate.setSelected(false);
        ckbPropertyCopy.setSelected(false);
        ckbPropertyCopy.setEnabled(false);
        txtName.setText(name);
        txtName.setEditable(name == null);
        txtName.setBorder(defaultBorder);
        txtEditor.setEnabled(true);
        txtEditor.setEditable(isEdit);
        txtEditor.setText(value == null ? "" : String.valueOf(value));
        lblDataType.setVisible(true);
        cmbDataType.setVisible(true);
        cmbDataType.setEnabled(isEdit);
        cmbDataType.setSelectedItem(getType(value));
        if (value != null && Types.Timestamp.name().equals(cmbDataType.getSelectedItem())) {
            try {
                Matcher m = Properties.TIMESTAMP.matcher(value.toString());
                if (m.find()) {
                    if (!m.group(1).startsWith("0")) {
                        Calendar cal = Calendar.getInstance();
                        cal.set(Integer.parseInt(m.group(1)), //year
                                Integer.parseInt(m.group(2)) - 1, //month
                                Integer.parseInt(m.group(3)), //date
                                Integer.parseInt(m.group(4)), //hrs
                                Integer.parseInt(m.group(5)), //min
                                Integer.parseInt(m.group(6))); //sec
                        spnDate.setValue(cal.getTime());
                        rbSetDate.setSelected(true);
                    } else {
                        spnDate.setValue(new Date());
                        rbCurrDate.setSelected(true);
                    }
                    ckbAddSub.setSelected(!"0".equals(m.group(7)));
                    if (ckbAddSub.isSelected()) {
                        String[] pair = m.group(7).split("\\.");
                        int days = Integer.parseInt(pair[0]);
                        int secs = Integer.parseInt(pair[1]);
                        spnDays.setValue(days);
                        spnSeconds.setValue(days == 0 && m.group(7).startsWith("-") ? -secs : secs);
                    }
                    if (!m.group(8).trim().isEmpty()) {
                        rbCustom.setSelected(true);
                        txtFormat.setText(m.group(8));
                    } else {
                        rbNumber.setSelected(true);
                    }
                }
            } catch (NumberFormatException ex) {
                mainPanel.addError(ex);
            }
        }
        ckbAddSubActionPerformed(null);
        rbNumberActionPerformed(null);
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
        Amphibia.resetUndoManager(txtEditor);
        lblError.setVisible(false);

        return dialog;
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
        }
        return Properties.getValue(value);
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
        if (value instanceof String && Properties.TIMESTAMP.matcher(value.toString()).matches()) {
            return "Timestamp";
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
        GridBagConstraints gridBagConstraints;

        pnlNewProperty = new JPanel();
        lblTitle = new JLabel();
        cmbPropertyTypes = new JComboBox<>();
        ckbPropertyCreate = new JCheckBox();
        ckbPropertyCopy = new JCheckBox();
        pnlTimestamp = new JPanel();
        pnlTimestamp1 = new JPanel();
        rbSetDate = new JRadioButton();
        rbCurrDate = new JRadioButton();
        pnlTimestamp2 = new JPanel();
        lblDate = new JLabel();
        spnDate = new JSpinner();
        pnlTimestamp3 = new JPanel();
        sprTimestamp = new JSeparator();
        ckbAddSub = new JCheckBox();
        lblDays = new JLabel();
        spnDays = new JSpinner();
        lblSeconds = new JLabel();
        spnSeconds = new JSpinner();
        pnlTimestamp4 = new JPanel();
        sprFormat = new JSeparator();
        lblOutFormat = new JLabel();
        rbNumber = new JRadioButton();
        rbCustom = new JRadioButton();
        txtFormat = new JTextField();
        lblJavaDocs = new JLabel();
        rbDateGroup = new ButtonGroup();
        rbFormatGroup = new ButtonGroup();
        pnlHeader = new JPanel();
        lblName = new JLabel();
        txtName = new JTextField();
        pnlMiddle = new JPanel();
        splEditor = new JScrollPane();
        txtEditor = new JTextArea();
        pnlFooter = new JPanel();
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

        pnlTimestamp.setBorder(BorderFactory.createEmptyBorder(10, 1, 1, 10));
        pnlTimestamp.setLayout(new BoxLayout(pnlTimestamp, BoxLayout.Y_AXIS));

        pnlTimestamp1.setLayout(new FlowLayout(FlowLayout.LEFT));

        rbDateGroup.add(rbSetDate);
        rbSetDate.setText(bundle.getString("setDate")); // NOI18N
        rbSetDate.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                rbSetDateActionPerformed(evt);
            }
        });
        pnlTimestamp1.add(rbSetDate);

        rbDateGroup.add(rbCurrDate);
        rbCurrDate.setSelected(true);
        rbCurrDate.setText(bundle.getString("currentDate")); // NOI18N
        rbCurrDate.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                rbCurrDateActionPerformed(evt);
            }
        });
        pnlTimestamp1.add(rbCurrDate);

        pnlTimestamp.add(pnlTimestamp1);

        pnlTimestamp2.setLayout(new FlowLayout(FlowLayout.LEFT));

        lblDate.setText(bundle.getString("date")); // NOI18N
        pnlTimestamp2.add(lblDate);

        spnDate.setModel(new SpinnerDateModel(new Date(1525929190492L), null, null, Calendar.DAY_OF_MONTH));
        spnDate.setPreferredSize(new Dimension(150, 20));
        pnlTimestamp2.add(spnDate);

        pnlTimestamp.add(pnlTimestamp2);

        GridBagLayout jPanel2Layout = new GridBagLayout();
        jPanel2Layout.columnWidths = new int[] {0, 5, 0};
        jPanel2Layout.rowHeights = new int[] {0, 5, 0, 5, 0, 5, 0};
        pnlTimestamp3.setLayout(jPanel2Layout);
        gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.fill = GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        pnlTimestamp3.add(sprTimestamp, gridBagConstraints);

        ckbAddSub.setText(bundle.getString("addSubtractTime")); // NOI18N
        ckbAddSub.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                ckbAddSubActionPerformed(evt);
            }
        });
        gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.anchor = GridBagConstraints.WEST;
        pnlTimestamp3.add(ckbAddSub, gridBagConstraints);

        lblDays.setText(bundle.getString("days")); // NOI18N
        gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.anchor = GridBagConstraints.WEST;
        pnlTimestamp3.add(lblDays, gridBagConstraints);

        spnDays.setPreferredSize(new Dimension(100, 20));
        gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.anchor = GridBagConstraints.WEST;
        pnlTimestamp3.add(spnDays, gridBagConstraints);

        lblSeconds.setText(bundle.getString("seconds")); // NOI18N
        gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 6;
        gridBagConstraints.anchor = GridBagConstraints.WEST;
        pnlTimestamp3.add(lblSeconds, gridBagConstraints);

        spnSeconds.setModel(new SpinnerNumberModel(0, -86400, 86400, 10));
        spnSeconds.setPreferredSize(new Dimension(100, 20));
        gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 6;
        gridBagConstraints.anchor = GridBagConstraints.WEST;
        pnlTimestamp3.add(spnSeconds, gridBagConstraints);

        pnlTimestamp.add(pnlTimestamp3);

        GridBagLayout pnlTimestamp3Layout = new GridBagLayout();
        pnlTimestamp3Layout.columnWidths = new int[] {0, 5, 0, 5, 0};
        pnlTimestamp3Layout.rowHeights = new int[] {0, 5, 0, 5, 0, 5, 0, 5, 0};
        pnlTimestamp4.setLayout(pnlTimestamp3Layout);
        gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.gridwidth = 5;
        gridBagConstraints.fill = GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        pnlTimestamp4.add(sprFormat, gridBagConstraints);

        lblOutFormat.setText(bundle.getString("outFormat")); // NOI18N
        gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        pnlTimestamp4.add(lblOutFormat, gridBagConstraints);

        rbFormatGroup.add(rbNumber);
        rbNumber.setSelected(true);
        rbNumber.setText(bundle.getString("inMillSec")); // NOI18N
        rbNumber.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                rbNumberActionPerformed(evt);
            }
        });
        gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 4;
        pnlTimestamp4.add(rbNumber, gridBagConstraints);

        rbFormatGroup.add(rbCustom);
        rbCustom.setText(bundle.getString("customize")); // NOI18N
        rbCustom.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                rbCustomActionPerformed(evt);
            }
        });
        gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 4;
        pnlTimestamp4.add(rbCustom, gridBagConstraints);

        txtFormat.setText("EEE, d MMM yyyy HH:mm:ss a Z");
        txtFormat.setToolTipText("");
        txtFormat.setPreferredSize(new Dimension(200, 20));
        gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 6;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.fill = GridBagConstraints.HORIZONTAL;
        pnlTimestamp4.add(txtFormat, gridBagConstraints);

        lblJavaDocs.setForeground(new Color(0, 51, 255));
        lblJavaDocs.setText("https://docs.oracle.com");
        lblJavaDocs.setToolTipText("https://docs.oracle.com/javase/7/docs/api/java/text/SimpleDateFormat.html");
        lblJavaDocs.setCursor(new Cursor(Cursor.HAND_CURSOR));
        lblJavaDocs.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent evt) {
                lblJavaDocsMouseClicked(evt);
            }
        });
        gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 8;
        gridBagConstraints.gridwidth = 5;
        gridBagConstraints.anchor = GridBagConstraints.WEST;
        pnlTimestamp4.add(lblJavaDocs, gridBagConstraints);

        pnlTimestamp.add(pnlTimestamp4);

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

        pnlMiddle.setLayout(new OverlayLayout(pnlMiddle));

        txtEditor.setColumns(20);
        txtEditor.setRows(5);
        splEditor.setViewportView(txtEditor);

        pnlMiddle.add(splEditor);

        add(pnlMiddle, BorderLayout.CENTER);

        pnlFooter.setPreferredSize(new Dimension(603, 60));
        pnlFooter.setLayout(new GridLayout(2, 0));

        lblDataType.setText(bundle.getString("dataType")); // NOI18N
        pnlDataType.add(lblDataType);

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
        txtEditor.setEnabled(!Types.NULL.name().equals(cmbDataType.getSelectedItem()));
        txtEditor.setBackground(UIManager.getColor(txtEditor.isEnabled() ? "TextArea.background" : "TextArea.disabledBackground"));
        if (Types.Timestamp.name().equals(cmbDataType.getSelectedItem())) {
            splEditor.setVisible(false);
            pnlTimestamp.setVisible(true);
        } else {
            pnlTimestamp.setVisible(false);
            splEditor.setVisible(true);
        }
    }//GEN-LAST:event_cmbDataTypeItemStateChanged

    private void cmbDataTypeActionPerformed(ActionEvent evt) {//GEN-FIRST:event_cmbDataTypeActionPerformed
        if (dialog.isVisible() && Types.Properties.name().equals(cmbDataType.getSelectedItem())) {
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
        ckbPropertyCreate.setEnabled("Global".equals(cmbPropertyTypes.getSelectedItem()));
        ckbPropertyCopy.setEnabled("Global".equals(cmbPropertyTypes.getSelectedItem()));
    }//GEN-LAST:event_cmbPropertyTypesActionPerformed

    private void ckbPropertyCreateActionPerformed(ActionEvent evt) {//GEN-FIRST:event_ckbPropertyCreateActionPerformed
        ckbPropertyCopy.setEnabled(ckbPropertyCreate.isSelected());
    }//GEN-LAST:event_ckbPropertyCreateActionPerformed

    private void ckbAddSubActionPerformed(ActionEvent evt) {//GEN-FIRST:event_ckbAddSubActionPerformed
        ckbAddSub.setEnabled(rbCurrDate.isSelected());
        spnDays.setEnabled(rbCurrDate.isSelected() && ckbAddSub.isSelected());
        spnSeconds.setEnabled(rbCurrDate.isSelected() && ckbAddSub.isSelected());
        spnDate.setEnabled(rbSetDate.isSelected());
    }//GEN-LAST:event_ckbAddSubActionPerformed

    private void rbCurrDateActionPerformed(ActionEvent evt) {//GEN-FIRST:event_rbCurrDateActionPerformed
        ckbAddSubActionPerformed(null);
    }//GEN-LAST:event_rbCurrDateActionPerformed

    private void rbSetDateActionPerformed(ActionEvent evt) {//GEN-FIRST:event_rbSetDateActionPerformed
        ckbAddSubActionPerformed(null);
    }//GEN-LAST:event_rbSetDateActionPerformed

    private void rbNumberActionPerformed(ActionEvent evt) {//GEN-FIRST:event_rbNumberActionPerformed
        txtFormat.setEnabled(!rbNumber.isSelected());
    }//GEN-LAST:event_rbNumberActionPerformed

    private void rbCustomActionPerformed(ActionEvent evt) {//GEN-FIRST:event_rbCustomActionPerformed
        txtFormat.setEnabled(rbCustom.isSelected());
    }//GEN-LAST:event_rbCustomActionPerformed

    private void lblJavaDocsMouseClicked(MouseEvent evt) {//GEN-FIRST:event_lblJavaDocsMouseClicked
        try {
            Desktop.getDesktop().browse(new URI("https://docs.oracle.com/javase/7/docs/api/java/text/SimpleDateFormat.html"));
        } catch (IOException | URISyntaxException ex) {
            mainPanel.addError(ex);
        }
    }//GEN-LAST:event_lblJavaDocsMouseClicked


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private JCheckBox ckbAddSub;
    private JCheckBox ckbPropertyCopy;
    private JCheckBox ckbPropertyCreate;
    private JComboBox<String> cmbDataType;
    private JComboBox<String> cmbPropertyTypes;
    private JLabel lblDataType;
    private JLabel lblDate;
    private JLabel lblDays;
    JLabel lblError;
    private JLabel lblJavaDocs;
    private JLabel lblName;
    private JLabel lblOutFormat;
    private JLabel lblSeconds;
    private JLabel lblTitle;
    private JPanel pnlDataType;
    private JPanel pnlFooter;
    private JPanel pnlHeader;
    private JPanel pnlMiddle;
    private JPanel pnlNewProperty;
    private JPanel pnlTimestamp;
    private JPanel pnlTimestamp1;
    private JPanel pnlTimestamp2;
    private JPanel pnlTimestamp3;
    private JPanel pnlTimestamp4;
    private JRadioButton rbCurrDate;
    private JRadioButton rbCustom;
    private ButtonGroup rbDateGroup;
    private ButtonGroup rbFormatGroup;
    private JRadioButton rbNumber;
    private JRadioButton rbSetDate;
    private JScrollPane splEditor;
    private JSpinner spnDate;
    private JSpinner spnDays;
    private JSpinner spnSeconds;
    private JSeparator sprFormat;
    private JSeparator sprTimestamp;
    private JTextArea txtEditor;
    private JTextField txtFormat;
    private JTextField txtName;
    // End of variables declaration//GEN-END:variables

}
