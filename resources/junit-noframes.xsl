<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
	version="1.0" xmlns:lxslt="http://xml.apache.org/xslt" xmlns:string="xalan://java.lang.String"
	xmlns:java="http://xml.apache.org/xslt/java" exclude-result-prefixes="java">

	<xsl:output method="html" indent="yes" encoding="UTF-8"
		doctype-public="-//W3C//DTD HTML 4.01 Transitional//EN" />

	<xsl:param name="NAME">
		<xsl:value-of
			select="java:java.lang.System.getProperty('PROJECT_NAME')" />
	</xsl:param>
	
	<xsl:decimal-format decimal-separator="."
		grouping-separator="," />
	<xsl:param name="TITLE">
		<xsl:value-of select="$NAME" /> Report
	</xsl:param>
	<xsl:param name="NOW">
		<xsl:value-of
			select="java:format(java:java.text.SimpleDateFormat.new('MMMM d, yyyy HH:mm:ss'), java:java.util.Date.new())" />
	</xsl:param>

	<xsl:template match="testsuites">
		<html>
			<head>
				<title>
					<xsl:value-of select="$TITLE" />
				</title>
				<style type="text/css">
					body, table {
						font-family: verdana,arial,helvetica;
						font-size: 12px;
					}
					body {
						color: #000000;
						margin: 20px;
					}
					#title {
						margin-bottom: 30px;
					}
					#logo {
						position: absolute;
						top: 20px;
						right: 20px;
						height: 50px;
					}
					table {
						width: 100%;
					}
					table.details tr th {
						font-weight: bold;
						text-align: left;
						background: #a6caf0;
					}
					table.details tr td {
						background: #eeeee0;
						word-wrap: break-word;
						white-space: pre-wrap;
						max-width: 500px;
					}
					td[nowrap] {
						white-space: nowrap !important;
					}
					p {
						line-height: 1.5em;
						margin-top: 0.5em;
						margin-bottom: 1.0em;
					}
					h1 {
						margin: 0px 0px 5px;
					}
					h2 {
						margin-top: 1em;
						margin-bottom: 0.5em;
					}
					h3, h4, h5, h6 {
						margin-bottom: 0.5em;
					}
					.Success {
						font-weight: bold;
						color: green;
					}
					.Error {
						font-weight: bold;
						color: red;
					}
					.Failure {
						font-weight: bold;
						color: purple;
					}
				</style>
			</head>
			<body>
				<a name="top"></a>
				<xsl:call-template name="pageHeader" />

				<!-- Summary part -->
				<xsl:call-template name="summary" />
				<hr align="left" />

				<!-- Package List part -->
				<xsl:call-template name="packagelist" />
				<hr align="left" />

				<!-- For each package create its part -->
				<xsl:call-template name="packages" />
				<hr align="left" />

				<!-- For each class create the part -->
				<xsl:call-template name="testcases" />

			</body>
		</html>
	</xsl:template>



	<!-- ================================================================== -->
	<!-- Write a list of all packages with an hyperlink to the anchor of -->
	<!-- of the package name. -->
	<!-- ================================================================== -->
	<xsl:template name="packagelist">
		<h2>Project</h2>
		<table class="details" border="0" cellpadding="5" cellspacing="2">
			<xsl:call-template name="project.test.header" />
			<!-- list all packages recursively -->
			<xsl:for-each
				select="./testsuite[not(./@package = preceding-sibling::testsuite/@package)]">
				<xsl:sort select="@package" />
				<xsl:variable name="testsuites-in-package"
					select="/testsuites/testsuite[./@package = current()/@package]" />
				<xsl:variable name="testCount" select="sum($testsuites-in-package/@tests)" />
				<xsl:variable name="errorCount"
					select="sum($testsuites-in-package/@errors)" />
				<xsl:variable name="failureCount"
					select="sum($testsuites-in-package/@failures)" />
				<xsl:variable name="skippedCount"
					select="sum($testsuites-in-package/@skipped)" />
				<xsl:variable name="timeCount" select="sum($testsuites-in-package/@time)" />
				<xsl:variable name="passedCount" select="$testCount - $errorCount - $failureCount - $skippedCount" />

				<!-- write a summary for the package -->
				<tr valign="top">
					<!-- set a nice color depending if there is an error/failure -->
					<xsl:attribute name="class">
						<xsl:choose>
							<xsl:when test="$failureCount &gt; 0">Failure</xsl:when>
							<xsl:when test="$errorCount &gt; 0">Error</xsl:when>
						</xsl:choose>
					</xsl:attribute>
					<td>
						<a href="#{$NAME}">
							<xsl:value-of select="$NAME" />
						</a>
					</td>
					<td>
						<xsl:value-of select="$testCount" />
					</td>
					<td>
						<xsl:value-of select="$passedCount" />
					</td>
					<td>
						<xsl:value-of select="$errorCount" />
					</td>
					<td>
						<xsl:value-of select="$failureCount" />
					</td>
					<td>
						<xsl:value-of select="$skippedCount" />
					</td>
					<td>
						<xsl:call-template name="display-time">
							<xsl:with-param name="value" select="$timeCount" />
						</xsl:call-template>
					</td>
				</tr>
			</xsl:for-each>
		</table>
	</xsl:template>


	<!-- ================================================================== -->
	<!-- Write a package level report -->
	<!-- It creates a table with values from the document: -->
	<!-- Name | Tests | Errors | Failures | Time -->
	<!-- ================================================================== -->
	<xsl:template name="packages">
		<!-- create an anchor to this package name -->
		<xsl:for-each
			select="/testsuites/testsuite[not(./@package = preceding-sibling::testsuite/@package)]">
			<xsl:sort select="@package" />
			<a name="{@package}"></a>
			<h3>
				Project: <xsl:value-of select="$NAME" />
			</h3>

			<table class="details" border="0" cellpadding="5" cellspacing="2">
				<xsl:call-template name="testsuite.test.header" />

				<!-- match the testsuites of this package -->
				<xsl:apply-templates
					select="/testsuites/testsuite[./@package = current()/@package]"
					mode="print.test">
					<xsl:sort select="@timestamp" />
				</xsl:apply-templates>
			</table>
			<p />
			<a href="#top">Back to top</a>
			<p />
			<p />
		</xsl:for-each>
	</xsl:template>
	
	<xsl:template name="testcases">
		<xsl:for-each
			select="/testsuites/testsuite">
			<xsl:sort select="@timestamp" />
			<xsl:variable name="testsuite"
					select="/testsuites/testsuite[./@name = current()/@name]" />
					
			<h3 id="{$testsuite/@name}" style="background: #df245c; color: white; padding: 10px;">
				 TestSuite: <xsl:value-of select="$testsuite/@name" />
			</h3>

			<xsl:for-each
				select="$testsuite/testcase[./@parent = $testsuite/@name][not(./@classname = preceding-sibling::testcase/@classname)]">
				<xsl:variable name="testcase"
					select="$testsuite/testcase[./@classname = current()/@classname]" />
				<a name="$testcase/@classname"></a>
				<h4>
					TestCase: <xsl:value-of select="$testcase/@classname" />
				</h4>
	
				<table class="details" border="0" cellpadding="5" cellspacing="2">
					<xsl:call-template name="teststep.test.header" />
	

					<xsl:apply-templates
						select="$testcase"
						mode="print.test" />
				</table>
				<p />
				<a href="#top">Back to top</a>
				<p />
				<p />
			</xsl:for-each>
		</xsl:for-each>
	</xsl:template>

	<xsl:template name="summary">
		<h2>Summary</h2>
		<xsl:variable name="testCount" select="sum(testsuite/@tests)" />
		<xsl:variable name="errorCount" select="sum(testsuite/@errors)" />
		<xsl:variable name="failureCount" select="sum(testsuite/@failures)" />
		<xsl:variable name="skippedCount" select="sum(testsuite/@skipped)" />
		<xsl:variable name="timeCount" select="sum(testsuite/@time)" />
		<xsl:variable name="successRate"
			select="($testCount - $failureCount - $errorCount) div $testCount" />
		<table class="details" border="0" cellpadding="5" cellspacing="2">
			<tr valign="top">
				<th>Tests</th>
				<th>Failures</th>
				<th>Errors</th>
				<th>Skipped</th>
				<th>Success rate</th>
				<th>Time</th>
			</tr>
			<tr valign="top">
				<xsl:attribute name="class">
				<xsl:choose>
					<xsl:when test="$failureCount &gt; 0">Failure</xsl:when>
					<xsl:when test="$errorCount &gt; 0">Error</xsl:when>
				</xsl:choose>
			</xsl:attribute>
				<td>
					<xsl:value-of select="$testCount" />
				</td>
				<td>
					<xsl:value-of select="$failureCount" />
				</td>
				<td>
					<xsl:value-of select="$errorCount" />
				</td>
				<td>
					<xsl:value-of select="$skippedCount" />
				</td>
				<td>
					<xsl:call-template name="display-percent">
						<xsl:with-param name="value" select="$successRate" />
					</xsl:call-template>
				</td>
				<td>
					<xsl:call-template name="display-time">
						<xsl:with-param name="value" select="$timeCount" />
					</xsl:call-template>
				</td>

			</tr>
		</table>
		<table border="0">
			<tr>
				<td style="text-align: justify;">
					Note:
					<i>failures</i>
					are anticipated and checked for with assertions while
					<i>errors</i>
					are unanticipated.
				</td>
			</tr>
		</table>
	</xsl:template>

	<!-- Page HEADER -->
	<xsl:template name="pageHeader">
		<h1 id="title">
			<xsl:value-of select="$TITLE" />
			from
			<xsl:value-of select="$NOW" />
		</h1>
		<img
			src="data:image/png;base64,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"
			id="logo" />
		<hr />
	</xsl:template>

	<!-- class header -->
	<xsl:template name="project.test.header">
		<tr valign="top">
			<th width="80%">Name</th>
			<th>Tests</th>
			<th>Passed</th>
			<th>Errors</th>
			<th>Failures</th>
			<th>Skipped</th>
			<th nowrap="nowrap">Time(s)</th>
		</tr>
	</xsl:template>
	
	<!-- class header -->
	<xsl:template name="testsuite.test.header">
		<tr valign="top">
			<th width="80%">TestSuite</th>
			<th>Tests</th>
			<th>Passed</th>
			<th>Errors</th>
			<th>Failures</th>
			<th>Skipped</th>
			<th nowrap="nowrap">Time(s)</th>
			<th nowrap="nowrap">Time Stamp</th>
		</tr>
	</xsl:template>

	<!-- method header -->
	<xsl:template name="teststep.test.header">
		<tr valign="top">
			<th>TestStep</th>
			<th>Status</th>
			<th>Type</th>
			<th>Time(s)</th>
		</tr>
	</xsl:template>


	<!-- class information -->
	<xsl:template match="testsuite" mode="print.test">
		<tr valign="top">
			<!-- set a nice color depending if there is an error/failure -->
		<xsl:attribute name="class">
			<xsl:choose>
				<xsl:when test="@failures[.&gt; 0]">Failure</xsl:when>
				<xsl:when test="@errors[.&gt; 0]">Error</xsl:when>
			</xsl:choose>
		</xsl:attribute>
		
			<!-- print testsuite information -->
			<td>
				<a href="#{@name}">
					<xsl:value-of select="@name" />
				</a>
			</td>
			<td>
				<xsl:value-of select="@tests" />
			</td>
			<td>
				<xsl:value-of select="@tests - @errors - @failures - @skipped" />
			</td>
			<td>
				<xsl:value-of select="@errors" />
			</td>
			<td>
				<xsl:value-of select="@failures" />
			</td>
			<td>
				<xsl:value-of select="@skipped" />
			</td>
			<td>
				<xsl:call-template name="display-time">
					<xsl:with-param name="value" select="@time" />
				</xsl:call-template>
			</td>
			<td nowrap="nowrap">
				<xsl:value-of select="@timestamp" />
			</td>
		</tr>
	</xsl:template>

	<xsl:template match="testcase" mode="print.test">
		<tr valign="top">
			<xsl:attribute name="class">
			<xsl:choose>
				<xsl:when test="failure | error">Error</xsl:when>
			</xsl:choose>
		</xsl:attribute>
			<td>
				<b><xsl:value-of select="@classname" /></b>
				<xsl:choose>
					<xsl:when test="string-length(@name) &gt; 0"> :: <xsl:value-of select="@name" /></xsl:when>
				</xsl:choose>
			</td>
			<xsl:choose>
				<xsl:when test="failure">
					<td>Failure</td>
					<td>
						<xsl:apply-templates select="failure" />
					</td>
				</xsl:when>
				<xsl:when test="error">
					<td>Error</td>
					<td>
						<xsl:apply-templates select="error" />
					</td>
				</xsl:when>
				<xsl:when test="skipped">
					<td>Skipped</td>
					<td>
						<xsl:apply-templates select="skipped" />
					</td>
				</xsl:when>
				<xsl:otherwise>
					<td class="Success">Success</td>
					<td class="Error">
						<xsl:apply-templates select="system-err" />
					</td>
				</xsl:otherwise>
			</xsl:choose>
			<td>
				<xsl:call-template name="display-time">
					<xsl:with-param name="value" select="@time" />
				</xsl:call-template>
			</td>
		</tr>
	</xsl:template>


	<xsl:template match="failure">
		<xsl:call-template name="display-failures" />
	</xsl:template>

	<xsl:template match="error">
		<xsl:call-template name="display-failures" />
	</xsl:template>

	<xsl:template match="skipped">
		<xsl:call-template name="display-failures" />
	</xsl:template>
	
	<xsl:template match="system-err">
		<xsl:call-template name="display-failures" />
	</xsl:template>

	<!-- Style for the error, failure and skipped in the testcase template -->
	<xsl:template name="display-failures">
		<xsl:choose>
			<xsl:when test="not(@message)"></xsl:when>
			<xsl:otherwise>
				<xsl:value-of select="@message" />
				<br />
				<br />
			</xsl:otherwise>
		</xsl:choose>
		<!-- display the stacktrace -->
		<code>
			<xsl:call-template name="br-replace">
				<xsl:with-param name="word" select="." />
			</xsl:call-template>
		</code>
		<!-- the later is better but might be problematic for non-21" monitors... -->
		<!--pre><xsl:value-of select="."/></pre -->
	</xsl:template>

	<!-- template that will convert a carriage return into a br tag @param word 
		the text from which to convert CR to BR tag -->
	<xsl:template name="br-replace">
		<xsl:param name="word" />
		<xsl:choose>
			<xsl:when test="contains($word, '&#xa;')">
				<xsl:value-of select="substring-before($word, '&#xa;')" />
				<br />
				<xsl:call-template name="br-replace">
					<xsl:with-param name="word"
						select="substring-after($word, '&#xa;')" />
				</xsl:call-template>
			</xsl:when>
			<xsl:otherwise>
				<xsl:value-of select="$word" />
			</xsl:otherwise>
		</xsl:choose>
	</xsl:template>

	<xsl:template name="display-time">
		<xsl:param name="value" />
		<xsl:value-of select="format-number($value,'0.000')" />
	</xsl:template>

	<xsl:template name="display-percent">
		<xsl:param name="value" />
		<xsl:value-of select="format-number($value,'0.00%')" />
	</xsl:template>

</xsl:stylesheet>