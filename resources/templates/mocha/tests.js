'use strict';

const superagent = require('superagent'),
		assert = require('assert'),
		debug = require('debug')('amphibia:tests'),
		error = require('debug')('amphibia:error');

const GLOBALS = {
<% GLOBALS %>
};

const HEADERS = {
<% HEADERS %>
};

<% TESTS %>