/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.equinix.amphibia.components;

import static com.equinix.amphibia.components.Editor.Entry;

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
import java.io.File;
import java.io.IOException;
import java.util.ResourceBundle;
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
    private final DefaultComboBoxModel<ComboItem> referenceModel;

    /**
     * Creates new form TableEditDialog
     *
     * @param mainPanel
     */
    public ReferenceDialog(MainPanel mainPanel) {
        this.mainPanel = mainPanel;

        referenceModel = new DefaultComboBoxModel<>();

        initComponents();

        bundle = Amphibia.getBundle();

        applyButton = new JButton(bundle.getString("apply"));
        applyButton.addActionListener((ActionEvent evt) -> {
            ComboItem item = (ComboItem) cmbPath.getSelectedItem();
            ((JSONObject) entry.json).element(entry.name, item.file != null ? item.label : JSONNull.getInstance());
            String[] contents = null;
            if (chbEdit.isSelected() && item.file != null && item.file.exists()) {
                try {
                    contents = IO.write(txtPreview.getText(), item.file, true);
                } catch (Exception ex) {
                    lblError.setText(String.format(bundle.getString("error_convert"), "JSON"));
                    return;
                }
            }
            if (contents != null) {
                mainPanel.history.addHistory(item.file.getAbsolutePath(), contents[0], contents[1]);
            }
            mainPanel.history.saveEntry(entry, collection);
            mainPanel.reloadCollection(collection);
            lblError.setText("");
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

    private Object[] initDialog(TreeCollection collection, Entry entry) {
        this.collection = collection;
        this.entry = entry;
        txtPath.setVisible(true);
        cmbPath.setVisible(false);
        lblError.setText("");
        txtPreview.setEnabled(true);
        chbEdit.setSelected(false);
        txtPreview.setBackground(UIManager.getColor("TextArea.background"));
        txtPreview.setEditable(MainPanel.selectedNode.getType() == TreeCollection.TYPE.TEST_ITEM);
        Object[] info = getFileInfo();
        if (info != null) {
            txtPath.setText(String.valueOf(entry.value));
            txtName.setText(entry.name);
            reviewPath((File) info[2]);
        } else {
            mainPanel.resourceEditDialog.openEditDialog(entry, entry.value, true);
        }
        return info;
    }

    private void reviewPath(File file) {
        if (file != null && file.exists()) {
            Amphibia.setText(txtPreview, splPreview, IO.readFile(file, mainPanel.editor));
        } else {
            txtPreview.setText("");
        }
    }

    @SuppressWarnings("NonPublicExported")
    public void openViewDialog(TreeCollection collection, Entry entry) {
        if (initDialog(collection, entry) == null) {
            return;
        }
        optionPane.setOptions(null);
        dialog.setVisible(true);
    }

    @SuppressWarnings("NonPublicExported")
    public void openEditDialog(TreeCollection collection, Entry entry) {
        Object[] info = initDialog(collection, entry);
        if (info == null) {
            return;
        }
        optionPane.setOptions(new Object[]{applyButton, cancelButton});
 
        if (info[0] != null) { //path
            referenceModel.removeAllElements();
            referenceModel.addElement(new ComboItem("NULL"));
            referenceModel.addElement(new ComboItem(bundle.getString("browse")));

            txtPath.setVisible(false);
            cmbPath.setVisible(true);
            cmbPath.setSelectedIndex(0);
            File dir = (File) info[1];
            if (dir.exists()) {
                for (String file : dir.list()) {
                    String path = info[0] + "/" + file;
                    referenceModel.addElement(new ComboItem(path, IO.newFile(dir, file)));
                    if (path.equals(entry.value)) {
                        cmbPath.setSelectedIndex(referenceModel.getSize() - 1);
                    }
                }
            }
            if (cmbPath.getSelectedIndex() == 0 && entry.value instanceof String) {
                cmbPath.insertItemAt(new ComboItem(entry.value), 2);
                cmbPath.setSelectedIndex(2);
            }
            isPreviewEnabled();
        }
        dialog.setVisible(true);
    }

    private boolean isPreviewEnabled() {
        txtPreview.setEnabled(cmbPath.getSelectedIndex() > 0);
        txtPreview.setBackground(UIManager.getColor(txtPreview.isEnabled() ? "TextArea.background" : "TextArea.disabledBackground"));
        return txtPreview.isEnabled();
    }

    public Object[] getFileInfo() {
        TreeIconNode.ResourceInfo info = MainPanel.selectedNode.info;
        if (info == null) {
            return null;
        }
        String dirPath = "data/%s/%s/%s";
        String resourceId = info.resource.getString("resourceId");
        String testSuiteName = info.testSuite.getString("name");
        if ("file".equals(entry.name)) {
            File file = IO.getFile(collection, entry.value.toString());
            if (!file.exists()) {
                file = IO.newFile(entry.value.toString());
            }
            return new Object[]{null, file.getParentFile(), file};
        } else if ("request".equals(entry.getParent().toString())) {
            switch (entry.name) {
                case "body":
                    dirPath = String.format(dirPath, resourceId, "requests", testSuiteName);
                    break;
                case "schema":
                    dirPath = String.format(dirPath, resourceId, "schemas", "requests");
                    break;
                default:
                    return new Object[]{};
            }
        } else {
            switch (entry.name) {
                case "body":
                    dirPath = String.format(dirPath, resourceId, "responses", testSuiteName);
                    break;
                case "schema":
                    dirPath = String.format(dirPath, resourceId, "schemas", "responses");
                    break;
                case "asserts":
                    dirPath = String.format(dirPath, resourceId, "schemas", "asserts");
                    break;
                default:
                    return null;
            }
        }
        File file;
        if (entry.value instanceof String && !entry.value.toString().isEmpty()) {
            file = IO.getFile(collection, entry.value.toString());
            if (!file.exists()) {
                file = IO.newFile(entry.value.toString());
            }
        } else {
            return new Object[]{dirPath, IO.getFile(collection, dirPath), null};
        }
        return new Object[]{dirPath, file.getParentFile(), file};
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
        gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.gridy = 2;
        gridBagConstraints.fill = GridBagConstraints.HORIZONTAL;
        pnlHeader.add(cmbPath, gridBagConstraints);

        add(pnlHeader, BorderLayout.NORTH);

        pnlBottom.setLayout(new BorderLayout());

        lblPreview.setFont(new Font("Tahoma", 1, 11)); // NOI18N
        lblPreview.setText(bundle.getString("preview")); // NOI18N
        pnlBottom.add(lblPreview, BorderLayout.NORTH);

        splPreview.setViewportView(txtPreview);

        pnlBottom.add(splPreview, BorderLayout.CENTER);

        add(pnlBottom, BorderLayout.CENTER);

        pnlFooter.setLayout(new GridLayout(2, 0));

        chbEdit.setText(bundle.getString("enableEdit")); // NOI18N
        chbEdit.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                chbEditActionPerformed(evt);
            }
        });
        pnlFooter.add(chbEdit);

        lblError.setFont(new Font("Tahoma", 0, 12)); // NOI18N
        lblError.setForeground(new Color(255, 0, 0));
        lblError.setHorizontalAlignment(SwingConstants.CENTER);
        lblError.setBorder(BorderFactory.createEmptyBorder(5, 1, 5, 1));
        pnlFooter.add(lblError);

        add(pnlFooter, BorderLayout.SOUTH);
    }// </editor-fold>//GEN-END:initComponents

    private void cmbPathPopupMenuWillBecomeInvisible(PopupMenuEvent evt) {//GEN-FIRST:event_cmbPathPopupMenuWillBecomeInvisible
        isPreviewEnabled();
        java.awt.EventQueue.invokeLater(() -> {
            if (cmbPath.getSelectedIndex() == 1) {
                Object[] info = getFileInfo();
                File dir = ((File) info[1]);
                if (!dir.exists()) {
                    dir.mkdirs();
                }
                JFileChooser jc = new JFileChooser();
                jc.setFileFilter(new FileNameExtensionFilter("JSON File", "json", "text"));
                jc.setCurrentDirectory(dir);
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
                        txtPreview.setEditable(true);
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
    }//GEN-LAST:event_chbEditActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    JCheckBox chbEdit;
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
