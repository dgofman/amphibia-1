/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.equinix.amphibia.components;

import static com.equinix.amphibia.agent.converter.Profile.RESOURCE_TYPE_URL;
import static com.equinix.amphibia.agent.converter.Profile.RESOURCE_TYPE_FILE;

import static com.equinix.amphibia.Amphibia.getUserPreferences;
import static com.equinix.amphibia.components.TreeCollection.*;
import static com.equinix.amphibia.components.TreeCollection.TYPE.*;

import com.equinix.amphibia.agent.converter.Swagger;
import com.equinix.amphibia.agent.builder.Properties;
import com.equinix.amphibia.Amphibia;
import com.equinix.amphibia.ExpertNodes;
import com.equinix.amphibia.HistoryManager;
import com.equinix.amphibia.IO;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Component;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Timer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import javax.swing.border.Border;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTree;
import javax.swing.ToolTipManager;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import net.sf.json.JSON;
import net.sf.json.JSONArray;
import net.sf.json.JSONNull;
import net.sf.json.JSONObject;

/**
 *
 * @author dgofman
 */
public final class MainPanel extends javax.swing.JPanel {

    public static TreeIconNode selectedNode;

    public final TreeIconNode treeNode;
    public final TreeIconNode debugTreeNode;

    public final TreeIconNode reportTreeNode;
    public final DefaultTreeModel treeModel;
    public final DefaultTreeModel debugTreeModel;
    public final DefaultTreeModel reportTreeModel;
    public final HistoryManager history;
    public final Profile profile;

    private Amphibia amphibia;
    private ResourceBundle bundle;
    private TreePopupMenuBuilder menuBuilder;

    public ResourceEditDialog resourceEditDialog;
    public ResourceOrderDialog resourceOrderDialog;
    public ReferenceDialog referenceEditDialog;
    public ResourceAddDialog resourceAddDialog;
    public TransferDialog transferDialog;
    public GlobalVariableDialog globalVarsDialog;
    public SaveDialog saveDialog;

    private final Preferences userPreferences = getUserPreferences();

    private static final Logger logger = Amphibia.getLogger(MainPanel.class.getName());

