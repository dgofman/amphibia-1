package com.equinix.amphibia.agent.builder;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;

import net.sf.json.JSONArray;
import net.sf.json.JSONNull;
import net.sf.json.JSONObject;

/**
 *
 * @author dgofman
 */
@SuppressWarnings("unchecked")
public class Properties {

    private JSONObject globals;
    private JSONObject project;
    private JSONObject testsuite;
    private JSONObject testcase;
    private JSONObject teststep;

    public static final String GLOBAL = "Global";
    public static final String PROJECT = "Project";
    public static final String TESTSUITE = "TestSuite";
    public static final String TESTCASE = "TestCase";
    public static final String TESTSTEP = "TestStep";

    public static final String TESTS_FILE_FORMAT = "data/%s/tests/%s/%s.json";
    public static final Pattern PATTERN_1 = Pattern.compile("\\$\\{#(.*?)#(.*?)\\}", Pattern.DOTALL | Pattern.MULTILINE);
    public static final Pattern PATTERN_2 = Pattern.compile("\\$\\{#(.*?)\\}", Pattern.DOTALL | Pattern.MULTILINE);

    public static final String[] PROPERTY_NAMES = new String[]{GLOBAL, PROJECT, TESTSUITE, TESTCASE, TESTSTEP};

    private static final Logger LOGGER = ProjectAbstract.getLogger(Properties.class.getName());

    public static final String PROPERTY_WARNING = "propertyWarning";
    public static final List<PropertyChangeListener> eventListeners = new ArrayList<>();

    private static final Set<String> warnings = new HashSet<>();
    private static Object NULL = new Object();

    public Properties(JSONArray globals, JSONObject project) {
        this(new JSONObject(), new JSONObject());
        globals.forEach((item) -> {
            JSONObject props = (JSONObject) item;
            this.globals.put(props.getString("name"), props.get("value"));
        });
        project.keySet().forEach((key) -> {
            this.project.put(key, replace(project.get(key), key));
        });
    }

    public Properties(JSONObject globals, JSONObject project) {
        this.globals = globals;
        this.project = project;
    }

    public static void clearLogs() {
        warnings.clear();
    }

    public Properties setTestSuite(JSONObject testsuite) {
        return setTestSuite(null, testsuite);
    }

    public Properties setTestSuite(File file, JSONObject testsuite) {
        if (this.testsuite == null) {
            this.testsuite = new JSONObject();
        }
        JSONObject.fromObject(testsuite).keySet().forEach((key) -> {
            this.testsuite.put(key, replace(file, testsuite.get(key), key));
        });
        return this;
    }

    public Properties setTestCase(JSONObject testcase) {
        return setTestCase(null, testcase);
    }

    public Properties setTestCase(File file, JSONObject testcase) {
        if (this.testcase == null) {
            this.testcase = new JSONObject();
        }
        JSONObject.fromObject(testcase).keySet().forEach((key) -> {
            this.testcase.put(key, replace(file, testcase.get(key), key));
        });
        return this;
    }

    public Properties setTestStep(JSONObject teststep) {
        return setTestStep(null, teststep);
    }

    public Properties setTestStep(File file, JSONObject teststep) {
        if (this.teststep == null) {
            this.teststep = new JSONObject();
        }
        JSONObject.fromObject(teststep).keySet().forEach((key) -> {
            this.teststep.put(key, replace(file, teststep.get(key), key));
        });
        return this;
    }

    public String replace(Object replace) {
        return String.valueOf(replace(replace, null));
    }

    public final Object replace(Object replace, Object propKey) {
        return replace(null, replace, propKey);
    }

