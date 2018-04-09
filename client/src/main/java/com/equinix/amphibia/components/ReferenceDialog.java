/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template testFile, choose Tools | Templates
 * and open the template in the editor.
 */
package com.equinix.amphibia.components;

import static com.equinix.amphibia.components.Editor.Entry;

import com.equinix.amphibia.Amphibia;
import com.equinix.amphibia.IO;
import com.equinix.amphibia.agent.builder.Properties;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.util.ResourceBundle;
import java.util.regex.Matcher;
import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import net.sf.json.JSON;
import net.sf.json.JSONArray;
import net.sf.json.JSONNull;
import net.sf.json.JSONObject;

/**
 *
 * @author dgofman
 */
public final class ReferenceDialog extends javax.swing.JPanel {

    private MainPanel mainPanel;
    private Entry entry;
    private TreeCollection collection;
    private JDialog dialog;
    private JOptionPane optionPane;
    private JButton applyButton;
    private JButton cancelButton;
    private ResourceBundle bundle;
    private String originalPreviewBody;
    private JSONObject newProperties;
    private final DefaultComboBoxModel<ComboItem> referenceModel;

    private static final String ASSERTS_DIR_FORMAT = "data/%s/asserts";

    /**
     * Creates new form TableEditDialog
     *
     * @param mainPanel
     */
    public ReferenceDialog(MainPanel mainPanel) {
        this.mainPanel = mainPanel;

        newProperties = new JSONObject();
        referenceModel = new DefaultComboBoxModel<>();

        initComponents();

        Amphibia.addUndoManager(txtPreview);

        bundle = Amphibia.getBundle();

        applyButton = new JButton(bundle.getString("apply"));
        applyButton.addActionListener((ActionEvent evt) -> {
            lblError.setText("");
            ComboItem item = (ComboItem) cmbPath.getSelectedItem();
            TreeIconNode node = entry.node;
            boolean isTestCase = node.getType() == TreeCollection.TYPE.TESTCASE;
            ((JSONObject) entry.json).element(entry.name, item.file != null ? item.label : JSONNull.getInstance());

            try {
                String parent = entry.getParent().toString();
                if ("request|response".contains(parent)) {
                    final int[] isModified = new int[]{0};
                    JSONObject testReqOrRes = new JSONObject();
                    JSONObject sourceJSON;
                    if (isTestCase) {
                        File testFile = IO.getFile(collection, node.jsonObject().getString("file"));
                        JSONObject testJSON = (JSONObject) IO.getJSON(testFile);
                        testReqOrRes = testJSON.getJSONObject(parent);
                        sourceJSON = testJSON;
                        try {
                            String[] contents = IO.write(IO.prettyJson(testJSON.toString()), testFile, true);
                            mainPanel.history.addHistory(testFile.getAbsolutePath(), contents[0], contents[1]);
                        } catch (Exception ex) {
                            addError(String.format(bundle.getString("error_convert"), "JSON"), ex);
                            return;
                        }
                    } else {
                        sourceJSON = node.jsonObject();
                    }
                    JSONObject reqOrRes = (JSONObject) sourceJSON.getOrDefault(parent, new JSONObject());

                    if (!newProperties.isEmpty()) {
                        JSONObject testProperties = (JSONObject) testReqOrRes.getOrDefault("properties", new JSONObject());
                        JSONObject properties = (JSONObject) reqOrRes.getOrDefault("properties", new JSONObject());
                        newProperties.keySet().forEach((key) -> {
                            if (txtPreview.getText().contains("`${#" + key + "}`")) {
                                Object val1 = testProperties.getOrDefault(key, null);
                                Object val2 = newProperties.get(key);
                                if ((val1 == null && val2 != null) || (val1 != null && !val1.equals(val2))) {
                                    isModified[0] = 1;
                                    properties.put(key, val2);
                                }
                            }
                        });
                        if (properties.isEmpty() && !isTestCase) {
                            reqOrRes.remove("properties");
                        } else {
                            reqOrRes.put("properties", properties);
                        }
                    }

                    if (!item.label.equals(testReqOrRes.getOrDefault("body", null)) &&
                        !item.label.equals(reqOrRes.getOrDefault("body", null))) {
                        isModified[0] = 1;
                        reqOrRes.put("body", item.label);
                    }

                    if (isModified[0] == 1) {
                        if (reqOrRes.isEmpty() && !isTestCase) {
                            sourceJSON.remove(parent);
                        } else {
                            sourceJSON.put(parent, reqOrRes);
                        }
                    }
                }
            } catch (Exception ex) {
                addError(String.format(bundle.getString("error_convert"), "JSON"), ex);
                return;
            }

            if (chbEdit.isSelected() && item.file != null && item.file.getParentFile() != null) {
                try {
                    String[] contents = IO.write(txtPreview.getText(), item.file, true);
                    mainPanel.history.addHistory(item.file.getAbsolutePath(), contents[0], contents[1]);
                } catch (Exception ex) {
                    addError(String.format(bundle.getString("error_convert"), "JSON"), ex);
                    return;
                }
            }

            mainPanel.history.saveEntry(entry, collection);
            mainPanel.reloadCollection(collection);
            dialog.setVisible(false);
        });
        cancelButton = new JButton(bundle.getString("cancel"));
        cancelButton.addActionListener((ActionEvent evt) -> {
            dialog.setVisible(false);
        });

        optionPane = new JOptionPane(this);
        dialog = Amphibia.createDialog(optionPane, true);
        dialog.setResizable(true);
        dialog.setSize(new Dimension(800, 600));
        java.awt.EventQueue.invokeLater(() -> {
            dialog.setLocationRelativeTo(mainPanel);
        });
    }

