import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.annotation.Retention;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.internal.AssumptionViolatedException;
import org.junit.rules.TestName;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

import net.sf.json.JSONArray;

public class AmphibiaBaseTest {

	public static JUnitTestWatcher watcher = new JUnitTestWatcher();
	public static Level loggerLevel = Level.ALL;


	@Rule
	public TestName testName = new TestName();

	protected final Logger logger;

	public AmphibiaBaseTest() {
		super();
		logger = Logger.getLogger(getClass().getName());
		ConsoleHandler handler = new ConsoleHandler();
		handler.setLevel(loggerLevel);
		handler.setFormatter(new SimpleFormatter());
		logger.addHandler(handler);
		// System.setProperty("javax.net.debug", "all");
	}

	public static String getDetails(Method method) {
		return getDetails(method.getAnnotation(Details.class));
	}

	public static String getDetails(Description description) {
		return getDetails(description.getAnnotation(Details.class));
	}

	public static String getDetails(Details details) {
		return details != null ? details.description() : "";
	}

	@Rule
	public TestWatcher testWatchThis = new TestWatcher() {
		@Override
		protected void skipped(AssumptionViolatedException e, Description description) {
			watcher.skipped(description, e);
		}

		@Override
		protected void failed(Throwable e, Description description) {
			watcher.failed(description, e);
		}

		@Override
		protected void succeeded(Description description) {
			watcher.succeeded(description);
		}
	};

	protected void onError(Response response) throws Exception {
		if (response.exception != null) {
			Method method = this.getClass().getDeclaredMethod(testName.getMethodName(), new Class[] {});
			watcher.onError(response, method);
			logger.log(Level.SEVERE, response.output.toString(), response.exception);
			throw response.exception;
		}
	}

	protected Response request(URL url, String method, String body) {
		return request(url, method, body, null);
	}

	protected void assertEquals(Object expected, Object actual) {
		Assert.assertEquals(expected, actual);
	}

	protected void assertTrue(boolean condition) {
		Assert.assertTrue(condition);
	}

	protected Response request(URL url, String method, String body, Object[][] headers) {
		Response response = new Response();
		HttpURLConnection conn = null;
		BufferedReader in = null;
		logger.info(url + "\n" + JSONArray.fromObject(headers).toString() + "\n" + body);
		try {
			PrintWriter pw = new PrintWriter(response.output);
			conn = (HttpURLConnection) url.openConnection();
			conn.setDoInput(true);
			conn.setDoOutput(true);
			conn.setRequestMethod(method);
			conn.setRequestProperty("Accept", "*/*");

			if (headers != null) {
				for (Object[] item : headers) {
					conn.setRequestProperty(item[0].toString().toLowerCase(), String.valueOf(item[1]));
				}
			}

			if (body != null && !body.isEmpty()) {
				conn.getOutputStream().write(body.getBytes("UTF-8"));
			}

			InputStream content;
			try {
				content = (InputStream) conn.getInputStream();
			} catch (Exception e) {
				response.isError = true;
				response.exception = e;
				content = (InputStream) conn.getErrorStream();
			}
			if (content != null) {
				in = new BufferedReader(new InputStreamReader(content));
				String line;
				while ((line = in.readLine()) != null) {
					pw.println(line);
				}
				in.close();
			}
		} catch (Exception e) {
			response.isError = true;
			response.exception = e;
		} finally {
			try {
				response.statusCode = conn.getResponseCode();
			} catch (IOException e) {
				e.printStackTrace();
			}
			response.header = conn.getHeaderFields();
			conn.disconnect();
		}
		return response;
	}

	public static class Response {
		public boolean isError = false;
		public Integer statusCode = -1;
		public Exception exception = null;
		public Map<String, List<String>> header;
		public StringWriter output = new StringWriter();
	}

	public static class JUnitTestWatcher {

		public HttpURLConnection getConnection(URL url) throws IOException {
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setConnectTimeout(getConnectTimeout());
			conn.setReadTimeout(getReadTimeout());
			return conn;
		}

		public int getConnectTimeout() {
			return 10000;
		}

		public int getReadTimeout() {
			return 30000;
		}

		protected void onError(Response response, Method method) {
		}

		public void skipped(Description description, AssumptionViolatedException e) {
		}

		public void failed(Description description, Throwable e) {
		}

		public void succeeded(Description description) {
		}
	}

	@Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
	public @interface Details {
		public String description() default "";
	}
}