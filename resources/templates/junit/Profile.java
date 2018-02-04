import java.lang.reflect.Method;
import java.util.logging.Level;

import org.junit.internal.AssumptionViolatedException;
import org.junit.runner.Description;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runners.model.InitializationError;

public class Profile {

	public static void main(String[] args) throws InitializationError {
		AmphibiaBaseTest.loggerLevel = Level.OFF;
		AmphibiaBaseTest.watcher = new AmphibiaBaseTest.JUnitTestWatcher() {
			@Override
			public void skipped(Description description, AssumptionViolatedException e) {
				System.out.println("skipped: " + AmphibiaBaseTest.getDetails(description));
			}

			@Override
			public void failed(Description description, Throwable e) {
				System.out.println("failed: " +  AmphibiaBaseTest.getDetails(description));
			}

			@Override
			public void succeeded(Description description) {
				System.out.println("succeeded: " + AmphibiaBaseTest.getDetails(description));
			}
			
			@Override
			public void onError(AmphibiaBaseTest.Response response, Method method) {
				System.out.println(AmphibiaBaseTest.getDetails(method) + "\n"+  response.output);
			}
		};

		Result result = JUnitCore.runClasses(EquinixOrder.getAllTests());
		System.out.println("FAILED: " + result.getFailureCount());
	}
}