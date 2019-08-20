package com.tibco.ep.sp.contrib.smileoperators;

import java.io.File;
import java.util.Scanner;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.streambase.sb.unittest.CSVTupleMaker;
import com.streambase.sb.unittest.Enqueuer;
import com.streambase.sb.unittest.Expecter;
import com.streambase.sb.unittest.SBServerManager;
import com.streambase.sb.unittest.ServerManagerFactory;

public class ClassifTest {
	private static int[] irisLabels;
	private static double[][] irisData;

	private static SBServerManager server;

	@BeforeClass
	public static void setupServer() throws Exception {
		// create a StreamBase server and load applications once for all tests in this class
		server = ServerManagerFactory.getEmbeddedServer();
		server.startServer();
		server.loadApp("com.tibco.ep.sp.contrib.smileoperators.ClassificationTests");
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
	
	@Before
	public void loadIris() throws Exception {		
		int size = 150;
		Scanner iris = new Scanner(new File(ClassifTest.class.getResource("iris.csv").toURI()));
		iris.nextLine();
		irisLabels = new int[size];
		irisData = new double[4][size];
		for (int i = 0; i < size; i++) {
			String[] tokens = iris.nextLine().replaceAll("\\[|\\]|\"", "").split(",");
			irisLabels[i] = Integer.parseInt(tokens[4]);
			for (int j = 0; j < tokens.length - 1; j++) {
	    		irisData[j][i] = Double.parseDouble(tokens[j]);
			}
		}
		iris.close();
	}

	@Test
	// KNN test. Also checks that early predicts don't work.
	public void testKNN() throws Exception {
		Enqueuer predict = server.getEnqueuer("predictKNN");
		Enqueuer train = server.getEnqueuer("trainKNN");
		Expecter expecter = new Expecter(server.getDequeuer("OutKNN"));
		predict.enqueue(CSVTupleMaker.MAKER, "\"[1,2]\"");
		basicInput(train);
		train.enqueue(CSVTupleMaker.MAKER, "null, \"[null,null]\"");
		basicTest(predict, expecter);
	}
	
	@Test
	// LDA test. Also checks that model errors but doesn't crash on partially null tuples.
	public void testLDA() throws Exception {
		Enqueuer predict = server.getEnqueuer("predictLDA");
		Enqueuer train = server.getEnqueuer("trainLDA");
		Expecter expecter = new Expecter(server.getDequeuer("OutLDA"));
		basicInput(train);
		train.enqueue(CSVTupleMaker.MAKER, "0, \"[null,null]\"");
		train.enqueue(CSVTupleMaker.MAKER, "null, \"[0.0,2.0]\"");
		predict.enqueue(CSVTupleMaker.MAKER, "\"[null,1.0]\"");
		train.enqueue(CSVTupleMaker.MAKER, "null, \"[null,null]\"");
		basicTest(predict, expecter);
	}

	@Test
	// QDA test. Also checks that model errors but doesn't crash on inconsistent parameter amounts.
	public void testQDA() throws Exception {
		Enqueuer predict = server.getEnqueuer("predictQDA");
		Enqueuer train = server.getEnqueuer("trainQDA");
		Expecter expecter = new Expecter(server.getDequeuer("OutQDA"));
		basicInput(train);
		train.enqueue(CSVTupleMaker.MAKER, "1, \"[3,1,2]\"");
		train.enqueue(CSVTupleMaker.MAKER, "1, \"[]\"");
		train.enqueue(CSVTupleMaker.MAKER, "1, \"[1]\"");
		train.enqueue(CSVTupleMaker.MAKER, "null, \"[null,null]\"");
		basicTest(predict, expecter);
	}
	
	@Test
	//LogReg test. Also checks the model is unaffected by training tuples after building.
	public void testLogReg() throws Exception {
		Enqueuer predict = server.getEnqueuer("predictLogReg");
		Enqueuer train = server.getEnqueuer("trainLogReg");
		Expecter expecter = new Expecter(server.getDequeuer("OutLogReg"));
		basicInput(train);
		train.enqueue(CSVTupleMaker.MAKER, "null, \"[null,null]\"");
		train.enqueue(CSVTupleMaker.MAKER, "0, \"[3,3]\"");
		train.enqueue(CSVTupleMaker.MAKER, "null, \"[null,null]\"");
		basicTest(predict, expecter);
	}
	
	@Test
	//Decision Tree test.
	public void testDTree() throws Exception {
		Enqueuer predict = server.getEnqueuer("predictDTree");
		Enqueuer train = server.getEnqueuer("trainDTree");
		Expecter expecter = new Expecter(server.getDequeuer("OutDTree"));
		trainIris(train);
		testIris(predict, expecter);
	}
	
	@Test
	//Random Forest test.
	public void testRanFor() throws Exception {
		Enqueuer predict = server.getEnqueuer("predictRanFor");
		Enqueuer train = server.getEnqueuer("trainRanFor");
		Expecter expecter = new Expecter(server.getDequeuer("OutRanFor"));
		trainIris(train);
		testIris(predict, expecter);
	}
	
	@Test
	//Gradient Boosted Trees test.
	public void testGBTrees() throws Exception {
		Enqueuer predict = server.getEnqueuer("predictGBTrees");
		Enqueuer train = server.getEnqueuer("trainGBTrees");
		Expecter expecter = new Expecter(server.getDequeuer("OutGBTrees"));
		trainIris(train);
		testIris(predict, expecter);
	}
	
	@Test
	//AdaBoost test.
	public void testABoost() throws Exception {
		Enqueuer predict = server.getEnqueuer("predictABoost");
		Enqueuer train = server.getEnqueuer("trainABoost");
		Expecter expecter = new Expecter(server.getDequeuer("OutABoost"));
		trainIris(train);
		testIris(predict, expecter);
	}
	
	@Test
	//Naive Bayes test.
	public void testNaiveBayes() throws Exception {
		Enqueuer predict = server.getEnqueuer("predictBayes");
		Enqueuer train = server.getEnqueuer("trainBayes");
		Expecter expecter = new Expecter(server.getDequeuer("OutBayes"));
		train.enqueue(CSVTupleMaker.MAKER, "0, \"[2,2]\"");
		train.enqueue(CSVTupleMaker.MAKER, "1, \"[3,2]\"");
		predict.enqueue(CSVTupleMaker.MAKER, "\"[3.0,2.0]\"");
		expecter.expect(CSVTupleMaker.MAKER, "\"[3.0,2.0]\", 1");
		train.enqueue(CSVTupleMaker.MAKER, "0, \"[1,1]\"");
		train.enqueue(CSVTupleMaker.MAKER, "1, \"[5,0]\"");
		predict.enqueue(CSVTupleMaker.MAKER, "\"[3.0,2.0]\"");
		expecter.expect(CSVTupleMaker.MAKER, "\"[3.0,2.0]\", 0");
	}
	
	@Test
	//Support Vector Machine test.
	public void testSVM() throws Exception {
		Enqueuer predict = server.getEnqueuer("predictSVM");
		Enqueuer train = server.getEnqueuer("trainSVM");
		Expecter expecter = new Expecter(server.getDequeuer("OutSVM"));
		train.enqueue(CSVTupleMaker.MAKER, "0, \"[2.0,2.0]\"");
		train.enqueue(CSVTupleMaker.MAKER, "1, \"[3.0,2.0]\"");
		predict.enqueue(CSVTupleMaker.MAKER, "\"[3.0,4.0]\"");
		expecter.expect(CSVTupleMaker.MAKER, "\"[3.0,4.0]\", 1");
		train.enqueue(CSVTupleMaker.MAKER, "0, \"[3.0,3.0]\"");
		train.enqueue(CSVTupleMaker.MAKER, "1, \"[2.0,1.0]\"");
		predict.enqueue(CSVTupleMaker.MAKER, "\"[3.0,4.0]\"");
		expecter.expect(CSVTupleMaker.MAKER, "\"[3.0,4.0]\", 0");
	}
	
	private void basicInput(Enqueuer train) throws Exception {
		train.enqueue(CSVTupleMaker.MAKER, "0, \"[0,0]\"");
		train.enqueue(CSVTupleMaker.MAKER, "0, \"[1,0]\"");
		train.enqueue(CSVTupleMaker.MAKER, "0, \"[0,1]\"");
		train.enqueue(CSVTupleMaker.MAKER, "1, \"[3,2]\"");
		train.enqueue(CSVTupleMaker.MAKER, "1, \"[2,2]\"");
		train.enqueue(CSVTupleMaker.MAKER, "1, \"[3,1]\"");
	}
	
	private void basicTest(Enqueuer predict, Expecter expecter) throws Exception {
		predict.enqueue(CSVTupleMaker.MAKER, "\"[1.0,1.0]\"");
		predict.enqueue(CSVTupleMaker.MAKER, "\"[2.0,1.0]\"");
		expecter.expect(CSVTupleMaker.MAKER, "\"[1.0,1.0]\", 0");
		expecter.expect(CSVTupleMaker.MAKER, "\"[2.0,1.0]\", 1");
	}
	
	private void trainIris(Enqueuer train) throws Exception {    	
    	for (int i = 0; i < irisLabels.length; i++) {
    		String makerString = irisLabels[i] + ", \"[";
    		for (int j = 0; j < irisData.length - 1; j++) {
    			makerString += irisData[j][i] + ",";
    		}
    		makerString += irisData[irisData.length - 1][i] + "]\"";
        	train.enqueue(CSVTupleMaker.MAKER, makerString);
    	}
		train.enqueue(CSVTupleMaker.MAKER, "null, \"[null,null,null,null]\"");
	}
	
	// An attempt was made to mitigate this, but this method can randomly fail sometimes, 
	// due to the somewhat random nature of some of the algorithms that use it.
	private void testIris(Enqueuer predict, Expecter expecter) throws Exception {
		for (int i = 40; i < 50; i++) {
			String makerString = "\"[";
    		for (int j = 0; j < irisData.length - 1; j++) {
    			makerString += irisData[j][i] + ",";
    		}
    		makerString += irisData[irisData.length - 1][i] + "]\"";
        	predict.enqueue(CSVTupleMaker.MAKER, makerString);
        	
        	makerString = "\"[";
    		for (int j = 0; j < irisData.length - 1; j++) {
    			makerString += irisData[j][i] + ",";
    		}
    		makerString += irisData[irisData.length - 1][i] + "]\", " + irisLabels[i]; 
    		expecter.expect(CSVTupleMaker.MAKER, makerString);
		}
	}
	@After
	public void stopContainers() throws Exception {
		// after each test, dispose of the container instances
		server.stopContainers();
	}

}