    /**
     * Creates new form MainPanel
     */
    public MainPanel() {
        bundle = Amphibia.getBundle();
        treeNode = new TreeIconNode();
        treeModel = new DefaultTreeModel(treeNode);
        debugTreeNode = new TreeIconNode();
        debugTreeModel = new DefaultTreeModel(debugTreeNode);
        reportTreeNode = new TreeIconNode();
        reportTreeModel = new DefaultTreeModel(reportTreeNode);

        initComponents();

        referenceEditDialog = new ReferenceDialog(this);
        resourceEditDialog = new ResourceEditDialog(this);
        resourceAddDialog = new ResourceAddDialog(this);
        resourceOrderDialog = new ResourceOrderDialog(this);
        transferDialog = new TransferDialog(this);
        globalVarsDialog = new GlobalVariableDialog(this);
        saveDialog = new SaveDialog(this);

        spnMainPane.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, new PropertyChangeListener() {
            Timer timer = new Timer();

            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                Amphibia.updatePreferences(timer, () -> {
                    userPreferences.putInt(Amphibia.P_DIVIDER + "Main", spnMainPane.getDividerLocation());
                });
            }
        });
        spnMainPane.setDividerLocation(userPreferences.getInt(Amphibia.P_DIVIDER + "Main", 320));

        history = new HistoryManager(this, editor);

        editor.setMainPanel(this);
        wizard.setMainPanel(this);
        profile = new Profile(this, editor);

        final JTextArea errors = new JTextArea();
        final FontMetrics fm = getFontMetrics(errors.getFont());
        final Border border = BorderFactory.createEmptyBorder(2, 0, 2, 0);
        errors.setBorder(border);
        errors.setWrapStyleWord(true);
        errors.setLineWrap(true);
        errors.setOpaque(true);
        errors.setForeground(Color.red);

        DefaultTreeCellRenderer treeCellRenderer = new DefaultTreeCellRenderer() {
            @Override
            public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean expanded, boolean isLeaf, int row, boolean hasFocus) {

                TreeIconNode node = ((TreeIconNode) value);
                if (node.getType() == ERRORS) {
                    int width = 0;
                    String[] lines = value.toString().split("\n");
                    for (String line : lines) {
                        width = Math.max(width, (int) fm.getStringBounds(line, errors.getGraphics()).getWidth());
                    }
                    errors.setText(value.toString());
                    errors.setSize(width + 10, Short.MAX_VALUE);
                    return errors;
                }
                JComponent c = (JComponent) super.getTreeCellRendererComponent(tree, value, selected, expanded, isLeaf, row, hasFocus);
                if (node.getUserObject() instanceof TreeIconNode.TreeIconUserObject) {
                    TreeIconNode.TreeIconUserObject userObject = (TreeIconNode.TreeIconUserObject) node.getUserObject();
                    Icon icon = userObject.getIcon();
                    Icon stateIcon;
                    if (tree == treeNav) {
                        switch (userObject.getType()) {
                            case TESTS:
                            case REQUESTS:
                            case RESPONSES:
                            case SCHEMAS:
                                c.setForeground(new Color(0, 0, 0, 80));
                                BufferedImage b = new BufferedImage(icon.getIconWidth(), icon.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
                                Graphics2D g = b.createGraphics();
                                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f));
                                icon.paintIcon(null, g, 0, 0);
                                g.dispose();
                                icon = new ImageIcon(b);
                                break;
                        }
                    } else if ((stateIcon = node.getReportIcon()) != null) {
                        if (icon != null) {
                            BufferedImage b = new BufferedImage(icon.getIconWidth() + stateIcon.getIconWidth() + 2, icon.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
                            Graphics2D g = b.createGraphics();
                            icon.paintIcon(null, g, 0, 0);
                            stateIcon.paintIcon(null, g, icon.getIconWidth() + 2, 2);
                            g.dispose();
                            icon = new ImageIcon(b);
                        } else {
                            icon = stateIcon;
                        }
                    }
                    c.setEnabled(isNodeEnabled((TreeNode) value));
                    setEnabled(userObject.isEnabled());
                    setToolTipText(userObject.getTooltip());
                    setIcon(icon);
                }
                c.setBorder(border);
                return c;
            }
        };
        TreeExpansionListener treeExpansionListener = new TreeExpansionListener() {
            @Override
            public void treeExpanded(TreeExpansionEvent event) {
                TreePath path = event.getPath();
                TreeIconNode node = (TreeIconNode) path.getLastPathComponent();
                if (!node.getTreeIconUserObject().isEnabled()) {
                    ((JTree) event.getSource()).collapsePath(path);
                } else if (!profile.isRunning() && node.info != null) {
                    TreeCollection collection = node.getCollection();
                    if (node.info.states != null) {
                        node.info.states.set(getStateIndex(event.getSource()), 1);
                    } else {
                        JSONObject expandResources = collection.profile.jsonObject().getJSONObject("expandResources");
                        expandResources.element(node.info.file.getAbsolutePath(), true);
                    }
                    collection.profile.saveState(node);
                }
            }

            @Override
            public void treeCollapsed(TreeExpansionEvent event) {
                TreePath path = event.getPath();
                TreeIconNode node = (TreeIconNode) path.getLastPathComponent();
                if (!profile.isRunning() && node.info != null) {
                    TreeCollection collection = node.getCollection();
                    JSONObject expandResources = collection.profile.jsonObject().getJSONObject("expandResources");
                    int index = getStateIndex(event.getSource());
                    if (collection.isOpen()) {
                        Enumeration children = node.preorderEnumeration();
                        while (children.hasMoreElements()) {
                            TreeIconNode child = (TreeIconNode) children.nextElement();
                            if (child.info != null) {
                                if (child.info.states != null) {
                                    child.info.states.set(index, 0);
                                } else {
                                    expandResources.remove(child.info.file.getAbsolutePath());
                                }
                            }
                        }
                    }
                    if (node.info.states != null) {
                        node.info.states.set(index, 0);
                    } else {
                        expandResources.remove(node.info.file.getAbsolutePath());
                    }
                    collection.profile.saveState(node);
                }
            }
        };

        treeNav.setRowHeight(20);
        treeNav.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 0));
        treeNav.setEditable(false);
        treeNav.setRootVisible(false);
        treeNav.setShowsRootHandles(true);
        treeNav.setCellRenderer(treeCellRenderer);
        treeNav.addTreeExpansionListener(treeExpansionListener);
        treeNav.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent e) {
                TreePath path = treeNav.getPathForLocation(e.getX(), e.getY());
                if (path == null) {
                    return;
                }

                if (e.isPopupTrigger()) {
                    treeNav.setSelectionPath(path);
                }

                TreeIconNode selectedNode = (TreeIconNode) treeNav.getLastSelectedPathComponent();
                if (selectedNode != null) {
                    selectedNode.saveSelection();
                    TreeIconNode.TreeIconUserObject selectedUserObject = selectedNode.getTreeIconUserObject();
                    System.out.println("TYPE: " + selectedUserObject.getType());
                    selectNode(selectedNode);
                    if (selectedUserObject.getType() != null && e.isPopupTrigger()) {
                        JPopupMenu popup = menuBuilder.createPopupMenu(selectedNode);
                        popup.show(treeNav, e.getX(), e.getY());
                    } else if (e.getClickCount() == 2 && amphibia.btnAddToWizard.isEnabled()) {
                        wizard.addWizardTab();
                    }
                }
            }
        });

        debugTreeNav.setRowHeight(20);
        debugTreeNav.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 0));
        debugTreeNav.setEditable(false);
        debugTreeNav.setRootVisible(false);
        debugTreeNav.setShowsRootHandles(true);
        debugTreeNav.setCellRenderer(treeCellRenderer);
        debugTreeNav.addTreeExpansionListener(treeExpansionListener);
        debugTreeNav.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent e) {
                TreePath path = debugTreeNav.getPathForLocation(e.getX(), e.getY());
                if (path == null) {
                    return;
                }
                TreeIconNode selectedNode = (TreeIconNode) debugTreeNav.getLastSelectedPathComponent();
                if (selectedNode != null && selectedNode.source != null) {
                    selectedNode.source.saveSelection();
                    selectNode(selectedNode.source);

                    if (selectedNode.info != null) {
                        editor.spnConsole.getVerticalScrollBar().setValue(selectedNode.info.consoleLine);
                    }

                    if (selectedNode.getType() != null && e.isPopupTrigger()) {
                        JPopupMenu popup = menuBuilder.createPopupMenu(selectedNode);
                        popup.show(debugTreeNav, e.getX(), e.getY());
                    } else if (e.getClickCount() == 2 && amphibia.btnAddToWizard.isEnabled()) {
                        wizard.addWizardTab();
                    }
                }
            }
        });

        reportTreeNav.setRowHeight(-1);
        reportTreeNav.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 0));
        reportTreeNav.setEditable(false);
        reportTreeNav.setRootVisible(false);
        reportTreeNav.setShowsRootHandles(true);
        reportTreeNav.setCellRenderer(treeCellRenderer);

        ToolTipManager tooltip = ToolTipManager.sharedInstance();
        tooltip.setInitialDelay(200);
        tooltip.registerComponent(treeNav);
        tooltip.registerComponent(debugTreeNav);
        tooltip.registerComponent(reportTreeNav);
    }

    public JTree getTree() {
        return treeNav;
    }

    public int getStateIndex(Object tree) {
        return tree == treeNav ? TreeIconNode.STATE_PROJECT_EXPAND : TreeIconNode.STATE_DEBUG_EXPAND;
    }

    public void deleteProject(TreeCollection collection) {
        reset(collection);
        editor.resetHistory();
        amphibia.enableSave(false);
        amphibia.mnuProject.setEnabled(false);
        amphibia.tlbRun.setEnabled(false);
        amphibia.btnAddToWizard.setEnabled(false);
        treeNav.updateUI();
    }

    public void reset(TreeCollection collection) {
        collection.reset(treeModel);
        if (collection.project.debugNode != null) {
            collection.project.debugNode.removeAllChildren();
        }
        editor.selectedTreeNode(collection.project);
        editor.reset();
    }

    public void saveNodeValue(TreeIconNode node) {
        history.saveNode(node);
    }

    private void openCloseProject(TreeCollection collection, boolean isOpen) {
        collection.project.getTreeIconUserObject().setEnabled(isOpen);
        collection.project.debugNode.getTreeIconUserObject().setEnabled(isOpen);
        if (isOpen) {
            collection.expandNode(treeNav, collection.project);
            collection.expandNode(treeNav, collection.testsuites);
        } else {
            collection.collapseNode(treeNav, collection.project);
        }
        treeModel.nodeChanged(collection.project);
    }

    public void openCloseProject(boolean isOpen) {
        if (selectedNode != null) {
            TreeCollection collection = selectedNode.getCollection();
            collection.setOpen(isOpen);
            saveNodeValue(collection.profile);
        }
    }

    public void expandDefaultNodes(TreeCollection collection) {
        expandParendNode(collection.resources);
        expandParendNode(collection.testsuites);
        if (collection.testsuites.getChildCount() > 0) {
            expandParendNode((TreeIconNode) collection.testsuites.getChildAt(0));
        }
    }

    public void expandParendNode(TreeIconNode parent) {
        parent.getCollection().expandNode(treeNav, parent);
    }

    public static void setSelectedNode(TreeIconNode node) {
        selectedNode = node;
    }

    public void selectNode(TreeIconNode node) {
        setSelectedNode(node);
        if (node == null) {
            return;
        }
        TreeCollection collection = node.getCollection();

        debugTreeNode.removeAllChildren();
        debugTreeNode.add(collection.project.debugNode);
        debugTreeModel.reload(debugTreeNode);
        Enumeration children = collection.project.debugNode.preorderEnumeration();
        while (children.hasMoreElements()) {
            TreeIconNode child = (TreeIconNode) children.nextElement();
            if (child.info != null && child.info.states != null && child.info.states.getInt(TreeIconNode.STATE_DEBUG_EXPAND) == 1) {
                if (child.getParent() != null && debugTreeNav.isExpanded(new TreePath(((TreeIconNode) child.getParent()).getPath()))) {
                    debugTreeNav.expandPath(new TreePath(child.getPath()));
                }
            }
        }

        TreeIconNode debugNode = (node.debugNode != null) ? node.debugNode : collection.project.debugNode;
        editor.reset();
        amphibia.btnAddToWizard.setEnabled(node.getType() == TESTCASE || node.getType() == TEST_STEP_ITEM);
        amphibia.tlbRun.setEnabled(true);
        amphibia.mnuProject.setEnabled(true);
        treeNav.setSelectionPath(new TreePath(node.getPath()));
        debugTreeNav.setSelectionPath(new TreePath(debugNode.getPath()));
        Object raw = node.getCollection().profile.jsonObject();
        switch (node.getType()) {
            case PROJECT:
                raw = node.getCollection().project.jsonObject();
                break;
            case LINK:
            case INTERFACES:
            case INTERFACE:
            case RULES:
            case SWAGGER:
            case REQUEST_ITEM:
            case RESPONSE_ITEM:
            case SCHEMA_ITEM:
                raw = node.getTreeIconUserObject().json;
                break;
            case TEST_ITEM:
                raw = node.info.testStepInfo;
                break;
            case TESTSUITE:
            case TESTCASES:
            case TESTS:
            case REQUESTS:
            case SCHEMAS:
                if (node.getParent() != null && ((TreeIconNode) node.getParent()).getType() != TESTCASES) {
                    raw = "";
                }
                break;
            case TESTCASE:
            case TEST_STEP_ITEM:
                addRaw("Request URL: ", true).addRaw(node.getTreeIconUserObject().getTooltip());
                addRaw("\nRequest Method: ", true).addRaw(node.jsonObject().getString("method"));
                addRaw("\nResult Status Code: ", true).addRaw(node.info.getResultStatus(node).toString());
                addRaw("\n\nRequest Headers:", true);
                final JSONObject headers = node.info.getRequestHeader(node);
                headers.keySet().forEach((key) -> {
                    addRaw("\n" + key + ": ", true).addRaw(node.info.properties.replace(headers.get(key)));
                });
                addRaw("\n\nRequest Body:\n", true);
                addRaw(node.info.getRequestBody(collection), Profile.BLUE);

                addRaw("\n\nExpected Response Body:\n", true);
                addRaw(node.info.getResponseBody(collection), Profile.GREEN);
                raw = null;
                break;
        }

        try {
            if (raw instanceof JSON) {
                addRaw(IO.prettyJson(raw.toString()));
            } else if (raw != null) {
                addRaw(raw.toString());
            }
        } catch (Exception ex) {
            logger.log(Level.SEVERE, ex.toString(), ex);
        }
        javax.swing.SwingUtilities.invokeLater(() -> {
            editor.spnRaw.getVerticalScrollBar().setValue(0);
            editor.spnRaw.getHorizontalScrollBar().setValue(0);
        });

        if (node.getCollection().isOpen()) {
            editor.selectedTreeNode(node);
        }
        wizard.selectNode(node);
    }

    private MainPanel addRaw(String text, Color color) {
        SimpleAttributeSet attr = new SimpleAttributeSet();
        StyleConstants.setForeground(attr, color);
        return addRaw(text, attr);
    }

    private MainPanel addRaw(String text, boolean isBold) {
        SimpleAttributeSet attr = new SimpleAttributeSet();
        StyleConstants.setBold(attr, isBold);
        return addRaw(text, attr);
    }

    private MainPanel addRaw(String text) {
        return addRaw(text, new SimpleAttributeSet());
    }

    private MainPanel addRaw(String text, SimpleAttributeSet attr) {
        StyledDocument doc = editor.txtRaw.getStyledDocument();
        try {
            doc.insertString(doc.getLength(), text, attr);
        } catch (BadLocationException ex) {
            logger.log(Level.SEVERE, ex.toString(), ex);
        }
        return this;
    }

    public void addError(Exception ex) {
        editor.addError(ex);
    }

    public void setParent(Amphibia amphibia) {
        this.amphibia = amphibia;
        menuBuilder = new TreePopupMenuBuilder(amphibia, this);
    }

    public TreeCollection getSelectedProject(File projectFile) {
        int count = treeModel.getChildCount(treeNode);
        for (int i = 0; i < count; i++) {
            TreeIconNode node = (TreeIconNode) treeModel.getChild(treeNode, i);
            TreeIconNode.TreeIconUserObject useObject = node.getTreeIconUserObject();
            if (projectFile.getAbsolutePath().equals(useObject.getFullPath())) {
                return useObject.getCollection();
            }
        }
        return new TreeCollection();
    }

    public boolean loadProject(TreeCollection collection) {
        File projectFile = collection.getProjectFile();
        JSONObject projectJson = (JSONObject)IO.getJSON(projectFile, editor);
        collection.project.addJSON(projectJson);
        collection.project.addTooltip(projectFile.getAbsolutePath());
        globalVarsDialog.mergeVariables(projectJson.getJSONArray("globals"));
        
        try {
            collection.profile.load(collection.getProjectDir());
            JSONObject json = collection.profile.profileJSON;
            collection.setProjectProfile(json);
        } catch (Exception ex) {
            addError(ex);
        }
        
        wizard.updateEndPoints();

        treeNode.add(collection.project);

        return true;
    }

    private boolean reloadProject(TreeCollection collection) {
        reset(collection);

        TreeIconNode debugProjectNode = new TreeIconNode(collection.project);
        JSONObject projectJson = collection.project.jsonObject();
        JSONArray globals = new JSONArray();
        try {
            Object[][] vars = GlobalVariableDialog.getGlobalVarData();
            int columnIndex = Amphibia.instance.getSelectedEnvDataIndex();
            for (Object[] data : vars) {
                globals.add(new HashMap<Object, Object>() {
                    {
                        put("name", data[1]);
                        put("value", data[columnIndex]);
                    }
                });
            }
        } catch (Exception e) {
            editor.addError(e, Amphibia.getBundle().getString("error_open_json"));
            return false;
        }
        
        File profileFile = IO.getFile(collection, "data/profile.json");
        JSONObject json = IO.getBackupJSON(profileFile, editor);
        collection.setProjectProfile(json);
        
        JSONObject projectProps = JSONObject.fromObject(projectJson.getJSONObject("projectProperties"));
        JSONObject profileProps = json.getJSONObject("properties");
        profileProps.keySet().forEach((key) -> {
            projectProps.put(key, profileProps.get(key));
        });
        collection.project.jsonObject().element("properties", projectProps);
        
        Properties projectProperties = new Properties(globals, projectProps);

        projectProperties.setTestCase(json.getJSONObject("properties"));
        collection.setProjectProperties(projectProperties);

        collection.profile.addJSON(json)
                .addTooltip(profileFile.getAbsolutePath());
        
        collection.project.add(collection.resources);
        collection.project.add(collection.interfaces);
        collection.project.add(collection.profile);
        collection.project.add(collection.testsuites);
        collection.project.add(collection.common);

        Map<String, TreeIconNode.ResourceInfo> resourceInfoMap = new HashMap<>();

        JSONArray projectResources = projectJson.getJSONArray("projectResources");
        JSONArray interfacesJSON = projectJson.getJSONArray("interfaces");
        JSONArray resources = json.getJSONArray("resources");
        JSONArray testsuites = json.getJSONArray("testsuites");
        JSONObject common = json.getJSONObject("common");

        Map<String, JSONObject> resourceMap = new HashMap<>();
        projectResources.forEach((item) -> {
            JSONObject resource = (JSONObject) item;
            resourceMap.put(resource.getString("resourceId"), resource);
        });
        
        Map<Object, JSONObject> interfacesMap = new HashMap<>();
        JSONArray interfaceList = new JSONArray();
        interfacesJSON.forEach((item) -> {
            JSONObject interfaceJSON = IO.toJSONObject(item);
            String interfaceId = interfaceJSON.getString("id");
            resources.forEach((r) -> {
                JSONObject resource = (JSONObject) r;
                if (interfaceId.equals(resource.getString("interface"))) {
                    if (resource.containsKey("name")) {
                        interfaceJSON.element("name", resource.getString("name"));
                    }
                    if (resource.containsKey("basePath")) {
                        interfaceJSON.element("basePath", resource.getString("basePath"));
                    }
                    JSONObject headers = resource.getJSONObject("headers");
                    headers.keySet().forEach((key) -> {
                        interfaceJSON.getJSONObject("headers").put(key, headers.get(key));
                    });
                    interfacesMap.put(interfaceJSON.getString("id"), interfaceJSON);
                    TreeIconNode iNode = collection.addTreeNode(collection.interfaces, interfaceJSON.getString("name"), INTERFACE, false)
                            .addProperties(INTERFACE_PROPERTIES)
                            .addTooltip(interfaceJSON.getString("basePath"))
                            .addJSON(interfaceJSON);
                    iNode.info = new TreeIconNode.ResourceInfo(profileFile);
                    iNode.info.resource = resource;
                    interfaceList.add(interfaceJSON);
                }
            });
        });
        collection.interfaces.addJSON(interfaceList);

        testsuites.forEach((item) -> {
            JSONObject testsuite = (JSONObject) item;
            String resourceId = testsuite.getString("resource");
            JSONObject resource = resourceMap.get(resourceId);
            JSONObject testSuiteInfo = resource.getJSONObject("testsuites").getJSONObject(testsuite.getString("name"));
            String dirPath = String.format(ExpertNodes.dirFormat, resourceId, testsuite.getString("name"));
            File dir = IO.getFile(collection, dirPath);
            TreeIconNode.ResourceInfo info = new TreeIconNode.ResourceInfo(dir, resource, testsuite, testSuiteInfo, null, null, null);
            Properties properties = projectProperties.cloneProperties();
            properties.setTestSuite(testSuiteInfo.getJSONObject("properties"));
            info.properties = properties;
            resourceInfoMap.put(dir.getAbsolutePath(), info);
            for (String name : dir.list()) {
                String path = dirPath + "/" + name;
                File file = IO.newFile(dir, name);
                if (file.exists()) {
                    JSONArray testcases = testSuiteInfo.getJSONArray("testcases");
                    for (int i = 0; i < testcases.size(); i++) {
                        JSONObject testCaseInfo = testcases.getJSONObject(i);
                        JSONObject interfaceJSON = null;
                        if (resource.containsKey("interfaceId") && !resource.getString("interfaceId").isEmpty()) {
                            interfaceJSON = interfacesMap.get(resource.getString("interfaceId"));
                        }
                        JSONObject testCaseHeaders = interfaceJSON == null ? new JSONObject() : IO.toJSONObject(interfaceJSON.getJSONObject("headers").toString());
                        if (testCaseInfo.containsKey("headers")) {
                            JSONObject headers = testCaseInfo.getJSONObject("headers");
                            headers.keySet().forEach((key) -> {
                                testCaseHeaders.put(key, headers.get(key));
                            });
                        }
                        if (!resourceInfoMap.containsKey(path) && path.equals(String.format(ExpertNodes.pathFormat, resourceId, testsuite.getString("name"), testCaseInfo.getString("name")))) {
                            info = new TreeIconNode.ResourceInfo(file, resource, testsuite, testSuiteInfo, testCaseInfo, testCaseHeaders, (JSONObject) IO.getJSON(file, editor));
                            properties = projectProperties.cloneProperties();
                            properties.setTestSuite(testSuiteInfo.getJSONObject("properties"));
                            if (testsuite.containsKey("properties")) {
                                properties.setTestSuite(testsuite.getJSONObject("properties"));
                            }
                            properties.setTestCase(IO.toJSONObject(testCaseInfo.getJSONObject("properties")));
                            info.properties = properties;
                            resourceInfoMap.put(path, info);
                            break;
                        }
                    }
                }
            }
        });

        for (int i = 0; i < resources.size(); i++) {
            TreeIconNode resourceNode;
            JSONObject resource = resources.getJSONObject(i);
            String type = resource.getString("type");
            if (type.equals(RESOURCE_TYPE_URL) || type.equals(RESOURCE_TYPE_FILE)) {
                boolean isURL = type.equals(RESOURCE_TYPE_URL);
                resourceNode = collection.addTreeNode(collection.resources, resource.getString("source"), SWAGGER, !isURL)
                        .addProperties(RESOURCE_PROPERTIES)
                        .addJSON(resource);
            } else {
                resourceNode = collection.addTreeNode(collection.resources, bundle.getString("wizard"), WIZARD, false)
                        .addProperties(RESOURCE_PROPERTIES)
                        .addJSON(resource);
            }

            if (!resource.containsKey("states")) {
                resource.element("states", new int[]{0});
            }
            resourceNode.info = new TreeIconNode.ResourceInfo(resource.getJSONArray("states"));

            if (resource.containsKey("interface")) {
                JSONObject interfaceJSON = interfacesMap.get(resource.getString("interface"));
                if (interfaceJSON != null) {
                    TreeIconNode iNode = collection.addTreeNode(resourceNode, interfaceJSON.getString("name"), INTERFACE, false)
                            .addProperties(INTERFACE_PROPERTIES)
                            .addTooltip(interfaceJSON.getString("basePath"))
                            .addJSON(interfaceJSON);
                    iNode.info = new TreeIconNode.ResourceInfo(profileFile);
                    iNode.info.resource = resource;
                }
            }

            String props = resource.getString("properties");
            if (props != null && !props.isEmpty()) {
                collection.addTreeNode(resourceNode, props, RULES, true)
                        .addProperties(RULES_PROPERTIES)
                        .addJSON(IO.getJSON(IO.newFile(props), editor));
            }
        }
        collection.resources.addJSON(resources);

        JSONObject testsuitesJson = new JSONObject();
        for (int i = 0; i < testsuites.size(); i++) {
            JSONObject testsuite = testsuites.getJSONObject(i);
            String name = testsuite.getString("name");
            String resourceId = testsuite.getString("resource");
            JSONObject resource = resourceMap.get(resourceId);
            JSONObject interfaceJSON;
            if (resource.containsKey("interfaceId")) {
                interfaceJSON = interfacesMap.get(resource.getString("interfaceId"));
            } else {
                interfaceJSON = IO.toJSONObject("{\"name\": \"\", \"basePath\": \"\"}");
            }

            String relPath = String.format(ExpertNodes.dirFormat, resourceId, Swagger.stripName(name));
            File dir = IO.getFile(collection, relPath);
            JSONObject testsuiteJSON = new JSONObject();
            testsuiteJSON.put("disabled", testsuite.get("disabled") == Boolean.TRUE);
            testsuiteJSON.put("name", name);
            testsuiteJSON.put("endpoint", resource.getString("endpoint"));
            testsuiteJSON.put("properties", JSONNull.getInstance());
            testsuiteJSON.put("testcases", JSONNull.getInstance());
            testsuiteJSON.put("index", i);
            testsuiteJSON.put("interface", interfaceJSON.getString("name"));

            if (dir.exists()) {
                TreeIconNode testsuiteNode = collection.addTreeNode(collection.testsuites, name, TESTSUITE, false)
                        .addProperties(TESTSUITE_PROPERTIES);
                testsuiteNode.getTreeIconUserObject().setEnabled(testsuite.get("disabled") != Boolean.TRUE);
                testsuiteNode.info = resourceInfoMap.get(dir.getAbsolutePath());
                testsuiteNode.info.properties = projectProperties;
                if (!testsuite.containsKey("states")) {
                    testsuite.element("states", new int[]{0, 0, 0});
                }
                testsuiteNode.info.states = testsuite.getJSONArray("states");

                testsuiteJSON.element("properties", testsuiteNode.info.testSuiteInfo.getJSONObject("properties"));
                if (testsuiteNode.info.testSuite.containsKey("properties")) {
                    IO.replaceValues(testsuiteNode.info.testSuite.getJSONObject("properties"), testsuiteJSON.getJSONObject("properties"));
                }

                TreeIconNode debugSuiteNode = new TreeIconNode(testsuiteNode).addJSON(testsuite);
                debugProjectNode.add(debugSuiteNode);

                JSONArray testcases = new JSONArray();
                for (Iterator it = testsuite.getJSONArray("testcases").iterator(); it.hasNext();) {
                    JSONObject testcase = (JSONObject) it.next();
                    String path = testcase.getString("path");
                    TreeIconNode.ResourceInfo info = resourceInfoMap.get(path);
                    if (info == null) {
                        logger.log(Level.SEVERE, "The data is undefined: " + path, new NullPointerException(path));
                    } else {
                        testcases.add(testcase.getString("name"));
                        Properties properties = info.properties.cloneProperties();
                        JSONObject testCaseProperties = IO.toJSONObject(properties.getProperty("TestCase"));
                        JSONObject testCaseHeaders = IO.toJSONObject(info.testCaseHeaders);

                        if (testcase.containsKey("properties")) {
                            JSONObject props = testcase.getJSONObject("properties");
                            props.keySet().forEach((key) -> {
                                Object prop = props.get(key);
                                if (prop instanceof JSONObject && ((JSONObject) prop).isNullObject()) {
                                    testCaseProperties.remove(key);
                                } else {
                                    testCaseProperties.put(key, prop);
                                }
                            });
                        }

                        if (testcase.containsKey("headers")) {
                            JSONObject headers = testcase.getJSONObject("headers");
                            headers.keySet().forEach((key) -> {
                                Object header = headers.get(key);
                                if (header instanceof JSONObject && ((JSONObject) header).isNullObject()) {
                                    testCaseHeaders.remove(key);
                                } else {
                                    testCaseHeaders.put(key, header);
                                }
                            });
                        }

                        final JSONObject testcaseJSON = IO.toJSONObject(info.testStepInfo);
                        final JSONObject testCaseInheritedProperties = new JSONObject();
                        info.properties.getProperty("Global").keySet().forEach((key) -> {
                            testCaseInheritedProperties.put(key, "${#Global$" + key + "}");
                        });
                        info.properties.getProperty("Project").keySet().forEach((key) -> {
                            testCaseInheritedProperties.put(key, "${#Project$" + key + "}");
                        });
                        info.properties.getProperty("TestSuite").keySet().forEach((key) -> {
                            testCaseInheritedProperties.put(key, "${#TestSuite$" + key + "}");
                        });
                        info.properties.getProperty("TestCase").keySet().forEach((key) -> {
                            testCaseInheritedProperties.put(key, "${#TestCase$" + key + "}");
                        });
                        testCaseProperties.keySet().forEach((key) -> {
                            testCaseInheritedProperties.put(key, "${#TestCase$" + key + "}");
                        });
                        if (testcase.containsKey("transfer")) {
                            JSONObject transferProps = testcase.getJSONObject("transfer");
                            transferProps.keySet().forEach((key) -> {
                                String values;
                                if (testCaseInheritedProperties.containsKey(key)) {
                                    values = testCaseInheritedProperties.get(key)
                                            + ", \n${#TestCase:" + transferProps.get(key) + "}";
                                } else {
                                    values = "${#TestCase:" + transferProps.get(key) + "}";
                                }
                                testCaseInheritedProperties.put(key, values);
                            });
                            testcaseJSON.element("transfer", transferProps);
                        }

                        String url = "${#Global#" + resource.getString("endpoint") + "}" + interfaceJSON.getString("basePath") + info.testCaseInfo.getString("path");

                        TreeIconNode.ResourceInfo testCaseInfo = info.clone(testcase);
                        testCaseInfo.properties.setTestCase(IO.toJSONObject(testCaseInfo.testStepInfo.getJSONObject("request").getJSONObject("properties")));
                        testCaseInfo.properties.setTestCase(IO.toJSONObject(testCaseInfo.testStepInfo.getJSONObject("response").getJSONObject("properties")));
                        testCaseInfo.properties.setTestCase(IO.toJSONObject(testcase.getOrDefault("properties", new JSONObject())));

                        testcaseJSON.element("name", testcase.getString("name"));
                        testcaseJSON.element("disabled", testcase.get("disabled") == Boolean.TRUE);
                        testcaseJSON.element("file", path);
                        testcaseJSON.element("headers", testCaseHeaders);
                        testcaseJSON.element("properties", testCaseProperties);
                        testcaseJSON.element("method", info.testCaseInfo.getString("method"));
                        testcaseJSON.element("url", url);
                        testcaseJSON.element("interface", interfaceJSON.getString("name"));
                        testcaseJSON.element("reqPath", properties.replace(info.testCaseInfo.getString("path")).replaceAll("&amp;", "&"));
                        String tooltipURL = properties.replace(url).replaceAll("&amp;", "&");
                        TreeIconNode testcaseNode = collection.addTreeNode(testsuiteNode, testcase.getString("name"), TESTCASE, false)
                                .addProperties(TESTCASE_PROPERTIES)
                                .addTooltip(tooltipURL);
                        testcaseNode.getTreeIconUserObject().setEnabled(testcase.get("disabled") != Boolean.TRUE);
                        testcaseNode.info = testCaseInfo;
                        if (testcase.containsKey("line")) {
                            testcaseNode.info.consoleLine = testcase.getInt("line");
                        }
                        if (!testcase.containsKey("states")) {
                            testcase.element("states", new int[]{0, 0, 0, 0, 0});
                        }
                        testcaseNode.info.states = testcase.getJSONArray("states");
                        if (testcaseNode.info.states.getInt(TreeIconNode.STATE_OPEN_PROJECT_OR_WIZARD_TAB) == 1) {
                            wizard.replaceNode(testcaseNode);
                        }

                        TreeIconNode debugTestCaseNode = new TreeIconNode(testcaseNode).addJSON(testcase);
                        debugSuiteNode.add(debugTestCaseNode);

                        JSONArray teststeps = new JSONArray();
                        JSONObject inheritedProperties = IO.toJSONObject(testCaseInheritedProperties);
                        testcase.getJSONArray("steps").forEach((item) -> {
                            JSONObject step = (JSONObject) item;
                            boolean isCommon = step.containsKey("common");
                            
                            TreeIconNode.ResourceInfo stepInfo = testCaseInfo.clone(testcase, step);

                            teststeps.add(step.getString("name"));
                            JSONObject testStepJSON = IO.toJSONObject(testcaseNode.info.testStepInfo);
                            testStepJSON.element("file", path);
                            testStepJSON.element("method", info.testCaseInfo.getString("method"));
                            testStepJSON.element("url", url);

                            step.keySet().forEach((key) -> {
                                HistoryManager.replace(step, testStepJSON);
                            });
                            testStepJSON.element("disabled", step.get("disabled") == Boolean.TRUE);

                            JSONObject stepInheritedProperties = IO.toJSONObject(inheritedProperties);
                            final JSONObject requestProp = testStepJSON.getJSONObject("request").getJSONObject("properties");
                            final JSONObject responseProp = testStepJSON.getJSONObject("response").getJSONObject("properties");

                            if (isCommon && common.containsKey(step.getString("common"))) {
                                testStepJSON.element("common", step.getString("common"));
                                JSONObject commonItem = common.getJSONObject(step.getString("common"));
                                stepInfo.common = commonItem;
                                JSONObject customReqProps = commonItem.getJSONObject("request").getJSONObject("properties");
                                customReqProps.keySet().forEach((key) -> {
                                    requestProp.put(key, customReqProps.get(key));
                                    stepInheritedProperties.put(key, "${#Common$" + key + "}");
                                });
                                JSONObject customResProps = commonItem.getJSONObject("response").getJSONObject("properties");
                                customResProps.keySet().forEach((key) -> {
                                    responseProp.put(key, customResProps.get(key));
                                    stepInheritedProperties.put(key, "${#Common$" + key + "}");
                                });
                            }

                            if (step.containsKey("request")) {
                                JSONObject customStepProps = step.getJSONObject("request").getJSONObject("properties");
                                customStepProps.keySet().forEach((key) -> {
                                    requestProp.put(key, customStepProps.get(key));
                                    stepInheritedProperties.put(key, "${#TestStep$" + key + "}");
                                });
                            }

                            if (step.containsKey("response")) {
                                JSONObject customStepProps = step.getJSONObject("response").getJSONObject("properties");
                                customStepProps.keySet().forEach((key) -> {
                                    responseProp.put(key, customStepProps.get(key));
                                    stepInheritedProperties.put(key, "${#TestStep$" + key + "}");
                                });
                            }

                            stepInfo.properties.setTestStep(responseProp);
                            stepInfo.properties.setTestStep(requestProp);

                            JSONObject testStepHeader = IO.toJSONObject(testCaseHeaders);
                            if (step.containsKey("headers")) {
                                JSONObject headers = step.getJSONObject("headers");
                                headers.keySet().forEach((key) -> {
                                    Object header = headers.get(key);
                                    if (header instanceof JSONObject && ((JSONObject) header).isNullObject()) {
                                        testStepHeader.remove(key);
                                    } else {
                                        testStepHeader.put(key, header);
                                    }
                                });
                            }
                            testStepJSON.element("headers", testStepHeader);
                            testStepJSON.element("reqPath", properties.replace(info.testCaseInfo.getString("path")).replaceAll("&amp;", "&"));

                            if (step.containsKey("transfer")) {
                                JSONObject transferProps = step.getJSONObject("transfer");
                                transferProps.keySet().forEach((key) -> {
                                    String values;
                                    if (stepInheritedProperties.containsKey(key)) {
                                        values = stepInheritedProperties.get(key)
                                                + ", \n${#TestStep:" + transferProps.get(key) + "}";
                                    } else {
                                        values = "${#TestStep:" + transferProps.get(key) + "}";
                                    }
                                    stepInheritedProperties.put(key, values);

                                    if (testCaseInheritedProperties.containsKey(key)) {
                                        values = testCaseInheritedProperties.get(key)
                                                + ", \n${#TestStep#" + step.getString("name") + ":" + transferProps.get(key) + "}";
                                    } else {
                                        values = "${#TestStep#" + step.getString("name") + ":" + transferProps.get(key) + "}";
                                    }
                                    testCaseInheritedProperties.put(key, values);
                                });
                                testStepJSON.getJSONObject("response").element("transfer", transferProps);
                            }

                            testStepJSON.element("inherited-properties", stepInheritedProperties);

                            String tootltip = stepInfo.properties.replace(url).replaceAll("&amp;", "&");
                            
                            TreeIconNode testStepNode;    
                            if (!isCommon || testcaseNode.info.states.getInt(TreeIconNode.STATE_HIDE_COMMONS) == 0) {
                                testStepNode = collection.insertTreeNode(testcaseNode, step.getString("name"), TEST_STEP_ITEM);
                            } else {
                                testStepNode = new TreeIconNode(collection, step.getString("name"), null, false);
                                testStepNode.getTreeIconUserObject().setType(TEST_STEP_ITEM);
                            }
                            testStepNode.addProperties(isCommon ? TEST_STEP_LINK_PROPERTIES : TEST_STEP_ITEM_PROPERTIES)
                                    .addTooltip(tootltip)
                                    .addJSON(testStepJSON);
                            if (isCommon) {
                                testStepNode.getTreeIconUserObject().setIcon(LINK);
                            }
                            
                            testStepNode.getTreeIconUserObject().setEnabled(step.get("disabled") != Boolean.TRUE);
                            testStepNode.info = stepInfo;
                            if (step.containsKey("line")) {
                                testStepNode.info.consoleLine = step.getInt("line");
                            }
                            if (!step.containsKey("states")) {
                                step.element("states", new int[]{-1, -1, 0, 0});
                            }
                            testStepNode.info.states = step.getJSONArray("states");
                            if (testStepNode.info.states.getInt(TreeIconNode.STATE_OPEN_PROJECT_OR_WIZARD_TAB) == 1) {
                                wizard.replaceNode(testStepNode);
                            }

                            debugTestCaseNode.add(new TreeIconNode(testStepNode, null).addJSON(step));
                        });
                        testcaseJSON.element("teststeps", teststeps);
                        testcaseJSON.element("inherited-properties", testCaseInheritedProperties);
                        testcaseNode.addJSON(testcaseJSON);
                    }
                }
                IO.replaceValue(testsuiteJSON, "testcases", testcases);
                testsuiteNode.addJSON(testsuiteJSON);
            }

            testsuitesJson.element(name, relPath);
        }

        collection.testsuites
                .addProperties(VIEW_ITEM_PROPERTIES)
                .addJSON(testsuitesJson);

        common.keySet().forEach((key) -> {
            JSONObject commonItem = common.getJSONObject(key.toString());
            collection.addTreeNode(collection.common, key.toString(), LINK, false)
                    .addProperties(LINK_PROPERTIES)
                    .addJSON(commonItem);

        });

        if (Amphibia.isExpertView()) {
            ExpertNodes.createNodes(collection, projectResources, resourceInfoMap, interfacesMap);
        }

        if (collection.project.getParent() != null) {
            treeModel.reload(collection.project);
        }
        debugTreeModel.reload();

        JSONObject states = json.getJSONObject("states");
        try {
            collection.project.info = new TreeIconNode.ResourceInfo(states.getJSONArray("project"));
            collection.resources.info = new TreeIconNode.ResourceInfo(states.getJSONArray("resources"));
            collection.interfaces.info = new TreeIconNode.ResourceInfo(states.getJSONArray("interfaces"));
            collection.testsuites.info = new TreeIconNode.ResourceInfo(states.getJSONArray("testsuites"));
            collection.common.info = new TreeIconNode.ResourceInfo(states.getJSONArray("common"));
            collection.tests.info = new TreeIconNode.ResourceInfo(states.getJSONArray("tests"));
            collection.schemas.info = new TreeIconNode.ResourceInfo(states.getJSONArray("schemas"));
            collection.requests.info = new TreeIconNode.ResourceInfo(states.getJSONArray("requests"));
            collection.responses.info = new TreeIconNode.ResourceInfo(states.getJSONArray("responses"));
        } catch (Exception ex) {
            logger.log(Level.SEVERE, ex.toString(), ex);
        }

        debugProjectNode.info = collection.project.info;

        amphibia.enableSave(true);
        amphibia.mnuOpen.setEnabled(!collection.isOpen());
        amphibia.mnuClose.setEnabled(collection.isOpen());
        wizard.removeDeadNodes();

        IO.write(collection.profile, editor);
        return true;
    }

    public void reloadAll() {
        for (int i = 0; i < treeModel.getChildCount(treeNode); i++) {
            TreeIconNode projectNode = (TreeIconNode) treeModel.getChild(treeNode, i);
            projectNode.removeAllChildren();
            reloadCollection(projectNode.getCollection(), false);
        }
        selectNode(selectedNode);
    }

    public void reloadCollection(TreeCollection collection) {
        reloadCollection(collection, true);
    }

    private void reloadCollection(TreeCollection collection, boolean isSelectNode) {
        reloadProject(collection);
        refreshCollection(collection);
        if (isSelectNode) {
            selectNode(selectedNode);
        }
    }

    private void refreshCollection(TreeCollection collection) {
        openCloseProject(collection, collection.isOpen());
        if (collection.isOpen()) {
            try {
                JSONObject expandResources = collection.profile.jsonObject().getJSONObject("expandResources");
                Enumeration children = collection.project.preorderEnumeration();
                while (children.hasMoreElements()) {
                    TreeIconNode node = (TreeIconNode) children.nextElement();
                    if (node.info != null && treeNav.isExpanded(new TreePath(((TreeIconNode) node.getParent()).getPath()))) {
                        if (node.info.states != null) {
                            if (node.info.states.getInt(TreeIconNode.STATE_PROJECT_EXPAND) == 1) {
                                treeNav.expandPath(new TreePath(node.getPath()));
                            }
                        } else if (expandResources.containsKey(node.info.file.getAbsolutePath())) {
                            treeNav.expandPath(new TreePath(node.getPath()));
                        }
                    }
                }
            } catch (Exception e) {
                logger.log(Level.SEVERE, e.toString(), e);
            }
        }
    }

    public boolean undo() {
        return editor.undo();
    }

    public boolean redo() {
        return editor.redo();
    }

    private boolean isNodeEnabled(TreeNode node) {
        Object userObject = ((TreeIconNode) node).getUserObject();
        if (userObject instanceof TreeIconNode.TreeIconUserObject) {
            TreeIconNode.TreeIconUserObject c = (TreeIconNode.TreeIconUserObject) userObject;
            if (c.json instanceof JSONObject) {
                JSONObject json = (JSONObject) c.json;
                if (json.containsKey("disabled") && json.getBoolean("disabled")) {
                    return false;
                }
            }
            if (node.getParent() != null) {
                return isNodeEnabled(node.getParent());
            }
        }
        return true;
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        spnMainPane = new JSplitPane();
        tabLeft = new JTabbedPane();
        spnTreeNav = new JScrollPane();
        treeNav = new JTree();
        spnDebugTreeNav = new JScrollPane();
        debugTreeNav = new JTree();
        spnReport = new JScrollPane();
        reportTreeNav = new JTree();
        tabRight = new JTabbedPane();
        editor = new Editor();
        wizard = new Wizard();

        setLayout(new BoxLayout(this, BoxLayout.LINE_AXIS));

        spnMainPane.setDividerLocation(320);
        spnMainPane.setDividerSize(3);

        treeNav.setModel(treeModel);
        spnTreeNav.setViewportView(treeNav);

        ResourceBundle bundle = ResourceBundle.getBundle("com/equinix/amphibia/messages"); // NOI18N
        tabLeft.addTab(bundle.getString("projects"), new ImageIcon(getClass().getResource("/com/equinix/amphibia/icons/project_16.png")), spnTreeNav); // NOI18N

        debugTreeNav.setModel(this.debugTreeModel);
        spnDebugTreeNav.setViewportView(debugTreeNav);

        tabLeft.addTab(bundle.getString("debugger"), new ImageIcon(getClass().getResource("/com/equinix/amphibia/icons/debugger_16.png")), spnDebugTreeNav); // NOI18N

        reportTreeNav.setModel(reportTreeModel);
        spnReport.setViewportView(reportTreeNav);

        tabLeft.addTab(bundle.getString("report"), new ImageIcon(getClass().getResource("/com/equinix/amphibia/icons/export_junit_16.png")), spnReport); // NOI18N

        spnMainPane.setLeftComponent(tabLeft);

        tabRight.addTab(bundle.getString("editor"), new ImageIcon(getClass().getResource("/com/equinix/amphibia/icons/editor_16.png")), editor); // NOI18N
        tabRight.addTab(bundle.getString("wizard"), new ImageIcon(getClass().getResource("/com/equinix/amphibia/icons/wizard_16.png")), wizard); // NOI18N

        spnMainPane.setRightComponent(tabRight);

        add(spnMainPane);
    }// </editor-fold>//GEN-END:initComponents

    // Variables declaration - do not modify//GEN-BEGIN:variables
    JTree debugTreeNav;
    public Editor editor;
    private JTree reportTreeNav;
    private JScrollPane spnDebugTreeNav;
    private JSplitPane spnMainPane;
    private JScrollPane spnReport;
    JScrollPane spnTreeNav;
    public JTabbedPane tabLeft;
    public JTabbedPane tabRight;
    JTree treeNav;
    public Wizard wizard;
    // End of variables declaration//GEN-END:variables
}