    private void addError(String error, Exception ex) {
        lblError.setText(error);
        mainPanel.addError(ex);
    }

    private boolean initDialog(TreeCollection collection, Entry entry) {
        this.collection = collection;
        this.entry = entry;
        txtPath.setVisible(true);
        cmbPath.setVisible(false);
        pnlFooter.setVisible(false);
        lblError.setText("");
        chbEdit.setSelected(false);
        chbStoreValues.setSelected(false);
        chbStoreValues.setEnabled(false);
        txtPreview.setEditable(false);
        txtPreview.setEnabled(true);
        txtPreview.setBackground(UIManager.getColor("TextArea.background"));

        if (entry.node.info == null && entry.node.getType() != TreeCollection.TYPE.LINK) {
            mainPanel.resourceEditDialog.openEditDialog(entry, entry.value, true);
            return false;
        } else {
            if (!IO.isNULL(entry.value)) {
                String filePath = (String) entry.value;
                txtPath.setText(filePath);
                reviewPath(filePath != null ? IO.getFile(filePath) : null);
            }
            txtName.setText(entry.name);
            return true;
        }
    }

    private void reviewPath(File file) {
        if (file != null && file.exists()) {
            newProperties = new JSONObject();
            Amphibia.setText(txtPreview, splPreview, IO.readFile(file, mainPanel.editor));
        } else {
            txtPreview.setText("");
        }
        Amphibia.resetUndoManager(txtPreview);
    }

    @SuppressWarnings("NonPublicExported")
    public void openViewDialog(TreeCollection collection, Entry entry) {
        if (!initDialog(collection, entry)) {
            return;
        }
        optionPane.setOptions(null);
        dialog.setVisible(true);
    }

