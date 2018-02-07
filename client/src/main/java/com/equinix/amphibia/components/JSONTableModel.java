/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.equinix.amphibia.components;

import com.equinix.amphibia.Amphibia;

import java.util.ResourceBundle;
import javax.swing.JTree;
import javax.swing.tree.TreeNode;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import net.sf.json.JSON;

/**
 *
 * @author dgofman
 */
public final class JSONTableModel extends JTreeTable.AbstractTreeTableModel {

    private final Editor.Entry rootEntry;
    private TreeIconNode node;
    private JSON json;
    private Object[][] properties;

    private final ResourceBundle bundle;
    private final String[] columns;

    private final Class[] cTypes = {JTree.class, String.class, JTreeTable.EditValueRenderer.class};

    public JSONTableModel() {
        super(null);
        throw new IllegalArgumentException();
    }

    private JSONTableModel(ResourceBundle bundle) {
        super(new Editor.Entry(null, "root"));
        this.rootEntry = (Editor.Entry) root;
        this.bundle = bundle;
        this.columns = new String[]{
            bundle.getString("name"),
            bundle.getString("value"),
            ""
        };
    }

    public JSONTableModel updateModel(TreeIconNode node, JSON json, Object[][] properties) {
        this.node = node;
        this.json = json;
        this.properties = properties;

        boolean isInherit = Amphibia.getUserPreferences().getBoolean(Amphibia.P_INHERIT_PROPERTIES, true);
        
        rootEntry.children.clear();
        if (json instanceof JSONObject) {
            for (Object[] prop : properties) {
                Object name = prop[0];
                if (name != null) {
                    Object element = ((JSONObject) json).get(name.toString());
                    if (!isInherit && "available-properties".equals(name)) {
                        continue;
                    }
                    rootEntry.add(node, json, name.toString(), element, prop[1], prop, name);
                } else if (prop.length == 2) {
                    JSONObject j = (JSONObject) json;
                    j.keySet().forEach((key) -> {
                        rootEntry.add(node, json, key.toString(), j.get(key), prop[1], prop, key.toString());
                    });
                }
            }
        } else if (json instanceof JSONArray) {
            JSONArray array = (JSONArray) json;
            for (int i = 0; i < array.size(); i++) {
                Object value = array.get(i);
                Editor.Entry entry = new Editor.Entry(node, value instanceof String ? value.toString() : " ");
                entry.setType(null);
                rootEntry.children.add(entry);
                for (Object[] prop : properties) {
                    Object name = prop[0];
                    if (name != null) {
                        Object element = array.getJSONObject(i).get(name.toString());
                        entry.add(node, json, name.toString(), element, prop[1], prop, name);
                    } else {
                        entry.add(node, json, String.valueOf(i), array.getJSONObject(i), prop[1], prop, name);
                    }
                }
            }
        }
        return this;
    }

    public static JSONTableModel cloneModel(JSONTableModel model) {
        JSONTableModel clone = createModel(model.bundle);
        return clone.updateModel(model.node, model.json, model.properties);
    }

    public static JSONTableModel createModel(ResourceBundle bundle) {
        return new JSONTableModel(bundle);
    }

    public JSON getJSON() {
        return json;
    }

    @Override
    public int getColumnCount() {
        return columns.length;
    }

    @Override
    public String getColumnName(int column) {
        return columns[column];
    }

    @Override
    public Class getColumnClass(int column) {
        return cTypes[column];
    }

    @Override
    public Object getValueAt(Object node, int column) {
        Editor.Entry entry = (Editor.Entry) node;
        if (entry != null) {
            switch (column) {
                case 0:
                    return entry;
                case 1:
                    return entry.isLeaf ? entry.value : null;
                case 2:
                    return entry.getType();
            }
        }
        return null;
    }

    @Override
    public void setValueAt(Object aValue, Object node, int column) {
    }

    @Override
    public Object getChild(Object node, int index) {
        return ((TreeNode) node).getChildAt(index);
    }

    @Override
    public int getChildCount(Object node) {
        if (!((TreeNode) node).isLeaf()) {
            return ((TreeNode) node).getChildCount();
        }
        return 0;
    }
}