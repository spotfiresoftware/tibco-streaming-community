# SMILE Operators

This project integrates the Java [SMILE](https://github.com/haifengl/smile) statistics and machine learning library into StreamBase as operators. Currently, Smile's [correlation](https://github.com/haifengl/smile/blob/master/math/src/main/java/smile/stat/hypothesis/CorTest.java) and [classification](https://github.com/haifengl/smile/tree/master/core/src/main/java/smile/classification) features have been implemented. They are each their own classes and operators.

## Operators

### Correlation

The SMILE Correlation operator calculates how correlated one variable is to each of a list of other variables in a data set.

This operator has one input port and one output port. It needs two fields: a double variable, and either a list of doubles or a tuple where all fields are doubles. In either case, the last n-tuples's values are stored in a sliding window. On specified intervals, the values of the important variable are compared to each other variable's values using one of four correlation methods, then a tuple containing all of the calculated correlations is emitted.

There are a couple things to be aware of when choosing a correlation type. SMILE's Spearman calculation occasionally results in values outside of [-1,1], the reason is unclear. The other is that Kendall starts to become slow at much lower maximum window sizes than the other three calculations.

### Classification

The SMILE Classification operator builds a prediction model based on training data, then predicts the class of a list of parameters.

This operator has two input ports and one output port. The lower input port, the training port, collects data for building a SMILE classification model, while the upper port, the predicting port, is used to predict classes using the model. When a class is predicted, as long  as a model has been built, the input tuple is emitted with an additional int field containing the prediction. Both input ports require a list of doubles as parameters. The lower port also requires an int class field.

There are two categories which SMILE's classification models fall into: batch, and online. The majority of the models are batch. With batch, a model will be built when the training port receives a tuple that contains only nulls in all of its fields. With online, the models will be built by the time one training tuple is received, and will update with every incoming training tuple.

## Operator internals

### Correlation

All the data points are kept in an array of lists. When a tuple arrives that would bring the sliding window size above its maximum, the oldest entry in each list is replaced instead.

There both time based and tuple based emission modes, one of which is chosen in the operator properties. Both will eventually call `createOutput` which calls the selected type of correlation calculation on the current data set, then puts the resulting correlated values into a tuple to emit.

In tuple based emission, there's a simple counter which to check whether a given input tuple should trigger output.

In time based emission, a thread is created upon the initialization of the operator. This thread is an intermediate step to prevent multiple emissions from piling up when there's a slow calculation. Every n seconds, it calls another runnable as long as the previous instance of the other runnable has finished. This other runnable itself creates and emits the tuple. It will avoid recreating a tuple when there has not been any new input.

### Classification

When a tuple is received on the training port, if the model is online the model immediately trains on it. If the chosen model is a batch model, then the tuple's data is stored to build a batch model later.

Depending on the mode, the classifier is either `classifier` or `onClassifier`. Both interfaces have a predict method, but the online classifier also has a method to learn using one data point.

When a tuple is received on the predicting port, as long as a model has been created, this predict method is used, with its result being added to the input tuple before outputting the tuple.

The exact time models are built can differ. The Multilayered Perceptron Neural Network and Support Vector Machines online models are built upon initialization, while Naive Bayes is built once there is one input tuple (it needs the number parameters, which is not a property).

There are three SMILE classification models that have been left out: Fisher's Linear Discriminant, Radial Basis Function Network, and Maximum Entropy. All of the code for Fishers is included but commented out, due to a bug where it doesn't work if there are more than two classes. Both of the other two would work fine, but would add a great deal of complexity to the operator if included. [RBFNetworks](https://github.com/haifengl/smile/blob/master/core/src/main/java/smile/classification/RBFNetwork.java) would add multiple complicated properties to an already crowded page. [Maxent](https://github.com/haifengl/smile/blob/master/core/src/main/java/smile/classification/Maxent.java) uses integer parameters in place of doubles, which is an easy conversion to implement, but one that would need to be shown for the user to not mishandle, and would break the uniform nature of the code in multiple areas.

## Eventflow

This project includes a variety of its own sample eventflow files.

* Batch Classification Sample: an example of running training then predicting. This sample evaluates how accurate the model is via its testing set after running the training set. Every batch model is configured to run properly. To use, run the BatchTrainer feed sim, then the BatchTesting feed sim.
* Classification Tests: the tests for the classification operator.
* Correlation Sample: finds the sensors which are most correlated to a failure variable at differing points of time. Calculates and emits the correlations every .75 seconds, and emits variable with notably high correlation separately. Run the OnlineSample feed sim.
* Correlation Tests: the tests for the correlation operator.
* Online Classification Sample: displays how an online model can change over time without completely rebuilding a model. The model gradually receives training data, while every 2 seconds it gives predictions to the same dataset to gauge its current accuracy. Run the CorrelationSample feed sim.
