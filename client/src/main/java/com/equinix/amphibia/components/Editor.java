/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.equinix.amphibia.components;

import static com.equinix.amphibia.components.TreeCollection.TYPE.*;
import static com.equinix.amphibia.components.JTreeTable.EditValueRenderer.TYPE.*;

import com.equinix.amphibia.IO;
import com.equinix.amphibia.Amphibia;
import com.sksamuel.diffpatch.DiffMatchPatch;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.ResourceBundle;
import java.util.logging.Level;
import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.JToggleButton;
import javax.swing.JTree;
import javax.swing.ListSelectionModel;
import javax.swing.OverlayLayout;
import javax.swing.SwingConstants;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.text.BadLocationException;
import javax.swing.text.StyledDocument;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import net.sf.json.JSON;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.*;

/**
 *
 * @author dgofman
 */
public final class Editor extends BaseTaskPane {

    private static final DiffMatchPatch DIFF = new DiffMatchPatch();

    private final File history;
    private final URI historyURI;
    private final String historyInfo;

    private final List<String> histories = new ArrayList<>();
    private int historyIndex;

    private JTreeTable treeTable;
    private JSONTableModel defaultModel;
    private DefaultTableModel historyModel;
    private DefaultComboBoxModel serversModel;

    private JSONTableModel jsonModel;

    public int loadMaxLastHistory;

