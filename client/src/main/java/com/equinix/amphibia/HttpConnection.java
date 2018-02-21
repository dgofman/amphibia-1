/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.equinix.amphibia;

import static com.equinix.amphibia.agent.converter.Profile.HTTP_STATUS_CODE;

import com.equinix.amphibia.agent.builder.Properties;
import com.equinix.amphibia.components.AssertDialog;
import com.equinix.amphibia.components.Profile;
import com.equinix.amphibia.components.TreeCollection;
import com.equinix.amphibia.components.TreeIconNode;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.ResourceBundle;
import java.util.prefs.Preferences;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import net.sf.json.JSON;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

/**
 *
 * @author dgofman
 */
public final class HttpConnection {

    private Preferences userPreferences;
    private HttpURLConnection conn;
    private IHttpConnection out;

    public static int DEFAULT_TIMEOUT = 60;

    public HttpConnection(IHttpConnection out) {
        this.out = out;
        userPreferences = Amphibia.getUserPreferences();
    }

    @SuppressWarnings("NonPublicExported")
    public Result request(String name, String method, TreeIconNode node) throws Exception {
        final TreeCollection collection = node.getCollection();
        TreeIconNode.ResourceInfo info = node.info;
        Result result = request(collection.getProjectProperties(), name, method, node.getTreeIconUserObject().getTooltip(), info.getRequestHeader(node), info.getRequestBody(collection));
        if (node.jsonObject().containsKey("response")) {
            JSONObject response = node.jsonObject().getJSONObject("response");
            if (response.containsKey("asserts")) {
                try {
                    Object statusCode = info.properties.getValue(HTTP_STATUS_CODE, -1);
                    assertionValidation(result, response.getJSONArray("asserts"), statusCode, response.getString("body"));
                } catch (Exception ex) {
                    addError(result, "ASSERT", ex);
                    out.info("\nASSERT:\n", true);
                    out.info(ex.getMessage() + "\n", Profile.RED);
                }
            }
        }
        return result;
        
    }

    @SuppressWarnings("NonPublicExported")
    public Result request(Properties properties, String name, String method, String url, JSONObject headers, String reqBody) throws Exception {
        conn = null;
        Result result = new Result();
        BufferedReader in;
        long startTime = new Date().getTime();

        out.info("NAME: ", true).info(name + "\n");
        try {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            out.info(method + ": ", true).info(url + "\n");
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(userPreferences.getInt(Amphibia.P_CONN_TIMEOUT, DEFAULT_TIMEOUT) * 1000);
            conn.setReadTimeout(userPreferences.getInt(Amphibia.P_READ_TIMEOUT, DEFAULT_TIMEOUT) * 1000);

            conn.setDoInput(true);
            conn.setDoOutput(true);
            conn.setRequestMethod(method);
            conn.setRequestProperty("Accept", "*/*");

            out.info("HEADERS:\n", true);
            if (conn != null) {
                SimpleAttributeSet attr = new SimpleAttributeSet();
                StyleConstants.setBold(attr, true);
                StyleConstants.setItalic(attr, true);
                headers.keySet().forEach((key) -> {
                    String value = properties.replace(headers.get(key));
                    out.info(key + ": ");
                    out.info(value + "\n", attr);
                    conn.setRequestProperty(key.toString(), value);
                });
            }

            out.info("BODY:\n", true).info(reqBody + "\n", Profile.BLUE);

            if (conn != null && reqBody != null && !reqBody.isEmpty()) {
                conn.getOutputStream().write(reqBody.getBytes("UTF-8"));
            }

            InputStream content;
            try {
                if (conn == null) { //User pressed stop button
                    throw new SocketTimeoutException(result.content = "Connection aborted");
                }
                content = (InputStream) conn.getInputStream();
            } catch (IOException e) {
                addError(result, name, e);
                content = (InputStream) conn.getErrorStream();
            }
            if (content != null) {
                in = new BufferedReader(new InputStreamReader(content));
                String line;
                while ((line = in.readLine()) != null) {
                    pw.println(line);
                }
                in.close();

                try {
                    result.content = IO.prettyJson(sw.toString());
                } catch (Exception e) {
                    result.content = sw.toString();
                }
            }
        } catch (IOException e) {
            addError(result, name, e);
        } finally {
            result.time = new Date().getTime() - startTime;
            out.info("\nSTATUS: ", true).info(result.statusCode + "\n");
            out.info("TIME: ", true).info(result.time + " ms\n");
            out.info("RESULT:\n", true);
            if (result.exception == null) {
                out.info(result.content + "\n", Profile.GREEN);
            } else {
                out.info(result.content + "\n", Profile.RED);
                out.info("\nERROR:\n", true);
                out.info(result.getErrorStackTrace() + "\n", Profile.RED);
            }

            if (conn != null) {
                try {
                    result.statusCode = conn.getResponseCode();
                } catch (IOException e) {
                    throw e;
                }
                conn.getHeaderFields().entrySet().forEach((entry) -> {
                    if (entry.getKey() != null) {
                        result.headers.put(entry.getKey(), entry.getValue());
                    }
                });
                out.info("HEADERS:\n", true);
                out.info(result.headers.toString(4));
                conn.disconnect();
            }
        }
        conn = null;
        out.info("\n");
        return result;
    }

