import java.util.Map;

import net.sf.json.JSONObject;

public class Builder {

    public static void main(String[] args) throws Exception {
        Map<String, Object> results = com.equinix.amphibia.agent.builder.Builder.execute(args);
        if (results != null) {
            System.out.println(JSONObject.fromObject(results));
        }
    }
}