    @SuppressWarnings("NonPublicExported")
    public void openEditDialog(TreeCollection collection, Entry entry) {
        if (!initDialog(collection, entry)) {
            return;
        }
        TreeIconNode node = entry.node;
        optionPane.setOptions(new Object[]{applyButton, cancelButton});
        newProperties = new JSONObject();
        
        referenceModel.removeAllElements();
        referenceModel.addElement(new ComboItem("NULL"));
        referenceModel.addElement(new ComboItem(bundle.getString("browse")));

        int selectedIndex = 0;
        String resoursePath = null;
        if (node.info != null) {
            String resourceId = node.info.resource.getString("resourceId");
            String testSuiteName = node.info.testSuite.getString("name");
            resoursePath = String.format("data/%s/" + entry.rootName + "/%s/", resourceId, testSuiteName);
            if ("response".equals(entry.getParent().toString())) {
                String assertsPath = String.format(ASSERTS_DIR_FORMAT, resourceId);
                File dir = IO.getFile(node.getCollection(), assertsPath);
                if (dir.exists()) {
                    for (String fileName : dir.list()) {
                        String path = assertsPath + "/" + fileName;
                        referenceModel.addElement(new ComboItem(path, IO.getFile(node.getCollection(), path)));
                    }
                }
            }
        }
        
        String filePath = (String) entry.value;
        if (!IO.isNULL(filePath)) {
            if (resoursePath == null || !filePath.contains(resoursePath)) {
                selectedIndex = referenceModel.getSize();
                referenceModel.addElement(new ComboItem(filePath, IO.getFile(node.getCollection(), filePath)));
            }
        } else {
            File resourseDir;
            if (resoursePath != null && (resourseDir = IO.getFile(resoursePath)).exists()) {
                for (String fileName : resourseDir.list()) {
                    String path = resoursePath + fileName;
                    if (path.equals(filePath)) {
                        selectedIndex = referenceModel.getSize();
                    }
                    referenceModel.addElement(new ComboItem(path, IO.newFile(resourseDir, fileName)));
                }
            }
        }

        cmbPath.setSelectedIndex(selectedIndex);
        isPreviewEnabled();

        txtPath.setVisible(false);
        cmbPath.setVisible(true);
        pnlFooter.setVisible(true);

        dialog.setVisible(true);
    }