    /**
     * Creates new form Converter
     */
    @SuppressWarnings("OverridableMethodCallInConstructor")
    public Editor() {
        super();

        historyInfo = "info.properties";
        history = IO.newFile(Amphibia.getAmphibiaHome(), ".history.zip");
        historyURI = URI.create("jar:" + history.toURI());

        serversModel = new DefaultComboBoxModel<>(new String[]{bundle.getString("mockServer")});

        historyModel = new DefaultTableModel(
                new Object[][]{},
                new String[]{
                    bundle.getString("date"), bundle.getString("file"), bundle.getString("content"), ""
                }
        );

        defaultDividerLocation = 300;
        loadMaxLastHistory = userPreferences.getInt(Amphibia.P_HISTORY, 50);

        initComponents();

        WizardTab.ExtendedStyledEditorKit editorKit = new WizardTab.ExtendedStyledEditorKit();
        String contentType = editorKit.getContentType();
        txtRaw.setEditorKitForContentType(contentType, editorKit);
        txtRaw.setEditorKit(editorKit);

        setDividerLocation(userPreferences.getInt(propertyChangeName, getDividerLocation()));

        defaultModel = JSONTableModel.createModel(bundle);
        JTreeTable.RowEventListener rowListener = (JTreeTable table, int row, int column, Object cellValue) -> {
            TreeIconNode node = MainPanel.selectedNode;
            if (node.getParent() == null) {
                return;
            }
            TreeCollection.TYPE type = node.getType();
            TreeCollection collection = node.getCollection();
            Entry entry = (Entry) table.getModel().getValueAt(row, 0);
            Object value = table.getModel().getValueAt(row, 1);
            if (cellValue == ADD) {
                mainPanel.resourceEditDialog.openCreateDialog(entry);
            } else if (cellValue != null && cellValue == VIEW) {
                mainPanel.resourceEditDialog.openEditDialog(entry, value, false);
            } else if (cellValue == EDIT || cellValue == EDIT_LIMIT) {
                if (entry.parent != null && entry.parent.type == TRANSFER) {
                    mainPanel.transferDialog.openDialog(node, entry);
                } else {
                    mainPanel.resourceEditDialog.openEditDialog(entry, value, true);
                }
            } else if (cellValue == ADD_RESOURCES) {
                if (type == TESTSUITE) {
                    mainPanel.resourceAddDialog.showTestCaseDialog(collection);
                } else {
                    mainPanel.resourceAddDialog.showTestStepDialog(node);
                }
            } else if (cellValue == REFERENCE_EDIT) {
                if ((type == TESTSUITE && "testcases".equals(entry.parent.name))
                        || (type == TESTCASE && "teststeps".equals(entry.parent.name))) {
                    mainPanel.resourceOrderDialog.openDialog(node, entry.getParent().getIndex(entry));
                } else {
                    mainPanel.referenceEditDialog.openEditDialog(collection, entry);
                }
            } else if (cellValue == REFERENCE) {
                mainPanel.referenceEditDialog.openViewDialog(collection, entry);
            } else if (cellValue == TRANSFER) {
                mainPanel.transferDialog.openDialog(node, entry);
            } else if (cellValue == ASSERTS) {
                mainPanel.assertDialog.openDialog(node, entry);
            }
        };
        treeTable = new JTreeTable(defaultModel, rowListener);
        treeTable.setAutoCreateColumnsFromModel(false);
        treeTable.getColumnModel().getColumn(0).setPreferredWidth(300);
        treeTable.setAutoResizeMode(JTable.AUTO_RESIZE_NEXT_COLUMN);
        treeTable.getColumnModel().getColumn(1).setPreferredWidth(500);
        treeTable.getColumnModel().getColumn(2).setResizable(false);
        treeTable.getColumnModel().getColumn(2).setMaxWidth(20);
        treeTable.setShowVerticalLines(true);
        treeTable.setFillsViewportHeight(true);
        treeTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int row = treeTable.rowAtPoint(e.getPoint());
                    int col = treeTable.columnAtPoint(e.getPoint());
                    Object cellValue = treeTable.getValueAt(row, 2);
                    rowListener.fireEvent(treeTable, row, col, cellValue);
                }
            }
        });
        pnlTop.add(treeTable.getScrollPane());

        JTree tableTree = treeTable.getTree();
        tableTree.setShowsRootHandles(true);
        tableTree.setRootVisible(false);

        int buttonIndex = 3;
        JButton button = new JButton("...");
        Font font = treeTable.getFont();
        TableCellRenderer renderer = tblHistory.getDefaultRenderer(Object.class);
        Cursor handCursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);
        Cursor defaultCursor = Cursor.getDefaultCursor();

        treeTable.getTableHeader().setFont(font.deriveFont(Font.BOLD));
        treeTable.getTableHeader().setReorderingAllowed(false);

        tblHistory.setDefaultEditor(Object.class, null);
        tblHistory.getTableHeader().setFont(font.deriveFont(Font.BOLD));
        tblHistory.setAutoCreateColumnsFromModel(false);
        tblHistory.getTableHeader().setResizingAllowed(true);
        tblHistory.getTableHeader().setReorderingAllowed(false);
        tblHistory.getColumnModel().getColumn(0).setPreferredWidth(130);
        tblHistory.getColumnModel().getColumn(1).setPreferredWidth(255);
        tblHistory.setAutoResizeMode(JTable.AUTO_RESIZE_NEXT_COLUMN);
        tblHistory.getColumnModel().getColumn(2).setPreferredWidth(600);
        tblHistory.getColumnModel().getColumn(buttonIndex).setResizable(false);
        tblHistory.getColumnModel().getColumn(buttonIndex).setMaxWidth(20);
        tblHistory.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        tblHistory.setDefaultRenderer(Object.class, (JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) -> {
            if (column == buttonIndex) {
                return button;
            } else {
                Component c = renderer.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                ((JComponent) c).setToolTipText(value.toString());
                return c;
            }
        });
        tblHistory.addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int column = tblHistory.columnAtPoint(e.getPoint());
                if (column == buttonIndex) {
                    tblHistory.setCursor(handCursor);
                } else {
                    tblHistory.setCursor(defaultCursor);
                }
            }
        });

        tblHistory.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int column = tblHistory.columnAtPoint(e.getPoint());
                if (column == buttonIndex) {
                    int row = tblHistory.rowAtPoint(e.getPoint());
                    String filePath = tblHistory.getValueAt(row, 1).toString();
                    String diff = tblHistory.getValueAt(row, 2).toString();
                    try {
                        mainPanel.resourceEditDialog.openEditDialog(row, filePath, diff);
                    } catch (Exception ex) {
                        addError(ex);
                    }
                }
            }
        });

        setComponents(tbpOutput, treeProblems);
    }

    private String getHistoryContent(String path) {
        String content = null;
        FileSystem zipfs = null;
        try {
            zipfs = getFileSystem();
            content = IO.readInputStream(Files.newInputStream(zipfs.getPath(path)));
        } catch (IOException ex) {
            addError(ex);
        } finally {
            try {
                if (zipfs != null) {
                    zipfs.close();
                }
            } catch (IOException ex) {
                addError(ex, path);
            }
        }
        return content;
    }

    public void deleteHistory(int row) throws IOException {
        try (FileSystem zipfs = getFileSystem()) {
            String timeDir = tblHistory.getValueAt(row, 3).toString();
            Iterator<String> lines = histories.iterator();
            while (lines.hasNext()) {
                if (lines.next().startsWith(timeDir)) {
                    lines.remove();
                    break;
                }
            }
            Path historyPath = zipfs.getPath(historyInfo);
            Files.deleteIfExists(historyPath);
            Files.write(historyPath, String.join("\n", histories).getBytes(), StandardOpenOption.CREATE);
            deleteHistoryDir(zipfs, timeDir);
            historyModel.removeRow(row);
            historyIndex = 0;
            tblHistory.clearSelection();
        }
    }

    private void deleteHistoryDir(FileSystem zipfs, String dir) throws IOException {
        Files.deleteIfExists(zipfs.getPath(dir + "/diff.txt"));
        Files.deleteIfExists(zipfs.getPath(dir + "/content.json"));
        Files.deleteIfExists(zipfs.getPath(dir));
    }

    public void loadHistory() {
        try {
            getFileSystem().close();
        } catch (IOException ex) {
            history.delete(); //corrupted archive file
        }

        FileSystem zipfs = null;
        try {
            zipfs = getFileSystem();
            Files.createDirectories(zipfs.getPath("0")); //last content directory
            BufferedReader reader = new BufferedReader(new InputStreamReader(Files.newInputStream(zipfs.getPath(historyInfo))));
            int index = 0;
            String line;
            while ((line = reader.readLine()) != null) {
                try {
                    histories.add(line);
                    String[] time_path = line.split("=");
                    long time = Long.valueOf(time_path[0]);
                    if (time != 0) {
                        if (index++ > loadMaxLastHistory) {
                            break;
                        }
                        String diff = IO.readInputStream(Files.newInputStream(zipfs.getPath(time + "/diff.txt")));
                        historyModel.addRow(new Object[]{dateMediumFormat.print(time), time_path[1], diff, time});
                    }
                } catch (NullPointerException | NumberFormatException | StringIndexOutOfBoundsException e) {
                    logger.log(Level.SEVERE, "Line number: " + index + "::" + line, e);
                }
            }
        } catch (IOException ex) {
        } finally {
            try {
                if (zipfs != null) {
                    zipfs.close();
                }
            } catch (IOException ex) {
                addError(ex);
            }
        }
    }

    public boolean addHistory(Date setDate, String filePath, String oldContent, String newContent) {
        if (newContent == null || newContent.equals(oldContent)) {
            return false;
        }
        new Thread() {
            @Override
            public void run() {
                Date date = setDate;
                if (date == null) {
                    date = new Date();
                }
                long time = date.getTime();
                String diff = DIFF.patch_toText(DIFF.patch_make(oldContent, newContent));
                Object[] items = new Object[]{dateMediumFormat.print(time), filePath, diff, time};

                FileSystem zipfs = null;
                try {
                    zipfs = getFileSystem();
                    Files.createDirectories(zipfs.getPath(String.valueOf(time)));

                    while (historyIndex > 0) {
                        String timeDir = histories.get(0).split("=")[0];
                        if (!"0".equals(timeDir)) {
                            deleteHistoryDir(zipfs, timeDir);
                            historyModel.removeRow(0);
                            historyIndex--;
                        }
                        histories.remove(0);
                    }
                    Amphibia.instance.enableUndo(true);
                    Amphibia.instance.enableRedo(false);
                    if (histories.size() > 0) {
                        histories.remove(0); //remove last saved content
                    }
                    histories.add(0, "0=" + filePath);
                    Path contentPath = zipfs.getPath("0/content.json");
                    Files.deleteIfExists(contentPath);
                    Files.createDirectories(zipfs.getPath("0"));
                    Files.write(contentPath, newContent.getBytes(), StandardOpenOption.CREATE);

                    histories.add(1, time + "=" + filePath);
                    Path diffPath = zipfs.getPath(time + "/diff.txt");
                    Files.write(diffPath, diff.getBytes());
                    contentPath = zipfs.getPath(time + "/content.json");
                    Files.write(contentPath, oldContent.getBytes());

                    historyModel.insertRow(0, items);

                    Path historyPath = zipfs.getPath(historyInfo);
                    Files.deleteIfExists(historyPath);
                    Files.write(historyPath, String.join("\n", histories).getBytes(), StandardOpenOption.CREATE);
                } catch (IOException ex) {
                    addError(ex);
                } finally {
                    try {
                        if (zipfs != null) {
                            zipfs.close();
                        }
                    } catch (IOException ex) {
                        addError(ex);
                    }
                }
            }
        }.start();
        return true;
    }

    public FileSystem getFileSystem() throws IOException {
        return FileSystems.newFileSystem(historyURI, Collections.singletonMap("create", String.valueOf(!history.exists())));
    }

    @Override
    public DefaultMutableTreeNode addError(String error) {
        return super.addError(error);
    }

    public boolean undo() {
        if (histories.size() > historyIndex + 1) {
            tblHistory.setRowSelectionInterval(0, historyIndex);
            return patchHistory(histories.get(++historyIndex), historyIndex < histories.size() - 1);
        }
        return false;
    }

    public boolean redo() {
        if (historyIndex > 0) {
            if (--historyIndex > 0) {
                tblHistory.setRowSelectionInterval(0, historyIndex - 1);
            } else {
                tblHistory.clearSelection();
            }
            return patchHistory(histories.get(historyIndex), historyIndex > 0);
        }
        return false;
    }

    public boolean patchHistory(String line, boolean isEnabled) {
        try {
            String[] time_path = line.split("=");
            File file = IO.newFile(time_path[1]);
            file = IO.getBackupOrFile(file);
            String oldContent = getHistoryContent(time_path[0] + "/content.json");
            IO.write(oldContent, file);
            mainPanel.reloadAll(true);
            return isEnabled;
        } catch (IOException ex) {
            addError(ex);
        }
        return false;
    }

    public void selectedTreeNode(TreeIconNode node) {
        TreeIconNode.TreeIconUserObject userObject = node.getTreeIconUserObject();
        txtInfo.setText(userObject.getFullPath());
        if (userObject.properties != null) {
            jsonModel = JSONTableModel.createModel(bundle).updateModel(node, userObject.json, userObject.properties);
            treeTable.setModel(jsonModel);
        }
    }

    @Override
    public void clear() {
        if (tabs.getSelectedIndex() == Amphibia.TAB_CONSOLE) {
            mainPanel.runner.resetConsole();
        } else {
            super.clear();
        }
    }

    @Override
    public void refresh() {
        tree.updateUI();
        treeProblems.updateUI();
        treeTable.updateUI();
    }

    @Override
    public void reset() {
        super.reset();
        txtInfo.setText("");
        txtRaw.setText("");
        treeTable.setModel(defaultModel);
    }

    public void resetHistory() {
        histories.clear();
        historyIndex = 0;
        Amphibia.instance.enableUndo(false);
        Amphibia.instance.enableRedo(false);
    }

    public void deleteHistory() {
        history.delete();
        historyModel.getDataVector().clear();
        historyModel.fireTableStructureChanged();
        resetHistory();
        loadHistory();
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
        pnlInfo = new JPanel();
        txtInfo = new JTextField();
        pnlEditor = new JPanel();
        pnlOutput = new JPanel();
        tbpOutput = new JTabbedPane();
        srlProblems = new JScrollPane();
        treeProblems = new JTree();
        spnRaw = new JScrollPane();
        txtRaw = new JTextPane();
        spnConsole = new JScrollPane();
        txtConsole = new JTextPane();
        pnlServers = new JPanel();
        pnlServersTop = new JPanel();
        cmbServers = new JComboBox<>();
        btnStart = new JToggleButton();
        btnStop = new JButton();
        spnServers = new JScrollPane();
        txtServers = new JTextPane();
        pnlProfile = new JPanel();
        jScrollPane1 = new JScrollPane();
        jTextArea1 = new JTextArea();
        splHistory = new JScrollPane();
        tblHistory = new JTable();
        pnlTabRightButtons = new JPanel();
        lblClear = new JLabel();

        setDividerLocation(300);
        setDividerSize(3);
        setOrientation(JSplitPane.VERTICAL_SPLIT);

        pnlTop.setLayout(new BorderLayout());

        pnlInfo.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        pnlInfo.setLayout(new BorderLayout());

        txtInfo.setEditable(false);
        txtInfo.setBackground(new Color(255, 255, 255));
        pnlInfo.add(txtInfo, BorderLayout.CENTER);

        pnlTop.add(pnlInfo, BorderLayout.PAGE_START);
        pnlTop.add(pnlEditor, BorderLayout.CENTER);

        setLeftComponent(pnlTop);

        pnlOutput.setLayout(new OverlayLayout(pnlOutput));

        tbpOutput.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent evt) {
                tbpOutputStateChanged(evt);
            }
        });
        tbpOutput.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent evt) {
                tbpOutputMouseClicked(evt);
            }
        });

        treeProblems.setModel(treeProblemsModel);
        treeProblems.setRootVisible(false);
        srlProblems.setViewportView(treeProblems);

        ResourceBundle bundle = ResourceBundle.getBundle("com/equinix/amphibia/messages"); // NOI18N
        tbpOutput.addTab(bundle.getString("problems"), new ImageIcon(getClass().getResource("/com/equinix/amphibia/icons/error_16.png")), srlProblems); // NOI18N

        spnRaw.setViewportView(txtRaw);

        tbpOutput.addTab(bundle.getString("raw"), new ImageIcon(getClass().getResource("/com/equinix/amphibia/icons/raw_16.png")), spnRaw); // NOI18N

        spnConsole.setViewportView(txtConsole);

        tbpOutput.addTab(bundle.getString("console"), new ImageIcon(getClass().getResource("/com/equinix/amphibia/icons/console_16.png")), spnConsole); // NOI18N

        pnlServers.setLayout(new BorderLayout());

        pnlServersTop.setLayout(new FlowLayout(FlowLayout.LEFT));

        cmbServers.setFont(new Font("Tahoma", 1, 11)); // NOI18N
        cmbServers.setModel(serversModel);
        cmbServers.setPreferredSize(new Dimension(200, 24));
        pnlServersTop.add(cmbServers);

        btnStart.setIcon(new ImageIcon(getClass().getResource("/com/equinix/amphibia/icons/run_16.png"))); // NOI18N
        btnStart.setToolTipText(bundle.getString("start")); // NOI18N
        btnStart.setFocusable(false);
        btnStart.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                btnStartActionPerformed(evt);
            }
        });
        pnlServersTop.add(btnStart);

        btnStop.setIcon(new ImageIcon(getClass().getResource("/com/equinix/amphibia/icons/stop-16.png"))); // NOI18N
        btnStop.setToolTipText(bundle.getString("stop")); // NOI18N
        btnStop.setFocusable(false);
        btnStop.setHorizontalTextPosition(SwingConstants.CENTER);
        btnStop.setVerticalTextPosition(SwingConstants.BOTTOM);
        btnStop.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                btnStopActionPerformed(evt);
            }
        });
        pnlServersTop.add(btnStop);

        pnlServers.add(pnlServersTop, BorderLayout.NORTH);

        spnServers.setViewportView(txtServers);

        pnlServers.add(spnServers, BorderLayout.CENTER);

        tbpOutput.addTab(bundle.getString("servers"), new ImageIcon(getClass().getResource("/com/equinix/amphibia/icons/servers_16.png")), pnlServers); // NOI18N

        pnlProfile.setLayout(new BorderLayout());

        jTextArea1.setColumns(20);
        jTextArea1.setRows(5);
        jScrollPane1.setViewportView(jTextArea1);

        pnlProfile.add(jScrollPane1, BorderLayout.CENTER);

        tbpOutput.addTab(bundle.getString("profile"), new ImageIcon(getClass().getResource("/com/equinix/amphibia/icons/stopwatch_16.png")), pnlProfile); // NOI18N

        tblHistory.setModel(historyModel        );
        splHistory.setViewportView(tblHistory);

        tbpOutput.addTab(bundle.getString("history"), new ImageIcon(getClass().getResource("/com/equinix/amphibia/icons/history_16.png")), splHistory); // NOI18N

        pnlOutput.add(tbpOutput);

        pnlTabRightButtons.setLayout(new FlowLayout(FlowLayout.RIGHT, 10, 3));

        lblClear.setIcon(new ImageIcon(getClass().getResource("/com/equinix/amphibia/icons/clear.png"))); // NOI18N
        lblClear.setToolTipText(bundle.getString("clear")); // NOI18N
        lblClear.setCursor(new Cursor(Cursor.HAND_CURSOR));
        lblClear.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent evt) {
                lblClearMouseClicked(evt);
            }
        });
        pnlTabRightButtons.add(lblClear);

        pnlOutput.add(pnlTabRightButtons);

        setRightComponent(pnlOutput);
    }// </editor-fold>//GEN-END:initComponents

    private void lblClearMouseClicked(MouseEvent evt) {//GEN-FIRST:event_lblClearMouseClicked
        clear();
    }//GEN-LAST:event_lblClearMouseClicked

    private void tbpOutputStateChanged(ChangeEvent evt) {//GEN-FIRST:event_tbpOutputStateChanged
        pnlTabRightButtons.setVisible(tbpOutput.getSelectedIndex() == Amphibia.TAB_PROBLEMS || tbpOutput.getSelectedIndex() == Amphibia.TAB_CONSOLE);
    }//GEN-LAST:event_tbpOutputStateChanged

    private void tbpOutputMouseClicked(MouseEvent evt) {//GEN-FIRST:event_tbpOutputMouseClicked
        Rectangle r = new Rectangle(lblClear.getX(), lblClear.getY(), lblClear.getWidth(), lblClear.getHeight());
        boolean b = r.contains(evt.getPoint());
        if (b) {
            lblClear.dispatchEvent(evt);
        }
    }//GEN-LAST:event_tbpOutputMouseClicked

    private void btnStopActionPerformed(ActionEvent evt) {//GEN-FIRST:event_btnStopActionPerformed
        btnStart.setSelected(false);
        StyledDocument doc = txtServers.getStyledDocument();
        try {
            doc.insertString(doc.getLength(), "Ternimated\n", null);
        } catch (BadLocationException ex) {
            logger.log(Level.SEVERE, ex.toString(), ex);
        }
    }//GEN-LAST:event_btnStopActionPerformed

    private void btnStartActionPerformed(ActionEvent evt) {//GEN-FIRST:event_btnStartActionPerformed
        txtServers.setText("Started...\n");
    }//GEN-LAST:event_btnStartActionPerformed


    // Variables declaration - do not modify//GEN-BEGIN:variables
    JToggleButton btnStart;
    JButton btnStop;
    JComboBox<String> cmbServers;
    JScrollPane jScrollPane1;
    JTextArea jTextArea1;
    JLabel lblClear;
    JPanel pnlEditor;
    JPanel pnlInfo;
    JPanel pnlOutput;
    JPanel pnlProfile;
    JPanel pnlServers;
    JPanel pnlServersTop;
    JPanel pnlTabRightButtons;
    JPanel pnlTop;
    JScrollPane splHistory;
    JScrollPane spnConsole;
    JScrollPane spnRaw;
    JScrollPane spnServers;
    JScrollPane srlProblems;
    JTable tblHistory;
    JTabbedPane tbpOutput;
    JTree treeProblems;
    JTextPane txtConsole;
    JTextField txtInfo;
    JTextPane txtRaw;
    JTextPane txtServers;
    // End of variables declaration//GEN-END:variables

    static final public class Entry implements TreeNode {

        public TreeIconNode node;
        public JSON json;
        public String name;
        public Object value;
        public boolean isLeaf;
        public int editMode;
        public Boolean isDynamic;
        public Boolean isDelete;
        public List<Entry> children;
        private Entry parent;
        public String rootName;

        private JTreeTable.EditValueRenderer.TYPE type;

        public Entry(TreeIconNode node, String name) {
            this.node = node;
            this.name = name;
            this.type = EDIT;
            this.editMode = JTreeTable.EDIT_DELETE;
            this.children = new ArrayList<>();
            this.isDynamic = false;
            this.isDelete = false;
        }

        public Entry(TreeIconNode node, Entry parent, JSON json, String name, Object value, boolean isLeaf, boolean isDynamic) {
            this(node, name);
            this.parent = parent;
            this.json = json;
            this.value = value;
            this.isLeaf = isLeaf;
            this.isDynamic = isDynamic;
        }

        public void setType(JTreeTable.EditValueRenderer.TYPE type) {
            this.type = type;
            if (type == JTreeTable.EditValueRenderer.TYPE.VIEW || type == JTreeTable.EditValueRenderer.TYPE.REFERENCE) {
                this.editMode = JTreeTable.READ_ONLY;
            } else {
                boolean editAndDelete = true;
                if (isDynamic) {
                    switch (node.getType()) {
                        case PROJECT:
                            if ("properties".equals(parent.toString())) {
                                JSONObject props = node.getCollection().getProjectProfile().getJSONObject("properties");
                                editAndDelete = props.containsKey(name);
                            }
                            break;
                        case INTERFACE:
                            if ("headers".equals(parent.toString())) {
                                editAndDelete = node.info.resource.getJSONObject("headers").containsKey(name);
                            }
                            break;
                        case TESTSUITE:
                            if ("properties".equals(parent.toString()) && node.info.testSuite.containsKey("properties")) {
                                editAndDelete = node.info.testSuite.getJSONObject("properties").containsKey(name);
                            }
                            break;
                        case TESTCASE:
                            editAndDelete = containsKey(node.info.testCase);
                            break;
                        case TEST_STEP_ITEM:
                            editAndDelete = containsKey(node.info.testStep);
                            break;
                    }
                }
                this.editMode = editAndDelete ? JTreeTable.EDIT_RESET : JTreeTable.EDIT_ONLY;
            }
        }

        public JTreeTable.EditValueRenderer.TYPE getType() {
            return type;
        }

        private boolean containsKey(JSONObject json) {
            if (json != null) {
                if ("headers".equals(parent.toString()) && json.containsKey("headers")) {
                    return json.getJSONObject("headers").containsKey(name);
                } else if ("properties".equals(parent.toString()) && json.containsKey("properties")) {
                    return json.getJSONObject("properties").containsKey(name);
                } else if (json.containsKey(rootName)) {
                    if ("request".equals(rootName) || "response".equals(rootName)) {
                        return (json.getJSONObject(rootName).containsKey(getParent().toString())
                                && json.getJSONObject(rootName).getJSONObject(getParent().toString()).containsKey(name));
                    }
                    return json.getJSONObject(rootName).containsKey(name);
                }
            }
            return false;
        }

        public Object getValue(Object value) {
            if (value instanceof JSONObject && ((JSONObject) value).isNullObject()) {
                return null;
            }
            return value;
        }

        public Entry add(TreeIconNode node, JSON parentJson, String name, Object value, Object propType, Object[] props, Object rootName) {
            if (rootName == null) {
                rootName = name;
            }
            boolean isleaf = !(JTreeTable.isNotLeaf(propType) || !(propType instanceof JTreeTable.EditValueRenderer.TYPE));
            Entry entry = new Entry(node, this, parentJson, name, value, isleaf, false);
            entry.rootName = rootName.toString();
            if (propType instanceof JTreeTable.EditValueRenderer.TYPE) {
                entry.setType((JTreeTable.EditValueRenderer.TYPE) propType);
            } else {
                entry.setType(null);
            }
            children.add(entry);

            JSONObject jo = null;
            if (value instanceof JSONObject) {
                jo = (JSONObject) value;
            } else if (value instanceof JSONArray && !(propType instanceof Object[][])) {
                JSONArray list = (JSONArray) value;
                for (int i = 0; i < list.size(); i++) {
                    Entry child = new Entry(node, entry, list, " ", list.get(i), true, true);
                    if (props != null && props.length == 3) {
                        child.setType((JTreeTable.EditValueRenderer.TYPE) props[2]);
                    } else {
                        child.setType(EDIT);
                    }
                    entry.children.add(child);
                }
                return entry;
            }

            if (propType instanceof Object[][]) {
                for (Object[] prop : (Object[][]) propType) {
                    String key = prop[0].toString();
                    if (jo != null) {
                        entry.add(node, jo, key, getValue(jo.get(key)), prop[1], prop, rootName);
                    } else {
                        JSONArray list = (JSONArray) value;
                        for (int i = 0; i < list.size(); i++) {
                            jo = list.getJSONObject(i);
                            entry.add(node, jo, key, getValue(jo.get(key)), prop[1], prop, rootName);
                        }
                    }
                }
                return entry;
            }

            if (jo != null && !jo.isNullObject()) {
                Iterator<String> keyItr = jo.keys();
                while (keyItr.hasNext()) {
                    String key = keyItr.next();
                    Entry child = new Entry(node, entry, jo, key, getValue(jo.get(key)), true, true);
                    child.rootName = rootName.toString();
                    if (props.length > 2) {
                        child.setType((JTreeTable.EditValueRenderer.TYPE) props[2]);
                    } else {
                        child.setType(EDIT);
                    }
                    entry.children.add(child);
                }
            }
            return entry;
        }

        @Override
        public String toString() {
            return name;
        }

        @Override
        public boolean isLeaf() {
            return this.isLeaf;
        }

        @Override
        public TreeNode getChildAt(int childIndex) {
            return children.get(childIndex);
        }

        @Override
        public int getChildCount() {
            return children.size();
        }

        @Override
        public int getIndex(TreeNode node) {
            return children.indexOf(node);
        }

        @Override
        public TreeNode getParent() {
            return parent;
        }

        @Override
        public boolean getAllowsChildren() {
            return true;
        }

        @Override
        public Enumeration children() {
            return Collections.enumeration(children);
        }
    }
}
