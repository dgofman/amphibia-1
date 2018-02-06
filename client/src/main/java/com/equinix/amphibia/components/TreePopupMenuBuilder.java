/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.equinix.amphibia.components;

import static com.equinix.amphibia.Amphibia.getUserPreferences;
import static com.equinix.amphibia.components.TreeCollection.TYPE.*;

import com.equinix.amphibia.Amphibia;
import com.equinix.amphibia.IO;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ResourceBundle;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import net.sf.json.JSONArray;


/**
 *
 * @author dgofman
 */
public final class TreePopupMenuBuilder implements ActionListener {

    private final Amphibia amphibia;
    private final MainPanel mainPanel;
    private final ResourceAddDialog resourcesDialog;

    private final ResourceBundle bundle;

    private TreeIconNode selectedNode;
    
    private final Preferences userPreferences = getUserPreferences();
    
    private static final Logger logger = Amphibia.getLogger(TreePopupMenuBuilder.class.getName());

    public TreePopupMenuBuilder(Amphibia amphibia, MainPanel mainPanel) {
        this.amphibia = amphibia;
        this.mainPanel = mainPanel;
        bundle = Amphibia.getBundle();
        resourcesDialog = new ResourceAddDialog(mainPanel);
    }

    @SuppressWarnings("NonPublicExported")
    public JPopupMenu createPopupMenu(TreeIconNode selectedNode) {
        this.selectedNode = selectedNode;

        JPopupMenu popup = new JPopupMenu();
        TreeIconNode.TreeIconUserObject userObject = selectedNode.getTreeIconUserObject();
        TreeIconNode.ResourceInfo info = selectedNode.info;

        switch (userObject.getType()) {
            case PROJECT:
                addMenu(popup, "mnuRename", "RENAME:PROJECT");
                if (userObject.isEnabled()) {
                    addMenu(popup, "mnuReload", "RELOAD");
                    addMenu(popup, "mnuCloseProject", "CLOSE");
                } else {
                    addMenu(popup, "mnuOpenProject", "OPEN");
                }
                addMenu(popup, "mnuDeleteProject", "DELETE");
                break;
            case INTERFACE:
                addMenu(popup, "mnuRename", "RENAME:INTERFACE");
                break;
            case TESTSUITE:
                addMenu(popup, "addResources", "ADD_TESTCASES");
                addMenu(popup, "disable", "DISABLED").setSelected(!userObject.isEnabled());
                break;
            case TESTCASE:
                addMenu(popup, "hideCommon", "HIDE_COMMON").setSelected(info.states.getInt(TreeIconNode.STATE_HIDE_COMMONS) == 1);
                addMenu(popup, "addResources", "ADD_TESTSTEPS");
            case TEST_STEP_ITEM:
                addMenu(popup, "addToWizard", "OPEN_TESTCASE");
                addMenu(popup, "mnuRename", "RENAME:" + userObject.getType());
                addMenu(popup, bundle.getString("edit") + "/" + bundle.getString("clone") + "/" + bundle.getString("delete"), "EDIT:" + userObject.getType(), false);
                addMenu(popup, "disable", "DISABLED").setSelected(!userObject.isEnabled());
                break;
            default:
                break;

        }
        return popup;
    }

    private JMenuItem addMenu(JComponent parent, String label, String actionCommand) {
        return addMenu(parent, label, actionCommand, true);
    }

    private JMenuItem addMenu(JComponent parent, String label, String actionCommand, boolean localize) {
        JMenuItem menu;
        if ("DISABLED".equals(actionCommand) || "HIDE_COMMON".equals(actionCommand)) {
            menu = new JCheckBoxMenuItem(localize ? bundle.getString(label) : label);
        } else {
            menu = new JMenuItem(localize ? bundle.getString(label) : label);
        }
        menu.setActionCommand(actionCommand);
        menu.setMargin(new java.awt.Insets(0, 0, 0, 25));
        menu.addActionListener(this);
        parent.add(menu);
        return menu;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        mainPanel.tabLeft.setSelectedIndex(0);
        JMenuItem menuitem = (JMenuItem) e.getSource();
        String[] commands = menuitem.getActionCommand().split(":");
        TreeCollection collection = selectedNode.getCollection();
        switch (commands[0]) {
            case "RENAME":
                mainPanel.history.renameResource();
                break;
            case "CLOSE":
                mainPanel.openCloseProject(false);
                break;
            case "OPEN":
                mainPanel.openCloseProject(true);
                break;
            case "RELOAD":
                mainPanel.reloadCollection(collection);
                break;
            case "ADD_TESTCASES":
                mainPanel.resourceAddDialog.showTestCaseDialog(collection);
                break;
            case "ADD_TESTSTEPS":
                mainPanel.resourceAddDialog.showTestStepDialog(selectedNode);
                break;
            case "OPEN_TESTCASE":
                mainPanel.wizard.addWizardTab();
                break;
            case "HIDE_COMMON":
                selectedNode.info.states.set(TreeIconNode.STATE_HIDE_COMMONS, menuitem.isSelected() ? 1 : 0);
                mainPanel.history.saveNode(collection.profile);
                break;
            case "DISABLED":
                Editor.Entry entry = new Editor.Entry(selectedNode, "disabled");
                entry.value = menuitem.isSelected();
                mainPanel.history.saveEntry(entry, collection);
                break;
            case "EDIT":
                mainPanel.resourceOrderDialog.openDialog((TreeIconNode)selectedNode.getParent(), selectedNode.getParent().getIndex(selectedNode));
                break;
            case "DELETE":
                int dialogResult = JOptionPane.showConfirmDialog(mainPanel, bundle.getString("tip_deleting"), bundle.getString("title"), JOptionPane.YES_NO_OPTION);
                if (dialogResult == JOptionPane.YES_OPTION) {
                    mainPanel.deleteProject(collection);
                    JSONArray list = IO.toJSONArray(userPreferences.get(Amphibia.P_PROJECT_UUIDS, "[]"));
                    for (int i = 0; i < list.size(); i++) {
                        if (collection.getProjectFile().getAbsolutePath().equals(list.getString(i))) {
                            list.remove(i);
                            userPreferences.put(Amphibia.P_PROJECT_UUIDS, list.toString());
                            break;
                        }
                    }
                    mainPanel.treeModel.removeNodeFromParent(collection.project);
                    mainPanel.debugTreeModel.removeNodeFromParent(collection.project.debugNode);
                    mainPanel.deleteProject(collection);
                }
                break;
        }
    }
}
