/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.equinix.amphibia;

import static com.equinix.amphibia.agent.builder.ProjectAbstract.RED;
import static com.equinix.amphibia.agent.runner.HttpConnectionImpl.DEFAULT_TIMEOUT;
import static com.equinix.amphibia.agent.runner.HttpConnectionImpl.Result;

import com.equinix.amphibia.agent.runner.IHttpConnection;
import com.equinix.amphibia.agent.builder.Properties;
import com.equinix.amphibia.agent.runner.HttpConnectionImpl;
import com.equinix.amphibia.components.AssertDialog;
import com.equinix.amphibia.components.TreeCollection;
import com.equinix.amphibia.components.TreeIconNode;

import java.net.HttpURLConnection;
import java.util.ResourceBundle;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import net.sf.json.JSON;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.math.NumberUtils;

/**
 *
 * @author dgofman
 */
public final class HttpConnection {

    private final IHttpConnection out;
    private final HttpConnectionImpl impl;
    
    private static final Logger logger = Amphibia.getLogger(HttpConnection.class.getName());

    public HttpConnection(IHttpConnection out) {
        this.out = out;
        Preferences userPreferences = Amphibia.getUserPreferences();
        impl = new HttpConnectionImpl(out,
                userPreferences.getInt(Amphibia.P_CONN_TIMEOUT, DEFAULT_TIMEOUT),
                userPreferences.getInt(Amphibia.P_READ_TIMEOUT, DEFAULT_TIMEOUT));
    }

    @SuppressWarnings("NonPublicExported")
    public Result request(String name, String method, TreeIconNode node) throws Exception {
        final TreeCollection collection = node.getCollection();
        Result result = request(node, collection.getProjectProperties(), name, method, 
                node.getTreeIconUserObject().getTooltip(), node.info.getRequestHeader(node), node.info.getRequestBody());
        assertionValidation(node, node.info.properties, result);
        return result;
    }

    public Result request(TreeIconNode node, Properties properties, String name, String method, String url, JSONObject headers, String reqBody) throws Exception {
        return impl.request(properties, name, method, url, headers, reqBody);
    }
    
    public void propertyTransfer(TreeIconNode node, Properties properties, Result result) throws Exception {
        JSONObject transfer = new JSONObject();
        if (node != null && node.info != null) {
            if (node.info.testCase.containsKey("transfer")) {
                transfer.putAll(node.info.testCase.getJSONObject("transfer"));
            }
            if (node.info.testStep != null && node.info.testStep.containsKey("transfer")) {
                transfer.putAll(node.info.testStep.getJSONObject("transfer"));
            }
        }
        if (!transfer.isEmpty()) {
            JSON content = IO.toJSON(result.content);
            JSONObject testStep = properties.getProperty(Properties.TESTSTEP);
            transfer.keySet().forEach((name) -> {
                Object prop = transfer.get(name);
                if (prop instanceof String && prop.toString().startsWith("/")) {
                    Object value = content;
                    String[] keys = prop.toString().split("/");
                    for (int i = 1; i < keys.length; i++) {
                        String key = keys[i];
                        if (value instanceof JSONObject) {
                            if (((JSONObject) value).containsKey(key)) {
                                value = ((JSONObject) value).get(key);
                            } else {
                                value = null;
                                break;
                            }
                        } else if (value instanceof JSONArray) {
                            if (NumberUtils.isNumber(key) && ((JSONArray) value).size() > Integer.parseInt(key)) {
                                value = ((JSONArray) value).get(Integer.parseInt(key));
                            } else {
                                value = null;
                                break;
                            }
                        }
                    }
                    testStep.put(name, value);
                } else {
                    testStep.put(name, prop);
                }
            });
        }
    }
    
    public int assertionValidation(TreeIconNode node, Properties properties, Result result) throws Exception {
        if (node != null && node.jsonObject().containsKey("response")) {
            JSONObject response = node.jsonObject().getJSONObject("response");
            if (response.containsKey("asserts")) {
                try {
                    Object statusCode = node.info.getResultStatus();
                    assertionValidation(result, properties, response.getJSONArray("asserts"), statusCode, response.getString("body"));
                    return 1;
                } catch (Exception ex) {
                    addError(result, "ASSERT", ex);
                    out.info("\nASSERT:\n", true);
                    out.info(ex.getMessage() + "\n", RED);
                    return 0;
                }
            }
        }
        return -1;
    }

    public void assertionValidation(Result result, Properties properties, JSONArray asserts, Object statusCode, String bodyFile) throws Exception {
        ResourceBundle bundle = Amphibia.getBundle();
        if (asserts != null) {
            if (!asserts.contains(AssertDialog.ASSERTS.NOTEQUALS.toString()) && !String.valueOf(statusCode).equals(String.valueOf(result.statusCode))) {
                throw new Exception(String.format(bundle.getString("error_http_code_equals"), statusCode));
            }
            for (Object assertType : asserts) {
                if (AssertDialog.ASSERTS.NOTEQUALS.toString().equals(assertType)) {
                    if (result.statusCode == statusCode) {
                        throw new Exception(String.format(bundle.getString("error_http_code_not_equals"), statusCode));
                    }
                } else if (bodyFile != null) {
                    if (result.content == null) {
                        throw new Exception(bundle.getString("error_response_body_null"));
                    } else if (AssertDialog.ASSERTS.ORDERED.toString().equals(assertType)) {
                        JSON json = IO.getJSON(bodyFile);
                        String expected = IO.prettyJson(json.toString());
                        expected = properties.replace(expected);
                        String actual = IO.prettyJson(result.content);
                        if (!expected.equals(actual)) {
                            logger.info(StringUtils.difference(actual, expected));
                            throw new Exception(String.format(bundle.getString("error_response_body_match"), actual, expected));
                        }
                    } else {
                        JSON expected = IO.getJSON(bodyFile);
                        String jsonStr = properties.replace(expected.toString());
                        try {
                            expected = IO.toJSON(jsonStr);
                        } catch (Exception ex) {
                            jsonStr = properties.replace(expected.toString(), true);
                            expected = IO.toJSON(jsonStr);
                        }

                        JSON actual = IO.toJSON(result.content);
                        assertionValidation(assertType, actual, expected);
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
        try {
            HttpConnectionImpl.assertionValidation(sb, node1, node2);
        } catch (IllegalArgumentException e) {
            throw new Exception(String.format(bundle.getString("error_response_body_missing_field"), e.getMessage(), actual.toString(), expected.toString()));
        } catch (IllegalStateException e) {
            throw new Exception(String.format(bundle.getString("error_response_unexpected_value"), e.getMessage(), actual.toString(), expected.toString()));
        }
    }

    public HttpURLConnection urlConnection() {
        return impl.urlConnection();
    }

    public void disconnect() {
        impl.disconnect();
    }

    public void addError(Result result, String name, Throwable t) {
        impl.addError(result, name, t);
    }
}
