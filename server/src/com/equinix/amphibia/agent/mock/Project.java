package com.equinix.amphibia.agent.mock;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.io.IOUtils;

import com.equinix.amphibia.agent.builder.Properties;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import net.sf.json.JSONObject;

@SuppressWarnings("unchecked")
public class Project implements HttpHandler {

    private static final Logger LOGGER = Logger.getLogger(Project.class.getName());

    private final File projectDir;
    private final JSONObject projectJSON;

    private JSONObject interfacesJSON;
    private JSONObject pathJSON;

    private static final String FILE_FORMAT = "data/%s/tests/%s/%s.json";

    public Project(CommandLine cmd, String projectFile) throws IOException {
        LOGGER.log(Level.INFO, "projectFile: " + projectFile);
        File file = new File(projectFile);
        projectDir = file.getParentFile();
        projectJSON = getContent(new FileInputStream(file));
        init();
        startServer(cmd.getOptionValue(Server.PORT));
    }

    protected void startServer(String p) throws IOException {
        int port = p == null ? Server.DEFAUL_PORT : Integer.parseInt(p);
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", this);
        server.setExecutor(null);
        server.start();
        LOGGER.log(Level.INFO, "v1.0 | Server is running on port: " + port);
    }

    protected void init() {
        pathJSON = new JSONObject();
        interfacesJSON = new JSONObject();

        projectJSON.getJSONArray("interfaces").forEach((item) -> {
            JSONObject intrf = (JSONObject) item;
            interfacesJSON.put(intrf.getString("id"), intrf);
        });

        projectJSON.getJSONArray("projectResources").forEach((item1) -> {
            JSONObject resource = (JSONObject) item1;
            String interfaceId = resource.getString("interfaceId");
            if (!interfacesJSON.containsKey(interfaceId)) {
                LOGGER.log(Level.SEVERE, "Invalid interfaceId: " + interfaceId);
            } else {
                JSONObject iterfJSON = interfacesJSON.getJSONObject(interfaceId);
                JSONObject testsuites = resource.getJSONObject("testsuites");
                resource.getJSONObject("testsuites").keySet().forEach((key) -> {
                    JSONObject testsuite = testsuites.getJSONObject(key.toString());
                    testsuite.getJSONArray("testcases").forEach((item2) -> {
                        JSONObject testcase = (JSONObject) item2;
                        testcase.put("interfaceId", interfaceId);
                        testcase.put("resourceId", resource.getString("resourceId"));
                        testcase.put("testsuiteName", key);
                        String basePath = iterfJSON.getString("basePath");
                        pathJSON.put(testcase.get("method") + "::" + (basePath.startsWith("/") ? "" : "/") + basePath
                                + testcase.getString("path").split("\\?")[0], testcase);
                    });
                });
            }
        });
    }

    @Override
    public void handle(HttpExchange request) throws IOException {
        String response = "";
        try {
            String reqName = request.getRequestMethod() + "::" + request.getRequestURI().getPath();
            if (pathJSON.containsKey(reqName)) {
                JSONObject testcase = pathJSON.getJSONObject(reqName);
                String resourceId = testcase.getString("resourceId");
                String testSuiteName = testcase.getString("testsuiteName");
                String testCaseName = testcase.getString("name");
                String path = String.format(FILE_FORMAT, resourceId, testSuiteName, testCaseName);
                File testFile = new File(projectDir, path);
                if (testFile.exists()) {
                    JSONObject testJSON = getContent(new FileInputStream(testFile));
                    String resonseBodyPath = testJSON.getJSONObject("response").getString("body");
                    LOGGER.info(resonseBodyPath);
                    File responseFile = new File(projectDir, resonseBodyPath);
                    if (resonseBodyPath != null && responseFile.exists()) {
                        response = IOUtils.toString(new FileInputStream(responseFile));
                        JSONObject properties = testJSON.getJSONObject("response").getJSONObject("properties");
                        StringBuilder sb = new StringBuilder(response);
                        Matcher m = Pattern.compile("\\$\\{#(.*?)\\}", Pattern.DOTALL | Pattern.MULTILINE).matcher(response);
                        while (m.find()) {
                            String key = m.group(1);
                            if (key.contains("#")) {
                                continue;
                            }
                            Object propValue = properties.getOrDefault(key, null);
                            int offset = response.length() - sb.length();
                            Properties.replace(sb, m.start(0) - offset, m.end(1) - offset + 1, propValue);
                        }
                        response = sb.toString();
                    }
                }
                if (response != null) {
                    JSONObject properties = testcase.getJSONObject("properties");
                    request.getResponseHeaders().set("Content-Type", "appication/json");
                    request.sendResponseHeaders(
                            properties.containsKey("HTTPStatusCode") ? 
                            properties.getInt("HTTPStatusCode") : HttpURLConnection.HTTP_OK, response.length());
                } else {
                    request.sendResponseHeaders(HttpURLConnection.HTTP_NO_CONTENT, -1);
                    request.close();
                    return;
                }
            } else {
                response = "Unable to identify proxy for host: default and url: " + request.getRequestURI().getPath();
                request.sendResponseHeaders(HttpURLConnection.HTTP_BAD_REQUEST, response.length());
            }
            OutputStream os = request.getResponseBody();
            os.write(response.getBytes());
            os.close();
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }
    }

    protected JSONObject getContent(InputStream is) throws IOException {
        if (is == null) {
            return null;
        }
        return JSONObject.fromObject(IOUtils.toString(is));
    }
}
