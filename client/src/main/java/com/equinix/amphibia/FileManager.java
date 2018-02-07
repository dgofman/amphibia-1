/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.equinix.amphibia;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import net.sf.json.JSON;

/**
 *
 * @author dgofman
 */
public final class FileManager {
    
    private static final Map<String, Record> records = new HashMap<>();
    
    @SuppressWarnings("NonPublicExported")
    public static Record getRecord(File file) {
        Record record = records.get(file.getAbsolutePath());
        if (record == null || record.lastModified != file.lastModified()) {
            return null;
        }
        return record;
    }
    
    public static String getContent(File file) {
        Record record = getRecord(file);
        return record != null ? record.content : null;
    }
    
    public static void addContent(File file, String content) {
        records.put(file.getAbsolutePath(),  new Record(file, content));
    }
    
    public static void deleteContent(File file) {
        records.remove(file.getAbsolutePath());
    }
    
    static class Record {
        public File file;
        public String content;
        public JSON json;
        public long lastModified;

        public Record(File file, String content) {
            this.file = file;
            this.content = content;
            this.lastModified = file.lastModified();
        }
        
        public JSON toJSON() throws Exception {
            if (this.json == null) {
                this.json = IO.toJSON(content);
            }
            return this.json;
        }
    }
}
