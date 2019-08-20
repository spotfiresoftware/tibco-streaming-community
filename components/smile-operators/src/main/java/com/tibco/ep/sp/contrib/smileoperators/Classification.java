package com.tibco.ep.sp.contrib.smileoperators;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;

import com.streambase.sb.*;
import com.streambase.sb.Schema.Field;
import com.streambase.sb.operator.*;

import smile.classification.*;
import smile.math.distance.*;
import smile.math.Math;
import smile.math.kernel.MercerKernel;
import smile.math.kernel.GaussianKernel;
import smile.math.kernel.HellingerKernel;
import smile.math.kernel.LaplacianKernel;
import smile.math.kernel.LinearKernel;

/**
 * The SMILE Classification integration operator.
 * </p>
 * It has two input ports, one for training and one for predicting. Both input schemas
 * must have a consistently-sized list of doubles for training/predicting data, and the 
 * training port also requires a labels field.
 * </p>
 * There are two categories of classifier: online and batch. Batch learning enforces that
 * all training data has been sent before any classifying is done, and it does its training
 * all at once, signaled by an all-null tuple. Online trains on an event-by-event basis,
 * and allows tuples to predict and tuples to learn from to be received and processed 
 * in any order. In either category, the operator emits an output tuple when it receives
 * a tuple to predict the classification of, but does not produce any output when given 
 * a tuple to train with.
 */
public class Classification extends Operator implements Parameterizable {

	public static final long serialVersionUID = 1561379988543L;
	@SuppressWarnings("unused")
	private Logger logger;
	private String displayName = "Classifier (SMILE)";
	private int inputPorts = 2;
	private int outputPorts = 1;
	private Schema outputSchema;
	
	// General properties
	
	/**
	 * Whether this classifier is batch or online.
	 */
	private boolean online;
	
	/**
	 * The selected type of batch classifier.
	 */
	private String batchType; 
	
	/**
	 * The selected type of online classifier.
	 */
	private String onlineType;
	
	/**
	 * The field which to get parameter lists on. Must match on both ports, as it's used 
	 * in both training and predicting.
	 */
	private String paramField;
	
	/**
	 * The field which to get training labels on.
	 */
	private String labelField;
	
	/**
	 * The field to emit the predicted classification on.
	 */
	private String classOut; 
	
	/**
	 * The number of possible classifications.
	 */
	private int classes; 
	
	private boolean reset;
	
	// General variables
	/**
	 * The data structure to hold the training data's parameters.
	 */
	private ArrayList<double[]> trainingData; 

	/**
	 * The data structure to hold the training data's classifications.
	 */
	private ArrayList<Integer> trainingLabels;
	
	/**
	 * The batch classifier. Will be null until a fully null training tuple 
	 */
	private Classifier<double[]> classifier;
	
	/**
	 * The online classifier. The same as classifier, but has a online learning method. 
	 * It will be initialized on startup, or on first incoming tuple if it's a bayes classifier.
	 */
	private OnlineClassifier<double[]> onClassifier;
	
	/**
	 * The number of parameters each incoming data list (should) have.
	 */
	private int numParams = 0;
	
	// Knn properties
	/**
	 * How to gauge distance to calculate the closest k neighbors
	 */
	private String distType;
	
	/**
	 * The number of closest neighbors which to look at.
	 */
	private int neighbors;

	// RDA & Logistic Regression property
	/**
	 * A regularization factor.
	 */
	private double regFactor;
	
	// Decision Trees properties
	/**
	 * The minimum number of endpoints in the tree.
	 */
	private int minLeaves;
	
	/**
	 * The maximum number of endpoints in the tree.
	 */
	private int maxLeaves;
	
	/**
	 * The rule for calculating when to split in a tree in string form.
	 */
	private String ruleType;
	
	/**
	 * The rule for calculating when to split in a tree.
	 */
	private DecisionTree.SplitRule splitRule; // variable: the splitting rule
	
	// Random Forest Properties
	
	/**
	 * The number of trees to use in a random forest
	 */
	private int numTrees;
	
	/**
	 * The sampling rate for training tree. 1.0 is with replacement, lower is without.
	 */
	private double samplingRate;
	
	// Gradiant Boosted Trees property
	
	/**
	 * Boosted trees learning rate.
	 */
	private double shrinkage;
	 
	// Neural Net properties
	/**
	 * Which error strategy to use. This will define the type of activation function, 
	 * since there's only a few possible combinations.
	 */
	private String errorType;
	
	/**
	 * The number of nodes per layer. This also defines how many layers. The first 
	 */
	private String layerUnits;
	
