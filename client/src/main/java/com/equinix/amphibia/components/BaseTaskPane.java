/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.equinix.amphibia.components;

import static com.equinix.amphibia.Amphibia.getUserPreferences;

import com.equinix.amphibia.Amphibia;

import java.awt.Component;
import java.awt.Container;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTree;
import javax.swing.TransferHandler;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

/**
 *
 * @author dgofman
 */
public class BaseTaskPane extends javax.swing.JSplitPane {

    protected JTabbedPane tabs;
    protected List<String> titles;
    protected JTree tree;
    protected MainPanel mainPanel;
    protected ResourceBundle bundle;
    protected DateTimeFormatter dateFormat;
    protected DateTimeFormatter dateMediumFormat;

    protected final DefaultTreeModel treeProblemsModel;
    protected final DefaultMutableTreeNode warnings;
    protected final DefaultMutableTreeNode errors;

    protected final Preferences userPreferences = getUserPreferences();
    protected static final Logger logger = Amphibia.getLogger(BaseTaskPane.class.getName());
    protected int defaultDividerLocation;

    protected String propertyChangeName;
    protected Timer refreshTimer;
    
    /**
     * Creates new form BaseTaskPane
     */
    @SuppressWarnings("OverridableMethodCallInConstructor")
    public BaseTaskPane() {
        refreshTimer = new Timer();
        propertyChangeName = Amphibia.P_DIVIDER + getClass().getSimpleName();
        bundle = Amphibia.getBundle();
        DefaultMutableTreeNode treeNode = new DefaultMutableTreeNode();
        warnings = new DefaultMutableTreeNode(bundle.getString("warnings"));
        errors = new DefaultMutableTreeNode(bundle.getString("errors"));
        treeNode.add(warnings);
        treeNode.add(errors);
        treeProblemsModel = new DefaultTreeModel(treeNode);
        changeLocale();
        initComponents();

        addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, new PropertyChangeListener() {
            Timer timer = new Timer();

            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                Amphibia.updatePreferences(timer, () -> {
                    userPreferences.putInt(propertyChangeName, BaseTaskPane.this.getDividerLocation());
                });
            }
        });
    }

    public void changeLocale() {
        dateFormat = DateTimeFormat.shortDateTime().withLocale(Locale.getDefault());
        dateMediumFormat = DateTimeFormat.mediumDateTime().withLocale(Locale.getDefault());
    }

    protected void setComponents(JTabbedPane tbpOutput, JTree tree) {
        this.tabs = tbpOutput;
        this.tree = tree;
        tree.setRowHeight(20);
        tree.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 0));
        tree.setCellRenderer(new CustomTreeCellRenderer());
        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent e) {
                TreePath path = tree.getPathForLocation(e.getX(), e.getY());
                if (path == null) {
                    return;
                }

                if (e.isPopupTrigger()) {
                    tree.setSelectionPath(path);
                    JPopupMenu popup = new JPopupMenu();
                    JMenuItem menu = new JMenuItem(bundle.getString("copy"));
                    menu.setMargin(new java.awt.Insets(0, 0, 0, 25));
                    menu.addActionListener((ActionEvent e1) -> {
                        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                        tree.getTransferHandler().exportToClipboard(tree, clipboard, TransferHandler.COPY);
                    });
                    popup.add(menu);
                    popup.show(tree, e.getX(), e.getY());
                }
            }
        });
        titles = new ArrayList<>(tbpOutput.getTabCount());
        for (int i = 0; i < tbpOutput.getTabCount(); i++) {
            titles.add(i, tbpOutput.getTitleAt(i));
        }
    }

    public void clear() {
        if (tabs.getSelectedIndex() == Amphibia.TAB_PROBLEMS) {
            warnings.removeAllChildren();
            errors.removeAllChildren();
            treeProblemsModel.reload();
        }
    }

    public DefaultMutableTreeNode addWarning(String warning) {
        return addToTree(warnings, warning);
    }

    public DefaultMutableTreeNode addError(String error) {
        return addToTree(errors, error);
    }

    public DefaultMutableTreeNode addError(Throwable ex, String error) {
        logger.log(Level.SEVERE, ex.toString(), ex);
        return addError(error);
    }

    public DefaultMutableTreeNode addError(Throwable ex) {
        if (ex instanceof FileNotFoundException) {
            return addError(ex, String.format(bundle.getString("error_missing_file"), ex.getMessage()));
        } else {
            return addError(ex, ex.toString());
        }
    }

    public DefaultMutableTreeNode addError(File file, Exception ex) {
        return addError(ex, String.format(bundle.getString("error_load_file"), file.getAbsolutePath(), ex.toString()));
    }

    public void showHideTab(int index, boolean b) {
        Container panel = tabs.getParent();
        tabs.setTitleAt(index, b ? titles.get(index) : "");
        tabs.setEnabledAt(index, b);
        if (!panel.isVisible()) {
            tabs.getParent().setVisible(true);
            setDividerLocation(defaultDividerLocation);
        }
        if (b) {
            return;
        }
        for (int i = 0; i < tabs.getTabCount(); i++) {
            if (tabs.isEnabledAt(i)) {
                tabs.setSelectedIndex(i);
                return;
            }
        }
        panel.setVisible(false);
    }

    public void activateTab(int index, JMenuItem menu) {
        showHideTab(index, true);
        tabs.setSelectedIndex(index);
        menu.setSelected(true);
    }

    protected DefaultMutableTreeNode addToTree(DefaultMutableTreeNode parent, String value) {
        if (userPreferences.getBoolean(Amphibia.P_SWITCH_PROBLEMS, true)) {
            activateTab(Amphibia.TAB_PROBLEMS, Amphibia.instance.mnuProblems);
        }
        DefaultMutableTreeNode node = new DefaultMutableTreeNode(value);
        parent.add(node);
        tree.expandPath(new TreePath(parent.getPath()));
        if (parent.getChildCount() > 0) {
            treeProblemsModel.nodesWereInserted(parent, new int[]{parent.getChildCount() - 1});
        }
        refreshTimer.cancel();
        refreshTimer = new Timer();
        refreshTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                java.awt.EventQueue.invokeLater(() -> {
                    tree.updateUI();
                });
            }
        }, 500);
        
        return node;
    }

    public void refresh() {
        /* TODO in derived classes */
    }

    public void reset() {
        /* TODO in derived classes */
    }

    public JTree getTree() {
        return tree;
    }

    public JTabbedPane getTabs() {
        return tabs;
    }

    /**
     * @param mainPanel
     */
    public void setMainPanel(MainPanel mainPanel) {
        this.mainPanel = mainPanel;
        setDividerLocation(userPreferences.getInt(propertyChangeName, getDividerLocation()));
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        setLayout(null);
    }// </editor-fold>//GEN-END:initComponents


    // Variables declaration - do not modify//GEN-BEGIN:variables
    // End of variables declaration//GEN-END:variables
    class CustomTreeCellRenderer extends DefaultTreeCellRenderer {

        ImageIcon warningIcon = new ImageIcon(getClass().getResource("/com/equinix/amphibia/icons/warning_16.png"));
        ImageIcon errorIcon = new ImageIcon(getClass().getResource("/com/equinix/amphibia/icons/error_16.png"));

        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean expanded,
                boolean isLeaf, int row, boolean focused) {
            Component c = super.getTreeCellRendererComponent(tree, value, selected, expanded, isLeaf, row, focused);
            if (value == warnings) {
                setIcon(warningIcon);
            } else if (value == errors) {
                setIcon(errorIcon);
            }
            return c;
        }
    }
}
