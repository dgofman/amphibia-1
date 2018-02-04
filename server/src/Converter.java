import java.util.Map;

import com.equinix.amphibia.agent.converter.Converter.RESOURCE_TYPE;

import net.sf.json.JSONObject;

public class Converter {

    public static void main(String[] args) throws Exception {
        Map<RESOURCE_TYPE, Object> results = com.equinix.amphibia.agent.converter.Converter.execute(args);
        if (results != null) {
            System.out.println(String.join("\n", JSONObject.fromObject(results).toString()));
        }
    }
}