	/**
	 * Learning rate factor. Combines with decay to reduce old weights.
	 */
	private double learningRate;
	
	/**
	 * Momentum learning factor.
	 */
	private double momentum;
	
	/**
	 * Weight decay factor. Combines with learning rate to reduce old weights.
	 */
	private double decay;
	
	// Support Vector Machine properties
	
	/**
	 * Which methodology to use when classes > 2.
	 */
	private String multiStrat;
	
	/**
	 * The margin penalty parameter to use.
	 */
	private double softMargin;
	
	/**
	 * Which type of Mercer Kernel to use. SMILE offers a few more, but they required additional complexity for the operator.
	 */
	private String kernel;
	
	/**
	 * The value that the kernel uses if applicable.
	 */
	private double kernelParam; 
	
	// Naive Bayes properties
	/**
	 * Which mathematical model to use. They don't differ greatly; binomial is used when classes = 2, multinomial adds the 
	 * value of each variable to the appropriate sum polyaurn is the same as multi but x2.
	 */
	private String bayesModel;
	
	/**
	 * The Bayes smoothing constant. The bigger this is the more weight is initially given to the assumption of uniformity.
	 */
	private double bayesSmoother;

	/**
	 * Constructor. Sets the default value for every property except the input field names.
	 */
	public Classification() {
		super();
		logger = getLogger();
		setPortHints(inputPorts, outputPorts);
		setDisplayName(displayName);
		setShortDisplayName(this.getClass().getSimpleName());
		setOnline(false);
		setBatchType("K-Nearest Neighbor");
		setOnlineType("Multilayer Perceptron Neural Network");
		setParamField("");
		setLabelField("");
		setClassOut("prediction");
		setClasses(2); // as many of these as possible are SMILE default/suggested values
		setReset(true);
		setNeighbors(3);
		setDistType("Euclidean");
		setRegFactor(0.0);
		setMinLeaves(1);
		setMaxLeaves(6);
		setRuleType("Gini Impurity");
		setNumTrees(50);
		setSamplingRate(0.7);
		setShrinkage(0.01);
		setErrorType("Cross Entropy");
		setLayerUnits("2, 2");
		setLearningRate(0.1);
		setMomentum(0.0);
		setDecay(0.0);
		setMultiStrat("One vs one");
		setSoftMargin(1);
		setKernel("Gaussian");
		setKernelParam(0.5);
		setBayesModel("Multinomial");
		setBayesSmoother(1.0);
		onClassifier = null;
		classifier = null;
	}
	
	/**
	 * The typecheck method for this operator.
	 * It will check on every general property, then call the appropriate typecheck method for which 
	 * classification algorithm has been specified in said general properties.
	 * @throws TypecheckException
	 */
	public void typecheck() throws TypecheckException {
		requireInputPortCount(inputPorts);
		
		// label field and param field
		try {
			if (getInputSchema(1).getField(getLabelField()).getDataType() != DataType.INT) {
				throw new PropertyTypecheckException("labelField", 
						  getLabelField() + " needs to be of type int");
			}
		} catch (TupleException e1) {
			throw new PropertyTypecheckException("labelField",
					  "A learning int field needs to be specified");
		}
		
		try {
			if (!getInputSchema(0).getField(getParamField()).getCompleteDataType()
					.equals(CompleteDataType.forDoubleList())) {
				throw new PropertyTypecheckException("paramField",
						  getParamField() + " needs to be of type list of doubles");
			}
			if (!getInputSchema(1).getField(getParamField()).getCompleteDataType()
					.equals(CompleteDataType.forDoubleList())) {
				throw new PropertyTypecheckException("paramField",
						  getParamField() + " needs to match in both schemas");
			}
		} catch (TupleException e1) {
			throw new PropertyTypecheckException("paramField",
					  "A parameters list field needs to be specified");
		}
		
		String classType;
		if (online) {
			classType = onlineType;
		} else {
			classType = batchType;
		}
		
		// classes
		if (getClasses() <= 1) {
			throw new PropertyTypecheckException("classes", "The number of classes must be at least 2");
		}
									
		// Only typecheck the properties necessary for the selected class type. 
		switch (classType) {
			case "K-Nearest Neighbor": 						knnTypecheck();
															break;
			case "Linear Discriminant Analysis":			break;
//			case "Fisher's Linear Discriminant":			fldTypecheck();
			case "Quadratic Discriminant Analysis":			break;
			case "Regularized Discriminant Analysis":
			case "Logistic Regression":  					regTypecheck();
															break;
			case "Decision Tree":							dTreeTypecheck();
															break;
			case "Random Forest": 							ranForTypecheck();
															break;
			case "Gradient Boosted Trees":					gBTreesTypecheck();
															break;
			case "AdaBoost":								adaBoostTypecheck();
															break;
			case "Multilayer Perceptron Neural Network":	netTypecheck();
															break;
			case "Support Vector Machines":					svmTypecheck();
															break;
			case "Naive Bayes": 							bayesTypecheck();
			default:										break;
		}
		
		// creating output schema
		ArrayList<Field> fields = new ArrayList<Field>(getInputSchema(0).fields());	
		fields.add(new Schema.Field(getClassOut(), CompleteDataType.forInt()));
		setOutputSchema(0, new Schema(null, fields));
	}
	