    public void assertionValidation(Result result, JSONArray asserts, Object statusCode, String bodyFile) throws Exception {
        ResourceBundle bundle = Amphibia.getBundle();
        if (asserts != null) {
            if (asserts.isEmpty()) {
                if (result.statusCode != statusCode) {
                    throw new Exception(String.format(bundle.getString("error_http_code_equals"), statusCode));
                }
            } else {
                for (Object assertType : asserts) {
                    if (AssertDialog.ASSERTS.NOTEQUALS.toString().equals(assertType)) {
                        if (result.statusCode == statusCode) {
                            throw new Exception(String.format(bundle.getString("error_http_code_not_equals"), statusCode));
                        }
                    } else if (bodyFile != null) {
                        if (result.content == null) {
                            throw new Exception(bundle.getString("error_response_body_null"));
                        } else if (AssertDialog.ASSERTS.ORDERED.toString().equals(assertType)) {
                            JSON json = IO.getJSON(IO.getFile(bodyFile));
                            if (result.content.equals(IO.prettyJson(json.toString()))) {
                                throw new Exception(bundle.getString("error_response_body_match"));
                            }
                        } else {
                            JSON expected = IO.getJSON(IO.getFile(bodyFile));
                            JSON actual = IO.toJSON(result.content);
                            assertionValidation(assertType, actual, expected);
                        }
                    }
                }
            }
        }
    }

    public static void assertionValidation(Object assertType, JSON actual, JSON expected) throws Exception {
        ResourceBundle bundle = Amphibia.getBundle();
        StringBuilder sb = new StringBuilder("");

        JSON node1 = AssertDialog.ASSERTS.UNORDERED.toString().equals(assertType) ? actual : expected;
        JSON node2 = AssertDialog.ASSERTS.UNORDERED.toString().equals(assertType) ? expected : actual;
        new Object() {
            void walk(StringBuilder sb, Object node1, Object node2) throws Exception {
                if (node1 instanceof JSONArray && node2 instanceof JSONArray) {
                    JSONArray arr1 = (JSONArray) node1;
                    JSONArray arr2 = (JSONArray) node2;
                    int length = Math.max(arr1.size(), arr2.size());
                    for (int i = 0; i < length; i++) {
                        StringBuilder sba = new StringBuilder(sb).append(".[").append(i).append("]");
                        if (i < arr1.size() && i < arr2.size()) {
                            walk(sba, arr1.get(i), arr2.get(i));
                        } else if (i < arr1.size()) {
                            walk(sba, arr1.get(i), new JSONObject());
                        } else {
                            walk(sba, new JSONObject(), arr2.get(i));
                        }
                    }
                } else if (node1 instanceof JSONObject && node2 instanceof JSONObject) {
                    JSONObject obj1 = (JSONObject) node1;
                    JSONObject obj2 = (JSONObject) node2;
                    for (Object key : obj1.keySet()) {
                        StringBuilder sbo = new StringBuilder(sb).append(".").append(key);
                        if (!obj2.containsKey(key)) {
                            throw new Exception(String.format(bundle.getString("error_response_body_missing_field"), sbo.length() > 0 ? sbo.toString().substring(1) : ""));
                        } else {
                            walk(sbo, obj1.get(key), obj2.get(key));
                        }
                    }
                } else if (!String.valueOf(node1).equals(String.valueOf(node2))) {
                    throw new Exception(String.format(bundle.getString("error_response_unexpected_value"), sb.length() > 0 ? sb.toString().substring(1) : ""));
                }
            }
        }.walk(sb, node1, node2);
    }

    public HttpURLConnection urlConnection() {
        return conn;
    }

    public void disconnect() {
        if (conn != null) {
            conn.disconnect();
        }
        conn = null;
    }

    public void addError(Result result, String name, Throwable t) {
        if (result.exception == null) {
            result.exception = t;
            String message = name + " (" + t.getMessage() + ")";
            if (t instanceof java.net.UnknownHostException || t instanceof SocketTimeoutException) {
                out.addError(message);
            } else {
                out.addError(t, message);
            }
        }
    }

    public static class Result {

        public Throwable exception = null;
        public JSONObject headers = new JSONObject();
        public String content;
        public Object statusCode;
        public long time;

        public String[] createError() {

            return new String[]{exception.getMessage(),
                exception.getClass().getName(),
                getErrorStackTrace(), content != null ? content : ""};
        }

        public String getErrorStackTrace() {
            List<String> sb = new ArrayList<>();
            StackTraceElement[] stack = exception.getStackTrace();
            for (int i = 0; i < stack.length; i++) {
                String line = stack[i].toString();
                if (line.startsWith("com.equinix.amphibia")) {
                    if (i >= 4) {
                        sb.add(line);
                        break;
                    }
                }
                if (i < 4) {
                    sb.add(line);
                }
            }
            return String.join("\n\t", sb);
        }
    }
}
