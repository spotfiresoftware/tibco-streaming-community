package com.tibco.sb.compexch.redisadapter;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.streambase.sb.unittest.Expecter;
import com.streambase.sb.unittest.JSONSingleQuotesTupleMaker;
import com.streambase.sb.unittest.SBServerManager;
import com.streambase.sb.unittest.ServerManagerFactory;

public class TestRedisAdapters {

	private static SBServerManager server;

	@BeforeClass
	public static void setupServer() throws Exception {
		// create a StreamBase server and load applications once for all tests in this class
		server = ServerManagerFactory.getEmbeddedServer();
		server.startServer();
		server.loadApp("TestRedisAdapters.sbapp");
	}

	@AfterClass
	public static void stopServer() throws Exception {
		if (server != null) {
			server.shutdownServer();
			server = null;
		}
	}

	@Before
	public void startContainers() throws Exception {
		// before each test, startup fresh container instances
		server.startContainers();
	}

	@Test
	public void testx1() throws Exception {
		server.getEnqueuer("ToRedis").enqueue(JSONSingleQuotesTupleMaker.MAKER,
				"{'key':'x','value':'1'}");
		new Expecter(server.getDequeuer("FromRedis"))
				.expect(JSONSingleQuotesTupleMaker.MAKER,
						"{'channel':'x','value':'1'}");
	}

	@After
	public void stopContainers() throws Exception {
		// after each test, dispose of the container instances
		server.stopContainers();
	}

}
