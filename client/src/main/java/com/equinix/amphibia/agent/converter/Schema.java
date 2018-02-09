package com.equinix.amphibia.agent.converter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import java.util.LinkedHashMap;

import com.equinix.amphibia.agent.builder.ProjectAbstract;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.json.JSONObject;

@SuppressWarnings("serial")
public final class Schema {

    private final Swagger swagger;
    private JSONObject docDefinitions;
    private Map<Object, Object> fields;
    private Map<Object, Object> definitions;
    private Map<Object, Object> schema;

    public static final Map<String, String> schemas = new LinkedHashMap<String, String>();

    private static final Logger LOGGER = Logger.getLogger(Schema.class.getName());

    @SuppressWarnings("unchecked")
    public Schema(Swagger swagger, String ref, String childDir) throws Exception {
        this.swagger = swagger;
        this.schema = new HashMap<Object, Object>() {
            {
                put("type", "schema");
                put("extends", new ArrayList<>());
                put("fields", new LinkedHashMap<>());
                put("definitions", new LinkedHashMap<>());
            }
        };
        if ("false".equals(Converter.cmd.getOptionValue(Converter.SCHEMA))) {
            return;
        }

        this.docDefinitions = swagger.getDoc().getJSONObject("definitions");
        if (!this.docDefinitions.isNullObject()) {
            fields = (Map<Object, Object>) this.schema.get("fields");
            definitions = (Map<Object, Object>) this.schema.get("definitions");
            String definitionName = this.parse(fields, ref, null);
            if (!schemas.containsKey(ref)) {
                String path = save(swagger.getDataDir(), JSONObject.fromObject(schema).toString(), definitionName, childDir);
                schemas.put(ref, path);
            }
        }
    }

    protected String parse(Map<Object, Object> parent, String ref, List<String> paths) {
        String definitionName = Swagger.getDefinitionName(ref);
        JSONObject definition = this.docDefinitions.getJSONObject(definitionName);
        if (!definition.isNullObject()) {
            parseDefinition(definitionName, parent, definition, null);
        }
        return definitionName;
    }

    protected void parseDefinition(String definitionName, Map<Object, Object> parent, JSONObject ref, List<String> paths) {
        JSONObject properties = ref.getJSONObject("properties");
        for (Object name : properties.keySet()) {
            if (paths == null) {
                paths = new ArrayList<>();
            }
            JSONObject props = properties.getJSONObject(name.toString());
            Validator.validateDefinitionParam(name.toString(), swagger.getDoc(), props, definitionName, ref, paths);

            Map<String, Object> details = new LinkedHashMap<>();
            parent.put(name, details);
            if (props.containsKey("properties")) {
                details.put("$type", "object");
                details.put("$ref", name);
                Map<Object, Object> definition = new LinkedHashMap<>();
                definitions.put(name, definition);
                paths.add(name.toString());
                parseDefinition(definitionName, definition, props, paths);
            } else {
                if (props.containsKey("type")) {
                    details.put("$type", props.getString("type"));
                    JSONObject items = props.getJSONObject("items");
                    if (!items.isNullObject()) {
                        if (items.containsKey("$ref")) {
                            addDefinition(details, items.getString("$ref"), paths);
                        } else if (items.containsKey("properties")) {
                            Map<Object, Object> definition = new LinkedHashMap<>();
                            details.put("$ref", name);
                            definitions.put(name, definition);
                            paths.add(name.toString());
                            parseDefinition(definitionName, definition, items, paths);
                        } else if (items.containsKey("type")) {
                            Map<Object, Object> innerItems = new LinkedHashMap<>();
                            innerItems.put("$type", items.get("type"));
                            details.put("$items", innerItems);
                        }
                    }
                } else if (props.containsKey("$ref")) {
                    details.put("$type", "object");
                    if (props.containsKey("$ref")) {
                        addDefinition(details, props.getString("$ref"), paths);
                    }
                }
            }
        }
    }

    protected void addDefinition(Map<String, Object> details, String ref, List<String> paths) {
        String definitionName = Swagger.getDefinitionName(ref);
        details.put("$ref", definitionName);
        Map<Object, Object> definition = new LinkedHashMap<>();
        definitions.put(definitionName, definition);
        parse(definition, ref, paths);
    }

    public static File getSchemasDir(File dataDir) {
        return new File(dataDir, "schemas");
    }

    public static String save(File dataDir, String json, String fileName, String childDir) throws Exception {
        if ("false".equals(Converter.cmd.getOptionValue(Converter.SCHEMA))) {
            return null;
        }
        File path = getSchemasDir(dataDir);
        if (childDir != null) {
            path = new File(path, childDir);
        }
        File outputDir = new File(Profile.PROJECT_DIR, path.getPath());
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }
        File outputFile = new File(outputDir, fileName + ".json");
        PrintWriter writer = new PrintWriter(new FileOutputStream(outputFile, false));
        writer.println(Swagger.getJson(json));
        writer.close();
        LOGGER.log(Level.INFO, "The file saved successfully.\n{0}", outputFile);
        String filePath = ProjectAbstract.getRelativePath(outputFile.toURI());
        Converter.addResult(Converter.RESOURCE_TYPE.schemas, filePath);
        return filePath;
    }

    public Map<Object, Object> getSchema() {
        return schema;
    }

    public Map<Object, Object> getFields() {
        return fields;
    }

    public Map<Object, Object> getDefinitions() {
        return definitions;
    }
}
