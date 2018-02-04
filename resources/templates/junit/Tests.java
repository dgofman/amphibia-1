import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;
import org.junit.runners.Suite;
import org.junit.runners.model.InitializationError;

@SuppressWarnings("serial")
public class <% PROJECT_NAME %> extends Suite {

	public <% PROJECT_NAME %>() throws InitializationError {
		super(<% PROJECT_NAME %>.class, getAllTests());
	}

	public static Class<?>[] getAllTests() {
		return new Class<?>[] {
			<% TESTCASES %>
		};
	}

	public static final Map<String, String> GLOBALS = new HashMap<String, String>() {{
<% GLOBALS %>
	}};

	public static final Map<String, Object[][]> HEADERS = new HashMap<String, Object[][]>() {{
<% HEADERS %>
	}};

<% TESTS %>
}