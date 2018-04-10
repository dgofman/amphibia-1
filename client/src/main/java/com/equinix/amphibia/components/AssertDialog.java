/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.equinix.amphibia.components;

import com.equinix.amphibia.agent.converter.Profile;

import com.equinix.amphibia.Amphibia;
import com.equinix.amphibia.IO;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.ResourceBundle;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

/**
 *
 * @author dgofman
 */
public final class AssertDialog extends javax.swing.JPanel {

    private JDialog dialog;
    private Editor.Entry entry;
    private final JButton okButton;
    private final JButton cancelButton;
    
    public static enum ASSERTS {
        ORDERED,
        UNORDERED,
        MINIMUM,
        NOTEQUALS
    };

    /**
     * Creates new form JDialogTest
     *
     * @param mainPanel
     */
    @SuppressWarnings("LeakingThisInConstructor")
    public AssertDialog(MainPanel mainPanel) {

        initComponents();
        
        rbOrdered.setActionCommand(ASSERTS.ORDERED.toString());
        rbUnordered.setActionCommand(ASSERTS.UNORDERED.toString());
        rbMinimum.setActionCommand(ASSERTS.MINIMUM.toString());

        okButton = new JButton(UIManager.getString("OptionPane.okButtonText"));
        okButton.addActionListener((ActionEvent evt) -> {
            TreeIconNode node = MainPanel.selectedNode;
            TreeCollection collection = node.getCollection();
            JSONArray asserts = ((JSONObject) entry.json).getJSONArray("asserts");
            asserts.clear();
            if (rbNotEquals.isSelected()) {
                asserts.add(ASSERTS.NOTEQUALS);
            }
            if (chbBody.isSelected()) {
                asserts.add(btgBody.getSelection().getActionCommand());
            }
            if (node.getType() == TreeCollection.TYPE.TEST_STEP_ITEM) {
                mainPanel.history.saveEntry(entry, collection);
            } else {
                try {
                    File file = IO.getFile(collection, node.jsonObject().getString("file"));
                    if (file.exists()) {
                        JSONObject json = (JSONObject) IO.getJSON(file);
                        json.getJSONObject(entry.rootName).put("asserts", asserts);
                        String[] contents = IO.write(json.toString(), file, true);
                        mainPanel.history.addHistory(file.getAbsolutePath(), contents[0], contents[1]);
                        mainPanel.reloadCollection(collection);
                    }
                } catch (Exception ex) {
                    mainPanel.addError(ex);
                }
            }
            dialog.setVisible(false);
        });
        cancelButton = new JButton(UIManager.getString("OptionPane.cancelButtonText"));
        cancelButton.addActionListener((ActionEvent evt) -> {
            dialog.setVisible(false);
        });
        dialog = Amphibia.createDialog(this, new Object[]{okButton, cancelButton}, true);
        dialog.setSize(new Dimension(530, 330));
        java.awt.EventQueue.invokeLater(() -> {
            dialog.setLocationRelativeTo(mainPanel);
        });
    }

