/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.equinix.amphibia.components;

import static com.equinix.amphibia.Amphibia.getUserPreferences;

import com.equinix.amphibia.Amphibia;
import com.equinix.amphibia.HttpConnection;

import java.awt.Dimension;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.UIManager;

/**
 *
 * @author dgofman
 */
public class PreferenceDialog extends javax.swing.JPanel {

    private JDialog dialog;
    private DefaultComboBoxModel languageModel;
    private DefaultComboBoxModel themeModel;
    private MainPanel mainPanel;
    private ResourceBundle bundle;

    private final Preferences userPreferences = getUserPreferences();
    
    private static final Logger logger = Amphibia.getLogger(PreferenceDialog.class.getName());
    
    private final Map<String, Object> historySelection;
    
    /**
     * Creates new form PreferenceDialog
     */
    public PreferenceDialog(MainPanel mainPanel) {
        this.mainPanel = mainPanel;
        
        bundle = Amphibia.getBundle();
        languageModel = new DefaultComboBoxModel();
        themeModel = new DefaultComboBoxModel();
        historySelection = new HashMap<>();
                
        initComponents();
        
        sprHistory.setValue(mainPanel.editor.loadMaxLastHistory);
        
        languageModel.addElement(new ComboItem("English", "en_EN"));
        languageModel.addElement(new ComboItem("German", "de_DE"));
        languageModel.addElement(new ComboItem("Russian", "ru_RU"));
        languageModel.addElement(new ComboItem("Spanish", "es_ES"));
        
        Locale locale = Locale.getDefault();
        for (int i = 0; i < languageModel.getSize(); i++) {
            if (((ComboItem)languageModel.getElementAt(i)).value.equals(locale.toString())) {
                cmbLanguage.setSelectedIndex(i);
                break;
            }
        }

        String userLF = userPreferences.get(Amphibia.P_LOOKANDFEEL, UIManager.getSystemLookAndFeelClassName());
        for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
            if ("Nimbus".equals(info.getName())) {
                continue;
            }
            themeModel.addElement(new ComboItem(info.getName(), info.getClassName()));
            if (info.getClassName().equals(userLF)) {
                cmbThemes.setSelectedIndex(themeModel.getSize() - 1);
            }
        }
        historySelection.put(Amphibia.P_LOCALE, cmbLanguage.getSelectedItem());
        historySelection.put(Amphibia.P_LOOKANDFEEL, themeModel.getSelectedItem());
        
