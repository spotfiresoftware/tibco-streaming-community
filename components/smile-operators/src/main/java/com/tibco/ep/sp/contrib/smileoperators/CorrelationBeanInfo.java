package com.tibco.ep.sp.contrib.smileoperators;

import java.beans.IntrospectionException;

import com.streambase.sb.operator.parameter.EnumPropertyDescriptor;
import com.streambase.sb.operator.parameter.SBPropertyDescriptor;
import com.streambase.sb.operator.parameter.SBSimpleBeanInfo;

public class CorrelationBeanInfo extends SBSimpleBeanInfo {

	public SBPropertyDescriptor[] getPropertyDescriptorsChecked()
			throws IntrospectionException {
		
		String[] formatTypes = {"List", "Tuple"};
		String[] emissionTypes = {"Time", "Tuples"};
		String[] outputTypes = {"List of Tuples", "Double Fields"};
		String[] corrTypes = {"Pearson's r", "Spearman's ρ", "Kendall's τ", "Cramer's V"};
		SBPropertyDescriptor[] p = {
				new EnumPropertyDescriptor("format", Correlation.class, formatTypes).displayName("Data format:"),
				new EnumPropertyDescriptor("emission", Correlation.class, emissionTypes).displayName("Emit based on:"),
				new EnumPropertyDescriptor("outputType", Correlation.class, outputTypes).displayName("Emit results in:"),
				new SBPropertyDescriptor("periodSeconds", Correlation.class).displayName("Emit every _ seconds:"),
				new SBPropertyDescriptor("frequency", Correlation.class).displayName("Emit every _ tuples:"),
				new EnumPropertyDescriptor("corrType", Correlation.class, corrTypes).displayName("Correlation type:"),
				new SBPropertyDescriptor("correlationField", Correlation.class).displayName("Correlated field:"),
				new SBPropertyDescriptor("variablesField", Correlation.class).displayName("Variables field:"),
				new SBPropertyDescriptor("windowSize", Correlation.class).displayName("Sliding window's size:"),
				new SBPropertyDescriptor("nullValue", Correlation.class).displayName("Replace null with:"),
				new SBPropertyDescriptor("decimalDigits", Correlation.class).displayName("Returned number of digits:"),
				new SBPropertyDescriptor("tableSize", Correlation.class).displayName("Cramer's table's size:"),
		};
		return p;
	}
}
