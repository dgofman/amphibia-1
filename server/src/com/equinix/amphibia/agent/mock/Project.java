package com.equinix.amphibia.agent.mock;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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

    private final Map<Pattern, PathInfo> pathInfo = new HashMap<>();
    private final Pattern SPECIAL_REGEX_CHARS = Pattern.compile("[{}()\\[\\].+*?^$\\\\|]");

    public Project() {
    }

    public Project(CommandLine cmd, String projectFile) throws IOException {
        init(new File(projectFile));
        startServer(cmd.getOptionValue(Server.PORT));
    }

    public void startServer(String p) throws IOException {
        int port = p == null ? Server.DEFAUL_PORT : Integer.parseInt(p);
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", this);
        server.setExecutor(null);
        server.start();
        LOGGER.log(Level.INFO, "v1.0 | Server is running on port: " + port);
    }

    protected void init(File file) throws IOException {
        LOGGER.log(Level.INFO, "projectFile: " + file.getAbsolutePath());
        JSONObject projectJSON = getContent(new FileInputStream(file));
        JSONObject interfacesJSON = new JSONObject();

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
                        String path = fixPath(testcase.get("method") + "::" + (basePath.startsWith("/") ? "" : "/") + basePath
                                + testcase.getString("path").split("\\?")[0]);
                        path = SPECIAL_REGEX_CHARS.matcher(path).replaceAll("\\\\$0");
                        path = path.replaceAll("\\\\\\$\\\\\\{#(.*?)\\\\\\}", "(.*?)").replaceAll("\\\\\\{(.*?)\\\\\\}", "(.*?)");
                        if (path.endsWith("(.*?)")) {
                        	path = path.substring(0, path.length() - 5) + ".*";
                        }
                        pathInfo.put(Pattern.compile(path), new PathInfo(file.getParentFile(), testcase));
                        System.out.println(path);
                    });
                });
            }
        });
    }

    @Override
    public void handle(HttpExchange request) throws IOException {
        String response = "";
        try {
            String reqName = fixPath(request.getRequestMethod() + "::" + request.getRequestURI().getPath());
            List<Pattern> values  = pathInfo.keySet()
                    .stream()
                    .filter(pattern -> pattern.matcher(reqName).matches())
                    .collect(Collectors.toList());
            byte[] bs = null;
            if (values != null && !values.isEmpty()) {
                PathInfo info = pathInfo.get(values.get(0));
                JSONObject testcase = info.testcase;
                String resourceId = testcase.getString("resourceId");
                String testSuiteName = testcase.getString("testsuiteName");
                String testCaseName = testcase.getString("name");
                response = Properties.getBody(info.projectDir, resourceId, testSuiteName, testCaseName, false);
                if (response != null) {
                    bs = response.getBytes("UTF-8");
                    JSONObject properties = testcase.getJSONObject("properties");
                    request.getResponseHeaders().set("Content-Type", "appication/json");
                    request.sendResponseHeaders(
                            properties.containsKey("HTTPStatusCode") ? 
                            properties.getInt("HTTPStatusCode") : HttpURLConnection.HTTP_OK, bs.length);
                } else {
                    request.sendResponseHeaders(HttpURLConnection.HTTP_NO_CONTENT, -1);
                    request.close();
                    return;
                }
            } else {
                response = "Unable to identify proxy for host: default and url: " + request.getRequestURI().getPath();
                bs = response.getBytes("UTF-8");
                request.sendResponseHeaders(HttpURLConnection.HTTP_BAD_REQUEST, bs.length);
            }
            OutputStream os = request.getResponseBody();
            os.write(bs);
            os.close();
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }
    }

    protected String fixPath(String path) { 
        return path.replaceAll("//", "/");
    }

    protected JSONObject getContent(InputStream is) throws IOException {
        if (is == null) {
            return null;
        }
        return JSONObject.fromObject(IOUtils.toString(is));
    }
}

class PathInfo {
    public final File projectDir;
    public final JSONObject testcase;
    
    public PathInfo(File projectDir, JSONObject testcase) {
        this.projectDir = projectDir;
        this.testcase = testcase;
    }
}
