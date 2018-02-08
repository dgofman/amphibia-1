package com.equinix.amphibia.components;

import java.awt.Cursor;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.image.BufferedImage;
import java.util.EventObject;
import javax.swing.JTable;
import javax.swing.JTree;
import javax.swing.ListSelectionModel;
import javax.swing.LookAndFeel;
import javax.swing.SwingUtilities;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeSelectionModel;
import javax.swing.CellEditor;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.UIDefaults;
import javax.swing.event.CellEditorListener;
import javax.swing.event.ChangeEvent;
import javax.swing.event.EventListenerList;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.table.TableColumnModel;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;

@SuppressWarnings("NonPublicExported")
public final class JTreeTable extends JTable {

    public static final int EDIT_DELETE = 0;
    public static final int EDIT_RESET = 1;
    public static final int EDIT_ONLY = 2;
    public static final int READ_ONLY = 3;
    
    private ImageIcon lock_icon = new ImageIcon(getClass().getResource("/com/equinix/amphibia/icons/lock.png"));
    private ImageIcon unlock_icon = new ImageIcon(getClass().getResource("/com/equinix/amphibia/icons/unlock.png"));


    /**
     * A subclass of JTree.
     */
    private final TreeTableCellRenderer tree;
    private final JScrollPane scrollPane;

    public JTreeTable(AbstractTreeTableModel treeTableModel, RowEventListener listener) {
        super();
        
        scrollPane = new JScrollPane(this);

        // Create the tree. It will be used as a renderer and editor.
        tree = new TreeTableCellRenderer();

        // Force the JTable and JTree to share their row selection models.
        ListToTreeSelectionModelWrapper selectionWrapper = new ListToTreeSelectionModelWrapper();
        tree.setSelectionModel(selectionWrapper);
        setSelectionModel(selectionWrapper.getListSelectionModel());

        // Install the tree editor renderer and editor.
        setDefaultRenderer(JTree.class, tree);
        setDefaultEditor(JTree.class, new TreeTableCellEditor());

        // No grid.
        setShowGrid(false);

        // No intercell spacing
        setIntercellSpacing(new Dimension(0, 0));

        // And update the height of the trees row to match that of
        // the table.
        if (tree.getRowHeight() < 1) {
            // Metal looks better like this.
            setRowHeight(18);
        }
        this.setModel(treeTableModel);

        defaultRenderersByColumnClass.put(EditValueRenderer.class, (UIDefaults.LazyValue) t -> new EditValueRenderer(this, listener));
    }
    
    public JScrollPane getScrollPane() {
        return scrollPane;
    }

    public void setModel(AbstractTreeTableModel treeTableModel) {
        final TableColumnModel colModel = getColumnModel();
        int[] widths = new int[colModel.getColumnCount()];
        for (int i = 0; i < colModel.getColumnCount(); i++) {
            widths[i] = colModel.getColumn(i).getPreferredWidth();
        }
        int value = scrollPane.getVerticalScrollBar().getValue();
        tree.setModel(treeTableModel);
        super.setModel(new TreeTableModelAdapter(treeTableModel, tree));
        for (int i = 0; i < widths.length; i++) {
            colModel.getColumn(i).setPreferredWidth(widths[i]);
        }
        for (int i = 0; i < tree.getRowCount(); i++) {
            tree.expandRow(i);
        }
        java.awt.EventQueue.invokeLater(() -> {
            scrollPane.getVerticalScrollBar().setValue(value);
        });
    }

    @Override
    public void updateUI() {
        super.updateUI();
        if (tree != null) {
            tree.updateUI();
        }
        // Use the tree's default foreground and background colors in the
        // table.
        LookAndFeel.installColorsAndFont(this, "Tree.background",
                "Tree.foreground", "Tree.font");
    }

    @Override
    public int getEditingRow() {
        return (getColumnClass(editingColumn) == JTree.class) ? -1
                : editingRow;
    }

    /**
     * Overridden to pass the new rowHeight to the tree.
     */
    @Override
    public void setRowHeight(int rowHeight) {
        super.setRowHeight(rowHeight);
        if (tree != null && tree.getRowHeight() != rowHeight) {
            tree.setRowHeight(getRowHeight());
        }
    }

    /**
     * Returns the tree that is being shared between the model.
     *
     * @return
     */
    public JTree getTree() {
        return tree;
    }
    
    public static boolean isNotLeaf(Object value) {
        return (value == EditValueRenderer.TYPE.ADD || 
                value == EditValueRenderer.TYPE.ADD_RESOURCES || 
                value == EditValueRenderer.TYPE.TRANSFER);
    }

    static interface RowEventListener {

