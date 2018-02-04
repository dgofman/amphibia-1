	it('should test <% TESTCASE_NAME %> - <% SUMMARY %>' , done => {
		const url = `<% ENDPOINT %>/<% PATH %>`;
		const body = <% BODY %>;

		debug(`<% METHOD %>: ${url}`);
		debug(headers);
		debug(JSON.stringify(body, null, 2));

		superagent.<% METHOD_NAME %>(url)
			.set(headers)
			.send(body)
			.type('<% MEDIATYPE %>')
			.end((err, res) => {
				if (err) {
					error(err);
				}
				<% ASSERTIONS %>
				done();
		});
	});