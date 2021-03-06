/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.equinix.amphibia;

import com.equinix.amphibia.components.BaseTaskPane;
import com.equinix.amphibia.components.MainPanel;
import com.equinix.amphibia.components.TreeCollection;
import com.equinix.amphibia.components.TreeIconNode;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URL;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import net.sf.json.JSON;
import net.sf.json.JSONArray;
import net.sf.json.JSONNull;
import net.sf.json.JSONObject;
import org.apache.commons.collections.map.ListOrderedMap;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;

/**
 *
 * @author dgofman
 */
public class IO {
    
    public static final JSONNull NULL = JSONNull.getInstance();
    private static final Logger logger = Amphibia.getLogger(IO.class.getName());
    
    public static String join(Collection collection) {
        return (String) collection.stream().map(Object::toString).collect(Collectors.joining(","));
    }
    
    public static JSONArray toJSONArray(Object obj) {
        return isEmpty(obj) ? null : JSONArray.fromObject(obj);
    }
    
    public static JSONObject toJSONObject(Object obj) {
        return isEmpty(obj) ? null : JSONObject.fromObject(obj);
    }
    
    public static boolean isEmpty(Object value) {
        return (isNULL(value) || value.toString().trim().isEmpty());
    }

    public static boolean isNULL(Object value) {
        if (value == null || value instanceof JSONNull || value == NULL) {
            return true;
        } else if (value instanceof JSONObject) {
            return ((JSONObject) value).isNullObject();
        }
        return false;
    }
    
    public static Object isNULL(Object value, Object defaltValue) {
        return isNULL(value) ? defaltValue : value;
    }

    public static JSON toJSON(String json) throws Exception {
        if (json.startsWith("[")) {
            return IO.toJSONArray(json);
        } else {
            return IO.toJSONObject(json);
        }
    }

    public static JSON getJSON(String path) throws Exception {
        return getJSON(IO.getFile(path));
    }
    
    public static JSON getJSON(File file) throws Exception {
        JSON json = null;
        try {
            FileManager.Record record = FileManager.getRecord(file);
            if (record != null) {
                return record.toJSON();
            }
            json = toJSON(readFile(file));
        } catch (Exception ex) {
            throw new Exception("File: " + file.getAbsolutePath() + "\n" + ex.toString(), ex);
        }
        return json;
    }
    
    public static JSON getJSON(InputStream is) throws Exception {
        return toJSON(readInputStream(is));
    }
    
    public static JSON getJSON(URI uri) throws Exception {
        try (InputStream is = uri.toURL().openStream()) {
            return getJSON(is);
        }
    }
    
    public static File getBackupFile(File file) {
        return IO.newFile(file.getParentFile(), FilenameUtils.getBaseName(file.getName()) + ".bak");
    }
    
    public static File getBackupOrFile(File file) {
        File backup = getBackupFile(file);
        if (backup.exists()) {
            return backup;
        }
        return file;
    }

    public static String prettyJson(String value) throws Exception {
        try {
            if (value == null || value.isEmpty()) {
                return "";
            }
            ScriptEngine scriptEngine = new ScriptEngineManager().getEngineByName("JavaScript");
            scriptEngine.put("jsonString", value);
            scriptEngine.eval("result = JSON.stringify(JSON.parse(jsonString), null, 4)");
            String json = ((String) scriptEngine.get("result")).replaceAll(" {4}", "\t");
            return json == null || "null".equals(json) ? "" : json;
        } catch (ScriptException e) {
            throw new Exception("JSON: " + value, e);
        }
    }
    
    public static URI getResources(String path) {
        try {
            return getFile(path, "../resources", "resources/");
        } catch (Exception ex) {
            logger.log(Level.SEVERE, path, ex);
        }
        return null;
    }
    
    public static URI getFile(String path, String fileDir, String resourceDir) throws Exception {
        URL url = IO.class.getClassLoader().getResource(resourceDir + path);
        if (url != null) {
            return url.toURI();
        } else {
            File file = IO.newFile(fileDir, path);
            if (!file.exists()) {
                throw new FileNotFoundException("File path: " + file.getAbsolutePath());
            }
            return file.toURI();
        }
    }

    public static String readFile(File file, BaseTaskPane pane) {
        try {
            return readFile(file);
        } catch (IOException ex) {
            pane.addError(ex);
            return null;
        }
    }
    
    public static String readFile(URI uri) throws IOException {
        try (InputStream is = uri.toURL().openStream()) {
            return readInputStream(is);
        }
    }

    public static String readFile(File file) throws IOException {
        String content = FileManager.getContent(file);
        if (content == null) {
            content = readInputStream(new FileInputStream(file));
            FileManager.addContent(file, content);
        }
        return content;
    }
    
