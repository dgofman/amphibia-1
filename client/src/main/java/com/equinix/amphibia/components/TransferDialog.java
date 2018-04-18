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
import java.io.File;
import java.util.Arrays;
import java.util.ResourceBundle;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
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
import javax.swing.tree.TreeNode;
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
    private JButton applyButton;
    private ResourceBundle bundle;
    private Editor.Entry entry;
    private TreeIconNode node;
    private JSONObject bodyJSON;
    private TreeNode[] selectedTreeNode;
    private Object transferName;
    private Object transferValue;
    
    private final JOptionPane optionPane;
    private final JButton deleteButton;
    private final JButton cancelButton;
    private final MainPanel mainPanel;
    private final DefaultComboBoxModel targetModel;

    public final DefaultMutableTreeNode treeNode;
    public final DefaultTreeModel treeModel;
    
    public static final Pattern PATTERN_1 = Pattern.compile("\\$\\{#(.*?)#(.*?):(.*?)\\}", Pattern.DOTALL | Pattern.MULTILINE);

    /**
     * Creates new form TransferDialog
     * @param mainPanel
     */
    public TransferDialog(MainPanel mainPanel) {
        this.mainPanel = mainPanel;

        targetModel = new DefaultComboBoxModel();

        treeNode = new DefaultMutableTreeNode();
        treeModel = new DefaultTreeModel(treeNode);

        initComponents();
        
        Amphibia.addUndoManager(txtEditor);

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
                    for (TreeNode treeNode : node.getPath()) {
                        TreeItem treeItem = (TreeItem)((DefaultMutableTreeNode)treeNode).getUserObject();
                        if (treeItem != null) {
                            sb.append("/");
                            if (treeItem.itemIndex != -1) {
                                sb.append(treeItem.itemIndex);
                                if (treeNode.getChildCount() > 0) {
                                    sb.append("/").append(treeItem.label);
                                }
                            } else {
                                sb.append(treeItem.label);
                            }
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
            lblError.setText("");
            JSONObject json = node.getType() == TreeCollection.TYPE.TESTCASE ? node.info.testCase : node.info.testStep;
            JSONObject transfer = json.containsKey("transfer") ? json.getJSONObject("transfer") : new JSONObject();
            Object value = null;            
            if (this.rbAssignValue.isSelected()) {
                String dataType = cmbDataType.getSelectedItem().toString();
                try {
                    value = ResourceEditDialog.getValue(dataType, txtEditor.getText().trim());
                } catch (Exception ex) {
                    addError(String.format(bundle.getString("error_convert"), dataType), ex);
                    return;
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
        dialog.setSize(new Dimension(800, 650));
        java.awt.EventQueue.invokeLater(() -> {
            dialog.setLocationRelativeTo(mainPanel);
        });
    }
    
    private void saveNodeValue(TreeIconNode node) {
        mainPanel.saveNodeValue((TreeIconNode.ProfileNode) node.getCollection().profile);
    }

    public void openDialog(TreeIconNode node, Editor.Entry entry) {
        this.entry = entry;
        this.node = node;
        transferName = entry.name;
        transferValue = null;
        if (entry.getType() == EDIT) {
            optionPane.setOptions(new Object[]{applyButton, deleteButton, cancelButton});
            Matcher m = PATTERN_1.matcher(entry.name);
            if (m.find()) {
                transferName = m.group(3);
                optionPane.setOptions(null);
            }
            transferValue = ((JSONObject)entry.json).getOrDefault(entry.name, null);
        } else {
            optionPane.setOptions(new Object[]{applyButton, cancelButton});
        }
        lblError.setText("");
        txtPath.setText("");
        txtEditor.setText("");
        
        buildTargetModel();
        
        Object path = node.jsonObject().getJSONObject("response").getOrDefault("body", null);      
        txtBody.setText(String.valueOf(path));
        String[] treePath = new String[0];
        if (transferValue instanceof String && transferValue.toString().startsWith("/")) {
            treePath = transferValue.toString().split("/");
        }
        buildTree(path, Arrays.toString(treePath));
        
        if (entry.getType() == ADD || treePath.length > 0) {
            txtPath.setText(String.valueOf(transferValue));

            rbSelectPropertyPath.setEnabled(treeNode.getChildCount() > 0);

            if (!treeSchema.isSelectionEmpty()) {
                rbSelectPropertyPath.setSelected(true);
                rbSelectPropertyPathActionPerformed(null);
            } else {
                rbrEnterPropertyPath.setSelected(true);
                rbrEnterPropertyPathActionPerformed(null);
            }
        } else {
            rbAssignValue.setSelected(true);
            rbAssignValueActionPerformed(null);
            if (transferValue instanceof JSON) {
                try {
                    txtEditor.setText(IO.prettyJson(((JSON) transferValue).toString()));
                } catch (Exception ex) {
                }
            } else {
                txtEditor.setText(transferValue != null ? String.valueOf(transferValue) : "");
            }
            cmbDataType.setSelectedItem(ResourceEditDialog.getType(transferValue));
        }
        
        Amphibia.resetUndoManager(txtEditor);
        cmbDataTypeItemStateChanged(null);
        dialog.setVisible(true);
    }
    
    private void addError(String error, Exception ex) {
        lblError.setText(error);
        mainPanel.addError(ex);
    }
    
    private void buildTargetModel() {
        targetModel.removeAllElements();
        JSONObject ivailableProperties = node.jsonObject().getJSONObject("available-properties");
        ivailableProperties.keySet().forEach((key) -> {
            targetModel.addElement(key);
            if (entry.getType() == EDIT && key.toString().equals(transferName)) {
                cmbTarget.setSelectedIndex(targetModel.getSize() - 1);
            }
        });
        
        if (chbReqProperties.isSelected()) {
            JSONObject properties = (JSONObject) node.jsonObject().getJSONObject("request").getOrDefault("properties", new JSONObject());
            properties.keySet().forEach((key) -> {
                targetModel.addElement(key);
            });
        }
        if (chbResProperties.isSelected()) {
            JSONObject properties = (JSONObject) node.jsonObject().getJSONObject("response").getOrDefault("properties", new JSONObject());
            properties.keySet().forEach((key) -> {
                targetModel.addElement(key);
            });
        }
    }
    
    private void buildTree(Object path, String treePath) {
        selectedTreeNode = null;
        treeNode.removeAllChildren();
        if (path != null) {
            File test = IO.getFile(node.getCollection(), (String) path);
            if (test.exists()) {
                bodyJSON = (JSONObject) IO.getJSON(path.toString(), mainPanel.editor);
                if (bodyJSON != null) {
                    if (bodyJSON != null) {
                        rbSelectPropertyPath.setSelected(true);
                        buildTreeNode(treeNode, bodyJSON, treePath, -1);
                        java.awt.EventQueue.invokeLater(() -> {
                            for (int i = 0; i < treeSchema.getRowCount(); i++) {
                                treeSchema.expandRow(i);
                            }
                        });
                    }
                }
            }
        } else {
            rbAssignValue.setSelected(true);
        }
        treeModel.reload(treeNode);
        if (selectedTreeNode != null) {
            treeSchema.setSelectionPath(new TreePath(selectedTreeNode));
        }
        rbAssignValueActionPerformed(null);
    }
    
    private void buildTreeNode(DefaultMutableTreeNode node, JSON json, String treePath, int itemIndex) {
        if (json instanceof JSONArray) {
            JSONArray array = (JSONArray) json;
            for (int i = 0; i < array.size(); i++) {
                Object item = array.get(i);
                if (item instanceof JSONArray || item instanceof JSONObject) {
                    buildTreeNode(node, (JSON) item, treePath, i);
                } else {
                    DefaultMutableTreeNode child = new DefaultMutableTreeNode(new TreeItem(json, item, i));
                    node.add(child);
                    if(Arrays.toString(child.getPath()).equals(treePath)) {
                        selectedTreeNode = child.getPath();
                    }
                }
            }
        } else {
            JSONObject parent = (JSONObject) json;
            parent.keySet().forEach((key) -> {
                DefaultMutableTreeNode child = new DefaultMutableTreeNode(new TreeItem(parent, key, itemIndex));
                node.add(child);
                if(Arrays.toString(child.getPath()).equals(treePath)) {
                    selectedTreeNode = child.getPath();
                }
                Object item = parent.get(key);
                if (item instanceof JSONArray || item instanceof JSONObject) {
                    buildTreeNode(child, (JSON) item, treePath, -1);
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
        lblBody = new JLabel();
        txtBody = new JTextField();
        lblTarget = new JLabel();
        cmbTarget = new JComboBox<>();
        pnlProperties = new JPanel();
        chbReqProperties = new JCheckBox();
        chbResProperties = new JCheckBox();
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
        pnlHeader.setLayout(new BorderLayout());

        GridBagLayout pnlGridLayout = new GridBagLayout();
        pnlGridLayout.columnWidths = new int[] {0, 5, 0};
        pnlGridLayout.rowHeights = new int[] {0, 5, 0, 5, 0, 5, 0, 5, 0};
        pnlGrid.setLayout(pnlGridLayout);

        lblBody.setFont(new Font("Tahoma", 1, 11)); // NOI18N
        ResourceBundle bundle = ResourceBundle.getBundle("com/equinix/amphibia/messages"); // NOI18N
        lblBody.setText(bundle.getString("responseBody")); // NOI18N
        gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.ipadx = 5;
        gridBagConstraints.anchor = GridBagConstraints.WEST;
        pnlGrid.add(lblBody, gridBagConstraints);

        txtBody.setEditable(false);
        gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        pnlGrid.add(txtBody, gridBagConstraints);

        lblTarget.setFont(new Font("Tahoma", 1, 11)); // NOI18N
        lblTarget.setText(bundle.getString("targetProperty")); // NOI18N
        gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.gridwidth = GridBagConstraints.RELATIVE;
        gridBagConstraints.ipadx = 5;
        gridBagConstraints.anchor = GridBagConstraints.WEST;
        gridBagConstraints.insets = new Insets(5, 0, 5, 0);
        pnlGrid.add(lblTarget, gridBagConstraints);

        cmbTarget.setModel(this.targetModel);
        gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.fill = GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        pnlGrid.add(cmbTarget, gridBagConstraints);

        pnlProperties.setLayout(new FlowLayout(FlowLayout.LEFT));

        chbReqProperties.setSelected(true);
        chbReqProperties.setText(bundle.getString("reqProperties")); // NOI18N
        chbReqProperties.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                chbReqPropertiesActionPerformed(evt);
            }
        });
        pnlProperties.add(chbReqProperties);

        chbResProperties.setSelected(true);
        chbResProperties.setText(bundle.getString("resProperties")); // NOI18N
        chbResProperties.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                chbResPropertiesActionPerformed(evt);
            }
        });
        pnlProperties.add(chbResProperties);

        gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 6;
        gridBagConstraints.fill = GridBagConstraints.HORIZONTAL;
        pnlGrid.add(pnlProperties, gridBagConstraints);

        lblPath.setFont(new Font("Tahoma", 1, 11)); // NOI18N
        lblPath.setText(bundle.getString("propertyPath")); // NOI18N
        gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 7;
        gridBagConstraints.ipadx = 5;
        gridBagConstraints.anchor = GridBagConstraints.WEST;
        pnlGrid.add(lblPath, gridBagConstraints);
        gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 7;
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

        pnlFooter.setLayout(new BorderLayout());

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

        pnlFooter.add(pnlDataType, BorderLayout.NORTH);

        lblError.setFont(new Font("Tahoma", 0, 12)); // NOI18N
        lblError.setForeground(new Color(255, 0, 0));
        lblError.setHorizontalAlignment(SwingConstants.CENTER);
        lblError.setBorder(BorderFactory.createEmptyBorder(5, 1, 5, 1));
        pnlFooter.add(lblError, BorderLayout.CENTER);

        pnlValue.add(pnlFooter, BorderLayout.SOUTH);

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
        cmbDataType.setEnabled(true);
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
                    txtEditor.setText("${#" + s + "#" + cmbTarget.getSelectedItem() + "}");
                }
            });
        }
    }//GEN-LAST:event_cmbDataTypeActionPerformed

    private void chbReqPropertiesActionPerformed(ActionEvent evt) {//GEN-FIRST:event_chbReqPropertiesActionPerformed
        buildTargetModel();
    }//GEN-LAST:event_chbReqPropertiesActionPerformed

    private void chbResPropertiesActionPerformed(ActionEvent evt) {//GEN-FIRST:event_chbResPropertiesActionPerformed
        buildTargetModel();
    }//GEN-LAST:event_chbResPropertiesActionPerformed


    // Variables declaration - do not modify//GEN-BEGIN:variables
    JCheckBox chbReqProperties;
    JCheckBox chbResProperties;
    JComboBox<String> cmbDataType;
    JComboBox<String> cmbTarget;
    JLabel lblBody;
    JLabel lblDataType;
    JLabel lblError;
    JLabel lblPath;
    JLabel lblTarget;
    JPanel pnlCenter;
    JPanel pnlDataType;
    JPanel pnlFooter;
    JPanel pnlGrid;
    JPanel pnlHeader;
    JPanel pnlProperties;
    JPanel pnlPropertyPath;
    JPanel pnlValue;
    JRadioButton rbAssignValue;
    JRadioButton rbSelectPropertyPath;
    ButtonGroup rbgPropertyPath;
    JRadioButton rbrEnterPropertyPath;
    JScrollPane splEditor;
    JScrollPane spnSchema;
    JTree treeSchema;
    JTextField txtBody;
    JTextArea txtEditor;
    JTextField txtPath;
    // End of variables declaration//GEN-END:variables
}

class TreeItem {
    public JSON json;
    public String label;
    public int itemIndex;

    public TreeItem(JSON json, Object label, int itemIndex) {
        this.json = json;
        this.label = String.valueOf(label);
        this.itemIndex = itemIndex;
    }
    
    @Override
    public String toString() {
        return label;
    }
}
