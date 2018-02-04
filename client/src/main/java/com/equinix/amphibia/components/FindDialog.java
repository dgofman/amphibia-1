/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.equinix.amphibia.components;

import com.equinix.amphibia.Amphibia;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Enumeration;
import java.util.ResourceBundle;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;

/**
 *
 * @author dgofman
 */
public final class FindDialog extends javax.swing.JPanel {

    private JDialog dialog;
    private MainPanel mainPanel;
    private Enumeration findEnum;

    /**
     * Creates new form FindDialog
     *
     * @param mainPanel
     */
    public FindDialog(MainPanel mainPanel) {
        this.mainPanel = mainPanel;
        initComponents();

        dialog = Amphibia.createDialog(this, new Object[]{}, false);
        dialog.setSize(new Dimension(650, 120));
        java.awt.EventQueue.invokeLater(() -> {
            dialog.setLocationRelativeTo(mainPanel);
        });
    }

    public void openDialog() {
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

        pnlCenter = new JPanel();
        pnlTop = new JPanel();
        lblFind = new JLabel();
        txtFind = new JTextField();
        ckbMatchCase = new JCheckBox();
        pnlEast = new JPanel();
        btnFind = new JButton();
        btnNext = new JButton();
        btnClose = new JButton();

        setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 0));
        setLayout(new BorderLayout());

        pnlCenter.setLayout(new BorderLayout());

        pnlTop.setLayout(new BoxLayout(pnlTop, BoxLayout.LINE_AXIS));

        ResourceBundle bundle = ResourceBundle.getBundle("com/equinix/amphibia/messages"); // NOI18N
        lblFind.setText(bundle.getString("find")); // NOI18N
        lblFind.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 10));
        pnlTop.add(lblFind);
        pnlTop.add(txtFind);

        pnlCenter.add(pnlTop, BorderLayout.NORTH);

        ckbMatchCase.setText(bundle.getString("matchCase")); // NOI18N
        pnlCenter.add(ckbMatchCase, BorderLayout.CENTER);

        add(pnlCenter, BorderLayout.CENTER);

        pnlEast.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 5));
        pnlEast.setLayout(new BoxLayout(pnlEast, BoxLayout.Y_AXIS));

        btnFind.setText(bundle.getString("find")); // NOI18N
        btnFind.setMaximumSize(new Dimension(2147483647, 23));
        btnFind.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                btnFindActionPerformed(evt);
            }
        });
        pnlEast.add(btnFind);

        btnNext.setText(bundle.getString("findNext")); // NOI18N
        btnNext.setMaximumSize(new Dimension(2147483647, 23));
        btnNext.setName(""); // NOI18N
        btnNext.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                btnNextActionPerformed(evt);
            }
        });
        pnlEast.add(btnNext);

        btnClose.setText(bundle.getString("close")); // NOI18N
        btnClose.setMaximumSize(new Dimension(2147483647, 23));
        btnClose.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                btnCloseActionPerformed(evt);
            }
        });
        pnlEast.add(btnClose);

        add(pnlEast, BorderLayout.EAST);
    }// </editor-fold>//GEN-END:initComponents

    private void btnFindActionPerformed(ActionEvent evt) {//GEN-FIRST:event_btnFindActionPerformed
        findEnum = null;
        btnNextActionPerformed(evt);
    }//GEN-LAST:event_btnFindActionPerformed

    private void btnNextActionPerformed(ActionEvent evt) {//GEN-FIRST:event_btnNextActionPerformed
        if (findEnum == null) {
            DefaultMutableTreeNode root = (DefaultMutableTreeNode) mainPanel.treeModel.getRoot();
            findEnum = root.depthFirstEnumeration();
        }
        String source = ckbMatchCase.isSelected() ? txtFind.getText() : txtFind.getText().toLowerCase();
        while (findEnum.hasMoreElements()) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) findEnum.nextElement();
            String target = ckbMatchCase.isSelected() ? node.toString() : node.toString().toLowerCase();
            if (target.contains(source)) {
                TreePath path = new TreePath(node.getPath());
                mainPanel.treeNav.expandPath(path);
                mainPanel.treeNav.setSelectionPath(path);
                mainPanel.treeNav.scrollPathToVisible(path);
                javax.swing.SwingUtilities.invokeLater(() -> {
                    mainPanel.spnTreeNav.getHorizontalScrollBar().setValue(0);
                });
                return;
            }
        }
    }//GEN-LAST:event_btnNextActionPerformed

    private void btnCloseActionPerformed(ActionEvent evt) {//GEN-FIRST:event_btnCloseActionPerformed
        dialog.setVisible(false);
    }//GEN-LAST:event_btnCloseActionPerformed


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private JButton btnClose;
    private JButton btnFind;
    private JButton btnNext;
    private JCheckBox ckbMatchCase;
    private JLabel lblFind;
    private JPanel pnlCenter;
    private JPanel pnlEast;
    private JPanel pnlTop;
    private JTextField txtFind;
    // End of variables declaration//GEN-END:variables
}