        void fireEvent(JTreeTable table, int row, int column, Object value);
    }

    static final class EditValueRenderer extends AbstractCellEditor implements TableCellRenderer, MouseListener {

        public static enum TYPE {
            EDIT,
            EDIT_LIMIT,
            REFERENCE,
            REFERENCE_EDIT,
            TRANSFER,
            ADD,
            ADD_RESOURCES,
            VIEW
        }

        private JTreeTable table;
        private RowEventListener listener;
        private JButton editButton;
        private JLabel label;

        private int columnIndex;
        private int rowIndex;
        private Object currentValue;

        public EditValueRenderer(JTreeTable table, RowEventListener listener) {
            super();
            this.table = table;
            this.listener = listener;
            editButton = new JButton();
            editButton.setText("...");
            editButton.setOpaque(false);
            table.addMouseListener(this);

            label = new JLabel();
            label.setIcon(new ImageIcon(getClass().getResource("/com/equinix/amphibia/icons/plus-icon.png")));

            final Cursor handCursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);
            final Cursor defaultCursor = Cursor.getDefaultCursor();
            table.addMouseMotionListener(new MouseAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    int point = table.columnAtPoint(e.getPoint());
                    int view = table.convertColumnIndexToView(point);
                    if (table.getColumnClass(view) == EditValueRenderer.class) {
                        table.setCursor(handCursor);
                    } else {
                        table.setCursor(defaultCursor);
                    }
                }
            });
        }

        private void repaint() {
            ((TreeTableModelAdapter) table.getModel()).fireTableCellUpdated(table.getSelectedRow(), table.getSelectedColumn());
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            boolean isCurrent = (row == rowIndex && column == columnIndex);
            if (isCurrent) {
                currentValue = value;
            }
            if (isNotLeaf(value)) {
                return label;
            } else if (value != null) {
                editButton.getModel().setPressed(isCurrent);
                return editButton;
            }
            return null;
        }

        @Override
        public void mousePressed(MouseEvent e) {
            rowIndex = table.rowAtPoint(e.getPoint());
            columnIndex = table.columnAtPoint(e.getPoint());
            repaint();
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            int row = table.rowAtPoint(e.getPoint());
            int col = table.columnAtPoint(e.getPoint());
            if (currentValue != null && currentValue == table.getValueAt(row, col)) {
                listener.fireEvent(table, row, col, currentValue);
                currentValue = null;
                rowIndex = -1;
                columnIndex = -1;
            }
            repaint();
        }

        @Override
        public void mouseClicked(MouseEvent e) {
            mouseReleased(e);
        }

        @Override
        public void mouseEntered(MouseEvent e) {
        }

        @Override
        public void mouseExited(MouseEvent e) {
        }
    }

    static class AbstractCellEditor implements CellEditor {

        protected EventListenerList listenerList = new EventListenerList();

        @Override
        public Object getCellEditorValue() {
            return null;
        }

        @Override
        public boolean isCellEditable(EventObject e) {
            return true;
        }

        @Override
        public boolean shouldSelectCell(EventObject anEvent) {
            return false;
        }

        @Override
        public boolean stopCellEditing() {
            return true;
        }

        @Override
        public void cancelCellEditing() {
        }

        @Override
        public void addCellEditorListener(CellEditorListener l) {
            listenerList.add(CellEditorListener.class, l);
        }

        @Override
        public void removeCellEditorListener(CellEditorListener l) {
            listenerList.remove(CellEditorListener.class, l);
        }

        /*
     * Notify all listeners that have registered interest for
     * notification on this event type.
     * @see EventListenerList
         */
        protected void fireEditingStopped() {
            // Guaranteed to return a non-null array
            Object[] listeners = listenerList.getListenerList();
            // Process the listeners last to first, notifying
            // those that are interested in this event
            for (int i = listeners.length - 2; i >= 0; i -= 2) {
                if (listeners[i] == CellEditorListener.class) {
                    ((CellEditorListener) listeners[i + 1]).editingStopped(new ChangeEvent(this));
                }
            }
        }

        /*
     * Notify all listeners that have registered interest for
     * notification on this event type.
     * @see EventListenerList
         */
        protected void fireEditingCanceled() {
            // Guaranteed to return a non-null array
            Object[] listeners = listenerList.getListenerList();
            // Process the listeners last to first, notifying
            // those that are interested in this event
            for (int i = listeners.length - 2; i >= 0; i -= 2) {
                if (listeners[i] == CellEditorListener.class) {
                    ((CellEditorListener) listeners[i + 1]).editingCanceled(new ChangeEvent(this));
                }
            }
        }
    }

    static abstract class AbstractTreeTableModel extends DefaultTreeModel {

        public AbstractTreeTableModel(TreeNode root) {
            super(root);
        }

        public abstract int getColumnCount();

        public abstract String getColumnName(int column);

        public abstract Class getColumnClass(int column);

        public abstract void setValueAt(Object aValue, Object node, int column);

        public abstract Object getValueAt(Object node, int column);

        public boolean isCellEditable(Object node, int column) {
            return getColumnClass(column) == JTree.class;
        }
    }

    /**
     * A TreeCellRenderer that displays a JTree.
     */
    final class TreeTableCellRenderer extends JTree implements TableCellRenderer {

        /**
         * Last table/tree row asked to renderer.
         */
        protected int visibleRow;

        public TreeTableCellRenderer() {
            super();
            ImageIcon dummy_icon = new ImageIcon(new BufferedImage(lock_icon.getIconWidth(), lock_icon.getIconHeight(), BufferedImage.TYPE_INT_ARGB));
            this.setCellRenderer(new DefaultTreeCellRenderer() {
                @Override
                public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean expanded, boolean isLeaf, int row, boolean hasFocus) {
                    Component c = super.getTreeCellRendererComponent(tree, value, selected, expanded, isLeaf, row, hasFocus);
                    int editMode = ((Editor.Entry) value).editMode;
                    if (editMode == READ_ONLY) {
                        setIcon(lock_icon);
                    } else if (editMode == EDIT_ONLY) {
                        setIcon(unlock_icon);
                    }else {
                        setIcon(isLeaf ? dummy_icon : null);
                    }
                    return c;
                }
            });
        }

        /**
         * Sets the row height of the tree, and forwards the row height to the
         * table.
         */
        @Override
        public void setRowHeight(int rowHeight) {
            if (rowHeight > 0) {
                super.setRowHeight(rowHeight);
                if (JTreeTable.this != null
                        && JTreeTable.this.getRowHeight() != rowHeight) {
                    JTreeTable.this.setRowHeight(getRowHeight());
                }
            }
        }

        /**
         * This is overridden to set the height to match that of the JTable.
         *
         * @param w
         * @param h
         */
        @Override
        public void setBounds(int x, int y, int w, int h) {
            super.setBounds(x, 0, w, JTreeTable.this.getHeight());
        }

        /**
         * Sublcassed to translate the graphics such that the last visible row
         * will be drawn at 0,0.
         */
        @Override
        public void paint(Graphics g) {
            g.translate(0, -visibleRow * getRowHeight());
            super.paint(g);
        }

        /**
         * TreeCellRenderer method. Overridden to update the visible row.
         *
         * @return
         */
        @Override
        public Component getTableCellRendererComponent(JTable table,
                Object value,
                boolean isSelected,
                boolean hasFocus,
                int row, int column) {
            if (isSelected) {
                setBackground(table.getSelectionBackground());
            } else {
                setBackground(table.getBackground());
            }

            visibleRow = row;
            return this;
        }
    }

    /**
     * TreeTableCellEditor implementation. Component returned is the JTree.
     */
    class TreeTableCellEditor extends AbstractCellEditor implements
            TableCellEditor {

        @Override
        public Component getTableCellEditorComponent(JTable table,
                Object value,
                boolean isSelected,
                int r, int c) {
            return tree;
        }

        @Override
        public boolean isCellEditable(EventObject e) {
            if (e instanceof MouseEvent) {
                for (int counter = getColumnCount() - 1; counter >= 0;
                        counter--) {
                    if (getColumnClass(counter) == JTree.class) {
                        MouseEvent me = (MouseEvent) e;
                        MouseEvent newME = new MouseEvent(tree, me.getID(),
                                me.getWhen(), me.getModifiers(),
                                me.getX() - getCellRect(0, counter, true).x,
                                me.getY(), me.getClickCount(),
                                me.isPopupTrigger());
                        tree.dispatchEvent(newME);
                        break;
                    }
                }
            }
            return false;
        }

        @Override
        public Object getCellEditorValue() {
            // TODO Auto-generated method stub
            return null;
        }
    }

    /**
     * ListToTreeSelectionModelWrapper extends DefaultTreeSelectionModel to
     * listen for changes in the ListSelectionModel it maintains. Once a change
     * in the ListSelectionModel happens, the paths are updated in the
     * DefaultTreeSelectionModel.
     */
    final class ListToTreeSelectionModelWrapper extends DefaultTreeSelectionModel {

        /**
         * Set to true when we are updating the ListSelectionModel.
         */
        protected boolean updatingListSelectionModel;

        public ListToTreeSelectionModelWrapper() {
            super();
            getListSelectionModel().addListSelectionListener(createListSelectionListener());
        }

        /**
         * Returns the list selection model. ListToTreeSelectionModelWrapper
         * listens for changes to this model and updates the selected paths
         * accordingly.
         */
        ListSelectionModel getListSelectionModel() {
            return listSelectionModel;
        }

        /**
         * This is overridden to set <code>updatingListSelectionModel</code> and
         * message super. This is the only place DefaultTreeSelectionModel
         * alters the ListSelectionModel.
         */
        @Override
        public void resetRowSelection() {
            if (!updatingListSelectionModel) {
                updatingListSelectionModel = true;
                try {
                    super.resetRowSelection();
                } finally {
                    updatingListSelectionModel = false;
                }
            }
        }

        /**
         * Creates and returns an instance of ListSelectionHandler.
         */
        protected ListSelectionListener createListSelectionListener() {
            return new ListSelectionHandler();
        }

        /**
         * If <code>updatingListSelectionModel</code> is false, this will reset
         * the selected paths from the selected rows in the list selection
         * model.
         */
        protected void updateSelectedPathsFromSelectedRows() {
            if (!updatingListSelectionModel) {
                updatingListSelectionModel = true;
                try {
                    // This is way expensive, ListSelectionModel needs an
                    // enumerator for iterating.
                    int min = listSelectionModel.getMinSelectionIndex();
                    int max = listSelectionModel.getMaxSelectionIndex();

                    clearSelection();
                    if (min != -1 && max != -1) {
                        for (int counter = min; counter <= max; counter++) {
                            if (listSelectionModel.isSelectedIndex(counter)) {
                                TreePath selPath = tree.getPathForRow(counter);

                                if (selPath != null) {
                                    addSelectionPath(selPath);
                                }
                            }
                        }
                    }
                } finally {
                    updatingListSelectionModel = false;
                }
            }
        }

        /**
         * Class responsible for calling updateSelectedPathsFromSelectedRows
         * when the selection of the list changse.
         */
        class ListSelectionHandler implements ListSelectionListener {

            @Override
            public void valueChanged(ListSelectionEvent e) {
                updateSelectedPathsFromSelectedRows();
            }
        }
    }
}

