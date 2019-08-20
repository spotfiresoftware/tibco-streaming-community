package com.tibco.ep.sp.contrib.smileoperators;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import smile.stat.hypothesis.CorTest;
import smile.sort.QuickSort;

import com.streambase.sb.CompleteDataType;
import com.streambase.sb.DataType;
import com.streambase.sb.NullValueException;
import com.streambase.sb.Schema;
import com.streambase.sb.StreamBaseException;
import com.streambase.sb.Tuple;
import com.streambase.sb.TupleException;
import com.streambase.sb.operator.Operator;
import com.streambase.sb.operator.Parameterizable;
import com.streambase.sb.operator.TypecheckException;

/**
 * The SMILE Correlation integration operator.
 * </p>
 * The input schema must have a double field, and data in a tuple/list containing only doubles. The operator
 * then stores those values like a row, replacing the oldest row if the window size property has been
 * reached. On specified intervals, the operator emits a tuple containing a list of the correlation between 
 * each field in the data and the double variable. If the format is tuple, the list contains tuples that each
 * have a name and a correlation. If the format is list, the list only contains correlation doubles.
 * </p>
 * To calculate the correlation, this operator makes a SMILE CorTest and takes its cor field for each pairing of
 * the variable and each field in the data structure. There are four types: Pearson, Spearman, Kendall, and Cramer 
 * (in SMILE under the name chisq).
 * Internally, the first three have the exact same input format, but as chisq requires a rank table, some of the functionality
 * in the Spearman function has been lifted in order to make that table.
 */
public class Correlation extends Operator implements Parameterizable {

	public static final long serialVersionUID = 1410354061780L;
	private String displayName = "Correlator (SMILE)";
	private Schema outputSchema;
	private Logger logger;
	
	// Properties
	
	/**
	 * Which input format to use.
	 */
	private String format;
	
	/**
	 * Whether to emit based on every n seconds or by every k tuples.
	 */
	private String emission;
	
	/**
	 * What output type to use when input is a tuple.
	 */
	private String outputType;
	
	/**
	 * Which correlation calculation to use.
	 */
	private String corrType;
	
	/**
	 * The name of the field for the variable which to correlate with all others.
	 */
	private String correlationField;
	
	/**
	 * The name of the field for the list/tuple holding all other variable data.
	 */
	private String variablesField;
	
	/**
	 * The number of data points per variable to store.
	 */
	private int windowSize;
	
	/**
	 * The value which to replace nulls with.
	 */
	private double nullValue;
	
	/**
	 * The size of the table for Cramer's V
	 */
	private int tableSize;
	
	/**
	 * The number of digits after the decimal point to keep.
	 */
	private int decimalDigits;
	
	/**
	 * The number of tuples between emissions by tuple
	 */
	private int frequency;
	
	/**
	 * The number of seconds between emissions by time.
	 */
	private double periodSeconds;

	// Variables
	
	/**
	 * Which tuple this is when emitting every k tuples.
	 */
	private int position;
	
	/**
	 * The number of variables to correlate with the important variable.
	 */
	private int correlationsLength; 
	
	/**
	 * The data structure holding all the values.
	 */
	private ArrayList<Double>[] window;
	
	/**
	 * The next index in the arraylist to replace/add into.
	 */
	private int activeIndex;
	
	/**
	 * The schema which tuple elements of the output list use.
	 */
	private Schema dataSchema;
	
	/**
	 * A list of tuple field names. 
	 */
	private String[] names;
	
	/**
	 * A lock to stop the time thread from reading while updateWindow is writing.
	 */
	private Lock lock;
	
	/**
	 * A tuple holding current correlations. Null if there has been input since last calculation.
	 */
	private Tuple out;
	
