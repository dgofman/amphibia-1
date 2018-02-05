/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.equinix.amphibia.components;

import com.equinix.amphibia.Amphibia;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ResourceBundle;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;

/**
 *
 * @author dgofman
 */
public class TipDialog extends javax.swing.JPanel {

    private JDialog dialog;
    private String preferenceKey;
    
    /**
     * Creates new form TipDialog
     */
    public TipDialog(MainPanel mainPanel) {
        initComponents();
        dialog = Amphibia.createDialog(this, new Object[]{}, true);
        dialog.setSize(new Dimension(530, 300));
        java.awt.EventQueue.invokeLater(() -> {
            dialog.setLocationRelativeTo(mainPanel);
        });
    }
    
    public void openDialog(String message, String preferenceKey) {
        this.preferenceKey = preferenceKey;
        txtTip.setText(message);
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

        pnlTop = new JPanel();
        lblIcon = new JLabel();
        spnTip = new JScrollPane();
        txtTip = new JEditorPane();
        pnlFooter = new JPanel();
        ckbShow = new JCheckBox();
        btnClose = new JButton();

        setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10));
        setLayout(new BorderLayout());

        pnlTop.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 0));
        pnlTop.setLayout(new BorderLayout());

        lblIcon.setBackground(new Color(153, 153, 153));
        lblIcon.setIcon(new ImageIcon(getClass().getResource("/com/equinix/amphibia/icons/tip_icon.png"))); // NOI18N
        lblIcon.setVerticalAlignment(SwingConstants.TOP);
        lblIcon.setBorder(BorderFactory.createEmptyBorder(30, 20, 0, 20));
        lblIcon.setOpaque(true);
        pnlTop.add(lblIcon, BorderLayout.WEST);

        txtTip.setEditable(false);
        txtTip.setContentType("text/html"); // NOI18N
        spnTip.setViewportView(txtTip);

        pnlTop.add(spnTip, BorderLayout.CENTER);

        add(pnlTop, BorderLayout.CENTER);

        pnlFooter.setLayout(new BorderLayout());

        ResourceBundle bundle = ResourceBundle.getBundle("com/equinix/amphibia/messages"); // NOI18N
        ckbShow.setText(bundle.getString("showMessage")); // NOI18N
        pnlFooter.add(ckbShow, BorderLayout.CENTER);

        btnClose.setText(bundle.getString("close")); // NOI18N
        btnClose.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                btnCloseActionPerformed(evt);
            }
        });
        pnlFooter.add(btnClose, BorderLayout.EAST);

        add(pnlFooter, BorderLayout.SOUTH);
    }// </editor-fold>//GEN-END:initComponents

    private void btnCloseActionPerformed(ActionEvent evt) {//GEN-FIRST:event_btnCloseActionPerformed
        if (ckbShow.isSelected()) {
            Amphibia.userPreferences.putBoolean(preferenceKey, false);
        }
        dialog.setVisible(false);
    }//GEN-LAST:event_btnCloseActionPerformed


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private JButton btnClose;
    private JCheckBox ckbShow;
    private JLabel lblIcon;
    private JPanel pnlFooter;
    private JPanel pnlTop;
    private JScrollPane spnTip;
    private JEditorPane txtTip;
    // End of variables declaration//GEN-END:variables
}