class TreeTableModelAdapter extends AbstractTableModel {

    JTree tree;
    JTreeTable.AbstractTreeTableModel treeTableModel;

    public TreeTableModelAdapter(JTreeTable.AbstractTreeTableModel treeTableModel, JTree tree) {
        this.tree = tree;
        this.treeTableModel = treeTableModel;

        tree.addTreeExpansionListener(new TreeExpansionListener() {
            // Don't use fireTableRowsInserted() here; the selection model
            // would get updated twice.
            @Override
            public void treeExpanded(TreeExpansionEvent event) {
                fireTableDataChanged();
            }

            @Override
            public void treeCollapsed(TreeExpansionEvent event) {
                fireTableDataChanged();
            }
        });

        treeTableModel.addTreeModelListener(new TreeModelListener() {
            @Override
            public void treeNodesChanged(TreeModelEvent e) {
                delayedFireTableDataChanged();
            }

            @Override
            public void treeNodesInserted(TreeModelEvent e) {
                delayedFireTableDataChanged();
            }

            @Override
            public void treeNodesRemoved(TreeModelEvent e) {
                delayedFireTableDataChanged();
            }

            @Override
            public void treeStructureChanged(TreeModelEvent e) {
                delayedFireTableDataChanged();
            }
        });
    }

    // Wrappers, implementing TableModel interface.
    @Override
    public int getColumnCount() {
        return treeTableModel.getColumnCount();
    }

    @Override
    public String getColumnName(int column) {
        return treeTableModel.getColumnName(column);
    }

    @Override
    public Class getColumnClass(int column) {
        return treeTableModel.getColumnClass(column);
    }

    @Override
    public int getRowCount() {
        return tree.getRowCount();
    }

    protected Object nodeForRow(int row) {
        TreePath treePath = tree.getPathForRow(row);
        return treePath != null ? treePath.getLastPathComponent() : null;
    }

    @Override
    public Object getValueAt(int row, int column) {
        return treeTableModel.getValueAt(nodeForRow(row), column);
    }

    @Override
    public boolean isCellEditable(int row, int column) {
        return treeTableModel.isCellEditable(nodeForRow(row), column);
    }

    @Override
    public void setValueAt(Object value, int row, int column) {
        treeTableModel.setValueAt(value, nodeForRow(row), column);
    }

    /**
     * Invokes fireTableDataChanged after all the pending events have been
     * processed. SwingUtilities.invokeLater is used to handle this.
     */
    protected void delayedFireTableDataChanged() {
        SwingUtilities.invokeLater(this::fireTableDataChanged);
    }
}