	/**
	 * Constructor. Sets the default value for every property.
	 */
	public Correlation() {
		super();
		correlationsLength = 0;
		position = 1;
		setPortHints(1, 1);
		setDisplayName(displayName);
		setShortDisplayName(this.getClass().getSimpleName());
		setCorrType("Cramer's V");
		setPeriodSeconds(0.5);
		setFrequency(10);
		setCorrelationField("value");
		setVariablesField("data");
		setWindowSize(100);
		setNullValue(0.0);
		setDecimalDigits(3);
		setTableSize(10);
	}
	
	/**
	 * The typecheck method for this operator. Checks every property.
	 * @throws TypecheckException
	 */
	public void typecheck() throws TypecheckException {
		requireInputPortCount(1);
		String varField = "variablesField";
		String corField = "correlationField";

		// correlationField
		try {
			if (getInputSchema(0).getField(getCorrelationField()).getDataType() != DataType.DOUBLE) {
				throw new PropertyTypecheckException(corField,
						  getCorrelationField() + " needs to be of the type double");
			}
		} catch (TupleException e1) {
			throw new PropertyTypecheckException(corField, "A correlated value field needs to be specified");
		}
		
		// variablesField
		if (getFormat().equals("List")) { // if in a list format
			try {
				if (!getInputSchema(0).getField(getVariablesField())
						.getCompleteDataType().equals(CompleteDataType.forDoubleList())) {
					throw new PropertyTypecheckException(varField, 
							  getVariablesField() + " needs to be of the type list of doubles");
				}
			} catch (TupleException e1) {
				throw new PropertyTypecheckException(varField, "A data field needs to be specified");
			}
		} else { // if in a tuple format
			try {
				if (!getInputSchema(0).getField(getVariablesField()).getDataType().equals(DataType.TUPLE))
					throw new PropertyTypecheckException(varField, getVariablesField() + " needs to be a tuple");
				Schema.Field[] fields = getInputSchema(0).getField(getVariablesField()).getNestedFields();
				for (Schema.Field field : fields) {
					if (!field.getDataType().equals(DataType.DOUBLE))
						throw new PropertyTypecheckException(varField, getVariablesField() + " must only contain doubles");
						
				}
			} catch (TupleException e1) {
				throw new PropertyTypecheckException(varField, "A data field needs to be specified");
			}
		}
		
		// periodSeconds
		if (getPeriodSeconds() <= 0) {
			throw new PropertyTypecheckException("periodSeconds", "Cannot emit based on a non-positive number of seconds");
		}

		// frequency
		if (getFrequency() <= 0) {
			throw new PropertyTypecheckException("frequency", "Cannot emit based on a non-positive number of tuples");
		}
			
		// windowSize
		if (getWindowSize() <= 1) {
			throw new PropertyTypecheckException("windowSize", "Cannot calculate correlation with less than two values");
		}
		
		// decimalDigits
		if (getDecimalDigits() < 1) {
			throw new PropertyTypecheckException("decimalDigits", "There must be at least 1 digit after the decimal point");
		}
		
		// frequency
		if (getFrequency() < 1) {
			throw new PropertyTypecheckException("frequency", "The frequency which to output tuples must be positive");
		}
		
		// tableSize
		if (getCorrType().equals("Cramer's V") && getTableSize() <= 1) {
			throw new PropertyTypecheckException("tableSize", "Cramer table size must be at least 2");
		}
		
		// setting output schema
		if (getFormat().equals("List")) {
			Schema.Field[] fields = {Schema.createListField("correlations", CompleteDataType.forDouble())};
			outputSchema = setOutputSchema(0, new Schema(correlationField, fields));
		} else {
			if (getOutputType().equals("Double Fields")) {
				try {
					Schema.Field[] things = getInputSchema(0).getFieldsByName(new String[] {variablesField});
					outputSchema = setOutputSchema(0, things[0].getSchema());
				} catch (StreamBaseException e) {}
			} else {
				dataSchema = new Schema(null, new Schema.Field[] {Schema.createField(DataType.STRING, "name"),
					    Schema.createField(DataType.DOUBLE, "value")});
				Schema.Field[] fields = {Schema.createListField("correlations", CompleteDataType.forTuple(dataSchema))};
				outputSchema = setOutputSchema(0, new Schema(null, fields));
			}
		}
	}

