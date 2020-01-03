package com.tibco.sb.compexch.flowershop;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.streambase.sb.StreamBaseException;
import com.streambase.sb.TimeService;
import com.streambase.sb.runtime.ControllableTimeService;
import com.streambase.sb.unittest.Expecter;
import com.streambase.sb.unittest.JSONSingleQuotesTupleMaker;
import com.streambase.sb.unittest.SBServerManager;
import com.streambase.sb.unittest.ServerManagerFactory;
import com.tibco.ep.testing.framework.Configuration;
import com.tibco.ep.testing.framework.ConfigurationException;
import com.tibco.ep.testing.framework.TransactionalDeadlockDetectedException;
import com.tibco.ep.testing.framework.TransactionalMemoryLeakException;
import com.tibco.ep.testing.framework.UnitTest;

/**
 * FlowerShop testcase
 */
public class TestFlowerShop extends UnitTest {

    private static SBServerManager server;

    private static TimeService timeService;

    /**
     * Set up the server
     *
     * @throws StreamBaseException on start server error
     * @throws ConfigurationException on configuration failure
     * @throws InterruptedException on start server error
     */
    @BeforeClass
    public static void setupServer() throws StreamBaseException, ConfigurationException, InterruptedException {
        // create a StreamBase server and load applications once for all tests in this class
        server = ServerManagerFactory.getEmbeddedServer();
        server.startServer();
        
        // Create a ControllableTimeService to use as the server's time service
       timeService = new ControllableTimeService();
       server.setTimeService(timeService);
        
       server.loadApp("com.tibco.sb.compexch.flowershop.FlowerShop");
    }

    /**
     * Stop the server
     *
     * @throws StreamBaseException on shutdown failure
     * @throws InterruptedException on shutdown failure
     */
    @AfterClass
    public static void stopServer() throws InterruptedException, StreamBaseException {
        try {
            if (server != null) {
                server.shutdownServer();
                server = null;
            }
        } finally {
            Configuration.deactiveAndRemoveAll();
        }
    }

    /**
     * Start the containers
     *
     * @throws StreamBaseException on start container error
     */
    @Before
    public void startContainers() throws StreamBaseException {
        // before each test, startup fresh container instances
        server.startContainers();

        // Setup test framework before running tests
        this.initialize();
    }

    /**
     * test case
     */
    @Test
    public void testHappyPath() throws Exception {
        // ********** Phase1 Bid ********** 
        // Driver 1 parks at store 1
        server.getEnqueuer("GPSLocationEvent").enqueue(
            JSONSingleQuotesTupleMaker.MAKER,
            "{'driver_id':1,'raw_location':{'latitude':-80.0,'longitude':-80.0}}"
        );

        // 20 Delivery request from store 1
        List<String> inputs;
        inputs  = new ArrayList<>();
        for (int i = 0; i < 22; i++) {
            inputs.add( "{'store_id':1}");
        }
        server.getEnqueuer("DeliveryRequestEvent").enqueue(
            JSONSingleQuotesTupleMaker.MAKER,
            inputs
        );

        // Driver 1 gets the bid request
        List<String> outputs;
        outputs = new ArrayList<>();
        for (int i = 1; i < 22; i++) {
            outputs.add(String.format("{'bid_request':{'driver_id':1,'store_id':1,'delivery_request_id':%d}}", i));
        }
        new Expecter(server.getDequeuer("BidRequestOut")).expectUnordered(
            JSONSingleQuotesTupleMaker.MAKER,
            outputs
        );

        // ********** Phase2 Assignment ********** 
        // Driver 1 responds to bidder
        inputs = new ArrayList<>();
        for (int i = 1; i < 22; i++) {
            inputs.add(String.format("{'driver_id':1,'delivery_request_id':%d,'pickup_time_committed':'60'}", i));
        }
        server.getEnqueuer("DeliveryBidEvent").enqueue(
            JSONSingleQuotesTupleMaker.MAKER,
            inputs
        );
        
        timeService.advanceBy(21, TimeUnit.SECONDS); // 21 = 20(default ASSIGN_DELAY) + 1(TickOutstandingBids interval)
        // Manually Assign output
        outputs = new ArrayList<>();
        for (int i = 1; i < 22; i++) {
            outputs.add(String.format("{'delivery_request_id':%d,'store_id':1,'top5_drivers':[{'current_driver_ranking':3,'current_driver_bid':{'driver_id':1,'pickup_time_committed':'60'}}]}", i));
        }
        new Expecter(server.getDequeuer("ManualAssignOut")).expectUnordered(
            JSONSingleQuotesTupleMaker.MAKER,
            outputs
        );

        // Store select driver
        inputs = new ArrayList<>();
        for (int i = 1; i < 22; i++) {
            inputs.add(String.format("{'store_id':1,'delivery_request_id':%d,'driver_id':1,'committed_time':'60'}", i));
        }
        server.getEnqueuer("StoreDriverSelection").enqueue(
            JSONSingleQuotesTupleMaker.MAKER,
            inputs
        );

        // ********** Phase3 Delivery Process **********
        // Store confirms pickup
        timeService.advanceBy(24, TimeUnit.SECONDS);  // 21 + 24 = 45, 15 seconds before pickup_time_committed
        inputs = new ArrayList<>();
        for (int i = 1; i < 22; i++) {
            inputs.add(String.format("{'store_id':1,'delivery_request_id':%d}", i));
        }
        server.getEnqueuer("PickUpConfirmationEvent").enqueue(
            JSONSingleQuotesTupleMaker.MAKER,
            inputs
        );

        // Customer confirms deliver
        timeService.advanceBy(1, TimeUnit.MINUTES);
        inputs = new ArrayList<>();
        for (int i = 0; i < 22; i++) {
            inputs.add(String.format("{'driver_id':1,'delivery_request_id':%d}", i));
        }
        server.getEnqueuer("DeliveryConfirmationEvent").enqueue(
            JSONSingleQuotesTupleMaker.MAKER,
            inputs
        );

        // ********** Phase4 Ranking Evaluation ********** 
        new Expecter(server.getDequeuer("RankingIncrease")).expect(
            JSONSingleQuotesTupleMaker.MAKER,
            "{'driver_id':1}"
        );
        new Expecter(server.getDequeuer("ImprovementNote")).expectNothing();
        new Expecter(server.getDequeuer("RankingDecrease")).expectNothing();

        // ********** Phase5 Activity Monitoring ********** 
        timeService.advanceBy(1, TimeUnit.DAYS);
        new Expecter(server.getDequeuer("MonthlySummary")).expect(
            JSONSingleQuotesTupleMaker.MAKER,
            "{'DriverID':1,'IsWeak':false,'IsIdle':true,'IsConsistentWeak':false,'IsConsistentStrong':false,'IsImproving':false}"
        );
    }

    /**
     * Stop containers
     *
     * @throws StreamBaseException on stop container error
     * @throws TransactionalMemoryLeakException Leak detected
     * @throws TransactionalDeadlockDetectedException Deadlock detected
     */
    @After
    public void stopContainers() throws StreamBaseException, TransactionalMemoryLeakException, TransactionalDeadlockDetectedException {
        // Complete test framework and check for any errors
        this.complete();

        // after each test, dispose of the container instances
        server.stopContainers();
    }
}