	/** 
	 * Checks the correctness of the properties necessary to K-Nearest Neighbors. 
	 * @throws TypecheckException
	 */
	private void knnTypecheck() throws TypecheckException {
		// neighbors
		if (getNeighbors() <= 0) {
			throw new PropertyTypecheckException("neighbors", "The number of neighbors must be at least 1");
		}
				      					   
	}
	
//	/**
//	 * Checks that there are no more than two classes, as fishers seems to error out with more than two.
//	 * @throws TypecheckException
//	 */
//	private void fldTypecheck() throws TypecheckException {
//		if (getClasses() >= 3) throw new PropertyTypecheckException("classes", 
//									     "Fisher's does not support more than 2 classes");
//	}
	
	/**
	 * Checks the correctness of the property necessary to Logistic Regression and RDA.
	 * @throws TypecheckException
	 */
	private void regTypecheck() throws TypecheckException {
		if (getRegFactor() < 0.0) {
			throw new PropertyTypecheckException("regFactor", "The regularization factor must be at least 0");
		}
				 						 
	}
	
	/**
	 * Checks the correctness of the properties necessary to Decision Trees.
	 * Also sets the rule enum from the ruleType property.
	 * @throws TypecheckException
	 */
	private void dTreeTypecheck() throws TypecheckException {
		// minLeaves
		if (getMinLeaves() < 1) {
			throw new PropertyTypecheckException("minLeaves", "The minimum number of leaves must be at least 1");
		}
		
		// maxLeaves
		if (getMaxLeaves() < 2) {
			throw new PropertyTypecheckException("maxLeaves", "The maximum number of leaves must be at least 2");
		}
		
		// ruleType and splitRule 
		if (getRuleType().equals("Gini Impurity")) {
			this.splitRule = DecisionTree.SplitRule.GINI;
		} else if (getRuleType().equals("Entropy")) {
			this.splitRule = DecisionTree.SplitRule.ENTROPY;
		} else {
			this.splitRule = DecisionTree.SplitRule.CLASSIFICATION_ERROR;
		}
	}
	
	/**
	 * Checks the correctness of the properties necessary to Random Forests.
	 * Also calls decision tree typecheck, since decision trees will be made.
	 * @throws TypecheckException
	 */
	private void ranForTypecheck() throws TypecheckException {
		dTreeTypecheck();
		
		// numTrees
		if (getNumTrees() < 1) {
			throw new PropertyTypecheckException("numTrees", "The number of trees must be at least 1");
		}
		
		// samplingRate
		if (getSamplingRate() > 1) {
			throw new PropertyTypecheckException("samplingRate", "The maximum sampling rate is 1");
		}
		if (getSamplingRate() <= 0) {
			throw new PropertyTypecheckException("samplingRate", "the sampling rate must be positive");
		}
	}

	/**
	 * Checks the correctness of the property necessary to Gradient Boosted Trees.
	 * Also calls random forest typecheck, since it shares all random forest and some decision tree properties.
	 * @throws TypecheckException
	 */
	private void gBTreesTypecheck() throws TypecheckException {
		ranForTypecheck();
		
		// shrinkage
		if (getShrinkage() > 1) {
			throw new PropertyTypecheckException("shrinkage", "The maximum learning rate is 1");
		}
		if (getShrinkage() <= 0) {
			throw new PropertyTypecheckException("shrinkage", "the learning rate must be positive");
		}
	}
	
	/**
	 * Checks for the correctness of the properties necessary to Adaptive Boost.
	 * @throws TypecheckException
	 */
	private void adaBoostTypecheck() throws TypecheckException {
		
		// numTrees
		if (getNumTrees() < 1) {
			throw new PropertyTypecheckException("numTrees", "The number of trees must be at least 1");
		}
		// maxLeaves
		if (getMaxLeaves() < 2) {
			throw new PropertyTypecheckException("maxLeaves", "The maximum number of leaves must be at least 2");
		}
	}
	