	/**
	 * The init method for this operator. If the emission type is by time, make and schedule
	 * the task to emit every n seconds from its own thread(s).
	 * @throws StreamBaseException
	 */
	@Override
	public void init() throws StreamBaseException {
		super.init();
		logger = getLogger();
		if (emission.equals("Tuples")) return;
		
		// The lock is needed for this task so that it doesn't read while updateWindow is 
		// writing, but isn't needed for the single-threaded tuple-based emission.
		lock = new ReentrantLock();
		
		// Allow periods between a millisecond and roughly 
		int periodMilis = (int) (periodSeconds * 1000);
		
		// Create the runnable that calculates and emits output.
		Runnable actualTask = () -> {
			try {
				lock.lock();
				if (!this.isRunning()) return;
				
				// Don't output anything if there is 1 or less entries.
				if (correlationsLength != 0 && window != null && window[0].size() >= 2) {
					try {
						createOutput();
					} catch (StreamBaseException e) {
						logger.error("Error in calculating cramer", e);
					}
				}
			} finally {
				lock.unlock();
			}
			if (out != null) {
				try {
					// If sendOutput is called before an unlock, sometimes the unlock isn't reached, 
					// even if the unlock is in a finally block. No clue why, so it's here instead.
					sendOutput(0, out);
				} catch (StreamBaseException e) {
					logger.error("Error sending output", e);
				}
			}
		};
		
		// Create a runner that creates a thread on a schedule. This way this thread knows if the other thread
		// has finished running yet, so more won't spawn in case of a delayed execution.
		ScheduledExecutorService sees = Executors.newScheduledThreadPool(1);
		sees.scheduleAtFixedRate(new Runnable() {
			private final ExecutorService executor = Executors.newSingleThreadExecutor();
		    private Future<?> lastExecution;

			@Override
			public void run() {
				try {
			        if (lastExecution != null && !lastExecution.isDone()) {
			            return;
			        }
			        
			        lastExecution = executor.submit(actualTask);
				} catch (RuntimeException e) { // Prevent this thread from stopping on an exception
					logger.error("Encountered an issue:", e);
				}
			}
		}, 0, periodMilis, TimeUnit.MILLISECONDS);	
	}
	
	/**
	 * Processes the incoming tuple. Updates the window, then calculates and outputs the correlations.
	 * @throws StreamBaseException
	 */
	public void processTuple(int inputPort, Tuple tuple) throws StreamBaseException {
		if (correlationsLength == 0) {
			if (format.equals("Tuple")) {
				names = tuple.getTuple(getVariablesField()).getSchema().getFieldNames();
			}
		}

		updateWindow(tuple);
		if (emission.equals("Time")) return; // Time emits elsewhere
		if (window[0].size() < 2) return; // Cannot calculate covariance with one entry.
		
		// Check if a tuple should be created.
		position++;
		if (position < frequency) return;
		position = 0;
		createOutput();
		sendOutput(0, out);
	}
	
	/**
	 * Updates the sliding window with the content from an input tuple.
	 * @param tuple The tuple to get data from.
	 */
	private void updateWindow(Tuple tuple) throws StreamBaseException {
		// Get the data from the input tuple into a Double list.
		List<Double> inList = listFromTuple(tuple);
		// Also get the value from the field to correlate
		double corrVal = tuple.getDouble(correlationField);
		
		try {
			if (emission.equals("Time")) lock.lock();
			
			if (correlationsLength == 0) {
				initializeWindow(inList);
			} else if (inList.size() != correlationsLength) { // Can happen if using a list data type.
				throw new StreamBaseException(variablesField + " needs to have a consistent number of elements");
			}
			
			if (window[0].size() != windowSize) { // On first loop through window just add it to the window.
				window[0].add(corrVal);
				for (int i = 1; i <= correlationsLength; i++) {
					window[i].add(inList.get(i - 1));
				}
			} else { // Afterwards replace the oldest element in each list.
				window[0].set(activeIndex, corrVal);
				for (int i = 1; i <= correlationsLength; i++) {
					window[i].set(activeIndex, inList.get(i - 1));
				}
			}
			
			// Reset the tuple to emit so that createOutput will recalculate it.
			out = null;
		} finally {
			if (emission.equals("Time")) lock.unlock();
		}
		
		activeIndex++;
		if (activeIndex == windowSize) activeIndex = 0;
	}
	
