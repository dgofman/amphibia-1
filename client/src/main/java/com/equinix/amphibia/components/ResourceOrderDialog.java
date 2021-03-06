/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.equinix.amphibia.components;

import com.equinix.amphibia.Amphibia;
import com.equinix.amphibia.IO;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Arrays;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.lang.ArrayUtils;

/**
 *
 * @author dgofman
 */
public final class ResourceOrderDialog extends javax.swing.JPanel {

    private JDialog dialog;
    private JButton updateButton;
    private JButton cancelButton;
    private ResourceBundle bundle;
    private DefaultListModel resourceModel;
    private TreeCollection collection;
    
    private MainPanel mainPanel;
    private JSONArray arraySource;
    private JSONObject jsonSource;

    private static final Logger logger = Amphibia.getLogger(ResourceOrderDialog.class.getName());

    /**
     * Creates new form TableEditDialog
     *
     * @param mainPanel
     */
    @SuppressWarnings("LeakingThisInConstructor")
    public ResourceOrderDialog(MainPanel mainPanel) {
        this.mainPanel = mainPanel;
        resourceModel = new DefaultListModel();
        initComponents();

        bundle = Amphibia.getBundle();

        updateButton = new JButton(bundle.getString("update"));
        updateButton.addActionListener((ActionEvent evt) -> {
            try {
                if (arraySource != null) {
                    arraySource.clear();
                    for (int i = 0; i < resourceModel.size(); i++) {
                        arraySource.add(((ResourceItem) resourceModel.getElementAt(i)).json);
                    }
                } else {
                    jsonSource.clear();
                    for (int i = 0; i < resourceModel.size(); i++) {
                        ResourceItem item = (ResourceItem) resourceModel.getElementAt(i);
                        jsonSource.put(item.label, item.json);
                    }
                }
                mainPanel.saveNodeValue((TreeIconNode.ProfileNode)collection.profile);
                dialog.setVisible(false);
            } catch (Exception ex) {
                logger.log(Level.SEVERE, ex.toString(), ex);
            }
        });
        cancelButton = new JButton(bundle.getString("cancel"));
        cancelButton.addActionListener((ActionEvent evt) -> {
            dialog.setVisible(false);
        });

        dialog = Amphibia.createDialog(this, new Object[]{updateButton, cancelButton}, true);
        dialog.setSize(new Dimension(700, 400));
        java.awt.EventQueue.invokeLater(() -> {
            dialog.setLocationRelativeTo(mainPanel);
        });
    }