    public final Object replace(File file, Object replace, Object propKey) {
        if (replace == null || replace == JSONNull.getInstance()) {
            return JSONNull.getInstance();
        }
        String value = String.valueOf(replace);
        if (replace instanceof String) {
            StringBuilder sb = new StringBuilder(value);
            Matcher m = PATTERN_1.matcher(value);
            while (m.find()) {
                JSONObject source = getProperty(m.group(1));
                if (source == null) {
                } else {
                    String key = m.group(2);
                    int offset = value.length() - sb.length();
                    if (!source.containsKey(key)) {
                        sb.replace(m.start(0) - offset, m.end(2) - offset + 1, "`${#" + m.group(2) + "}`");
                        String msg = m.group() + " is undefined. " + (file != null ? "(" + file.getAbsolutePath() + ")" : "");
                        if (!warnings.contains(msg)) {
                            warnings.add(msg);
                            eventListeners.forEach((listener) -> {
                                listener.propertyChange(new PropertyChangeEvent(this, PROPERTY_WARNING, warnings, msg));
                            });
                        }
                    } else if (propKey != null) {
                        return source.get(key);
                    } else {
                        replace(sb, m.start(0) - offset, m.end(2) - offset + 1, source.get(key));
                    }
                }
            }
            //Replace by hierarchy
            m = PATTERN_2.matcher(value);
            while (m.find()) {
                String key = m.group(1);
                if (key.contains("#")) {
                    continue;
                }
                Object propValue = getValue(key, NULL);
                if (propValue == NULL) {
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

    public Object getValue(String key, Object defaulValue) {
        if (teststep != null && teststep.containsKey(key)) {
            return teststep.get(key);
        } else if (testcase != null && testcase.containsKey(key)) {
            return testcase.get(key);
        } else if (testsuite != null && testsuite.containsKey(key)) {
            return testsuite.get(key);
        } else if (project != null && project.containsKey(key)) {
            return project.get(key);
        } else if (globals != null && globals.containsKey(key)) {
            return globals.get(key);
        } else {
            return defaulValue;
        }
    }

    public static void replace(StringBuilder sb, int begin, int end, Object val) {
        if (begin > 0 && end < sb.length()) {
            char[] dst = new char[2];
            sb.getChars(begin - 1, begin, dst, 0);
            sb.getChars(end, end + 1, dst, 1);
            if (dst[0] == '`' && dst[1] == '`') {
                begin--;
                end++;
                if (!(val instanceof String)) {
                    if (begin > 0) {
                        sb.getChars(begin - 1, begin, dst, 0);
                        if (dst[0] == '"') {
                            begin--;
                        }
                    }
                    if (end < sb.length()) {
                        sb.getChars(end, end + 1, dst, 1);
                        if (dst[1] == '"') {
                            end++;
                        }
                    }
                }
            }
        }
        sb.replace(begin, end, String.valueOf(val));
    }

    public JSONObject getProperty(String name) {
        if (GLOBAL.equals(name)) {
            return globals;
        }
        if (PROJECT.equals(name)) {
            return project;
        }
        if (TESTSUITE.equals(name)) {
            return testsuite;
        }
        if (TESTCASE.equals(name)) {
            return testcase;
        }
        if (TESTSTEP.equals(name)) {
            return teststep;
        }
        return null;
    }

    public boolean isInheritKey(String propName, String key) {
        for (String name : PROPERTY_NAMES) {
            if (name.equals(propName)) {
                return false;
            }
            JSONObject json = getProperty(name);
            if (json != null && json.containsKey(key)) {
                return true;
            }
        }
        return false;
    }

    public static String getBody(File projectDir, String resourceId, String testSuiteName, String testCaseName, boolean isRequestBody) throws IOException {
        String path = String.format(Properties.TESTS_FILE_FORMAT, resourceId, testSuiteName, testCaseName);
        String body = null;
        String name = isRequestBody ? "request" : "response";
        File testFile = new File(projectDir, path);
        if (testFile.exists()) {
            JSONObject testJSON = JSONObject.fromObject(IOUtils.toString(new FileInputStream(testFile)));
            String resonseBodyPath = testJSON.getJSONObject(name).getString("body");
            LOGGER.log(Level.INFO, "\n{0}\n{1}", new Object[]{path, resonseBodyPath});
            File responseFile = new File(projectDir, resonseBodyPath);
            if (resonseBodyPath != null && responseFile.exists()) {
                body = IOUtils.toString(new FileInputStream(responseFile));
                JSONObject properties = testJSON.getJSONObject(name).getJSONObject("properties");
                StringBuilder sb = new StringBuilder(body);
                Matcher m = PATTERN_2.matcher(body);
                while (m.find()) {
                    String key = m.group(1);
                    if (key.contains("#")) {
                        continue;
                    }
                    Object propValue = properties.getOrDefault(key, null);
                    int offset = body.length() - sb.length();
                    Properties.replace(sb, m.start(0) - offset, m.end(1) - offset + 1, propValue);
                }
                body = sb.toString();
            }
        }
        return body;
    }

    public Properties cloneProperties() {
        Properties clone = new Properties(cloneJSON(globals), cloneJSON(project));
        clone.testsuite = cloneJSON(testsuite);
        clone.testcase = cloneJSON(testcase);
        clone.teststep = cloneJSON(teststep);
        return clone;
    }

    public static String getURL(String basePath, String path) {
        int len = basePath.length();
        path = (path.startsWith("/") ? "" : "/") + path;
        if (len > 0 && basePath.charAt(len - 1) == '/') {
            return basePath.substring(0, len - 1) + path;
        } else {
            return basePath + path;
        }
    }

    public JSONObject cloneJSON(JSONObject json) {
        return json == null ? null : JSONObject.fromObject(json);
    }
}
