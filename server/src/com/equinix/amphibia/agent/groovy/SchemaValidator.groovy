package com.equinix.amphibia.agent.groovy;

import groovy.json.JsonOutput;
import java.util.Map;
import java.io.IOException;
import java.util.LinkedHashMap;

import net.sf.json.JSON;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

@groovy.transform.TypeChecked
public class SchemaValidator extends AbstractScript {

	private String fileName;
	private JSONObject source;
	private JSON schema;
	private Map<String, Object> validator;

	public SchemaValidator(String fileName, JSONObject source, JSON schema) throws Exception {
		this.fileName = fileName;
		this.source = source;
		this.schema = schema;
		this.init();
	}

	@Override
	protected void init() throws Exception {
		super.init();
		if (this.schema != null) {
			validator = new LinkedHashMap<String, Object>();

			JSONArray exts = (JSONArray)schema.getAt("extends");
			if (exts != null) {
				for (Object file : exts) {
					this.parseSchema(getSchema((String)file));
				}
			}
			this.parseSchema(this.schema);
			log.info(JsonOutput.prettyPrint(JsonOutput.toJson(this.validator)));
			this.walk(this.source, this.validator);
		}
	}
	
	private void parseSchema(JSON schema) {
		JSONObject fields = (JSONObject)schema.getAt("fields");
		for (String key : fields.keySet()) {
			JSONObject field = (JSONObject)fields.getAt(key);
			String type = field.getAt("\$type");
			buildPath(validator, key, type == null ? "string" : type, field);
		}
	}

	private void buildPath(Map<String, Object> target, String path, String type, JSONObject field) {
		String[] arr = path.split("\\.");
		for (int i = 0; i < arr.length - 1; i++) {
			String node = arr[i];
			if (target.get(node) == null) {
				target.put(node, new LinkedHashMap<String, Object>());
			}
			target = (Map<String, Object>)target.get(node);
		}
		target.put(arr[arr.length - 1], type);
	}

	private void walk(JSONObject source, Map<String, Object> target) throws Exception {
		for (Object _key : source.keySet()) {
			String key = (String)_key;
			Object obj1 = source.getAt(key);
			Object obj2 = target.get(key);
			if (obj2 == null) {
				throw new IllegalArgumentException("Missing schema definition for the key: ${key}. Path: " + fileName);
			}
			if (obj2 instanceof Map) {
				walk((JSONObject)obj1, (Map<String, Object>)obj2);
			} else {
				@SuppressWarnings("rawtypes")
				Class clazz = null;
				switch((String)obj2) {
					case "string":
						clazz = String.class;
						break;
					case "bool":
						clazz = Boolean.class;
						break;
					case "object":
						clazz = JSONObject.class;
						break;
					case "array":
						clazz = JSONArray.class;
						break;
					case "number":
					case "integer":
						String value = String.valueOf(obj1);
						try {
							if (!Double.isNaN(Double.parseDouble(value)))
								return;
						} catch (Exception e) {}
						throw new NumberFormatException("Invalid number: " + value);
					default:
						throw new IllegalArgumentException("Unexpected type: key='${key}' - ${obj2}");
				}
				if (Runner.isNotNull(obj1) && !(obj1.getClass().equals(clazz))) {
					throw new IllegalArgumentException("Invalid type: key='${key}' - ${obj1.getClass()} != ${clazz}");
				}
			}
		}
	}
}