	/**
	 * Checks the correctness of the properties necessary to Multilayered Perceptron Neural Networks.
	 * @throws TypecheckException
	 */
	private void netTypecheck() throws TypecheckException {
		
		// layerUnits
		String[] parse = getLayerUnits().split(", ");
		for (String token : parse) {
			try {
				Integer.parseInt(token);
			} catch (NumberFormatException e) {
				throw new PropertyTypecheckException("layerUnits", 
						  "The units per network layer must be seperated by \", \"");
			}
		}
		if (parse.length < 2) 
			throw new PropertyTypecheckException("layerUnits", "There must be at least two layers");
		if (Integer.parseInt(parse[parse.length - 1]) != getClasses()) {
			if (!(Integer.parseInt(parse[parse.length - 1]) == 1 && getClasses() == 2)) {
				throw new PropertyTypecheckException("layerUnits", 
						  "The last network layer's units must be the same as the number of classes");
			}
		}
			
		
		// learningRate
		if (getLearningRate() < 0.0) {
			throw new PropertyTypecheckException("learningRate", "The learning rate must be at least 0.0");
		}
		
		// momentum 
		if (getMomentum() < 0.0) {
			throw new PropertyTypecheckException("momentum", "Momentum must be at least 0.0");
		}
		if (getMomentum() >= 1.0) {
			throw new PropertyTypecheckException("momentum", "Momentum must be lower than 1.0");
		}
		
		// decay
		if (getDecay() < 0.0) {
			throw new PropertyTypecheckException("decay", "Decay must be at least 0.0");
		}
		if (getDecay() > 0.1) {
			throw new PropertyTypecheckException("decay", "Maximum decay is 0.1");
		}
	}
	
	/**
	 * Checks the correctness of the properties necessary to Support Vector Machines.
	 * Also sets the enum strategy based on multiStrat, and the var kernel based on selecterKernel.
	 * @throws TypecheckException
	 */
	private void svmTypecheck() throws TypecheckException {
		
		// softMargin
		if (getSoftMargin() < 0.0) {
			throw new PropertyTypecheckException("softMargin", "The soft margin penalty parameter must be at least 0.0");
		}
		
		// Some kernels require a parameter. Check kernelParam here if needed
		if ((kernel.equals("Gaussian") || kernel.equals("Laplacian")) && getKernelParam() < 0.0) {
			throw new PropertyTypecheckException("kernelParam",	"The kernel sigma parameter must be at least 0.0");
		}
	}
			
	/**
	 * Checks the correctness of the properties necessary to Naive Bayes.
	 * @throws TypecheckException
	 */
	private void bayesTypecheck() throws TypecheckException {
		
		// bayesSmoother
		if (getBayesSmoother() <= 0.0) {
			throw new PropertyTypecheckException("bayesSmoother", "The Bayes smoothing pseudocount must be greater than 0");
		}

	}
	
	/**
	 * Processes the incoming tuple. Based on the port, either sends the tuple to training or predicting.
	 * @throws StreamBaseException
	 */
	public void processTuple(int inputPort, Tuple tuple) throws StreamBaseException {
		if (tuple == null) throw new StreamBaseException("Tuple is null");
		// If on the training port, call the training algorithm then stop.
		if (inputPort == 1) {
			train(tuple);
			return;
		}
		
		// Predict the class for the inputed parameters, then add it to the output tuple before sending it.
		if (hasNulls(tuple, inputPort)) throw new StreamBaseException("Tuple contains nulls");
		int predictedClass = predict(tuple);
		
		// SMILE's classifiers send a message to the logger then output -1 if something went wrong, 
		// so catch those -1s here.
		if (predictedClass == -1) return;
		
		Tuple out = outputSchema.createTuple();
		for (Field field : getInputSchema(0).fields()) {
			out.setField(field.getName(), tuple.getField(field));
		}
		out.setField(classOut, predictedClass);
		sendOutput(0, out);
	}
	
	/**
	 * Checks if there is at least one null in the input tuple.
	 * @param tuple The tuple to check.
	 * @param port The port which the tuple is on.
	 * @return true if there are any nulls.
	 * @throws TupleException 
	 */
	private boolean hasNulls(Tuple tuple, int port) throws TupleException {
		if (port == 1 && tuple.getField(labelField) == null) return true;
		if (tuple.getField(paramField) == null) return true;
		if (tuple.getList(paramField).contains(null)) return true;
		return false;
	}
	