        dialog = Amphibia.createDialog(this, new Object[]{}, false);
        dialog.setSize(new Dimension(520, 580));
        java.awt.EventQueue.invokeLater(() -> {
            dialog.setLocationRelativeTo(mainPanel);
        });
    }
    
    private void restart(boolean clearHistory) {
        int value = JOptionPane.showConfirmDialog(this, bundle.getString("tip_restart_app"));
        if (value == JOptionPane.OK_OPTION) {
            Runtime.getRuntime().addShutdownHook(new Thread() {
                @Override
                public void run() {
                    try {
                        String java = System.getProperty("java.home") + "/bin/java";
                        final StringBuffer cmd = new StringBuffer("\"" + java + "\" ");
                        String[] mainCommand = System.getProperty("sun.java.command").split(" ");
                        if (mainCommand[0].endsWith(".jar")) {
                            cmd.append("-jar ").append(new java.io.File(mainCommand[0]).getPath());
                        } else {
                            cmd.append("-cp \"").append(System.getProperty("java.class.path")).append("\" ").append(mainCommand[0]);
                        }
                        for (int i = 1; i < mainCommand.length; i++) {
                            cmd.append(" ");
                            cmd.append(mainCommand[i]);
                        }
                        logger.log(Level.SEVERE, cmd.toString());
                        Runtime.getRuntime().exec(cmd.toString());
                    } catch (IOException ex) {
                        logger.log(Level.SEVERE, ex.toString(), ex);
                    }
                }
            });
            if (clearHistory) {
                try {
                    userPreferences.clear();
                    userPreferences.flush();
                } catch (BackingStoreException ex) {
                    logger.log(Level.SEVERE, ex.toString(), ex);
                }
            }
            String exefile = System.getProperties().getProperty("launch4j.exefile");
            if (exefile != null) {
                try {
                    Runtime.getRuntime().exec(exefile);
                } catch (IOException ex) {
                    logger.log(Level.SEVERE, ex.toString(), ex);
                }
            }
            System.exit(0);
        }
    }
    
    public void openDialog() {
        chbSwitchDebug.setSelected(userPreferences.getBoolean(Amphibia.P_SWITCH_DEBUGGER, true));
        chbSwitchProblems.setSelected(userPreferences.getBoolean(Amphibia.P_SWITCH_PROBLEMS, true));
        chbSwitchConsole.setSelected(userPreferences.getBoolean(Amphibia.P_SWITCH_CONSOLE, true));
        includeSkipTest.setSelected(userPreferences.getBoolean(Amphibia.P_SKIPPED_TEST, true));
        chbShowInterfaces.setSelected(userPreferences.getBoolean(Amphibia.P_INTERFACE, true));
        
        sprConnTimeout.setValue(userPreferences.getInt(Amphibia.P_CONN_TIMEOUT, HttpConnection.DEFAULT_TIMEOUT));
        sprReadTimeout.setValue(userPreferences.getInt(Amphibia.P_READ_TIMEOUT, HttpConnection.DEFAULT_TIMEOUT));
        chbContinue.setSelected(userPreferences.getBoolean(Amphibia.P_CONTINUE_ON_ERROR, true));
        dialog.setVisible(true);
    }
    
    final class ComboItem {
        public String name;
        public String value;
        
        public ComboItem(String name, String value) {
            this.name = name;
            this.value = value;
        }
        
        @Override
        public String toString() {
            return name;
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

        lblLanguage = new JLabel();
        cmbLanguage = new JComboBox<>();
        lblThemes = new JLabel();
        cmbThemes = new JComboBox<>();
        lblGroupId = new JLabel();
        txtGroupId = new JTextField();
        lblHistory = new JLabel();
        btnReset = new JButton();
        btnClose = new JButton();
        sprHistory = new JSpinner();
        btnDeleteHistory = new JButton();
        pnlConnection = new JPanel();
        lblConnTimeout = new JLabel();
        sprConnTimeout = new JSpinner();
        lblReadTimeout = new JLabel();
        sprReadTimeout = new JSpinner();
        chbContinue = new JCheckBox();
        chbShowInterfaces = new JCheckBox();
        pnlAutoSwitch = new JPanel();
        chbSwitchDebug = new JCheckBox();
        chbSwitchProblems = new JCheckBox();
        chbSwitchConsole = new JCheckBox();
        includeSkipTest = new JCheckBox();

        setLayout(null);

        ResourceBundle bundle = ResourceBundle.getBundle("com/equinix/amphibia/messages"); // NOI18N
        lblLanguage.setText(bundle.getString("language")); // NOI18N
        add(lblLanguage);
        lblLanguage.setBounds(10, 13, 100, 14);

        cmbLanguage.setModel(this.languageModel);
        cmbLanguage.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                cmbLanguageActionPerformed(evt);
            }
        });
        add(cmbLanguage);
        cmbLanguage.setBounds(110, 10, 380, 20);

        lblThemes.setText(bundle.getString("themes")); // NOI18N
        add(lblThemes);
        lblThemes.setBounds(10, 43, 100, 14);

        cmbThemes.setModel(this.themeModel);
        cmbThemes.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                cmbThemesActionPerformed(evt);
            }
        });
        add(cmbThemes);
        cmbThemes.setBounds(110, 40, 380, 20);

        lblGroupId.setText("POM GroupId");
        add(lblGroupId);
        lblGroupId.setBounds(10, 73, 90, 14);

        txtGroupId.addFocusListener(new FocusAdapter() {
            public void focusLost(FocusEvent evt) {
                txtGroupIdFocusLost(evt);
            }
        });
        add(txtGroupId);
        txtGroupId.setBounds(110, 70, 380, 20);

        lblHistory.setText(bundle.getString("historyBuffer")); // NOI18N
        add(lblHistory);
        lblHistory.setBounds(10, 113, 90, 14);

        btnReset.setText(bundle.getString("resetDefault")); // NOI18N
        btnReset.setMargin(new Insets(2, 2, 2, 2));
        btnReset.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                btnResetActionPerformed(evt);
            }
        });
        add(btnReset);
        btnReset.setBounds(10, 490, 160, 23);

        btnClose.setText(bundle.getString("close")); // NOI18N
        btnClose.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                btnCloseActionPerformed(evt);
            }
        });
        add(btnClose);
        btnClose.setBounds(400, 490, 90, 23);
        add(sprHistory);
        sprHistory.setBounds(110, 110, 90, 20);

        btnDeleteHistory.setText(bundle.getString("deleteHistory")); // NOI18N
        btnDeleteHistory.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                btnDeleteHistoryActionPerformed(evt);
            }
        });
        add(btnDeleteHistory);
        btnDeleteHistory.setBounds(230, 110, 140, 23);

        pnlConnection.setBorder(BorderFactory.createTitledBorder(bundle.getString("connection"))); // NOI18N
        pnlConnection.setLayout(null);

        lblConnTimeout.setText(bundle.getString("connectionTimeout")); // NOI18N
        pnlConnection.add(lblConnTimeout);
        lblConnTimeout.setBounds(10, 23, 380, 14);
        pnlConnection.add(sprConnTimeout);
        sprConnTimeout.setBounds(399, 20, 70, 20);

        lblReadTimeout.setText(bundle.getString("readTimeout")); // NOI18N
        pnlConnection.add(lblReadTimeout);
        lblReadTimeout.setBounds(10, 53, 380, 14);
        pnlConnection.add(sprReadTimeout);
        sprReadTimeout.setBounds(400, 50, 70, 20);

        chbContinue.setText(bundle.getString("continueError")); // NOI18N
        chbContinue.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                chbContinueActionPerformed(evt);
            }
        });
        pnlConnection.add(chbContinue);
        chbContinue.setBounds(10, 80, 460, 23);

        chbShowInterfaces.setSelected(true);
        chbShowInterfaces.setText(bundle.getString("tip_open_interface")); // NOI18N
        chbShowInterfaces.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                chbShowInterfacesActionPerformed(evt);
            }
        });
        pnlConnection.add(chbShowInterfaces);
        chbShowInterfaces.setBounds(10, 110, 460, 23);

        add(pnlConnection);
        pnlConnection.setBounds(10, 190, 480, 150);

        pnlAutoSwitch.setBorder(BorderFactory.createTitledBorder(bundle.getString("autoSwitch"))); // NOI18N
        pnlAutoSwitch.setLayout(null);

        chbSwitchDebug.setSelected(true);
        chbSwitchDebug.setText(bundle.getString("switchDebug")); // NOI18N
        chbSwitchDebug.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                chbSwitchDebugActionPerformed(evt);
            }
        });
        pnlAutoSwitch.add(chbSwitchDebug);
        chbSwitchDebug.setBounds(10, 20, 460, 23);

        chbSwitchProblems.setSelected(true);
        chbSwitchProblems.setText(bundle.getString("switchProblems")); // NOI18N
        chbSwitchProblems.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                chbSwitchProblemsActionPerformed(evt);
            }
        });
        pnlAutoSwitch.add(chbSwitchProblems);
        chbSwitchProblems.setBounds(10, 50, 460, 23);

        chbSwitchConsole.setSelected(true);
        chbSwitchConsole.setText(bundle.getString("switchConsole")); // NOI18N
        chbSwitchConsole.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                chbSwitchConsoleActionPerformed(evt);
            }
        });
        pnlAutoSwitch.add(chbSwitchConsole);
        chbSwitchConsole.setBounds(10, 80, 460, 23);

        add(pnlAutoSwitch);
        pnlAutoSwitch.setBounds(10, 350, 480, 120);

        includeSkipTest.setSelected(true);
        includeSkipTest.setText(bundle.getString("includeSkipTest")); // NOI18N
        includeSkipTest.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                includeSkipTestActionPerformed(evt);
            }
        });
        add(includeSkipTest);
        includeSkipTest.setBounds(20, 150, 470, 23);
    }// </editor-fold>//GEN-END:initComponents

    private void cmbThemesActionPerformed(ActionEvent evt) {//GEN-FIRST:event_cmbThemesActionPerformed
        userPreferences.put(Amphibia.P_LOOKANDFEEL, ((ComboItem) cmbThemes.getSelectedItem()).value);
    }//GEN-LAST:event_cmbThemesActionPerformed

    private void txtGroupIdFocusLost(FocusEvent evt) {//GEN-FIRST:event_txtGroupIdFocusLost
        userPreferences.put(Amphibia.P_GROUP_ID, txtGroupId.getText());
    }//GEN-LAST:event_txtGroupIdFocusLost

    private void btnCloseActionPerformed(ActionEvent evt) {//GEN-FIRST:event_btnCloseActionPerformed
        if (cmbLanguage.getSelectedItem() != historySelection.get(Amphibia.P_LOCALE) ||
            themeModel.getSelectedItem() != historySelection.get(Amphibia.P_LOOKANDFEEL)) {
            restart(false);
        }
        userPreferences.putInt(Amphibia.P_HISTORY, (int)sprHistory.getValue());
        userPreferences.putInt(Amphibia.P_CONN_TIMEOUT, (int) sprConnTimeout.getValue());
        userPreferences.putInt(Amphibia.P_READ_TIMEOUT, (int) sprReadTimeout.getValue());
        dialog.setVisible(false);
    }//GEN-LAST:event_btnCloseActionPerformed

    private void cmbLanguageActionPerformed(ActionEvent evt) {//GEN-FIRST:event_cmbLanguageActionPerformed
        userPreferences.put(Amphibia.P_LOCALE, ((ComboItem) cmbLanguage.getSelectedItem()).value);
    }//GEN-LAST:event_cmbLanguageActionPerformed

    private void btnResetActionPerformed(ActionEvent evt) {//GEN-FIRST:event_btnResetActionPerformed
        restart(true);
    }//GEN-LAST:event_btnResetActionPerformed

    private void btnDeleteHistoryActionPerformed(ActionEvent evt) {//GEN-FIRST:event_btnDeleteHistoryActionPerformed
        mainPanel.editor.deleteHistory();
    }//GEN-LAST:event_btnDeleteHistoryActionPerformed

    private void chbSwitchConsoleActionPerformed(ActionEvent evt) {//GEN-FIRST:event_chbSwitchConsoleActionPerformed
        userPreferences.putBoolean(Amphibia.P_SWITCH_CONSOLE, chbSwitchConsole.isSelected());
    }//GEN-LAST:event_chbSwitchConsoleActionPerformed

    private void chbSwitchDebugActionPerformed(ActionEvent evt) {//GEN-FIRST:event_chbSwitchDebugActionPerformed
        userPreferences.putBoolean(Amphibia.P_SWITCH_DEBUGGER, chbSwitchDebug.isSelected());
    }//GEN-LAST:event_chbSwitchDebugActionPerformed

    private void chbSwitchProblemsActionPerformed(ActionEvent evt) {//GEN-FIRST:event_chbSwitchProblemsActionPerformed
        userPreferences.putBoolean(Amphibia.P_SWITCH_PROBLEMS, chbSwitchProblems.isSelected());
    }//GEN-LAST:event_chbSwitchProblemsActionPerformed

    private void includeSkipTestActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_includeSkipTestActionPerformed
        userPreferences.putBoolean(Amphibia.P_SKIPPED_TEST, includeSkipTest.isSelected());
    }//GEN-LAST:event_includeSkipTestActionPerformed

    private void chbContinueActionPerformed(ActionEvent evt) {//GEN-FIRST:event_chbContinueActionPerformed
        userPreferences.putBoolean(Amphibia.P_CONTINUE_ON_ERROR, chbContinue.isSelected());
    }//GEN-LAST:event_chbContinueActionPerformed

    private void chbShowInterfacesActionPerformed(ActionEvent evt) {//GEN-FIRST:event_chbShowInterfacesActionPerformed
        userPreferences.putBoolean(Amphibia.P_INTERFACE, chbShowInterfaces.isSelected());
    }//GEN-LAST:event_chbShowInterfacesActionPerformed


    // Variables declaration - do not modify//GEN-BEGIN:variables
    public JButton btnClose;
    public JButton btnDeleteHistory;
    public JButton btnReset;
    public JCheckBox chbContinue;
    public JCheckBox chbShowInterfaces;
    public JCheckBox chbSwitchConsole;
    public JCheckBox chbSwitchDebug;
    public JCheckBox chbSwitchProblems;
    public JComboBox<String> cmbLanguage;
    public JComboBox<String> cmbThemes;
    public JCheckBox includeSkipTest;
    public JLabel lblConnTimeout;
    public JLabel lblGroupId;
    public JLabel lblHistory;
    public JLabel lblLanguage;
    public JLabel lblReadTimeout;
    public JLabel lblThemes;
    public JPanel pnlAutoSwitch;
    public JPanel pnlConnection;
    public JSpinner sprConnTimeout;
    public JSpinner sprHistory;
    public JSpinner sprReadTimeout;
    public JTextField txtGroupId;
    // End of variables declaration//GEN-END:variables
}
