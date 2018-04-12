/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.equinix.amphibia.components;

import com.equinix.amphibia.agent.converter.Converter;

import com.equinix.amphibia.Amphibia;
import com.equinix.amphibia.IO;
import static com.equinix.amphibia.components.ResourceEditDialog.getValue;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowEvent;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import javax.swing.border.Border;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.CellEditor;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

/**
 *
 * @author dgofman
 */
public final class GlobalVariableDialog extends javax.swing.JFrame {

    private final MainPanel mainPanel;
    private final ResourceBundle bundle;
    private final TableModel globalVarsModel;

    private static final GlobalVarSource globalVarsSource = new GlobalVarSource();
    private static final NumberFormat numberFormat = NumberFormat.getInstance();
    private static final Preferences userPreferences = Amphibia.getUserPreferences();
    private static final Logger logger = Amphibia.getLogger(GlobalVariableDialog.class.getName());

    private String[] originalColumns;
    private Object[][] originalData;
    private String[] defaultHadersNames;
    private int cloneColumnIndex;

    public static final int defaultColumnIndex = 2;

    /**
     * Creates new form GlobalVaraibleDialog
     *
     * @param mainPanel
     */
    public GlobalVariableDialog(MainPanel mainPanel) {
        this.mainPanel = mainPanel;

        cloneColumnIndex = defaultColumnIndex;
        bundle = Amphibia.getBundle();

        defaultHadersNames = new String[]{"", bundle.getString("name"), bundle.getString("default")};

        byte[] data = userPreferences.getByteArray(Amphibia.P_GLOBAL_VARS, null);
        if (data != null) {
            try {
                GlobalVarSource gvm = (GlobalVarSource) new ObjectInputStream(new ByteArrayInputStream(data)).readObject();
                globalVarsSource.data = gvm.data;
                globalVarsSource.columns = gvm.columns;
            } catch (ClassCastException | IOException | ClassNotFoundException ex) {
                logger.log(Level.SEVERE, ex.toString(), ex);
            }
        }

        originalColumns = globalVarsSource.columns;
        originalData = globalVarsSource.data;

        globalVarsModel = new TableModel(globalVarsSource.data, globalVarsSource.columns) {
            @Override
            public boolean isCellEditable(int rowIndex, int columnIndex) {
                return columnIndex == 1;
            }
        };

        initComponents();

        final ImageIcon closeIcon = new ImageIcon(Amphibia.class.getResource("/com/equinix/amphibia/icons/close_16.png"));
        final JLabel closeLabel = new JLabel();
        closeLabel.setIcon(closeIcon);

        Font font = tblVars.getFont();
        JTableHeader header = tblVars.getTableHeader();
        header.setReorderingAllowed(false);
        header.setFont(font.deriveFont(Font.BOLD));
        tblVars.getColumnModel().getColumn(0).setMinWidth(16);
        tblVars.getColumnModel().getColumn(0).setMaxWidth(16);

        tblVars.setAutoCreateColumnsFromModel(false);
        tblVars.setColumnSelectionAllowed(true);
        tblVars.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);