	/**
	 * Checks every field in the tuple contains null.
	 * @param tuple The tuple to check.
	 * @param port The port which the tuple is on.
	 * @return true if there are any nulls.
	 * @throws NullValueException 
	 * @throws TupleException 
	 */
	private boolean allNulls(Tuple tuple, int port) throws TupleException {
		for (Field field : getInputSchema(port).fields()) {
			if (tuple.isNull(field));
			else if (field.getDataType().equals(DataType.LIST)) {
				if (!tuple.getList(field).contains(null)) return false;
			} else return false;	
		}
		return true;
	}
	
	/**
	 * Trains the model given a tuple. 
	 * If it is an online model, send the values from the tuple straight to the classifications learn method.
	 * Otherwise, if it's a batch model, store the values for now. Once a null tuple is received, train
	 * the model, and flip into accepting no more tuples for training. 
	 * @param tuple The tuple to use.
	 * @throws StreamBaseException
	 */
	private void train(Tuple tuple) throws StreamBaseException {
		// Upon receiving a fully null tuple build a model.
		// If there's only some nulls or if it's not a batch model, error out.
		if (hasNulls(tuple, 1)) { 
			if (!allNulls(tuple, 1) || online) throw new StreamBaseException("Tuple contains nulls");
			buildModel();
			return;
		}
		
		double[] params = listConverter(tuple, paramField);
		int label = tuple.getInt(labelField);
		
		// Check that the number of parameters is valid, and set numParams if need be.
		if (params.length == 0) throw new StreamBaseException("Empty parameter list");
		if (numParams == 0) numParams = params.length;
		else if (params.length != numParams) {
			throw new StreamBaseException("The number of parameters needs to be consistent");
		}
		
		if(!online) {
			trainingData.add(params);
			trainingLabels.add(label);	
		} else {
			// Bayes needs # of params for initialization. The other online algos don't.
			if (onClassifier == null && onlineType.equals("Naive Bayes")) {
				initBayes();
			}
			onClassifier.learn(params, label);
		}
	}

	/** 
	 * Builds a batch model with all of the collected data.
	 * @throws StreamBaseException 
	 */
	private void buildModel() throws StreamBaseException {
		// Convert the data into the format that SMILE accepts.
		double[][] data = new double[trainingData.size()][trainingData.get(0).length];
		for (int i = 0; i < trainingData.size(); i++) data[i] = trainingData.get(i).clone();
		int[] labels = listConverter(trainingLabels);
		
		if (Math.unique(labels).length != classes) {
			throw new StreamBaseException("All classes should be represented in the training set");
		}

		if (batchType.equals("Linear Discriminant Analysis")) {
			classifier = new LDA(data, labels);
		} else if (batchType.equals("Quadratic Discriminant Analysis")) {
			classifier = new QDA(data, labels);
		} else if (batchType.equals("Regularized Discriminant Analysis")) {
			classifier = new RDA(data, labels, regFactor);
		} else if (batchType.equals("K-Nearest Neighbor")) {
			Distance<double[]> dist = new EuclideanDistance();
			if (distType.equals("Correlation")) dist = new CorrelationDistance();
			else if (distType.equals("Manhattan")) dist = new ManhattanDistance();
			classifier = new KNN<double[]>(data, labels, dist, neighbors);
		} else if (batchType.equals("Logistic Regression")) {
			classifier = new LogisticRegression(data, labels, regFactor);
		} else if (batchType.equals("Decision Tree")) {
			classifier = new DecisionTree(data, labels, maxLeaves, minLeaves, splitRule);
		} else if (batchType.equals("Random Forest")) {
			int mtry = (int) Math.ceil(Math.sqrt(numParams));
			classifier = new RandomForest(null, data, labels, numTrees, maxLeaves, 
										  minLeaves, mtry, samplingRate, splitRule);
		} else if (batchType.equals("Gradient Boosted Trees")) {
			classifier = new GradientTreeBoost(null, data, labels, numTrees, maxLeaves, shrinkage, samplingRate);
		} else if (batchType.equals("AdaBoost")) {
			classifier = new AdaBoost(null, data, labels, numTrees, maxLeaves);
		}
		
		// Delete training set if it is no longer needed.
		if (reset) {
			trainingData = new ArrayList<double[]>();
			trainingLabels = new ArrayList<Integer>();
		}
	}
	
