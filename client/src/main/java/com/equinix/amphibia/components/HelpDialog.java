/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.equinix.amphibia.components;

import static com.equinix.amphibia.Amphibia.getUserPreferences;

import com.equinix.amphibia.Amphibia;
import com.equinix.amphibia.IO;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.print.PrinterException;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.net.URL;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import java.util.Timer;
import java.util.prefs.Preferences;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JToolBar;
import javax.swing.JTree;
import javax.swing.SwingConstants;
import javax.swing.WindowConstants;
import javax.swing.event.HyperlinkEvent;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

/**
 *
 * @author dgofman
 */
public final class HelpDialog extends javax.swing.JFrame {

    private static final Preferences userPreferences = getUserPreferences();
    private static final String HELP_PATH = "help/%s";
    
    private final DefaultMutableTreeNode treeNode;
    private final DefaultTreeModel treeModel;
    private final List<URL> urlStack;
    private final MainPanel mainPanel;
            
    private int urlIndex;
    
    /**
     * Creates new form HelpDialog
     * @param mainPanel
     */
    @SuppressWarnings("LeakingThisInConstructor")
    public HelpDialog(MainPanel mainPanel) {
        this.mainPanel = mainPanel;

        treeNode = new DefaultMutableTreeNode();
        treeModel = new DefaultTreeModel(treeNode);
        
        createTreeNodes(treeNode, (JSONArray) IO.getJSON(IO.getResources(String.format(HELP_PATH, "contents.json")), mainPanel.editor));
        initComponents();
        
        urlIndex = 0;
        urlStack = new ArrayList<>();
        mnuGoBack.setEnabled(false);
        mnuGoForward.setEnabled(false);
        
        Amphibia.setDefaultHTMLStyles(txContent);
        if (treeNode.getChildCount() > 0) {
            try {
                URL url = selectedNode((TreeItem)treeNode.getChildAt(0));
                urlStack.add(url);
            } catch (IOException ex) {
                mainPanel.addError(ex);
            }
        }

        treeNav.setRowHeight(20);
        treeNav.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 0));
        treeNav.setEditable(false);
        treeNav.setRootVisible(false);
        treeNav.setShowsRootHandles(true);
        treeNav.setCellRenderer(new DefaultTreeCellRenderer() {
            @Override
            public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean expanded, boolean isLeaf, int row, boolean hasFocus) {
                Component c = super.getTreeCellRendererComponent(tree, value, selected, expanded, isLeaf, row, hasFocus);
                setIcon(null);
                return c;
            }
        });
        treeNav.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent e) {
                TreePath path = treeNav.getPathForLocation(e.getX(), e.getY());
                if (path == null) {
                    return;
                }
                TreeItem node = (TreeItem) treeNav.getLastSelectedPathComponent();
                if (node != null) {
                    try {
                        URL url = selectedNode(node);
                        addUrl(url);
                    } catch (IOException ex) {
                        mainPanel.addError(ex);
                    }
                }
            }
        });
        
        txContent.setEditable(false);
        txContent.addHyperlinkListener((HyperlinkEvent event) -> {
            if (event.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                try {
                    treeNav.setSelectionRow(-1);
                    if (!addUrl(event.getURL())) {
                        return;
                    }
                    txContent.setPage(event.getURL());
                } catch (IOException e) {
                    mainPanel.addError(e);
                }
            }
        });

        splPanel.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, new PropertyChangeListener() {
            Timer timer = new Timer();

            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                Amphibia.updatePreferences(timer, () -> {
                    userPreferences.putInt(Amphibia.P_DIVIDER + "Help", splPanel.getDividerLocation());
                });
            }
        });
        splPanel.setDividerLocation(userPreferences.getInt(Amphibia.P_DIVIDER + "Help", 150));
        addComponentListener(new ComponentAdapter() {
            Timer timer = new Timer();

            @Override
            public void componentResized(ComponentEvent evt) {
                Amphibia.updatePreferences(timer, () -> {
                    Amphibia.setBounds(HelpDialog.this, Amphibia.P_HELP_BOUNDS);
                });
            }

            @Override
            public void componentMoved(ComponentEvent e) {
                Amphibia.updatePreferences(timer, () -> {
                    Amphibia.setBounds(HelpDialog.this, Amphibia.P_HELP_BOUNDS);
                });
            }
        });
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e){
                setVisible(false);
            }
        });
        
        setSize(630, 500);
        setIconImage(Amphibia.instance.icon.getImage());
        java.awt.EventQueue.invokeLater(() -> {
            setLocationRelativeTo(mainPanel);
        });
        
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        Amphibia.getBounds(this, Amphibia.P_HELP_BOUNDS);
    }
    
    public void openDialog() {
        setVisible(true);
    }
    
    private void createTreeNodes(DefaultMutableTreeNode parent, JSONArray children) {
        children.forEach((item) -> {
            TreeItem node = new TreeItem((JSONObject) item); 
            parent.add(node);
            if (node.json.containsKey("children")) {
                createTreeNodes(node, node.json.getJSONArray("children"));
            }
        });
    }
    
    private URL selectedNode(TreeItem node) throws IOException {
        URL url = IO.getResources(String.format(HELP_PATH, node.json.getString("link"))).toURL();
        txContent.setPage(url);
        return url;
    }
    
    private boolean addUrl(URL url) {
        if (url.toString().equals(urlStack.get(urlIndex).toString())) {
            return false;
        }
        urlStack.subList(urlIndex + 1, urlStack.size()).clear();
        urlIndex = urlStack.size();
        urlStack.add(url);
        mnuGoBack.setEnabled(true);
        mnuGoForward.setEnabled(false);
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

        toolbar = new JToolBar();
        mnuGoBack = new JButton();
        mnuGoForward = new JButton();
        spr1 = new JToolBar.Separator();
        btnPrinter = new JButton();
        splPanel = new JSplitPane();
        slpNav = new JScrollPane();
        treeNav = new JTree();
        slpContent = new JScrollPane();
        txContent = new JEditorPane();

        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        ResourceBundle bundle = ResourceBundle.getBundle("com/equinix/amphibia/messages"); // NOI18N
        setTitle(bundle.getString("mnuHelp")); // NOI18N

        toolbar.setRollover(true);

        mnuGoBack.setIcon(new ImageIcon(getClass().getResource("/com/equinix/amphibia/icons/arrow-left.png"))); // NOI18N
        mnuGoBack.setToolTipText(bundle.getString("mnuGoBack")); // NOI18N
        mnuGoBack.setFocusable(false);
        mnuGoBack.setHorizontalTextPosition(SwingConstants.CENTER);
        mnuGoBack.setVerticalTextPosition(SwingConstants.BOTTOM);
        mnuGoBack.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                mnuGoBackActionPerformed(evt);
            }
        });
        toolbar.add(mnuGoBack);

        mnuGoForward.setIcon(new ImageIcon(getClass().getResource("/com/equinix/amphibia/icons/arrow-right.png"))); // NOI18N
        mnuGoForward.setToolTipText(bundle.getString("mnuGoForward")); // NOI18N
        mnuGoForward.setFocusable(false);
        mnuGoForward.setHorizontalTextPosition(SwingConstants.CENTER);
        mnuGoForward.setVerticalTextPosition(SwingConstants.BOTTOM);
        mnuGoForward.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                mnuGoForwardActionPerformed(evt);
            }
        });
        toolbar.add(mnuGoForward);
        toolbar.add(spr1);

        btnPrinter.setIcon(new ImageIcon(getClass().getResource("/com/equinix/amphibia/icons/printer_16.png"))); // NOI18N
        btnPrinter.setToolTipText(bundle.getString("mnuPrinter")); // NOI18N
        btnPrinter.setFocusable(false);
        btnPrinter.setHorizontalTextPosition(SwingConstants.CENTER);
        btnPrinter.setVerticalTextPosition(SwingConstants.BOTTOM);
        btnPrinter.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                btnPrinterActionPerformed(evt);
            }
        });
        toolbar.add(btnPrinter);

        getContentPane().add(toolbar, BorderLayout.NORTH);

        splPanel.setDividerLocation(150);

        treeNav.setModel(this.treeModel);
        slpNav.setViewportView(treeNav);

        splPanel.setLeftComponent(slpNav);

        txContent.setContentType("text/html"); // NOI18N
        slpContent.setViewportView(txContent);

        splPanel.setRightComponent(slpContent);

        getContentPane().add(splPanel, BorderLayout.CENTER);

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void mnuGoBackActionPerformed(ActionEvent evt) {//GEN-FIRST:event_mnuGoBackActionPerformed
        try {
            if (--urlIndex < 1) {
                mnuGoBack.setEnabled(false);
            }
            mnuGoForward.setEnabled(true);
            txContent.setPage(urlStack.get(urlIndex));
        } catch (IOException ex) {
            mainPanel.addError(ex);
        }
    }//GEN-LAST:event_mnuGoBackActionPerformed

    private void mnuGoForwardActionPerformed(ActionEvent evt) {//GEN-FIRST:event_mnuGoForwardActionPerformed
        try {
            if (++urlIndex == urlStack.size() - 1) {
                mnuGoForward.setEnabled(false);
            }
            mnuGoBack.setEnabled(true);
            txContent.setPage(urlStack.get(urlIndex));
        } catch (IOException ex) {
            mainPanel.addError(ex);
        }
    }//GEN-LAST:event_mnuGoForwardActionPerformed

    private void btnPrinterActionPerformed(ActionEvent evt) {//GEN-FIRST:event_btnPrinterActionPerformed
        try {
            txContent.print();
        } catch (PrinterException ex) {
            mainPanel.addError(ex);
        }
    }//GEN-LAST:event_btnPrinterActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private JButton btnPrinter;
    private JButton mnuGoBack;
    private JButton mnuGoForward;
    private JScrollPane slpContent;
    private JScrollPane slpNav;
    private JSplitPane splPanel;
    private JToolBar.Separator spr1;
    private JToolBar toolbar;
    private JTree treeNav;
    private JEditorPane txContent;
    // End of variables declaration//GEN-END:variables

    class TreeItem extends DefaultMutableTreeNode {
        public JSONObject json;
        
        public TreeItem(JSONObject json) {
            super(json);
            this.json = json;
        }
        
        @Override
        public String toString() {
            return json.getString("label");
        }
    }
}
