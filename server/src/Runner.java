import java.util.Map;

import net.sf.json.JSONObject;

public class Runner {

    public static void main(String[] args) throws Exception {
        Map<String, Object> results = com.equinix.amphibia.agent.runner.Runner.execute(args);
        if (results != null) {
            System.out.println(JSONObject.fromObject(results));
        }
    }
}
