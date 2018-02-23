/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.equinix.amphibia.agent.runner;

import com.equinix.amphibia.agent.builder.ProjectAbstract;
import com.equinix.amphibia.agent.builder.Properties;

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
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

/**
 *
 * @author dgofman
 */
public final class HttpConnectionImpl {

    public static int DEFAULT_TIMEOUT = 60;

    private HttpURLConnection conn;
    private IHttpConnection out;
    private int connectTimeout;
    private int readTimeout;

    public HttpConnectionImpl(IHttpConnection out) {
        this(out, DEFAULT_TIMEOUT, DEFAULT_TIMEOUT);
    }

    public HttpConnectionImpl(IHttpConnection out, int connectTimeout, int readTimeout) {
        this.out = out;
        this.connectTimeout = connectTimeout;
        this.readTimeout = readTimeout;
    }

    @SuppressWarnings({"NonPublicExported", "unchecked"})
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
            conn.setConnectTimeout(connectTimeout * 1000);
            conn.setReadTimeout(readTimeout * 1000);

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

            out.info("BODY:\n", true).info(reqBody + "\n", ProjectAbstract.BLUE);

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
                    result.content = ProjectAbstract.prettyJson(sw.toString());
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
                out.info(result.content + "\n", ProjectAbstract.GREEN);
            } else {
                out.info(result.content + "\n", ProjectAbstract.RED);
                out.info("\nERROR:\n", true);
                out.info(result.getErrorStackTrace() + "\n", ProjectAbstract.RED);
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

    public static void assertionValidation(StringBuilder sb, Object node1, Object node2) throws Exception {
        if (node1 instanceof JSONArray && node2 instanceof JSONArray) {
            JSONArray arr1 = (JSONArray) node1;
            JSONArray arr2 = (JSONArray) node2;
            int length = Math.max(arr1.size(), arr2.size());
            for (int i = 0; i < length; i++) {
                StringBuilder sba = new StringBuilder(sb).append(".[").append(i).append("]");
                if (i < arr1.size() && i < arr2.size()) {
                    assertionValidation(sba, arr1.get(i), arr2.get(i));
                } else if (i < arr1.size()) {
                    assertionValidation(sba, arr1.get(i), new JSONObject());
                } else {
                    assertionValidation(sba, new JSONObject(), arr2.get(i));
                }
            }
        } else if (node1 instanceof JSONObject && node2 instanceof JSONObject) {
            JSONObject obj1 = (JSONObject) node1;
            JSONObject obj2 = (JSONObject) node2;
            for (Object key : obj1.keySet()) {
                StringBuilder sbo = new StringBuilder(sb).append(".").append(key);
                if (!obj2.containsKey(key)) {
                    throw new IllegalArgumentException(sbo.length() > 0 ? sbo.toString().substring(1) : "");
                } else {
                    assertionValidation(sbo, obj1.get(key), obj2.get(key));
                }
            }
        } else if (!String.valueOf(node1).equals(String.valueOf(node2))) {
            throw new IllegalStateException(sb.length() > 0 ? sb.toString().substring(1) : "");
        }
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

    public HttpURLConnection urlConnection() {
        return conn;
    }

    public void disconnect() {
        if (conn != null) {
            conn.disconnect();
        }
        conn = null;
    }

    public static class Result {

        public Throwable exception = null;
        public JSONObject headers = new JSONObject();
        public String content;
        public Object statusCode;
        public long time;

        public String[] createError() {

            return new String[]{
                exception.getMessage(),
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