	/**
	 * Using the built model, predict what the label/class should be given a set of parameters.
	 * @param tuple The tuple which to get the params from.
	 * @return The predicted class.
	 * @throws StreamBaseException
	 */
	private int predict(Tuple tuple) throws StreamBaseException {
		if (classifier != null) { 
			return classifier.predict(listConverter(tuple, paramField));
		} else if (onClassifier != null) {
			return onClassifier.predict(listConverter(tuple, paramField));
		}
		if (online) {
			throw new StreamBaseException("Predicting model has not yet been initialized");
		} else {
			throw new StreamBaseException("Predicting model has not yet been finalized. Send all nulls in the predicting port");
		}
	}
	
	/**
	 * Streambase init. Call a model init if appropriate.
	 */
	public void init() throws StreamBaseException {
		super.init();
		outputSchema = getRuntimeOutputSchema(0);
		if (online) {
			if (onlineType.equals("Multilayer Perceptron Neural Network")) {
				initNetwork();
			} else if (onlineType.equals("Support Vector Machines")) {
				initSVM();
			}
		} else {
			trainingData = new ArrayList<double[]>();
			trainingLabels = new ArrayList<Integer>();
		}
	}

	/**
	 * Creates a Multilayered Perceptron Neural Network classifier.
	 */
	private void initNetwork() {
		
		// Parse the layerUnits string.
		String[] tokens = getLayerUnits().split(", ");
		int[] unitsPerLayer = new int[tokens.length];
		for (int i = 0; i < tokens.length; i++) {
			unitsPerLayer[i] = Integer.parseInt(tokens[i]);
		}
		numParams = unitsPerLayer[0];
		if (unitsPerLayer[unitsPerLayer.length - 1] == 2 && classes == 1) unitsPerLayer[unitsPerLayer.length] = 1;
		
		// Get the error function and determine the activation function.
		NeuralNetwork.ErrorFunction errorFunc;
		NeuralNetwork.ActivationFunction activeFunc;
		
		if (errorType.equals("Least Squares")) {
			errorFunc = NeuralNetwork.ErrorFunction.LEAST_MEAN_SQUARES;
		} else {
			errorFunc = NeuralNetwork.ErrorFunction.CROSS_ENTROPY;
		}
		
		if (errorFunc.equals(NeuralNetwork.ErrorFunction.LEAST_MEAN_SQUARES)) {
			activeFunc = NeuralNetwork.ActivationFunction.LINEAR;
		} else if (classes == 2) {
			activeFunc = NeuralNetwork.ActivationFunction.LOGISTIC_SIGMOID;
		} else {
			activeFunc = NeuralNetwork.ActivationFunction.SOFTMAX;
		}
		
		NeuralNetwork tempNet = new NeuralNetwork(errorFunc, activeFunc, unitsPerLayer);
		tempNet.setLearningRate(learningRate);
		tempNet.setMomentum(momentum);
		tempNet.setWeightDecay(decay);
		onClassifier = tempNet.clone();
	}
	
	/**
	 * Creates a Support Vector Machine classifier.
	 */
	private void initSVM() {
		SVM.Multiclass strategy;
		if (multiStrat.equals("One vs one")) {
			strategy = SVM.Multiclass.ONE_VS_ONE;
		} else {
			strategy = SVM.Multiclass.ONE_VS_ALL;
		}
	
		// First create the Mercer Kernel for the SVM to use.
		MercerKernel<double[]> mercerKernel = null;
		switch (kernel) {
			case "Gaussian":	mercerKernel = new GaussianKernel(kernelParam);
								break;
			case "Hellinger":	mercerKernel = new HellingerKernel();
								break;
			case "Laplacian":	mercerKernel = new LaplacianKernel(kernelParam);
								break;
			case "Linear":		mercerKernel = new LinearKernel();
								break;
		}
		if (classes > 2) onClassifier = new SVM<double[]>(mercerKernel, softMargin, classes, strategy);
		else onClassifier = new SVM<double[]>(mercerKernel, softMargin);
	}
	
	/**
	 * Creates a Naive Bayes classifier.
	 */
	private void initBayes() {
		NaiveBayes.Model model;
		if (bayesModel.equals("Multinomial")) {
			model = NaiveBayes.Model.MULTINOMIAL;
		} else if (bayesModel.equals("Bernoulli")) {
			model = NaiveBayes.Model.BERNOULLI;
		} else {
			model = NaiveBayes.Model.POLYAURN;
		}
		onClassifier = new NaiveBayes(model, classes, numParams, bayesSmoother);
	}