	/**
	 * Sets up the window. 
	 * @param inList The list to use as the first entry.
	 */
	@SuppressWarnings("unchecked")
	private void initializeWindow(List<Double> inList) {
		correlationsLength = inList.size();
		window = new ArrayList[correlationsLength + 1];
		for (int i = 0; i <= correlationsLength; i++) {
			window[i] = new ArrayList<Double>(windowSize);
		}
		activeIndex = 0;
	}
	
	/**
	 * Gets the list of doubles from the input tuple.
	 * @param tuple the tuple to get the list from
	 * @return the created list
	 * @throws StreamBaseException
	 */
	@SuppressWarnings("unchecked")
	private List<Double> listFromTuple(Tuple tuple) throws StreamBaseException {
		List<Double> inList;
		if (format.equals("List")) {
			inList = (ArrayList<Double>) new ArrayList<>(tuple.getList(variablesField));
			for (int i = 0; i < correlationsLength; i++) { // Replace nulls with default value.
				if (inList.get(i) == null) inList.set(i, nullValue);
			}
		} else {
			inList = new ArrayList<Double>();
			Tuple varsTuple = tuple.getTuple(getVariablesField());
			for (String name : names) { // Iterate through each tuple to get the values.
				try { 
					inList.add(varsTuple.getDouble(name));
				} catch (NullValueException e) { // Replace nulls with default value.
					inList.add(nullValue);
				}
			}
		}
		return inList;
	}
	
	/**
	 * Calculate the current correlations, then process into a tuple.
	 * @param tuple
	 * @throws StreamBaseException
	 */
	private void createOutput() throws StreamBaseException {
		// out is up-to-date if it is not null.
		if (out != null) {
			return; 
		}
		out = outputSchema.createTuple();

		// Calculate the correlations for each pair.
		ArrayList<Double> correlations = null; 
		switch (corrType) {
			case "Pearson's r": correlations = pearsonCorrelation();
							 	break;
			case "Kendall's τ": correlations = kendallCorrelation();
								break;
			case "Spearman's ρ":correlations = spearmanCorrelation(); 
							 	break;
			case "Cramer's V":	correlations = cramerCorrelation();
								break;
		}
		roundDecimal(correlations, decimalDigits);

		// Then put the calculated correlations into the out tuple.
		if (format.equals("List")) {
			out.setList("correlations", correlations);
		} else if (outputType.equals("List of Tuples")){
			List<Tuple> outList = new ArrayList<Tuple>(correlationsLength);
			// Give each nested output tuple its old name as a field in addition to the value.
			for (int i = 0; i < correlationsLength; i++) {
				Tuple dataTuple = dataSchema.createTuple();
				dataTuple.setField("name", names[i]);
				dataTuple.setField("value", correlations.get(i));
				outList.add(dataTuple);
			}
			out.setList("correlations", outList);
		} else {
			for (int i = 0; i < correlationsLength; i++) {
				out.setField(names[i], correlations.get(i));
			}
		}
	}
		
