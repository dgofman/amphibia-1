		@Test
		@Details(description="<% SUMMARY %>")
		public void <% TESTCASE_CLASS_NAME %>() throws Exception {
			String url = String.format("%s<% PATH %>", <% ENDPOINT %>);
			String body = <% BODY %>;
			Response response = request(new URL(url), "<% METHOD %>", body, headers);
			onError(response);
			<% ASSERTIONS %>
		}