    public void openDialog(TreeIconNode node, Editor.Entry entry) {
        this.entry = entry;
        JSONArray asserts = ((JSONObject) entry.json).getJSONArray("asserts");
        rbEquals.setSelected(true);
        rbNotEquals.setSelected(asserts.contains(ASSERTS.NOTEQUALS));
        
        txtCode.setText(String.valueOf(node.jsonObject().getJSONObject("properties").getOrDefault(Profile.HTTP_STATUS_CODE, "")));
        txtBody.setText(node.jsonObject().getJSONObject("response").getString("body"));
        
        JRadioButton[] buttons = new JRadioButton[] {
            rbOrdered, rbUnordered, rbMinimum
        };
        boolean[] b = new boolean[] {
            asserts.contains(ASSERTS.ORDERED),
            asserts.contains(ASSERTS.UNORDERED),
            asserts.contains(ASSERTS.MINIMUM)
        };
        
        chbBody.setSelected(false);
        for (int i = 0; i < buttons.length; i++) {
            if (b[i] == true) {
                buttons[i].setSelected(true);
                chbBody.setSelected(true);
                break;
            }
        }
        chbBodyActionPerformed(null);
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

        btgHttpCode = new ButtonGroup();
        btgBody = new ButtonGroup();
        pnlCode = new JPanel();
        lblCode = new JLabel();
        txtCode = new JTextField();
        pnlCodeGroup = new JPanel();
        rbEquals = new JRadioButton();
        rbNotEquals = new JRadioButton();
        pnlBody = new JPanel();
        chbBody = new JCheckBox();
        pnlBodyGroup = new JPanel();
        txtBody = new JTextField();
        rbOrdered = new JRadioButton();
        rbUnordered = new JRadioButton();
        rbMinimum = new JRadioButton();

        setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        setLayout(new BorderLayout());

        pnlCode.setLayout(new BorderLayout());

        lblCode.setFont(new Font("Tahoma", 1, 11)); // NOI18N
        ResourceBundle bundle = ResourceBundle.getBundle("com/equinix/amphibia/messages"); // NOI18N
        lblCode.setText(bundle.getString("httpStatusCode")); // NOI18N
        pnlCode.add(lblCode, BorderLayout.NORTH);

        txtCode.setEditable(false);
        txtCode.setMargin(new Insets(5, 5, 5, 5));
        pnlCode.add(txtCode, BorderLayout.CENTER);

        pnlCodeGroup.setBorder(BorderFactory.createEmptyBorder(1, 20, 1, 1));

        btgHttpCode.add(rbEquals);
        rbEquals.setSelected(true);
        rbEquals.setText(bundle.getString("equals")); // NOI18N
        pnlCodeGroup.add(rbEquals);

        btgHttpCode.add(rbNotEquals);
        rbNotEquals.setText(bundle.getString("notEquals")); // NOI18N
        pnlCodeGroup.add(rbNotEquals);

        pnlCode.add(pnlCodeGroup, BorderLayout.SOUTH);

        add(pnlCode, BorderLayout.NORTH);

        pnlBody.setLayout(new BorderLayout());

        chbBody.setFont(new Font("Tahoma", 1, 11)); // NOI18N
        chbBody.setText(bundle.getString("responseBody")); // NOI18N
        chbBody.setVerticalAlignment(SwingConstants.BOTTOM);
        chbBody.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                chbBodyActionPerformed(evt);
            }
        });
        pnlBody.add(chbBody, BorderLayout.CENTER);

        pnlBodyGroup.setLayout(new BoxLayout(pnlBodyGroup, BoxLayout.Y_AXIS));

        txtBody.setEditable(false);
        txtBody.setMargin(new Insets(5, 5, 5, 5));
        pnlBodyGroup.add(txtBody);

        btgBody.add(rbOrdered);
        rbOrdered.setSelected(true);
        rbOrdered.setText(bundle.getString("orderedMatch")); // NOI18N
        pnlBodyGroup.add(rbOrdered);

        btgBody.add(rbUnordered);
        rbUnordered.setText(bundle.getString("unorderedMatch")); // NOI18N
        pnlBodyGroup.add(rbUnordered);

        btgBody.add(rbMinimum);
        rbMinimum.setText(bundle.getString("minReqFields")); // NOI18N
        pnlBodyGroup.add(rbMinimum);

        pnlBody.add(pnlBodyGroup, BorderLayout.SOUTH);

        add(pnlBody, BorderLayout.CENTER);
    }// </editor-fold>//GEN-END:initComponents

    private void chbBodyActionPerformed(ActionEvent evt) {//GEN-FIRST:event_chbBodyActionPerformed
        rbOrdered.setEnabled(chbBody.isSelected());
        rbUnordered.setEnabled(chbBody.isSelected());
        rbMinimum.setEnabled(chbBody.isSelected());
    }//GEN-LAST:event_chbBodyActionPerformed


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private ButtonGroup btgBody;
    private ButtonGroup btgHttpCode;
    private JCheckBox chbBody;
    private JLabel lblCode;
    private JPanel pnlBody;
    private JPanel pnlBodyGroup;
    private JPanel pnlCode;
    private JPanel pnlCodeGroup;
    private JRadioButton rbEquals;
    private JRadioButton rbMinimum;
    private JRadioButton rbNotEquals;
    private JRadioButton rbOrdered;
    private JRadioButton rbUnordered;
    private JTextField txtBody;
    private JTextField txtCode;
    // End of variables declaration//GEN-END:variables
}
