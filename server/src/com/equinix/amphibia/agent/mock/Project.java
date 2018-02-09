package com.equinix.amphibia.agent.mock;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.io.IOUtils;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import net.sf.json.JSONObject;

@SuppressWarnings("unchecked")
public class Project implements HttpHandler {

    private static final Logger LOGGER = Logger.getLogger(Project.class.getName());

    private JSONObject projectJSON;
    private JSONObject interfacesJSON;
    private JSONObject pathJSON;

    public Project(CommandLine cmd, String projectFile) throws IOException {
        LOGGER.log(Level.INFO, "projectFile: " + projectFile);
        projectJSON = getContent(new FileInputStream(projectFile));
        init();
        startServer(cmd.getOptionValue(Server.PORT));
    }

    protected void startServer(String p) throws IOException {
        int port = p == null ? Server.DEFAUL_PORT : Integer.parseInt(p);
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", this);
        server.setExecutor(null);
        server.start();
        LOGGER.log(Level.INFO, "server is running on port: " + port);
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
                        String basePath = iterfJSON.getString("basePath");
                        pathJSON.put(testcase.get("method") + "::" + (basePath.startsWith("/") ? "" : "/") + basePath
                                + testcase.getString("path").split("\\?")[0], testcase);
                    });
                });
            }
        });
    }

    @Override
    public void handle(HttpExchange t) throws IOException {
        String response;
        String key = t.getRequestMethod() + "::" + t.getRequestURI().getPath();
        if (pathJSON.containsKey(key)) {
            JSONObject testcase = pathJSON.getJSONObject(key);
            response = String.valueOf(testcase.get("body"));
            t.getResponseHeaders().set("Content-Type", "appication/json");
            t.sendResponseHeaders(200, response.length());
        } else {
            response = "Unable to identify proxy for host: default and url: " + t.getRequestURI().getPath();
            t.sendResponseHeaders(400, response.length());
        }
        OutputStream os = t.getResponseBody();
        os.write(response.getBytes());
        os.close();
    }

    protected JSONObject getContent(InputStream is) throws IOException {
        if (is == null) {
            return null;
        }
        return JSONObject.fromObject(IOUtils.toString(is));
    }
}