    public static String readInputStream(InputStream is) throws IOException {
        String str;
        try {
            str = IOUtils.toString(is);
        } catch (IOException ex) {
            throw ex;
        } finally {
            is.close();
        }
        return str;
    }
    
    public static String readFile(String filePath) throws IOException {
        return readFile(getFile(filePath));
    }
    
    public static String readFile(String filePath, BaseTaskPane pane) {
        return readFile(getFile(filePath), pane);
    }

    public static JSON getJSON(String filePath, BaseTaskPane pane) {
        return getJSON(getFile(filePath), pane);
    }

    public static JSON getJSON(File file, BaseTaskPane pane) {
        try {
            return getJSON(file);
        } catch (Exception ex) {
            pane.addError(file, ex);
            return null;
        }
    }
    
    public static JSON getJSON(URI uri, BaseTaskPane pane) {
        InputStream is = null;
        try {
            is = uri.toURL().openStream();
            return getJSON(is);
        } catch (Exception ex) {
            pane.addError(ex, uri.toString());
            return null;
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException ex) {
                    pane.addError(ex, uri.toString());
                }
            }
        }
    }

    public static File getFile(TreeCollection collection, String path) {
        return IO.newFile(collection.getProjectDir(), path);
    }
    
    public static File getFile(String path) {
        File file = newFile(System.getProperty("user.dir"), path);
        if (file != null && file.exists()) {
            return file;
        } else if (MainPanel.selectedNode != null) {
            file = getFile(MainPanel.selectedNode.getCollection(), path);
            if (file != null && file.exists()) {
                return file;
            }
        }
        return IO.newFile(path);
    }
    
    public static File newFile(String path) {
        return path == null ? null : newFile(new File(path));
    }
    
    public static File newFile(String parent, String child) {
        return (parent == null || child == null) ? null : newFile(new File(parent, child));
    }
    
    public static File newFile(File parent, String child) {
        return (parent == null || child == null) ? null : newFile(new File(parent, child));
    }
    
    public static File newFile(File file) {
        return file;
    }

    public static String[] write(TreeIconNode node, BaseTaskPane pane) {
        try {
            return write(node);
        } catch (Exception ex) {
            pane.addError(ex);
        }
        return new String[] {};
    }

    public static String[] write(TreeIconNode node) throws Exception {
        if (node instanceof TreeIconNode.ProfileNode) {
            TreeCollection collection = node.getCollection();
            return write(collection.getProjectProfile().toString(), collection.getBackupProfile(), true);
        }
        TreeIconNode.TreeIconUserObject userObject = node.getTreeIconUserObject();
        File file = IO.newFile(userObject.getFullPath());
        return write(userObject.json.toString(), file, true);
    }
    
    public static String[] write(String content, File file, boolean isJSON) throws Exception {
        if (isJSON) {
            content = prettyJson(content);
        }

        if (!file.exists()) {
            file.createNewFile();
        }
        String oldContent = readFile(file);
        write(content, file);
        return new String[] { oldContent, content };
    }
    
    public static void write(String content, File file) throws IOException {
        FileManager.deleteContent(file);
        try (OutputStream os = new FileOutputStream(file)) {
            IOUtils.write(content, os);
        }
    }

    public static void copy(File source, File target) throws IOException {
        FileManager.deleteContent(target);
        InputStream is = new FileInputStream(source);
        OutputStream os = new FileOutputStream(target);
        try {
            copy(is, os);
        } finally {
            is.close();
            os.close(); 
        }
    }

    public static void copy(URI source, File target) throws IOException {
        FileManager.deleteContent(target);
        InputStream is = source.toURL().openStream();
        OutputStream os = new FileOutputStream(target);
        try {
            copy(is, os);
        } finally {
            is.close();
            os.close(); 
        }
    }
    
    public static void copy(InputStream source, OutputStream target) throws IOException {
        IOUtils.copy(source, target);
    }
    
    public static void copyDir(File source, File target) throws IOException {
        FileUtils.copyDirectory(source, target);
    }
    
    public static void replaceValue(JSONObject source, String key, Object value) {
        Set<ListOrderedMap.Entry> entries = source.entrySet();
        entries.forEach((Map.Entry entry) -> {
            ListOrderedMap.Entry mapEntry = (ListOrderedMap.Entry) entry;
            if (key.equals(entry.getKey())) {
                entry.setValue(value);
            }
        });
    }
    
    public static void replaceValues(JSONObject source, JSONObject target) {
        Set<ListOrderedMap.Entry> entries = source.entrySet();
        entries.forEach((Map.Entry entry) -> {
            target.put(entry.getKey(), entry.getValue());
        });
    }
}
