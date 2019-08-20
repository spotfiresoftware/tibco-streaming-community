package com.tibco.ep.sp.contrib.smileoperators;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.streambase.sb.StreamBaseException;
import com.streambase.sb.Tuple;
import com.streambase.sb.unittest.CSVTupleMaker;
import com.streambase.sb.unittest.Dequeuer;
import com.streambase.sb.unittest.Enqueuer;
import com.streambase.sb.unittest.Expecter;
import com.streambase.sb.unittest.SBServerManager;
import com.streambase.sb.unittest.ServerManagerFactory;

import com.tibco.ep.testing.framework.Configuration;
import com.tibco.ep.testing.framework.ConfigurationException;
import com.tibco.ep.testing.framework.TransactionalDeadlockDetectedException;
import com.tibco.ep.testing.framework.TransactionalMemoryLeakException;
import com.tibco.ep.testing.framework.UnitTest;

import smile.stat.hypothesis.CorTest;

/**
 * Example test case
 */
public class CorrTest extends UnitTest {

    @SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(CorrTest.class);

    private static SBServerManager server;

    /**
     * Set up the server
     *
     * @throws StreamBaseException on start server error
     * @throws ConfigurationException on configuration failure
     * @throws InterruptedException on start server error
     */
    @BeforeClass
    public static void setupServer() throws StreamBaseException, ConfigurationException, InterruptedException {
        // Example configuration load
        // Configuration.forFile("engine.conf").load().activate();

        // create a StreamBase server and load modules once for all tests in this class
        server = ServerManagerFactory.getEmbeddedServer();
        server.startServer();
        server.loadApp("com.tibco.ep.sp.contrib.smileoperators.CorrelationTests");
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
            assertNotNull(server);
            server.shutdownServer();
            server = null;
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
     * Simple Pearson test.
     */
    @Test
    public void testPearson() throws Exception {
    	double[] y = {1, 2, 3, 4};
    	double[][] x = {{1, 2, 3, 4}, {2, 4, 1, 3}};
    	
    	Dequeuer dequeuer = server.getDequeuer("OutPearson");
    	Expecter expecter = new Expecter(dequeuer);
    	Enqueuer enqueuer = server.getEnqueuer("InPearson");
    	enqueuer.enqueue(CSVTupleMaker.MAKER, "1, \"[1,2]\"");
    	enqueuer.enqueue(CSVTupleMaker.MAKER, "2, \"[2,4]\"");
    	enqueuer.enqueue(CSVTupleMaker.MAKER, "3, \"[3,1]\"");
    	enqueuer.enqueue(CSVTupleMaker.MAKER, "4, \"[4,3]\"");
    	expecter.expect(CSVTupleMaker.MAKER, "\"[0.0,0.0]\"");
    	for (int i = 3; i <= 4; i++) {
    		double[] expect = new double[2];
        	expect[0] = CorTest.pearson(Arrays.copyOfRange(y, 0, i), Arrays.copyOfRange(x[0], 0, i)).cor;
        	expect[1] = CorTest.pearson(Arrays.copyOfRange(y, 0, i), Arrays.copyOfRange(x[1], 0, i)).cor;
        	expect = roundDecimal(expect, 3);
        	String makerString = "\"[" + expect[0] + "," + expect[1] + "]\"";
        	expecter.expect(CSVTupleMaker.MAKER, makerString);
    	}
    }
    
    
    /**
     * Simple Spearman test. Checks consistency over a larger number of data points.
     */
    @Test
    public void testSpearman() throws Exception {
    	Scanner scan = new Scanner(new File(CorrTest.class.getResource("SimpleTestCoded.csv").toURI()));
    	scan.nextLine();
    	
    	double[] y = new double[200];
    	double[][] x = new double[10][200];
    	for (int i = 0; i < 200; i++) {
    		String[] tokens = scan.nextLine().split(",");
    		y[i] = Double.parseDouble(tokens[0]);
    		for (int j = 0; j < tokens.length - 1; j++) {
        		x[j][i] = Double.parseDouble(tokens[j + 1]);
    		}
    	}
    	
    	
    	Enqueuer enqueuer = server.getEnqueuer("InSpearman");
    	Dequeuer dequeuer = server.getDequeuer("OutSpearman");
    	Expecter expecter = new Expecter(dequeuer);
    	for (int i = 0; i < y.length; i++) {
    		String makerString = y[i] + ", \"[";
    		for (int j = 0; j < x.length - 1; j++) {
    			makerString += x[j][i] + ",";
    		}
    		makerString += x[x.length - 1][i] + "]\"";
        	enqueuer.enqueue(CSVTupleMaker.MAKER, makerString);
    	}

    	expecter.expect(CSVTupleMaker.MAKER, "\"[-1.0,-1.0,1.0,-1.0,-1.0,-1.0,1.0,1.0,1.0,1.0]\"");
    	for (int i = 3; i <= 200 ; i++) {
    		double[] expect = new double[x.length];
    		for (int j = 0; j < x.length; j++) {
    			expect[j] = CorTest.spearman(Arrays.copyOfRange(y, 0, i), Arrays.copyOfRange(x[j], 0, i)).cor;
    		}
        	expect = roundDecimal(expect, 3);
    		String makerString = "\"[";
    		for (int j = 0; j < x.length - 1; j++) {
    			makerString += expect[j] + ",";
    		}
    		makerString += expect[x.length - 1] + "]\"";
        	expecter.expect(CSVTupleMaker.MAKER, makerString);
    	}
    	scan.close();
    }
    
    /**
     * Simple Kendall test. Also checks that correlation values are correct after the sliding window starts sliding.
     * @throws Exception
     */
    @Test
    public void testKendall() throws Exception {
    	double[] y = {7, 18, -9, 23, 27, 45, 19, 37, 39, 47, 8, 14, -2, 34, -3, 41, 36, 43, 3, 42};
    	double[][] x = {{25, 6, 31, 32, 28, 33, 29, 20, 18, 45, 0, 40, 5, 34, -3, 41, 36, 43, 3, 42}, 
    			{5, 24, -1, 46, 31, 50, 22, 32, 16, -3, 29, 8, 44, 14, 19, 30, -9, 18, 33, 26}};
    	
    	Dequeuer dequeuer = server.getDequeuer("OutKendall");
    	Enqueuer enqueuer = server.getEnqueuer("InKendall");
    	Expecter expecter = new Expecter(dequeuer);
    	for (int i = 0; i < y.length; i++) {
    		String makerString = y[i] + ", \"[";
    		for (int j = 0; j < x.length - 1; j++) {
    			makerString += x[j][i] + ",";
    		}
    		makerString += x[x.length - 1][i] + "]\"";
    		enqueuer.enqueue(CSVTupleMaker.MAKER, makerString);
    	}

    	expecter.expect(CSVTupleMaker.MAKER, "\"[-1.0,1.0]\"");
    	for (int i = 3; i <= 20; i++) {
    		double[] expect = new double[x.length];
    		int floor = Math.max(0, i - 10);
    		for (int j = 0; j < x.length; j++) {
    			expect[j] = CorTest.kendall(Arrays.copyOfRange(y, floor, i), Arrays.copyOfRange(x[j], floor, i)).cor;
    		}
        	expect = roundDecimal(expect, 3);
    		String makerString = "\"[";
    		for (int j = 0; j < x.length - 1; j++) {
    			makerString += expect[j] + ",";
    		}
    		makerString += expect[x.length - 1] + "]\"";
        	expecter.expect(CSVTupleMaker.MAKER, makerString);
    	}
    }
    
    /**
     * Simple Cramer test. 
     * @throws Exception
     */
    @Test
    public void testCramer() throws Exception {
    	double[] y = {22, 2, 30, 21, 25, 14, 15, 16, 17, 19, 12, 1, 13, 5, 8, 31};
    	double[][] x = {{26, 6, 30, 11, 27, 15, 16, 20, 21, 22, 23, 3, 13, 7, 10, 31}, {9, 28, 31, 30, 2, 19, 5, 7, 27, 29, 1, 21, 3, 15, 17, 18}};
    	int[][][][] tables = {{{{1, 0}, {0, 1}}, {{2, 0}, {0, 1}}, {{2, 0}, {0, 2}}, {{3, 0}, {0, 2}}, {{3, 0}, {0, 3}}, {{4, 0}, {0, 3}}, {{3, 1}, {1, 3}}, 
    		{{4, 1}, {1, 3}}, {{4, 1}, {1, 4}}, {{5, 1}, {1, 4}}, {{5, 1}, {1, 5}}, {{6, 1}, {1, 5}}, {{6, 1}, {1, 6}}, {{7, 1}, {1, 6}}, {{7, 1}, {1, 7}}}, 
    		{{{0, 1}, {1, 0}}, {{2, 0}, {0, 1}}, {{1, 1}, {1, 1}}, {{2, 1}, {1, 1}}, {{1, 2}, {2, 1}}, {{2, 2}, {2, 1}}, {{2, 2}, {2, 2}}, 
    		{{3, 2}, {2, 2}}, {{3, 2}, {2, 3}}, {{4, 2}, {2, 3}}, {{4, 2}, {2, 4}}, {{5, 2}, {2, 4}}, {{4, 3}, {3, 4}}, {{5, 3}, {3, 4}}, {{5, 3}, {3, 5}}}};
    	
    	Dequeuer dequeuer = server.getDequeuer("OutCramer");
    	Enqueuer enqueuer = server.getEnqueuer("InCramer");
    	Expecter expecter = new Expecter(dequeuer);
    	for (int i = 0; i < y.length; i++) {
    		String makerString = y[i] + ", \"[";
    		for (int j = 0; j < x.length - 1; j++) {
    			makerString += x[j][i] + ",";
    		}
    		makerString += x[x.length - 1][i] + "]\"";
        	enqueuer.enqueue(CSVTupleMaker.MAKER, makerString);
    	}

    	for (int i = 0; i < 15; i++) {
    		double[] expect = new double[x.length];
    		for (int j = 0; j < x.length; j++) {
    			expect[j] = CorTest.chisq(tables[j][i]).cor;
    		}
        	expect = roundDecimal(expect, 3);
    		String makerString = "\"[";
    		for (int j = 0; j < x.length - 1; j++) {
    			makerString += expect[j] + ",";
    		}
    		makerString += expect[x.length - 1] + "]\"";
        	expecter.expect(CSVTupleMaker.MAKER, makerString);
    	}
    }
    
    /**
     * Kendall timing test. Checks that emissions that would pile up are instead stopped. 
     * This test uses Kendall specifically because it is the first to slow down to a noticeable degree
     */
    @Test
    public void testDelay() throws Exception {
    	Scanner scan = new Scanner(new File(CorrTest.class.getResource("toyTesting.csv").toURI()));
    	scan.nextLine();
    	int size = 20000;
    	double[] y = new double[size];
    	double[][] x = new double[2][size];
    	for (int i = 0; i < 200; i++) {
    		String[] tokens = scan.nextLine().replaceAll("\\]|\\[|\"", "").split(",");
    		y[i] = Double.parseDouble(tokens[0]); 
    		x[0][i] = Double.parseDouble(tokens[1]);
    		x[1][i] = Double.parseDouble(tokens[2]);
    	}
    	
    	
    	Enqueuer enqueuer = server.getEnqueuer("InBigKendall");
    	Dequeuer dequeuer = server.getDequeuer("OutBigKendall");
    	for (int i = 0; i < size / 2 + 1; i++) {
    		String makerString = y[i] + ", \"[";
    		for (int j = 0; j < x.length - 1; j++) {
    			makerString += x[j][i] + ",";
    		}
    		makerString += x[x.length - 1][i] + "]\"";
        	enqueuer.enqueue(CSVTupleMaker.MAKER, makerString);
    	}
    	List<Tuple> foo = dequeuer.dequeue(100, 1000, TimeUnit.MILLISECONDS);
    	for (int i = size / 2 + 1; i < size; i++) {
    		String makerString = y[i] + ", \"[";
    		for (int j = 0; j < x.length - 1; j++) {
    			makerString += x[j][i] + ",";
    		}
    		makerString += x[x.length - 1][i] + "]\"";
        	enqueuer.enqueue(CSVTupleMaker.MAKER, makerString);
    	}
    	foo = dequeuer.dequeue(100, 3000, TimeUnit.MILLISECONDS);
    	assertTrue(foo.size() < 3000 / 500);
    	scan.close();
    }
    
    @Test
    public void testTupleList() throws Exception {
    	Enqueuer enqueuer = server.getEnqueuer("InTupleList");
    	Dequeuer dequeuer = server.getDequeuer("OutTupleList");
    	Expecter expecter = new Expecter(dequeuer);
    	enqueueTuple(enqueuer);
    	expecter.expect(CSVTupleMaker.MAKER, "\"[\"\"One,0.624\"\",\"\"Two,-0.655\"\",\"\"Three,0.97\"\"]\"");
    	expecter.expect(CSVTupleMaker.MAKER, "\"[\"\"One,0.581\"\",\"\"Two,-0.408\"\",\"\"Three,0.944\"\"]\"");
    }
    
    @Test
    public void testTupleFields() throws Exception {
    	Enqueuer enqueuer = server.getEnqueuer("InTupleFields");
    	Dequeuer dequeuer = server.getDequeuer("OutTupleFields");
    	Expecter expecter = new Expecter(dequeuer);
    	enqueueTuple(enqueuer);
    	expecter.expect(CSVTupleMaker.MAKER, "0.624,-0.655,0.97");
    	expecter.expect(CSVTupleMaker.MAKER, "0.581,-0.408,0.944");
    }
    
    private void enqueueTuple(Enqueuer enqueuer) throws Exception {
		double[] value = {0, 1, 2, 0, 1, 2}; 
		double[] one = {5.1, 7.0, 6.3, 4.9, 6.4, 5.8};
		double[] two = {3.5, 3.2, 3.3, 3.0, 3.2, 2.7};
		double[] three = {1.4, 4.7, 6.0, 1.4, 4.5, 5.1};
    	for (int i = 0; i < value.length; i++) {
    		String makerString = value[i] + ", \"";
    		makerString += one[i] + ",";
    		makerString += two[i] + ",";
    		makerString += three[i] + "\"";
        	enqueuer.enqueue(CSVTupleMaker.MAKER, makerString);
    	}
    	
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
    
	private double[] roundDecimal(double[] numbers, int decimalPlaces) {
		int multiplier = (int) Math.pow(10, decimalPlaces);
		for (int i = 0; i < numbers.length; i++) {
			if (Double.isNaN(numbers[i])) continue;
			int scaled = (int) (numbers[i] * multiplier);
			if (scaled < 0) {
				double forth = numbers[i] * multiplier - scaled;
				if (forth <= -0.50) scaled -= 1;
			} else {
				double forth = numbers[i] * multiplier - scaled;
				if (forth >= 0.50) scaled += 1;
			}
			numbers[i] = scaled / (double) multiplier;
		}
		return numbers;
	}
}