	/**
	 * Calls SMILE's implementation of Pearson's R covariance calculation on the value to correlate
	 * with every other variable in the window.
	 * @return A list of the calculated correlation values.
	 */
	private ArrayList<Double> pearsonCorrelation() {
		if (window[0].size() == 2) return new ArrayList<Double>(Collections.nCopies(window.length - 1, 0.0));
		double[] dependentVar = listConverter(window[0]);
		
		ArrayList<Double> correlations = new ArrayList<>(window.length - 1);
		for (int i = 1; i < window.length; i++) {
			correlations.add(CorTest.pearson(dependentVar, listConverter(window[i])).cor);
		}
		
		return correlations;
	}
	
	/**
	 * Calls SMILE's implementation of Spearman's Rho covariance calculation on the value to correlate
	 * with every other variable in the window.
	 * @return A list of the calculated correlation values.
	 */
	private ArrayList<Double> spearmanCorrelation() {
		double[] dependentVar = listConverter(window[0]);
		
		ArrayList<Double> correlations = new ArrayList<>(window.length - 1);
		for (int i = 1; i < window.length; i++) {
			correlations.add(CorTest.spearman(dependentVar, listConverter(window[i])).cor);
		}
		
		return correlations;
	}
	
	/**
	 * Calls SMILE's implementation of Kendall's Tau covariance calculation on the value to correlate
	 * with every other variable in the window.
	 * @return A list of the calculated correlation values.
	 */
	private ArrayList<Double> kendallCorrelation() {
		double[] dependentVar = listConverter(window[0]);
		
		ArrayList<Double> correlations = new ArrayList<>(window.length - 1);
		for (int i = 1; i < window.length; i++) {
			correlations.add(CorTest.kendall(dependentVar, listConverter(window[i])).cor);
		}
		
		return correlations;
	}
	
	/**
	 * Calls SMILE's implementation of Cramer's V covariance calculation on the value to correlate
	 * with every other variable in the window.
	 * This one is slightly different, as SMILE's Cramer V wants a table of integer ranks instead of 
	 * two vectors of doubles. 
	 * @return A list of the calculated correlation values.
	 */
	private ArrayList<Double> cramerCorrelation() {
		ArrayList<Double> correlations = new ArrayList<>(window.length - 1);
		for (int i = 1; i < window.length; i++) {
			correlations.add(CorTest.chisq(colsToTable(window[0], window[i])).cor);
		}
		
		return correlations;
	}

	/**
	 * Compresses two vectors into a table of integer rankings. 
	 * 
	 * SMILE's Spearman uses a private function called crank in order to rank, so to transform
	 * the vectors crank has been lifted, along with the other method SMILE's Spearman uses
	 * which is an importable Quicksort that keeps another vector's elements stable
	 * with the sorted one.
	 * @param col1 Vector one.
	 * @param col2 Vector two.
	 * @return A table representing the ranking of each element in both vectors.
	 */
	private int[][] colsToTable(ArrayList<Double> col1, ArrayList<Double> col2) {
		double[] x = listConverter(col1);
		double[] y = listConverter(col2);
		QuickSort.sort(x, y);
		crank(x);
		QuickSort.sort(y, x);
		crank(y);
		
		int[][] table = new int[tableSize][tableSize];
		for (int i = 0; i < x.length; i++) table[(int) x[i]][(int) y[i]]++;
		return table;
	}
	
    /**
     * Just lifted from SMILE's CorTest to rank vectors for Cramer.
     * @see smile.stat.hypothesis.CorTest#crank
     */
    private void crank(double[] w) {
        int n = w.length;
        int j = 1;
        while (j < n) {
            if (w[j] != w[j - 1]) {
                w[j - 1] = j;
                ++j;
            } else {
                int jt = j + 1;
                while (jt <= n && w[jt - 1] == w[j - 1]) {
                    jt++;
                }

                double rank = 0.5 * (j + jt - 1);
                for (int ji = j; ji <= (jt - 1); ji++) {
                    w[ji - 1] = rank;
                }

                @SuppressWarnings("unused")
				double t = jt - j;
                j = jt;
            }
        }

        if (j == n) {
            w[n - 1] = n;
        }
        
        for (int i = 0; i < w.length; i++) w[i] = ((w[i] - 1) / w.length) * tableSize;
    }

