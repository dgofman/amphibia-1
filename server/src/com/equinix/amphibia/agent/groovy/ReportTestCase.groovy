package com.equinix.amphibia.agent.groovy;

import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.lang.StringUtils;

public class ReportTestCase {
	
	public static int MAX_TRACE_LENGTH = 15;
	
	public static enum State {
		SUCCESS,
		ERROR,
		FAIL,
		SKIPPED
	};

	private String testCaseName;
	private String className;
	private State state;
	private Throwable t;
	private String stackTrace;
	private long time;

	public ReportTestCase(String testCaseName, String className) {
		this.testCaseName = testCaseName;
		this.className = className;
		this.state = State.SKIPPED;
		this.stackTrace = "";
	}
	
	public void addException(State state, Throwable t) {
		addException(state, t, t.getStackTrace());
	}
 
	public void addException(State state, Throwable t, StackTraceElement[] stackTrace) {
		this.state = state;
		this.t = t;
		if (stackTrace != null) {
			this.stackTrace = StringUtils.join(t.getStackTrace(), "\n\t", 0, MAX_TRACE_LENGTH);
		}
	}
	
	public void success() {
		this.state = State.SUCCESS;
	}
	
	public State getState() {
		return this.state;
	}
	
	public String getType() {
		return t.getClass().getName();
	}
	
	public String getMessage() {
		return StringEscapeUtils.escapeHtml(t.getMessage());
	}
	
	public String getStackTrace() {
		return stackTrace;
	}
	
	public String getClassName() {
		return className;
	}
	
	public String getTestCaseName() {
		return testCaseName;
	}
	
	public String getTagName() {
		return tagName;
	}
	
	public String getTime() {
		return String.valueOf(time / 1000);
	}

	public void setTime(long time) {
		this.time = time;
	}
	
	@Override
	public String toString() {
		return "[" + className + "] " + testCaseName + " (" + state + ")";
	}
}