    @SuppressWarnings("NonPublicExported")
    public void openDialog(TreeIconNode node, int index) {
        this.collection = node.getCollection();
        resourceModel.removeAllElements();
        if (node.getType() == TreeCollection.TYPE.COMMON) {
            jsonSource = collection.profile.jsonObject().getJSONObject("common");
            jsonSource.keySet().forEach((label) -> {
                resourceModel.addElement(new ResourceItem(label.toString(), jsonSource.getJSONObject(label.toString())));
            });
        } else {
            if (node.getType() == TreeCollection.TYPE.TESTSUITE) {
                arraySource = node.info.testSuite.getJSONArray("testcases");
            } else {
                arraySource = node.info.testCase.getJSONArray("steps");
            }
            arraySource.forEach((item) -> {
                resourceModel.addElement(new ResourceItem((JSONObject)item));
            });
        }
        lstResource.setSelectedIndex(index);
        txtName.setText(MainPanel.selectedNode.toString());
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
        GridBagConstraints gridBagConstraints;

        pnlHeader = new JPanel();
        lblName = new JLabel();
        txtName = new JTextField();
        pnlBotton = new JPanel();
        splResource = new JScrollPane();
        lstResource = new JList<>();
        pnlLeft = new JPanel();
        pnlButtons = new JPanel();
        btnUp = new JButton();
        btnDown = new JButton();
        fltSpace = new Box.Filler(new Dimension(0, 0), new Dimension(0, 0), new Dimension(32767, 0));
        btnClone = new JButton();
        btnRemove = new JButton();

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

        add(pnlHeader, BorderLayout.NORTH);

        pnlBotton.setLayout(new BorderLayout());

        lstResource.setModel(this.resourceModel);
        splResource.setViewportView(lstResource);

        pnlBotton.add(splResource, BorderLayout.CENTER);

        pnlButtons.setLayout(new GridLayout(5, 0, 0, 4));

        btnUp.setText(bundle.getString("up")); // NOI18N
        btnUp.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                btnUpActionPerformed(evt);
            }
        });
        pnlButtons.add(btnUp);

        btnDown.setText(bundle.getString("down")); // NOI18N
        btnDown.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                btnDownActionPerformed(evt);
            }
        });
        pnlButtons.add(btnDown);
        pnlButtons.add(fltSpace);

        btnClone.setText(bundle.getString("clone")); // NOI18N
        btnClone.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                btnCloneActionPerformed(evt);
            }
        });
        pnlButtons.add(btnClone);

        btnRemove.setText(bundle.getString("remove")); // NOI18N
        btnRemove.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                btnRemoveActionPerformed(evt);
            }
        });
        pnlButtons.add(btnRemove);

        pnlLeft.add(pnlButtons);

        pnlBotton.add(pnlLeft, BorderLayout.LINE_END);

        add(pnlBotton, BorderLayout.CENTER);
    }// </editor-fold>//GEN-END:initComponents

    private void btnDownActionPerformed(ActionEvent evt) {//GEN-FIRST:event_btnDownActionPerformed
        int[] indeces = lstResource.getSelectedIndices();
        int[] newIndeces = new int[indeces.length];
        ArrayUtils.reverse(indeces);
        for (int i = 0; i < indeces.length; i++) {
            int index = indeces[i];
            if (index != -1 && index < resourceModel.size() - 1) {
                resourceModel.add(index + 2, resourceModel.getElementAt(index));
                newIndeces[i] = index + 1;
                resourceModel.remove(index);
            }
        }
        lstResource.setSelectedIndices(newIndeces);
        if (indeces.length > 0) {
            lstResource.ensureIndexIsVisible(indeces[0] + 1);
        }
    }//GEN-LAST:event_btnDownActionPerformed

    private void btnUpActionPerformed(ActionEvent evt) {//GEN-FIRST:event_btnUpActionPerformed
        int[] indeces = lstResource.getSelectedIndices();
        int[] newIndeces = new int[indeces.length];
        ArrayUtils.reverse(indeces);
        for (int i = indeces.length - 1; i >= 0; i--) {
            int index = indeces[i];
            if (index != -1 && index > 0) {
                resourceModel.add(index - 1, resourceModel.getElementAt(index));
                newIndeces[i] = index - 1;
                resourceModel.remove(index + 1);
            }
        }
        lstResource.setSelectedIndices(newIndeces);
        if (indeces.length > 0) {
            lstResource.ensureIndexIsVisible(indeces[indeces.length - 1] - 1);
        }
    }//GEN-LAST:event_btnUpActionPerformed

    private void btnRemoveActionPerformed(ActionEvent evt) {//GEN-FIRST:event_btnRemoveActionPerformed
        int[] indeces = lstResource.getSelectedIndices();
        Arrays.sort(indeces);
        for (int i = indeces.length - 1; i >= 0; i--) {
            int index = indeces[i];
            if (index != -1) {
                resourceModel.remove(index);
            }
        }
        lstResource.setSelectedIndex(-1);
    }//GEN-LAST:event_btnRemoveActionPerformed

    private void btnCloneActionPerformed(ActionEvent evt) {//GEN-FIRST:event_btnCloneActionPerformed
        if (lstResource.getSelectedIndices().length > 1) {
            JOptionPane.showMessageDialog(this,
                bundle.getString("error_multiple_selections"),
                bundle.getString("title"),
                JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        int index = lstResource.getSelectedIndex();
        if (index != -1) {
            ResourceItem item = (ResourceItem) resourceModel.getElementAt(index);
            String label = (jsonSource != null) ? item.label : item.json.getString("name");
            String name = Amphibia.instance.inputDialog("tip_new_name", label, new String[]{}, dialog.getParent());
            if (name != null && !name.isEmpty()) {
                JSONObject json = IO.toJSONObject(item.json);
                if (jsonSource != null) {
                    jsonSource.put(name, json);
                } else {
                    json.put("name", name);
                    arraySource.add(json);
                }
                dialog.setVisible(false);
                mainPanel.saveNodeValue((TreeIconNode.ProfileNode)collection.profile);
            }
        }
    }//GEN-LAST:event_btnCloneActionPerformed


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private JButton btnClone;
    private JButton btnDown;
    private JButton btnRemove;
    private JButton btnUp;
    private Box.Filler fltSpace;
    private JLabel lblName;
    private JList<String> lstResource;
    private JPanel pnlBotton;
    private JPanel pnlButtons;
    private JPanel pnlHeader;
    private JPanel pnlLeft;
    private JScrollPane splResource;
    private JTextField txtName;
    // End of variables declaration//GEN-END:variables

    class ResourceItem {
    
        public String label;
        public JSONObject json;

        public ResourceItem(String label, JSONObject json) {
            this.label = label;
            this.json = json;
        }
        
        public ResourceItem(JSONObject json) {
            this(json.getString("name"), json);
        }

        @Override
        public String toString() {
            return label;
        }
    }
}