        Cursor handCursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);
        Cursor defaultCursor = Cursor.getDefaultCursor();
        JPanel cell = new JPanel();
        cell.setLayout(new BorderLayout(0, 0));
        JButton button = new JButton("...");
        button.setPreferredSize(new Dimension(20, 20));
        cell.add(button, BorderLayout.EAST);
        Border border = BorderFactory.createEmptyBorder(4, 5, 2, 5);
        TableCellRenderer renderer = tblVars.getDefaultRenderer(Object.class);
        tblVars.addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int column = tblVars.columnAtPoint(e.getPoint());
                if (column > 1) {
                    int row = tblVars.columnAtPoint(e.getPoint());
                    Rectangle bounds = tblVars.getCellRect(row, column, true);
                    if (e.getX() - bounds.x > bounds.width - button.getWidth()) {
                        tblVars.setCursor(handCursor);
                        return;
                    }
                }
                tblVars.setCursor(defaultCursor);
            }
        });
        
        int[] cellInfo = new int[2];
        ResourceEditDialog reDialog = mainPanel.resourceEditDialog;
        JButton cancelButton = new JButton(bundle.getString("cancel"));
        cancelButton.addActionListener((ActionEvent evt) -> {
            reDialog.getDialog().setVisible(false);
        });
        JButton applyButton = new JButton(bundle.getString("apply"));
        applyButton.addActionListener((ActionEvent evt) -> {
            String dataType = reDialog.cmbDataType.getSelectedItem().toString();
            try {
                Object value = getValue(dataType, reDialog.txtEditor.getText().trim());
                tblVars.setValueAt(value, cellInfo[0], cellInfo[1]);
                reDialog.getDialog().setVisible(false);
            } catch (Exception ex) {
                reDialog.lblError.setText(String.format(bundle.getString("error_convert"), dataType));
                reDialog.lblError.setVisible(true);
                logger.log(Level.SEVERE, ex.toString(), ex);
            }
        });
            
        tblVars.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.getClickCount() == 2 || tblVars.getCursor() == handCursor) {
                    cellInfo[0] = tblVars.rowAtPoint(e.getPoint());
                    cellInfo[1] = tblVars.columnAtPoint(e.getPoint());
                    Object cellValue = tblVars.getValueAt(cellInfo[0], cellInfo[1]);
                    reDialog.setDataTypes(new String[] { "NULL", "String", "Boolean", "Number", "JSON" });
                    reDialog.openEditDialog(tblVars.getValueAt(cellInfo[0], 1).toString(), cellValue, true,
                            new Object[] {applyButton, cancelButton});
                    
                }
            }
        });
        tblVars.setDefaultRenderer(Object.class, (JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) -> {
            JLabel label = (JLabel) renderer.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            if (value != null) {
                label.setToolTipText(value.toString());
            }
            if (column == 0) {
                return closeLabel;
            }
            Object val = globalVarsModel.getValueAt(row, 0);
            if (val != null && column == 1) {
                label.setIcon(Converter.ENDPOINT.equals(val) ? btnAddEndPoint.getIcon() : btnAddVar.getIcon());
            } else {
                label.setIcon(null);
                cell.add(label, 1);
                return cell;
            }
            label.setBorder(border);
            return label;
        });
        tblVars.setRowHeight(20);

        TableCellRenderer headerRenderer = header.getDefaultRenderer();
        header.setDefaultRenderer((JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) -> {
            Icon icon = null;
            if (column < defaultHadersNames.length) {
                value = defaultHadersNames[column];
            } else {
                icon = closeIcon;
            }
            JLabel label = (JLabel) headerRenderer.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            label.setToolTipText(value.toString());
            label.setIcon(icon);
            return label;
        });

        tblVars.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent me) {
                int columnIndex = tblVars.columnAtPoint(me.getPoint());
                if (columnIndex == 0) {
                    int rowIndex = tblVars.rowAtPoint(me.getPoint());
                    globalVarsModel.removeRow(rowIndex);
                }
            }
        });

        header.addMouseListener(new MouseAdapter() {
            @Override
            @SuppressWarnings("UseOfObsoleteCollectionType")
            public void mouseClicked(MouseEvent evt) {
                JTable table = ((JTableHeader) evt.getSource()).getTable();
                TableColumnModel colModel = table.getColumnModel();

                int index = colModel.getColumnIndexAtX(evt.getX());
                if (index > 1) {
                    cloneColumnIndex = index;
                    table.setRowSelectionInterval(0, table.getRowCount() - 1);
                    table.setColumnSelectionInterval(index, index);
                }
                if (index < 3) {
                    return;
                }
                Rectangle headerRect = header.getHeaderRect(index);
                if (headerRect.contains(evt.getX(), evt.getY()) && evt.getX() <= (headerRect.x + closeIcon.getIconWidth())) {
                    String colName = globalVarsSource.columns[index];
                    int n = JOptionPane.showConfirmDialog(GlobalVariableDialog.this,
                            String.format(bundle.getString("tip_delete_env"), colName), bundle.getString("title"),
                            JOptionPane.YES_NO_OPTION);
                    if (n == JOptionPane.YES_OPTION) {
                        String selectedEnv = userPreferences.get(Amphibia.P_SELECTED_ENVIRONMENT, null);
                        if (colName.equals(selectedEnv)) {
                            userPreferences.put(Amphibia.P_SELECTED_ENVIRONMENT, "default");
                        }

                        tblVars.removeColumn(tblVars.getColumnModel().getColumn(index));
                        globalVarsModel.removeColumn(index);
                        globalVarsSource.columns = globalVarsModel.getColumnNames();

                        if (index == cloneColumnIndex) {
                            cloneColumnIndex = defaultColumnIndex;
                        }
                    }
                }
            }
        });

        setSize(800, 500);
        setIconImage(Amphibia.instance.icon.getImage());
        java.awt.EventQueue.invokeLater(() -> {
            setLocationRelativeTo(mainPanel);
        });
    }

    public void openDialog() {
        if (!String.join("~", originalColumns).equals(String.join("~", globalVarsSource.columns))) {
            while (tblVars.getColumnModel().getColumnCount() != defaultHadersNames.length) {
                tblVars.removeColumn(tblVars.getColumnModel().getColumn(defaultHadersNames.length));
                globalVarsModel.removeColumn(defaultHadersNames.length);
            }
            for (int c = defaultHadersNames.length; c < originalColumns.length; c++) {
                TableColumn col = new TableColumn(c);
                col.setHeaderValue(originalColumns[c]);
                tblVars.addColumn(col);
                globalVarsModel.addColumn(originalColumns[c]);
            }
            originalColumns = globalVarsModel.getColumnNames();
        }

        globalVarsSource.data = originalData;
        globalVarsSource.columns = originalColumns;

        if (globalVarsSource.data.length == 0) {
            globalVarsSource.data = new Object[][]{{0, "", "http://"}};
        }
        globalVarsModel.setDataVector(globalVarsSource.data, globalVarsSource.columns);

        setVisible(true);
    }

    @SuppressWarnings("UseOfObsoleteCollectionType")
    public void mergeVariables(JSONArray variables) {
        Map<Object, Boolean> names = new HashMap<>();
        for (int r = 0; r < globalVarsModel.getRowCount(); r++) {
            names.put(globalVarsModel.getValueAt(r, Amphibia.NAME), true);
        }

        int envColumn = Amphibia.instance.getSelectedEnvDataIndex();
        variables.forEach((item) -> {
            JSONObject vars = (JSONObject) item;
            Object type = Converter.VARIABLE;
            if ("endpoint".equals(vars.getOrDefault("type", null))) {
                type = Converter.ENDPOINT;
            }
            if (!names.containsKey(vars.getString("name"))) {
                int row = globalVarsModel.getRowCount();
                globalVarsModel.addRow(new Object[]{type, vars.getString("name"), vars.get("value")});
                globalVarsModel.setValueAt(vars.get("value"), row, envColumn);
            }
        });

        Object[][] data = new Object[globalVarsModel.getRowCount()][];
        for (int r = 0; r < globalVarsModel.getRowCount(); r++) {
            data[r] = new Object[globalVarsSource.columns.length];
            for (int c = 0; c < globalVarsSource.columns.length; c++) {
                data[r][c] = globalVarsModel.getValueAt(r, c);
            }
        }
        saveModel(data);
    }

    public static String[] getGlobalVarColumns() {
        return globalVarsSource.columns;
    }

    public static Object[][] getGlobalVarData() {
        return globalVarsSource.data;
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        pnlWrap = new JPanel();
        pnlFooter = new JPanel();
        pnlFooterLeft = new JPanel();
        btnAddEndPoint = new JButton();
        btnAddVar = new JButton();
        btnAddEnv = new JButton();
        pnlFooterRight = new JPanel();
        btnApply = new JButton();
        btnCancel = new JButton();
        spnVars = new JScrollPane();
        tblVars = new JTable();
        pnlTop = new JPanel();
        lblHint = new JLabel();

        ResourceBundle bundle = ResourceBundle.getBundle("com/equinix/amphibia/messages"); // NOI18N
        setTitle(bundle.getString("globalVars")); // NOI18N
        getContentPane().setLayout(new BoxLayout(getContentPane(), BoxLayout.LINE_AXIS));

        pnlWrap.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        pnlWrap.setLayout(new BorderLayout(0, 5));

        pnlFooter.setLayout(new BorderLayout());

        pnlFooterLeft.setLayout(new FlowLayout(FlowLayout.LEFT));

        btnAddEndPoint.setIcon(new ImageIcon(getClass().getResource("/com/equinix/amphibia/icons/endpoint_16.png"))); // NOI18N
        btnAddEndPoint.setText(bundle.getString("addEndpoint")); // NOI18N
        btnAddEndPoint.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                btnAddEndPointActionPerformed(evt);
            }
        });
        pnlFooterLeft.add(btnAddEndPoint);

        btnAddVar.setIcon(new ImageIcon(getClass().getResource("/com/equinix/amphibia/icons/variable_16.png"))); // NOI18N
        btnAddVar.setText(bundle.getString("addVariable")); // NOI18N
        btnAddVar.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                btnAddVarActionPerformed(evt);
            }
        });
        pnlFooterLeft.add(btnAddVar);

        btnAddEnv.setIcon(new ImageIcon(getClass().getResource("/com/equinix/amphibia/icons/environment_16.png"))); // NOI18N
        btnAddEnv.setText(bundle.getString("addEnvironment")); // NOI18N
        btnAddEnv.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                btnAddEnvActionPerformed(evt);
            }
        });
        pnlFooterLeft.add(btnAddEnv);

        pnlFooter.add(pnlFooterLeft, BorderLayout.WEST);

        btnApply.setText(bundle.getString("apply")); // NOI18N
        btnApply.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                btnApplyActionPerformed(evt);
            }
        });
        pnlFooterRight.add(btnApply);

        btnCancel.setText(bundle.getString("cancel")); // NOI18N
        btnCancel.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                btnCancelActionPerformed(evt);
            }
        });
        pnlFooterRight.add(btnCancel);

        pnlFooter.add(pnlFooterRight, BorderLayout.EAST);

        pnlWrap.add(pnlFooter, BorderLayout.SOUTH);

        tblVars.setModel(globalVarsModel);
        spnVars.setViewportView(tblVars);

        pnlWrap.add(spnVars, BorderLayout.CENTER);

        pnlTop.setLayout(new BorderLayout());

        lblHint.setForeground(new Color(51, 51, 255));
        lblHint.setText(bundle.getString("tip_edit_column")); // NOI18N
        pnlTop.add(lblHint, BorderLayout.EAST);

        pnlWrap.add(pnlTop, BorderLayout.NORTH);

        getContentPane().add(pnlWrap);
    }// </editor-fold>//GEN-END:initComponents

    private void btnAddEndPointActionPerformed(ActionEvent evt) {//GEN-FIRST:event_btnAddEndPointActionPerformed
        String name = Amphibia.instance.inputDialog("tip_add_var", "", new String[]{}, this);
        if (name != null && !name.isEmpty()) {
            globalVarsModel.addRow(new Object[]{Converter.ENDPOINT, name, "http://"});
        }
    }//GEN-LAST:event_btnAddEndPointActionPerformed

    private void btnAddVarActionPerformed(ActionEvent evt) {//GEN-FIRST:event_btnAddVarActionPerformed
        String name = Amphibia.instance.inputDialog("tip_add_var", "", new String[]{}, this);
        if (name != null && !name.isEmpty()) {
            globalVarsModel.addRow(new Object[]{Converter.VARIABLE, name});
        }
    }//GEN-LAST:event_btnAddVarActionPerformed

    @SuppressWarnings("UseOfObsoleteCollectionType")
    private void btnAddEnvActionPerformed(ActionEvent evt) {//GEN-FIRST:event_btnAddEnvActionPerformed
        String name = Amphibia.instance.inputDialog("tip_add_env", "", globalVarsSource.columns, this);
        if (name != null && !name.isEmpty()) {
            userPreferences.put(Amphibia.P_SELECTED_ENVIRONMENT, name);

            java.util.Vector<java.util.Vector> rows = globalVarsModel.getDataVector();
            Object[] defaultValues = new Object[rows.size()];
            for (int r = 0; r < rows.size(); r++) {
                defaultValues[r] = rows.get(r).get(cloneColumnIndex);
            }

            TableColumn col = new TableColumn(globalVarsModel.getColumnCount());
            col.setHeaderValue(name);
            tblVars.addColumn(col);
            globalVarsModel.addColumn(name, defaultValues);
            globalVarsSource.columns = globalVarsModel.getColumnNames();

            javax.swing.SwingUtilities.invokeLater(() -> {
                spnVars.getVerticalScrollBar().setValue(spnVars.getVerticalScrollBar().getMaximum());
                spnVars.getHorizontalScrollBar().setValue(spnVars.getHorizontalScrollBar().getMaximum());
            });
        }
    }//GEN-LAST:event_btnAddEnvActionPerformed

    @SuppressWarnings("UseOfObsoleteCollectionType")
    private void btnApplyActionPerformed(ActionEvent evt) {//GEN-FIRST:event_btnApplyActionPerformed

        CellEditor cellEditor = tblVars.getCellEditor();
        if (cellEditor != null) {
            cellEditor.stopCellEditing();
        }
        java.util.Vector<java.util.Vector> rows = globalVarsModel.getDataVector();
        Object[][] data = new Object[rows.size()][];
        for (int r = 0; r < rows.size(); r++) {
            java.util.Vector row = rows.get(r);
            Object[] colData = new Object[row.size()];
            for (int c = 0; c < row.size(); c++) {
                colData[c] = row.get(c);
                if (colData[c] instanceof String) {
                    if ("true".equals(colData[c]) || "false".equals(colData[c])) {
                        colData[c] = Boolean.getBoolean(colData[c].toString());
                    } else {
                        try {
                            colData[c] = numberFormat.parse(String.valueOf(row.get(c)));
                        } catch (ParseException e) {
                            try {
                                colData[c] = IO.prettyJson(colData[c].toString());
                            } catch (Exception ex) {
                            }
                        }
                    }
                }
            }
            data[r] = colData;
        }
        saveModel(data);
        Amphibia.instance.resetEnvironmentModel();
        if (MainPanel.selectedNode != null) {
            mainPanel.reloadCollection(MainPanel.selectedNode.getCollection());
        }
        dispatchEvent(new WindowEvent(this, WindowEvent.WINDOW_CLOSING));

    }//GEN-LAST:event_btnApplyActionPerformed

    private void btnCancelActionPerformed(ActionEvent evt) {//GEN-FIRST:event_btnCancelActionPerformed
        dispatchEvent(new WindowEvent(this, WindowEvent.WINDOW_CLOSING));
    }//GEN-LAST:event_btnCancelActionPerformed

    private void saveModel(Object[][] data) {
        try {
            globalVarsSource.data = data;
            globalVarsSource.columns = globalVarsModel.getColumnNames();

            originalColumns = globalVarsSource.columns;
            originalData = globalVarsSource.data;

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ObjectOutputStream os = new ObjectOutputStream(out);
            os.writeObject(globalVarsSource);
            userPreferences.putByteArray(Amphibia.P_GLOBAL_VARS, out.toByteArray());
        } catch (IOException ex) {
            mainPanel.addError(ex);
        }
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private JButton btnAddEndPoint;
    private JButton btnAddEnv;
    private JButton btnAddVar;
    private JButton btnApply;
    private JButton btnCancel;
    private JLabel lblHint;
    private JPanel pnlFooter;
    private JPanel pnlFooterLeft;
    private JPanel pnlFooterRight;
    private JPanel pnlTop;
    private JPanel pnlWrap;
    private JScrollPane spnVars;
    private JTable tblVars;
    // End of variables declaration//GEN-END:variables

    class TableModel extends DefaultTableModel {

        public TableModel(Object[][] data, Object[] columnNames) {
            super(data, columnNames);
        }

        public void removeColumn(int column) {
            columnIdentifiers.remove(column);
            fireTableStructureChanged();
        }

        public String[] getColumnNames() {
            return (String[]) columnIdentifiers.toArray(new String[columnIdentifiers.size()]);
        }
    }
}

final class GlobalVarSource implements Serializable {

    public String[] columns;
    public Object[][] data;

    public GlobalVarSource() {
        columns = new String[]{"", "", ""};
        data = new Object[][]{};
    }
}
