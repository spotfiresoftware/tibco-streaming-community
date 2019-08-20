package com.tibco.ep.sp.contrib.smileoperators;

import java.beans.*;


import com.streambase.sb.operator.parameter.*;

/**
* A BeanInfo class controls what properties are exposed, add 
* metadata about properties (such as which properties are optional), and access 
* special types of properties that can't be automatically derived via reflection. 
* If a BeanInfo class is present, only the properties explicitly declared in
* this class will be exposed by StreamBase.
*/
public class ClassificationBeanInfo extends SBSimpleBeanInfo {

	/*
	* The order of properties below determines the order they are displayed within
	* the StreamBase Studio property view. 
	*/
	public SBPropertyDescriptor[] getPropertyDescriptorsChecked() throws IntrospectionException {
		UIHints gen = UIHints.create().setTab("General Properties").setTextWidth(UIHints.TextWidthHint.LONG);
		UIHints batch = UIHints.create().setTab("Batch Properties").setTextWidth(UIHints.TextWidthHint.LONG);
		UIHints online = UIHints.create().setTab("Online Properties").setTextWidth(UIHints.TextWidthHint.LONG);
		String[] onlineTypes = {"Multilayer Perceptron Neural Network", "Support Vector Machines", "Naive Bayes"};
		String[] batchTypes = {"K-Nearest Neighbor", "Linear Discriminant Analysis", //"Fisher's Linear Discriminant",
				"Quadratic Discriminant Analysis", "Regularized Discriminant Analysis", "Logistic Regression",
				"Decision Tree", "Random Forest", "Gradient Boosted Trees", "AdaBoost"};
		String[] distTypes = {"Euclidean", "Manhattan", "Correlation"};
		String[] rules = {"Gini Impurity", "Entropy", "Classification Error"};
		String[] errorTypes = {"Least Squares", "Cross Entropy"};
		String[] strategies = {"One vs one", "One vs all"};
		String[] kernels = {"Gaussian", "Hellinger", "Laplacian", "Linear"};
		String[] bayesModels = {"Multinomial", "Bernoulli", "Polyaurn"};
		
		SBPropertyDescriptor[] p = {
			new SBPropertyDescriptor("online", Classification.class).setUIHints(gen)
					.displayName("Online").description("online or batch learning"),
			new EnumPropertyDescriptor("onlineType", Classification.class, onlineTypes)
					.displayName("Online classifier type").setUIHints(gen),
			new EnumPropertyDescriptor("batchType", Classification.class, batchTypes)
					.displayName("Batch classifier type").setUIHints(gen),
			new SBPropertyDescriptor("paramField", Classification.class).setUIHints(gen)
					.displayName("Parameters field").description(""),
			new SBPropertyDescriptor("labelField", Classification.class).setUIHints(gen)
					.displayName("Input class field").description(""),
			new SBPropertyDescriptor("classOut", Classification.class).setUIHints(gen)
					.displayName("Predicted class field").description(""),
			new SBPropertyDescriptor("classes", Classification.class).setUIHints(gen)
					.displayName("Number of classes").description(""),
			new SBPropertyDescriptor("reset", Classification.class).setUIHints(batch)
					.displayName("Reset training data on model creation").description(""),
			new SBPropertyDescriptor("neighbors", Classification.class).setUIHints(batch)
					.displayName("KNN's number of neighbors").description(""), 
			new EnumPropertyDescriptor("distType", Classification.class, distTypes)
					.displayName("KNN distance type").setUIHints(batch),
			new SBPropertyDescriptor("regFactor", Classification.class).setUIHints(batch)
					.displayName("Regularization factor").description(""),
			new EnumPropertyDescriptor("ruleType", Classification.class, rules)
					.displayName("Decision Tree split rule").setUIHints(batch),
			new SBPropertyDescriptor("maxLeaves", Classification.class).setUIHints(batch)
					.displayName("Decision Tree maximum Leaves").description(""),
			new SBPropertyDescriptor("minLeaves", Classification.class).setUIHints(batch)
					.displayName("Decision Tree minimum leaves").description(""),
			new SBPropertyDescriptor("numTrees", Classification.class).setUIHints(batch)
					.displayName("Decision Tree number of trees").description(""),
			new SBPropertyDescriptor("samplingRate", Classification.class).setUIHints(batch)
					.displayName("Decision Tree sampling rate")
					.description("1.0 is sampling with replacement, < 1.0 is without replacement"),
			new SBPropertyDescriptor("shrinkage", Classification.class).setUIHints(batch)
					.displayName("Gradient Boosted Trees learning rate").description(""),
			new SBPropertyDescriptor("layerUnits", Classification.class).setUIHints(online)
					.displayName("Neural Network nodes per layer").description(""),
			new EnumPropertyDescriptor("errorType", Classification.class, errorTypes)
					.displayName("Neural Network error").setUIHints(online),
			new SBPropertyDescriptor("learningRate", Classification.class).setUIHints(online)
					.displayName("Neural Network learning rate").description(""),
			new SBPropertyDescriptor("momentum", Classification.class).setUIHints(online)
					.displayName("Neural Network momentum").description(""),
			new SBPropertyDescriptor("decay", Classification.class).setUIHints(online)
					.displayName("Neural Network weight decay").description(""),
			new EnumPropertyDescriptor("multiStrat", Classification.class, strategies)
					.displayName("SVM multiclass strategy").setUIHints(online),
			new SBPropertyDescriptor("softMargin", Classification.class).setUIHints(online)
					.displayName("SVM soft margin penalty").description(""),
			new EnumPropertyDescriptor("kernel", Classification.class, kernels)
					.displayName("SVM kernel").setUIHints(online),
			new SBPropertyDescriptor("kernelParam", Classification.class).setUIHints(online)
					.displayName("SVM kernel function parameter").description(""),
			new EnumPropertyDescriptor("bayesModel", Classification.class, bayesModels)
					.displayName("Naive Bayes model").setUIHints(online),
			new SBPropertyDescriptor("bayesSmoother", Classification.class).setUIHints(online)
					.displayName("Bayes Smoothing Pseudocount").description(""),
		};
		return p;
		
	}

}
