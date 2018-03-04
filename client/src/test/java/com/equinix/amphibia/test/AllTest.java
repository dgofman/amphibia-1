package com.equinix.amphibia.test;

/**
 *
 * @author dgofman
 */

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

public class AllTest extends TestCase {

    public AllTest(String name) {
        super(name);
    }

    public static Test suite() {
        TestSuite suite = new TestSuite();
        suite.addTest(AmphibiaTest.suite());
        return suite;
    }
}