	/**
	 * Rounds every number in a list of doubles to only have a few numbers after the decimal.
	 * @param numbers The list of numbers to round.
	 * @param decimalPlaces The place to round to.
	 */
	private void roundDecimal(ArrayList<Double> numbers, int decimalPlaces) {
		int multiplier = (int) Math.pow(10, decimalPlaces);
		for (int i = 0; i < numbers.size(); i++) {
			if (Double.isNaN(numbers.get(i)) || numbers.get(i) == null) continue;
			int scaled = (int) (numbers.get(i) * multiplier);
			if (scaled < 0) {
				double forth = numbers.get(i) * multiplier - scaled;
				if (forth <= -0.50) scaled -= 1;
			} else {
				double forth = numbers.get(i) * multiplier - scaled;
				if (forth >= 0.50) scaled += 1;
			}
			numbers.set(i, scaled / (double) multiplier);
		}
	}
	
	/**
	 * Turns a list into an array. 
	 * SB uses lists (object Double) while SMILE uses arrays (primitive double), so this needs to be used in translation.
	 * @param toConvert The list of Double objects to convert into an array of double primitives.
	 */
	private double[] listConverter(List<Double> toConvert) {
		return toConvert.stream().mapToDouble(Double::doubleValue).toArray();
	}
	
    
	/***************************************************************************************
	 * The getter and setter methods provided by the Parameterizable object.               *
	 * StreamBase Studio uses them to determine the name and type of each property,        *
	 * and to set and get the property values.                  			               *
	 ***************************************************************************************/
    
    public void setEmission(String emission) {
    	this.emission = emission;
    }
    
    public String getEmission() {
    	return this.emission;
    }
    
    public void setFormat(String name) {
    	this.format = name;
    }
    
    public String getFormat() {
    	return this.format;
    }
    
	public void setCorrType(String corrType) {
		this.corrType = corrType;
	}

	public String getCorrType() {
		return this.corrType;
	}
	
	public void setOutputType(String outputType) {
		this.outputType = outputType;
	}
	
	public String getOutputType() {
		return this.outputType;
	}
	
	public void setFrequency(int frequency) {
		this.frequency = frequency;
	}
	
	public int getFrequency() {
		return this.frequency;
	}
	
	public void setPeriodSeconds(double periodSeconds) {
		this.periodSeconds = periodSeconds;
	}
	
	public double getPeriodSeconds() {
		return this.periodSeconds;
	}
	
	public void setCorrelationField(String correlationField) {
		this.correlationField = correlationField;
	}

	public String getCorrelationField() {
		return this.correlationField;
	}
	
	public void setVariablesField(String variablesField) {
		this.variablesField = variablesField;
	}

	public String getVariablesField() {
		return this.variablesField;
	}
	
	public void setWindowSize(int windowSize) {
		this.windowSize = windowSize;
	}
	
	public int getWindowSize() {
		return this.windowSize;
	}
	
	public void setNullValue(double nullValue) {
		this.nullValue = nullValue;
	}
	
	public double getNullValue() {
		return this.nullValue;
	}
	
	public void setDecimalDigits(int decimalDigits) {
		this.decimalDigits = decimalDigits;
	}
	
	public int getDecimalDigits() {
		return this.decimalDigits;
	}
	
	public void setTableSize(int tableSize) {
		this.tableSize = tableSize;
	}
	
	public int getTableSize() {
		return this.tableSize;
	}
	
	public boolean shouldEnableOutputType() {
		return this.format.equals("Tuple");
	}
	
	public boolean shouldEnableFrequency() {
		return this.emission.equals("Tuples");
	}
	
	public boolean shouldEnablePeriodSeconds() {
		return this.emission.equals("Time");
	}

	public boolean shouldEnableTableSize() {
		return this.corrType.equals("Cramer's V");
	}
}