	/**
	 * Turns an object double list from a tuple into a primitive double array.
	 * @param tuple The tuple to get the list from.
	 * @param field The field which the list is in.
	 * @return The transformed array.
	 * @throws StreamBaseException 
	 */
	@SuppressWarnings("unchecked")
	private double[] listConverter(Tuple tuple, String field) throws StreamBaseException {
		try {
			return ((ArrayList<Double>) new ArrayList<>(tuple.getList(field)))
					.stream().mapToDouble(Double::doubleValue).toArray();
		} catch (TupleException e) {
			throw new StreamBaseException("Tuple conversion error");
		}
	}	

	/**
	 * Turns an object integer list into a primitive int array.
	 * @param toConvert the list.
	 * @return The transformed array.
	 */
	private int[] listConverter(List<Integer> toConvert) {
		return toConvert.stream().mapToInt(Integer::intValue).toArray();
	}
	
	/**
	*  The shutdown method is called when the StreamBase server is in the process of shutting down.
	*/
	public void shutdown() {
	}

	/***************************************************************************************
	 * The getter and setter methods provided by the Parameterizable object.               *
	 * StreamBase Studio uses them to determine the name and type of each property         *
	 * and obviously, to set and get the property values.                                  *
	 ***************************************************************************************/

	public void setOnline(boolean online) {
		this.online = online;
	}
	
	public boolean getOnline() {
		return this.online;
	}
	
	public void setBatchType(String batchType) {
		this.batchType = batchType;
	}

	public String getBatchType() {
		return this.batchType;
	}
	
	public void setOnlineType(String onlineType) {
		this.onlineType = onlineType;
	}

	public String getOnlineType() {
		return this.onlineType;
	}

	public void setParamField(String paramField) {
		this.paramField = paramField;
	}

	public String getParamField() {
		return this.paramField;
	}

	public void setLabelField(String labelField) {
		this.labelField = labelField;
	}

	public String getLabelField() {
		return this.labelField;
	}

	public void setClassOut(String classOut) {
		this.classOut = classOut;
	}

	public String getClassOut() {
		return this.classOut;
	}
	
	public void setClasses(int classes) {
		this.classes = classes;
	}
	
	public int getClasses() {
		return classes;
	}
	
	public void setReset(boolean reset) {
		this.reset = reset;
	}
 	
	public boolean getReset() {
		return this.reset;
	}
	
	public void setNeighbors(int neighbors) {
		this.neighbors = neighbors;
	}
	
	public int getNeighbors() {
		return neighbors;
	}
	
	public void setDistType(String distType) {
		this.distType = distType;
	}
	
	public String getDistType() {
		return this.distType;
	}

	public void setRegFactor(double regFactor) {
		this.regFactor = regFactor;
	}
	
	public double getRegFactor() {
		return this.regFactor;
	}
	
	public void setMinLeaves(int minLeaves) {
		this.minLeaves = minLeaves;
	}
	
	public int getMinLeaves() {
		return this.minLeaves;
	}
	
	public void setMaxLeaves(int maxLeaves) {
		this.maxLeaves = maxLeaves;
	}
	
	public int getMaxLeaves() {
		return this.maxLeaves;
	}
	
	public void setRuleType(String ruleType) {
		this.ruleType = ruleType;
	}
	
	public String getRuleType() {
		return this.ruleType;
	}
	
	public void setNumTrees(int numTrees) {
		this.numTrees = numTrees;
	}
	
	public int getNumTrees() {
		return this.numTrees;
	}
	
	public void setSamplingRate(double samplingRate) {
		this.samplingRate = samplingRate;
	}
	
	public double getSamplingRate() {
		return this.samplingRate;
	}
	
	public void setShrinkage(double shrinkage) {
		this.shrinkage = shrinkage;
	}
	
	public double getShrinkage() {
		return this.shrinkage;
	}
	
	public void setLayerUnits(String layerUnits) {
		this.layerUnits = layerUnits;
	}
	
	public String getLayerUnits() {
		return this.layerUnits;
	}

	public void setErrorType(String errorType) {
		this.errorType = errorType;
	}
	
	public String getErrorType() {
		return this.errorType;
	}
	
	public void setLearningRate(double learningRate) {
		this.learningRate = learningRate;
	}
	
	public double getLearningRate() {
		return this.learningRate;
	}
	
	public void setMomentum(double momentum) {
		this.momentum = momentum;
	}
	
	public double getMomentum() {
		return this.momentum;
	}
	
	public void setDecay(double decay) {
		this.decay = decay;
	}
	
	public double getDecay() {
		return this.decay;
	}
	
	public void setMultiStrat(String multiStrat) {
		this.multiStrat = multiStrat;
	}
	
