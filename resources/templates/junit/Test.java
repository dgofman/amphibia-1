	public static class <% TESTSUITE_CLASS_NAME %> extends AmphibiaBaseTest {

		String endpoint = GLOBALS.get("<% ENDPOINT %>");
		Object[][] headers = HEADERS.get("<% INTERFACE %>");

<% TESTCASES %>
	}