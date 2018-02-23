/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.equinix.amphibia;

import static com.equinix.amphibia.agent.builder.ProjectAbstract.RED;
import static com.equinix.amphibia.agent.converter.Profile.HTTP_STATUS_CODE;
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
import java.util.prefs.Preferences;
import net.sf.json.JSON;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

/**
 *
 * @author dgofman
 */
public final class HttpConnection {

    private IHttpConnection out;
    private HttpConnectionImpl impl;

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
                    out.info(ex.getMessage() + "\n", RED);
                }
            }
        }
        return result;
    }

    public Result request(Properties properties, String name, String method, String url, JSONObject headers, String reqBody) throws Exception {
        return impl.request(properties, name, method, url, headers, reqBody);
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
        try {
            HttpConnectionImpl.assertionValidation(sb, node1, node2);
        } catch (IllegalArgumentException e) {
            throw new Exception(String.format(bundle.getString("error_response_body_missing_field"), e.getMessage()));
        } catch (IllegalStateException e) {
            throw new Exception(String.format(bundle.getString("error_response_unexpected_value"), e.getMessage()));
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