	public String getMultiStrat() {
		return this.multiStrat;
	}
	
	public void setSoftMargin(double softMargin) {
		this.softMargin = softMargin;
	}
	
	public double getSoftMargin() {
		return this.softMargin;
	}

	public void setKernel(String kernel) {
		this.kernel = kernel;
	}
	
	public String getKernel() {
		return this.kernel;
	}
	
	public void setKernelParam(double kernelParam) {
		this.kernelParam = kernelParam;
	}
	
	public double getKernelParam() {
		return this.kernelParam;
	}
	
	public void setBayesModel(String bayesModel) {
		this.bayesModel = bayesModel;
	}
	
	public String getBayesModel() {
		return this.bayesModel;
	}
	
	public void setBayesSmoother(double bayesSmoother) {
		this.bayesSmoother = bayesSmoother;
	}
	
	public double getBayesSmoother() {
		return this.bayesSmoother;
	}
	
	/***************************************************************************************
	 * The should enable methods by the Parameterizable object.                            *
	 ***************************************************************************************/
	
	public boolean shouldEnableBatchType() {
		return !this.online;
	}

	public boolean shouldEnableOnlineType() {
		return this.online;
	}
	
	public boolean shouldEnableReset() {
		return !this.online;
	}
	
	public boolean shouldEnableNeighbors() {
		return !this.online && this.batchType.equals("K-Nearest Neighbor");
	}
	
	public boolean shouldEnableDistType() {
		return !this.online && this.batchType.equals("K-Nearest Neighbor");	
	}

	public boolean shouldEnableRegFactor() {
		return !this.online && (this.batchType.equals("Logistic Regression") || 
								this.batchType.equals("Regularized Discriminant Analysis"));
	}
	
	public boolean shouldEnableMinLeaves() {
		return !this.online && !this.batchType.equals("Gradient Boosted Trees") &&
				(this.batchType.equals("Decision Tree") || this.batchType.equals("Random Forest"));
	}
	
	public boolean shouldEnableMaxLeaves() {
		return !this.online && (this.batchType.equals("Decision Tree") || this.batchType.equals("Random Forest") 
				|| this.batchType.equals("Gradient Boosted Trees") || this.batchType.equals("AdaBoost"));
	}
	
	public boolean shouldEnableRuleType() {
		return !this.online && !this.batchType.equals("Gradient Boosted Trees") &&
				(this.batchType.equals("Decision Tree") || this.batchType.equals("Random Forest"));
	}
	
	public boolean shouldEnableNumTrees() {
		return !this.online && (this.batchType.equals("Random Forest") || 
				this.batchType.equals("Gradient Boosted Trees") || this.batchType.equals("AdaBoost"));
	}
	
	public boolean shouldEnableSamplingRate() {
		return !this.online && (this.batchType.equals("Random Forest") || this.batchType.equals("Gradient Boosted Trees"));	
	}
	
	public boolean shouldEnableShrinkage() {
		return !this.online && this.batchType.equals("Gradient Boosted Trees");
	}
	
	public boolean shouldEnableErrorType() {
		return this.online && this.onlineType.equals("Multilayer Perceptron Neural Network");
	}
	
	public boolean shouldEnableLayerUnits() {
		return this.online && this.onlineType.equals("Multilayer Perceptron Neural Network");
	}
	
	public boolean shouldEnableLearningRate() {
		return this.online && this.onlineType.equals("Multilayer Perceptron Neural Network");
	}
	
	public boolean shouldEnableMomentum() {
		return this.online && this.onlineType.equals("Multilayer Perceptron Neural Network");
	}
	
	public boolean shouldEnableDecay() {
		return this.online && this.onlineType.equals("Multilayer Perceptron Neural Network");
	}
	
	public boolean shouldEnableMultiStrat() {
		return this.online && this.classes >= 3 && this.onlineType.equals("Support Vector Machines");
	}

	public boolean shouldEnableSoftMargin() {
		return this.online && this.onlineType.equals("Support Vector Machines");
	}
	
	public boolean shouldEnablekernel() {
		return this.online && this.onlineType.equals("Support Vector Machines");
	}
	
	public boolean shouldEnableKernelParam() {
		return shouldEnablekernel() && (kernel.equals("Gaussian") || kernel.equals("Laplacian"));
	}
	
	public boolean shouldEnableBayesModel() {
		return this.online && this.onlineType.equals("Naive Bayes");
	}
	
	public boolean shouldEnableBayesSmoother() {
		return this.online && this.onlineType.equals("Naive Bayes");
	}
}
