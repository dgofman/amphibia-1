package com.equinix.amphibia.agent.builder;

import java.text.NumberFormat;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.sf.json.JSONArray;
import net.sf.json.JSONNull;
import net.sf.json.JSONObject;

/**
 *
 * @author dgofman
 */
public class Properties {

    private JSONObject globals;
    private JSONObject project;
    private JSONObject testsuite;
    private JSONObject testcase;
    private JSONObject teststep;

    private static final NumberFormat NUMBER = NumberFormat.getInstance();
    private static final Logger LOGGER = Logger.getLogger(Properties.class.getName());

    public Properties(JSONArray globals, JSONObject project) {
        this.globals = new JSONObject();
        this.project = project;
        globals.forEach((item) -> {
            JSONObject props = (JSONObject) item;
            this.globals.put(props.getString("name"), props.get("value"));
        });
    }

    public Properties(JSONObject globals, JSONObject project) {
        this.globals = globals;
        this.project = project;
    }

    public Properties setTestSuite(JSONObject testsuite) {
        if (this.testsuite == null) {
            this.testsuite = new JSONObject();
        }
        JSONObject.fromObject(testsuite).keySet().forEach((key) -> {
            this.testsuite.put(key, replace(testsuite.get(key), key));
        });
        return this;
    }
    
    public Properties setTestCase(JSONObject testcase) {
        if (this.testcase == null) {
            this.testcase = new JSONObject();
        }
        JSONObject.fromObject(testcase).keySet().forEach((key) -> {
            this.testcase.put(key, replace(testcase.get(key), key));
        });
        return this;
    }

    public Properties setTestStep(JSONObject teststep) {
        if (this.teststep == null) {
            this.teststep = new JSONObject();
        }
        JSONObject.fromObject(teststep).keySet().forEach((key) -> {
            this.teststep.put(key, replace(teststep.get(key), key));
        });
        return this;
    }

    public String replace(Object replace) {
        return String.valueOf(replace(replace, null));
    }

    public Object replace(Object replace, Object propKey) {
        if (replace == null || replace == JSONNull.getInstance()) {
            return JSONNull.getInstance();
        }
        String value = String.valueOf(replace);
        if (replace instanceof String) {
            StringBuilder sb = new StringBuilder(value);
            Matcher m = Pattern.compile("\\$\\{#(.*?)#(.*?)\\}", Pattern.DOTALL | Pattern.MULTILINE).matcher(value);
            while (m.find()) {
                JSONObject source = getProperty(m.group(1));
                if (source == null) {
                } else {
                    String key = m.group(2);
                    int offset = value.length() - sb.length();
                    if (!source.containsKey(key)) {
                        sb.replace(m.start(0) - offset, m.end(2) - offset + 1, "`${#" + m.group(2) + "}`");
                        LOGGER.log(Level.WARNING, "Value is undefined: {0}", m.group());
                    } else if (propKey != null) {
                        return source.get(key);
                    } else {
                        replace(sb, m.start(0) - offset, m.end(2) - offset + 1, source.get(key));
                    }
                }
            }
            //Replace by hierarchy
            m = Pattern.compile("\\$\\{#(.*?)\\}", Pattern.DOTALL | Pattern.MULTILINE).matcher(value);
            while (m.find()) {
                String key = m.group(1);
                if (key.contains("#")) {
                    continue;
                }
                Object propValue;
                if (teststep != null && teststep.containsKey(key)) {
                    propValue = teststep.get(key);
                } else if (testcase != null && testcase.containsKey(key)) {
                    propValue = testcase.get(key);
                } else if (testsuite != null && testsuite.containsKey(key)) {
                    propValue = testsuite.get(key);
                } else if (project != null && project.containsKey(key)) {
                    propValue = project.get(key);
                } else if (globals != null && globals.containsKey(key)) {
                    propValue = globals.get(key);
                } else {
                    if (propKey != null) {
                        continue;
                    }
                    propValue = null;
                }
                int offset = value.length() - sb.length();
                replace(sb, m.start(0) - offset, m.end(1) - offset + 1, propValue);
            }
            return sb.toString();
        }
        return replace;
    }
    
    public void replace(StringBuilder sb, int begin, int end, Object val) {
        if (begin > 0 && end < sb.length() - 2) {
            char[] dst = new char[2];
            sb.getChars(begin - 1, begin, dst, 0);
            sb.getChars(end, end + 1, dst, 1);
            if (dst[0] == '`' && dst[1] == '`') {
                if (val instanceof String) {
                    begin = begin - 1;
                    end = end + 1;
                } else {
                    begin = begin - 2;
                    end = end + 2;
                }
            }
        }
        sb.replace(begin, end, String.valueOf(val));
    }

    public JSONObject getProperty(String name) {
        if ("Global".equals(name)) {
            return globals;
        }
        if ("Project".equals(name)) {
            return project;
        }
        if ("TestSuite".equals(name)) {
            return testsuite;
        }
        if ("TestCase".equals(name)) {
            return testcase;
        }
        if ("TestStep".equals(name)) {
            return teststep;
        }
        return null;
    }

    public Properties cloneProperties() {
        Properties clone = new Properties(globals, project);
        clone.testsuite = testsuite;
        clone.testcase = testcase;
        clone.teststep = teststep;
        return clone;
    }
}
