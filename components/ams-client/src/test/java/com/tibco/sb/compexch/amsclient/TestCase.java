package com.tibco.sb.compexch.amsclient;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.streambase.sb.unittest.SBServerManager;
import com.streambase.sb.unittest.ServerManagerFactory;

/**
 * Example test case
 */
public class TestCase {

    private static SBServerManager server;

    /**
     * Set up the server
     * @throws Exception Initialization failed
     */
    @BeforeClass
    public static void setupServer() throws Exception {
        // create a StreamBase server and load applications once for all tests in this class
        server = ServerManagerFactory.getEmbeddedServer();
        server.startServer();
        server.loadApp("com.tibco.sb.compexch.amsclient.ams-client");
    }

    /**
     * Stop the server
     * @throws Exception Shutdown failed
     */
    @AfterClass
    public static void stopServer() throws Exception {
        if (server != null) {
            server.shutdownServer();
            server = null;
        }
    }

    /**
     * Start the containers
     * @throws Exception Container startup error
     */
    @Before
    public void startContainers() throws Exception {
        // before each test, startup fresh container instances
        server.startContainers();
    }

    /**
     * test cases
     * @throws Exception Test failure
     */
    @Test
    public void test() throws Exception {
    }

    /**
     * Stop containers
     * @throws Exception Container stop failed
     */
    @After
    public void stopContainers() throws Exception {
        // after each test, dispose of the container instances
        server.stopContainers();
    }
}
