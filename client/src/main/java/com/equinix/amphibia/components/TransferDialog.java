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
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.OverlayLayout;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import net.sf.json.JSON;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

/**
 *
 * @author dgofman
 */
public class TransferDialog extends javax.swing.JPanel {

    private JDialog dialog;
    private JOptionPane optionPane;
    private JButton applyButton;
    private JButton deleteButton;
    private JButton cancelButton;
    private ResourceBundle bundle;
    private Editor.Entry entry;

    private final MainPanel mainPanel;
    private final DefaultComboBoxModel targetModel;

    public final DefaultMutableTreeNode treeNode;
    public final DefaultTreeModel treeModel;
    
    private static final Logger logger = Logger.getLogger(TransferDialog.class.getName());

    /**
     * Creates new form TransferDialog
     */
    public TransferDialog(MainPanel mainPanel) {
        this.mainPanel = mainPanel;

        targetModel = new DefaultComboBoxModel();

        treeNode = new DefaultMutableTreeNode();
        treeModel = new DefaultTreeModel(treeNode);

        initComponents();

        treeSchema.setShowsRootHandles(true);
        treeSchema.setRootVisible(false);
        treeSchema.setRowHeight(20);
        treeSchema.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 0));
        treeSchema.setEditable(false);
        treeSchema.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent e) {
                TreePath path = treeSchema.getPathForLocation(e.getX(), e.getY());
                if (path == null) {
                    return;
                }
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) treeSchema.getLastSelectedPathComponent();
                if (node != null) {
                    StringBuilder sb = new StringBuilder();
                    for (Object name : node.getPath()) {
                        if (!name.toString().isEmpty()) {
                            sb.append("/").append(name);
                        }
                    }
                    txtPath.setText(sb.toString());
                }
            }
        });

        DefaultTreeCellRenderer renderer = (DefaultTreeCellRenderer) treeSchema.getCellRenderer();
        renderer.setLeafIcon(null);
        renderer.setClosedIcon(null);
        renderer.setOpenIcon(null);

        bundle = Amphibia.getBundle();

        applyButton = new JButton(bundle.getString("apply"));
        applyButton.addActionListener((ActionEvent evt) -> {
            TreeIconNode node = MainPanel.selectedNode;
            JSONObject json = node.getType() == TreeCollection.TYPE.TESTCASE ? node.info.testCase : node.info.testStep;
            JSONObject transfer = json.containsKey("transfer") ? json.getJSONObject("transfer") : new JSONObject();
            Object value = null;
            if (this.rbAssignValue.isSelected()) {
                String dataType = cmbDataType.getSelectedItem().toString();
                try {
                    value = ResourceEditDialog.getValue(dataType, txtEditor.getText().trim());
                } catch (Exception ex) {
                    lblError.setText(String.format(bundle.getString("error_convert"), dataType));
                    lblError.setVisible(true);
                    logger.log(Level.SEVERE, ex.toString(), ex);
                }
            } else if (!txtPath.getText().isEmpty()) {
                value = txtPath.getText();
            }
            if (value == null) {
                transfer.remove(cmbTarget.getSelectedItem());
            } else {
                transfer.put(cmbTarget.getSelectedItem(), value);
            }
            if (transfer.isEmpty()) {
                json.remove("transfer");
            } else {
                json.element("transfer", transfer);
            }
            saveNodeValue(node);
            dialog.setVisible(false);
        });
        deleteButton = new JButton(bundle.getString("delete"));
        deleteButton.addActionListener((ActionEvent evt) -> {
            TreeIconNode node = MainPanel.selectedNode;
            JSONObject json = node.getType() == TreeCollection.TYPE.TESTCASE ? node.info.testCase : node.info.testStep;
            JSONObject transfer = json.containsKey("transfer") ? json.getJSONObject("transfer") : new JSONObject();
            transfer.remove(cmbTarget.getSelectedItem());
            if (transfer.isEmpty()) {
                json.remove("transfer");
            } else {
                json.element("transfer", transfer);
            }
            saveNodeValue(node);
            dialog.setVisible(false);
        });
        cancelButton = new JButton(bundle.getString("cancel"));
        cancelButton.addActionListener((ActionEvent evt) -> {
            dialog.setVisible(false);
        });

        optionPane = new JOptionPane(this);
        dialog = Amphibia.createDialog(optionPane, true);
        dialog.setSize(new Dimension(610, 450));
        java.awt.EventQueue.invokeLater(() -> {
            dialog.setLocationRelativeTo(mainPanel);
        });
    }
    
    private void saveNodeValue(TreeIconNode node) {
        mainPanel.saveNodeValue((TreeIconNode.ProfileNode) node.getCollection().profile);
    }

    public void openDialog(TreeIconNode node, Editor.Entry entry) {
        this.entry = entry;
        Object pathValue = null;
        if (entry.getType() == EDIT) {
            JSONObject json = node.getType() == TreeCollection.TYPE.TESTCASE ? node.info.testCase : node.info.testStep;
            JSONObject transfer = json.containsKey("transfer") ? json.getJSONObject("transfer") : new JSONObject();
            if (transfer.containsKey(entry.name)) {
                pathValue = transfer.get(entry.name);
            }
            optionPane.setOptions(new Object[]{applyButton, deleteButton, cancelButton});
        } else {
            optionPane.setOptions(new Object[]{applyButton, cancelButton});
        }
        txtPath.setText("");
        txtEditor.setText("");
        targetModel.removeAllElements();
        treeNode.removeAllChildren();
        JSONObject inheritedProperties = node.jsonObject().getJSONObject("inherited-properties");
        inheritedProperties.keySet().forEach((key) -> {
            targetModel.addElement(key);
            if (entry.getType() == EDIT && key.toString().equals(entry.name)) {
                cmbTarget.setSelectedIndex(targetModel.getSize() - 1);
            }
        });
        String path = node.info.testCase.getString("path");
        File test = IO.getFile(node.getCollection(), path);
        if (test.exists()) {
            JSONObject testJSON = (JSONObject) IO.getJSON(node.getCollection(), path, mainPanel.editor);
            if (testJSON != null) {
                Object body = testJSON.getJSONObject("response").get("body");
                if (!IO.isNULL(body) && !body.toString().isEmpty()) {
                    JSON bodyJSON = IO.getJSON(node.getCollection(), body.toString(), mainPanel.editor);
                    if (bodyJSON != null) {
                        buildTree(treeNode, bodyJSON);
                        java.awt.EventQueue.invokeLater(() -> {
                            for (int i = 0; i < treeSchema.getRowCount(); i++) {
                                treeSchema.expandRow(i);
                            }
                        });
                    }
                }
            }
        }
        treeModel.reload(treeNode);
        lblError.setVisible(false);
        if (entry.getType() == ADD || (pathValue instanceof String && pathValue.toString().startsWith("/"))) {
            txtPath.setText(String.valueOf(pathValue));

            boolean b = treeNode.getChildCount() > 0;
            rbSelectPropertyPath.setEnabled(b);
            if (b) {
                rbSelectPropertyPath.setSelected(true);
                rbSelectPropertyPathActionPerformed(null);
            } else {
                rbrEnterPropertyPath.setSelected(true);
                rbrEnterPropertyPathActionPerformed(null);
            }
        } else {
            if (pathValue instanceof JSON) {
                try {
                    txtEditor.setText(IO.prettyJson(((JSON) pathValue).toString()));
                } catch (Exception ex) {
                }
            } else {
                txtEditor.setText(String.valueOf(pathValue));
            }
            cmbDataType.setSelectedItem(ResourceEditDialog.getType(pathValue));
            rbAssignValue.setSelected(true);
            rbAssignValueActionPerformed(null);
        }
        
        cmbDataTypeItemStateChanged(null);
        dialog.setVisible(true);
    }

    private void buildTree(DefaultMutableTreeNode node, JSON json) {
        if (json instanceof JSONArray) {
            ((JSONArray) json).forEach((item) -> {
                if (item instanceof JSONArray || item instanceof JSONObject) {
                    buildTree(node, (JSON) item);
                } else {
                    node.add(new DefaultMutableTreeNode(item));
                }
            });
        } else {
            JSONObject parent = (JSONObject) json;
            parent.keySet().forEach((key) -> {
                DefaultMutableTreeNode child = new DefaultMutableTreeNode(key);
                node.add(child);
                Object item = parent.get(key);
                if (item instanceof JSONArray || item instanceof JSONObject) {
                    buildTree(child, (JSON) item);
                }
            });
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

        rbgPropertyPath = new ButtonGroup();
        pnlHeader = new JPanel();
        pnlGrid = new JPanel();
        lblTarget = new JLabel();
        cmbTarget = new JComboBox<>();
        lblPath = new JLabel();
        txtPath = new JTextField();
        pnlPropertyPath = new JPanel();
        rbSelectPropertyPath = new JRadioButton();
        rbrEnterPropertyPath = new JRadioButton();
        rbAssignValue = new JRadioButton();
        pnlCenter = new JPanel();
        spnSchema = new JScrollPane();
        treeSchema = new JTree();
        pnlValue = new JPanel();
        splEditor = new JScrollPane();
        txtEditor = new JTextArea();
        pnlFooter = new JPanel();
        pnlDataType = new JPanel();
        lblDataType = new JLabel();
        cmbDataType = new JComboBox<>();
        lblError = new JLabel();

        setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
        setLayout(new BorderLayout());

        pnlHeader.setBorder(BorderFactory.createEmptyBorder(1, 1, 5, 1));
        pnlHeader.setLayout(new BorderLayout(5, 5));

        pnlGrid.setLayout(new GridBagLayout());

        lblTarget.setFont(new Font("Tahoma", 1, 11)); // NOI18N
        ResourceBundle bundle = ResourceBundle.getBundle("com/equinix/amphibia/messages"); // NOI18N
        lblTarget.setText(bundle.getString("targetProperty")); // NOI18N
        gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.gridwidth = GridBagConstraints.RELATIVE;
        gridBagConstraints.ipadx = 5;
        gridBagConstraints.anchor = GridBagConstraints.WEST;
        gridBagConstraints.insets = new Insets(5, 0, 5, 0);
        pnlGrid.add(lblTarget, gridBagConstraints);

        cmbTarget.setModel(this.targetModel);
        gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.fill = GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        pnlGrid.add(cmbTarget, gridBagConstraints);

        lblPath.setFont(new Font("Tahoma", 1, 11)); // NOI18N
        lblPath.setText(bundle.getString("propertyPath")); // NOI18N
        gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.gridy = 2;
        gridBagConstraints.ipadx = 5;
        gridBagConstraints.anchor = GridBagConstraints.WEST;
        pnlGrid.add(lblPath, gridBagConstraints);
        gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.gridy = 2;
        gridBagConstraints.fill = GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new Insets(5, 0, 5, 0);
        pnlGrid.add(txtPath, gridBagConstraints);

        pnlHeader.add(pnlGrid, BorderLayout.NORTH);

        rbgPropertyPath.add(rbSelectPropertyPath);
        rbSelectPropertyPath.setText(bundle.getString("selectPropertyPath")); // NOI18N
        rbSelectPropertyPath.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                rbSelectPropertyPathActionPerformed(evt);
            }
        });
        pnlPropertyPath.add(rbSelectPropertyPath);

        rbgPropertyPath.add(rbrEnterPropertyPath);
        rbrEnterPropertyPath.setText(bundle.getString("enterPropertyPath")); // NOI18N
        rbrEnterPropertyPath.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                rbrEnterPropertyPathActionPerformed(evt);
            }
        });
        pnlPropertyPath.add(rbrEnterPropertyPath);

        rbgPropertyPath.add(rbAssignValue);
        rbAssignValue.setText(bundle.getString("assignValue")); // NOI18N
        rbAssignValue.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                rbAssignValueActionPerformed(evt);
            }
        });
        pnlPropertyPath.add(rbAssignValue);

        pnlHeader.add(pnlPropertyPath, BorderLayout.PAGE_END);

        add(pnlHeader, BorderLayout.NORTH);

        pnlCenter.setLayout(new OverlayLayout(pnlCenter));

        treeSchema.setModel(this.treeModel);
        spnSchema.setViewportView(treeSchema);

        pnlCenter.add(spnSchema);

        pnlValue.setLayout(new BorderLayout());

        txtEditor.setColumns(20);
        txtEditor.setRows(5);
        splEditor.setViewportView(txtEditor);

        pnlValue.add(splEditor, BorderLayout.CENTER);

        pnlFooter.setPreferredSize(new Dimension(603, 60));
        pnlFooter.setLayout(new GridLayout(2, 0));

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

        pnlValue.add(pnlFooter, BorderLayout.PAGE_END);

        pnlCenter.add(pnlValue);

        add(pnlCenter, BorderLayout.CENTER);
    }// </editor-fold>//GEN-END:initComponents

    private void rbSelectPropertyPathActionPerformed(ActionEvent evt) {//GEN-FIRST:event_rbSelectPropertyPathActionPerformed
        if (rbSelectPropertyPath.isSelected()) {
            rbAssignValueActionPerformed(evt);
            treeSchema.setEnabled(true);
            treeSchema.setBackground(UIManager.getColor("TextArea.background"));
        }
    }//GEN-LAST:event_rbSelectPropertyPathActionPerformed

    private void rbrEnterPropertyPathActionPerformed(ActionEvent evt) {//GEN-FIRST:event_rbrEnterPropertyPathActionPerformed
        if (rbrEnterPropertyPath.isSelected()) {
            rbAssignValueActionPerformed(evt);
            txtPath.setEnabled(true);
            treeSchema.setEnabled(false);
            treeSchema.setBackground(UIManager.getColor("TextArea.disabledBackground"));
        }
    }//GEN-LAST:event_rbrEnterPropertyPathActionPerformed

    private void rbAssignValueActionPerformed(ActionEvent evt) {//GEN-FIRST:event_rbAssignValueActionPerformed
        txtPath.setEnabled(false);
        pnlValue.setVisible(rbAssignValue.isSelected());
        spnSchema.setVisible(!rbAssignValue.isSelected());
    }//GEN-LAST:event_rbAssignValueActionPerformed

    private void cmbDataTypeItemStateChanged(ItemEvent evt) {//GEN-FIRST:event_cmbDataTypeItemStateChanged
        txtEditor.setEnabled(!"NULL".equals(cmbDataType.getSelectedItem()));
        txtEditor.setBackground(UIManager.getColor(txtEditor.isEnabled() ? "TextArea.background" : "TextArea.disabledBackground"));
    }//GEN-LAST:event_cmbDataTypeItemStateChanged

    private void cmbDataTypeActionPerformed(ActionEvent evt) {//GEN-FIRST:event_cmbDataTypeActionPerformed
        if (dialog.isVisible() && "Properties".equals(cmbDataType.getSelectedItem())) {
            java.awt.EventQueue.invokeLater(() -> {
                String s = (String)JOptionPane.showInputDialog(this,
                    bundle.getString("properties_msg"),
                    bundle.getString("properties_title"),
                    JOptionPane.PLAIN_MESSAGE, null,
                    new String[] {"Global", "Project", "TestSuite", "TestCase", "TestStep"},
                    "TestCase");
                if (s != null && !s.isEmpty()) {
                    txtEditor.setText("${#" + s + "#...}");
                }
            });
        }
    }//GEN-LAST:event_cmbDataTypeActionPerformed


    // Variables declaration - do not modify//GEN-BEGIN:variables
    JComboBox<String> cmbDataType;
    JComboBox<String> cmbTarget;
    JLabel lblDataType;
    JLabel lblError;
    JLabel lblPath;
    JLabel lblTarget;
    JPanel pnlCenter;
    JPanel pnlDataType;
    JPanel pnlFooter;
    JPanel pnlGrid;
    JPanel pnlHeader;
    JPanel pnlPropertyPath;
    JPanel pnlValue;
    JRadioButton rbAssignValue;
    JRadioButton rbSelectPropertyPath;
    ButtonGroup rbgPropertyPath;
    JRadioButton rbrEnterPropertyPath;
    JScrollPane splEditor;
    JScrollPane spnSchema;
    JTree treeSchema;
    JTextArea txtEditor;
    JTextField txtPath;
    // End of variables declaration//GEN-END:variables
}