    private boolean isPreviewEnabled() {
        txtPreview.setEnabled(cmbPath.getSelectedIndex() > 0);
        txtPreview.setBackground(UIManager.getColor(txtPreview.isEnabled() ? "TextArea.background" : "TextArea.disabledBackground"));
        return txtPreview.isEnabled();
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

        pnlHeader = new JPanel();
        lblName = new JLabel();
        txtName = new JTextField();
        lblPath = new JLabel();
        txtPath = new JTextField();
        cmbPath = new JComboBox();
        pnlBottom = new JPanel();
        lblPreview = new JLabel();
        splPreview = new JScrollPane();
        txtPreview = new JTextArea();
        pnlFooter = new JPanel();
        chbEdit = new JCheckBox();
        chbStoreValues = new JCheckBox();
        lblError = new JLabel();

        setBorder(BorderFactory.createEmptyBorder(5, 10, 0, 10));
        setLayout(new BorderLayout());

        pnlHeader.setBorder(BorderFactory.createEmptyBorder(1, 1, 5, 1));
        pnlHeader.setLayout(new GridBagLayout());

        lblName.setFont(new Font("Tahoma", 1, 11)); // NOI18N
        ResourceBundle bundle = ResourceBundle.getBundle("com/equinix/amphibia/messages"); // NOI18N
        lblName.setText(bundle.getString("name")); // NOI18N
        gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.gridwidth = GridBagConstraints.RELATIVE;
        gridBagConstraints.ipadx = 5;
        gridBagConstraints.anchor = GridBagConstraints.WEST;
        gridBagConstraints.insets = new Insets(0, 0, 5, 0);
        pnlHeader.add(lblName, gridBagConstraints);

        txtName.setEditable(false);
        gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.fill = GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new Insets(0, 0, 5, 0);
        pnlHeader.add(txtName, gridBagConstraints);

        lblPath.setFont(new Font("Tahoma", 1, 11)); // NOI18N
        lblPath.setText(bundle.getString("path")); // NOI18N
        gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.gridy = 2;
        gridBagConstraints.ipadx = 5;
        gridBagConstraints.anchor = GridBagConstraints.WEST;
        pnlHeader.add(lblPath, gridBagConstraints);

        txtPath.setEditable(false);
        gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.gridy = 2;
        gridBagConstraints.fill = GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new Insets(0, 0, 5, 0);
        pnlHeader.add(txtPath, gridBagConstraints);

        cmbPath.setModel(this.referenceModel);
        cmbPath.addPopupMenuListener(new PopupMenuListener() {
            public void popupMenuCanceled(PopupMenuEvent evt) {
            }
            public void popupMenuWillBecomeInvisible(PopupMenuEvent evt) {
                cmbPathPopupMenuWillBecomeInvisible(evt);
            }
            public void popupMenuWillBecomeVisible(PopupMenuEvent evt) {
            }
        });
        cmbPath.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                cmbPathActionPerformed(evt);
            }
        });
        gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.gridy = 2;
        gridBagConstraints.fill = GridBagConstraints.HORIZONTAL;
        pnlHeader.add(cmbPath, gridBagConstraints);

        add(pnlHeader, BorderLayout.NORTH);

        pnlBottom.setLayout(new BorderLayout());

        lblPreview.setFont(new Font("Tahoma", 1, 11)); // NOI18N
        lblPreview.setText(bundle.getString("preview")); // NOI18N
        pnlBottom.add(lblPreview, BorderLayout.NORTH);

        txtPreview.addKeyListener(new KeyAdapter() {
            public void keyPressed(KeyEvent evt) {
                txtPreviewKeyPressed(evt);
            }
        });
        splPreview.setViewportView(txtPreview);

        pnlBottom.add(splPreview, BorderLayout.CENTER);

        add(pnlBottom, BorderLayout.CENTER);

        pnlFooter.setLayout(new BorderLayout());

        chbEdit.setText(bundle.getString("enableEdit")); // NOI18N
        chbEdit.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                chbEditActionPerformed(evt);
            }
        });
        pnlFooter.add(chbEdit, BorderLayout.WEST);

        chbStoreValues.setText(bundle.getString("storeValues")); // NOI18N
        chbStoreValues.setEnabled(false);
        chbStoreValues.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                chbStoreValuesActionPerformed(evt);
            }
        });
        pnlFooter.add(chbStoreValues, BorderLayout.CENTER);

        lblError.setFont(new Font("Tahoma", 0, 12)); // NOI18N
        lblError.setForeground(new Color(255, 0, 0));
        lblError.setHorizontalAlignment(SwingConstants.CENTER);
        lblError.setBorder(BorderFactory.createEmptyBorder(5, 1, 5, 1));
        pnlFooter.add(lblError, BorderLayout.SOUTH);

        add(pnlFooter, BorderLayout.SOUTH);
    }// </editor-fold>//GEN-END:initComponents

    private void cmbPathPopupMenuWillBecomeInvisible(PopupMenuEvent evt) {//GEN-FIRST:event_cmbPathPopupMenuWillBecomeInvisible
        isPreviewEnabled();
        java.awt.EventQueue.invokeLater(() -> {
            if (cmbPath.getSelectedIndex() == 1) {
                String dir = entry.node.getCollection().getProjectDir().getAbsolutePath();
                if (entry.node.info != null) {
                    String resourceId = entry.node.info.resource.getString("resourceId");
                    dir = !IO.isNULL(entry.value) ? IO.getFile((String) entry.value).getParent() :  String.format("data/%s/", resourceId);
                }
                JFileChooser jc = Amphibia.setFileChooserDir(new JFileChooser());
                File file = IO.getFile(dir);
                if (file.exists() && file.isDirectory()) {
                    jc.setCurrentDirectory(file);
                }
                jc.setFileFilter(new FileNameExtensionFilter("JSON File", "json", "text"));
                int rVal = jc.showSaveDialog(null);
                if (rVal == JFileChooser.CANCEL_OPTION) {
                    cmbPath.setSelectedIndex(0);
                    isPreviewEnabled();
                } else {
                    try {
                        if (!jc.getSelectedFile().exists()) {
                            jc.getSelectedFile().createNewFile();
                        }
                        reviewPath(jc.getSelectedFile());

                        for (int i = 0; i < referenceModel.getSize(); i++) {
                            if (String.valueOf(referenceModel.getElementAt(i).file).equals(jc.getSelectedFile().toString())) {
                                cmbPath.setSelectedIndex(i);
                                return;
                            }
                        }
                        String relPath = jc.getSelectedFile().getAbsolutePath().replace(collection.getProjectDir().getAbsolutePath(), "");
                        relPath = relPath.replaceAll("\\\\", "/");
                        if (relPath.charAt(0) == '/') {
                            relPath = relPath.substring(1);
                        }
                        referenceModel.addElement(new ComboItem(relPath, jc.getSelectedFile()));
                        cmbPath.setSelectedIndex(referenceModel.getSize() - 1);
                    } catch (IOException ex) {
                        mainPanel.addError(ex);
                    }
                }
            } else if (cmbPath.getSelectedItem() != null) {
                reviewPath(((ComboItem) cmbPath.getSelectedItem()).file);
            } else {
                reviewPath(null);
            }
        });
    }//GEN-LAST:event_cmbPathPopupMenuWillBecomeInvisible

    private void chbEditActionPerformed(ActionEvent evt) {//GEN-FIRST:event_chbEditActionPerformed
        txtPreview.setEditable(chbEdit.isSelected());
        chbStoreValues.setEnabled(chbEdit.isSelected());
        if (chbEdit.isSelected()) {
            chbStoreValues.setSelected(false);
        }
    }//GEN-LAST:event_chbEditActionPerformed

    private void chbStoreValuesActionPerformed(ActionEvent evt) {//GEN-FIRST:event_chbStoreValuesActionPerformed
        try {
            String text;
            if (chbStoreValues.isSelected()) {
                lblError.setText("");
                originalPreviewBody = txtPreview.getText();
                String resourseId = entry.node.info.resource.getString("resourceId");
                String assertsPath = String.format(ASSERTS_DIR_FORMAT, resourseId);
                boolean isAssertDir = cmbPath.getSelectedItem().toString().startsWith(assertsPath);
                JSON newJSON = IO.toJSON(originalPreviewBody);

                StringBuilder sb = new StringBuilder(isAssertDir ? "assert" : "");
                new Object() {
                    void walk(JSON json, StringBuilder sb) {
                        if (json instanceof JSONArray) {
                            JSONArray array = ((JSONArray) json);
                            for (int i = 0; i < array.size(); i++) {
                                StringBuilder ids = sb;
                                if (array.size() > 1) {
                                    ids = new StringBuilder(sb).append(sb.length() > 0 ? "." : "").append(i);
                                }
                                Object value = array.get(i);
                                if (value instanceof JSON) {
                                    walk((JSON) value, ids);
                                } else {
                                    newProperties.put(ids.toString(), value);
                                    array.set(i, "`${#" + ids.toString() + "}`");
                                }
                            }
                        } else {
                            JSONObject obj = (JSONObject) json;
                            obj.keySet().forEach((key) -> {
                                StringBuilder ids = new StringBuilder(sb).append(sb.length() > 0 ? "." : "").append(key);
                                Object value = obj.get(key);
                                if (value instanceof JSON) {
                                    walk((JSON) value, ids);
                                } else {
                                    Matcher m = Properties.PATTERN_2.matcher(String.valueOf(value));
                                    if (!m.find()) {
                                        newProperties.put(ids.toString(), value);
                                    }
                                    obj.put(key, "`${#" + ids.toString() + "}`");
                                }
                            });
                        }
                    }
                }.walk(newJSON, sb);
                text = IO.prettyJson(newJSON.toString());
            } else {
                text = originalPreviewBody;
            }
            Amphibia.setText(txtPreview, splPreview, text);
        } catch (Exception ex) {
            chbStoreValues.setSelected(false);
            addError(String.format(bundle.getString("error_convert"), "JSON"), ex);
        }
    }//GEN-LAST:event_chbStoreValuesActionPerformed

    private void txtPreviewKeyPressed(KeyEvent evt) {//GEN-FIRST:event_txtPreviewKeyPressed
        chbStoreValues.setSelected(false);
    }//GEN-LAST:event_txtPreviewKeyPressed

    private void cmbPathActionPerformed(ActionEvent evt) {//GEN-FIRST:event_cmbPathActionPerformed
        chbStoreValues.setSelected(false);
        newProperties = new JSONObject();
    }//GEN-LAST:event_cmbPathActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    JCheckBox chbEdit;
    JCheckBox chbStoreValues;
    JComboBox cmbPath;
    JLabel lblError;
    JLabel lblName;
    JLabel lblPath;
    JLabel lblPreview;
    JPanel pnlBottom;
    JPanel pnlFooter;
    JPanel pnlHeader;
    JScrollPane splPreview;
    JTextField txtName;
    JTextField txtPath;
    JTextArea txtPreview;
    // End of variables declaration//GEN-END:variables

    class ComboItem {

        public String label;
        public File file;

        public ComboItem(String label) {
            this(label, null);
        }

        public ComboItem(Object label) {
            this(label.toString(), IO.getFile(collection, label.toString()));
        }

        public ComboItem(String label, File file) {
            this.label = label;
            this.file = file;
        }

        @Override
        public String toString() {
            return label;
        }
    }
}
