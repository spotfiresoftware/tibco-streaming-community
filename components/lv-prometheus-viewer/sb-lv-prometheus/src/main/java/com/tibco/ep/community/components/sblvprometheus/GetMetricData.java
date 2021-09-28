/*
* Copyright © 2020. TIBCO Software Inc.
* This file is subject to the license terms contained
* in the license file that is distributed with this file.
*/
package com.tibco.ep.community.components.sblvprometheus;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.streambase.sb.CompleteDataType;
import com.streambase.sb.Schema;
import com.streambase.sb.StreamBaseException;
import com.streambase.sb.Tuple;
import com.streambase.sb.TupleException;
import com.streambase.sb.TupleJSONUtil;
import com.streambase.sb.adapter.common.AdapterUtil;
import com.streambase.sb.operator.Operator;
import com.streambase.sb.operator.Parameterizable;
import com.streambase.sb.operator.TypecheckException;


/**
 * Operator to parse Prometheus api/v1/metadata URL data
 */
public class GetMetricData extends Operator implements Parameterizable {

	public static final long serialVersionUID = 1601380368906L;
	private String displayName = "Parse Prometheus MetaData";
	// Local variables
	private int inputPorts = 1;
	private int outputPorts = 1;

	private Logger logger=AdapterUtil.getLogger(this, LogLevel.INFO);
	
	private final String dataName="data";
	
	private final String nameName="Name";
	private final String nameValue="Value";
	private final String otherLabels="OtherLabels";
	
	private Set<String> columnNames;
	private Map<String, Schema.Field> columnFields = new HashMap<String, Schema.Field>();

	//
	// While non-conforming for JSON, Prometheus uses string representation positive and negative infinity and NaN
	//
    private static final String PROMETHEUS_POS_INF = "+Inf";
    private static final String PROMETHEUS_NEG_INF = "-Inf";
    private static final String PROMETHEUS_NAN = "NaN";

	private Schema.Field fieldName=new Schema.Field(nameName, CompleteDataType.forString());
	private Schema.Field fieldOther=null;
	private Schema.Field fieldValue=new Schema.Field(nameValue, CompleteDataType.forDouble());
	
	private Schema outputSchema;
	
	private Schema tableSchema;
	
	/**

	 */
	public GetMetricData() {
		super();
		setPortHints(inputPorts, outputPorts);
		setDisplayName(displayName);
		setShortDisplayName(this.getClass().getSimpleName());
	}

	/**
	*/
	public void typecheck() throws TypecheckException {
		requireInputPortCount(inputPorts);
		Schema inputSchema=getInputSchema(0);
		if (!inputSchema.hasField(dataName)) {
			throw new TypecheckException(String.format("Input port must have %s field", dataName));
		}

		columnNames = Set.of(getTableschema().getFieldNames());
				
		if (!columnNames.contains(nameName)) {
			throw new TypecheckException("Table must contain a string field Name");
		}
		if (!columnNames.contains(nameValue)) {
			throw new TypecheckException("Table must contain a double field Value");
		}
		
		outputSchema = getTableschema();
		setOutputSchema(0, outputSchema);
	}

	/**
	* @param inputPort the input port that the tuple is from (ports are zero based)
	* @param tuple the tuple from the given input port
	* @throws StreamBaseException Terminates the application.
	*/
	public void processTuple(int inputPort, Tuple tuple) throws StreamBaseException {

		String jsonS=tuple.getString(dataName);
		
		String dataS=TupleJSONUtil.jsonPath(jsonS, "data");
		String dataSTrim=dataS.substring(1, dataS.length()-1);
		String resultTypeS=TupleJSONUtil.jsonPath(dataSTrim, "resultType");
		
		String resultS=TupleJSONUtil.jsonPath(dataSTrim, "result");
		String resultSTrim=resultS.substring(1, resultS.length()-1);
				
		// This takes the result string and returns an array with each element being a metric.
		JSONArray results=(JSONArray)TupleJSONUtil.parseJSONString(resultSTrim);

		for (int i=0; i<results.size(); i++) {
			JSONObject metricJS=results.getJSONObject(i);
			
			// Get the value
			JSONArray valueJA =metricJS.getJSONArray("value");
			String value=(String)valueJA.get(1);
			
			// get all the metrics
			JSONObject metricJO=(JSONObject)metricJS.get("metric");
			StringBuilder mFilter= new StringBuilder();
			String mName=null;
			boolean first=true;
			Tuple output=outputSchema.createTuple();
			output.setDouble(nameValue, getDouble(value));
			
			for (String k: metricJO.keySet()) {
					if ("__name__".equals(k)) {
						mName=(String)metricJO.get("__name__");
						continue;
					}
	
					if (columnNames.contains(k)) {
						// Set the known label name
						setLabel(output, k, (String)metricJO.get(k));
//						output.setString(k, (String)metricJO.get(k));
						continue;
					}
					
					if (first) {
						first=false;
					} else {
						mFilter.append(",");
					}
					// Just build up the filter
					mFilter.append(k).append("=").append((String)metricJO.get(k));
			}
			
			try {
				output.setString(nameName, mName);
				if (fieldOther!=null && mFilter.length()>0) {
					output.setString(fieldOther, mFilter.toString());
				}
				
				sendOutput(0,output);
			} catch (Exception e) {
				logger.warn(String.format("Failed to set output tuple: %s", e.getMessage()));
			}
		}
	}
	
	
	private void setLabel(Tuple output, String k, String v) throws TupleException {
		Schema.Field f = output.getSchema().getField(k);
		output.setField(f, v);
		
//		if (f.getCompleteDataType()==CompleteDataType.forString()) {
//			output.setField(f, v);
//		}
	}
	
	/*
	 * While non-conforming to JSON (rfc4627), Prometheus uses +/-Inf and Nan
	 */
	private Double getDouble(String value) {

	    if (value.equals(PROMETHEUS_POS_INF)) {
	        return Double.POSITIVE_INFINITY;
	    }
 
	    if (value.equals(PROMETHEUS_NEG_INF)) {
	        return Double.NEGATIVE_INFINITY;
	    }
 
	    if (value.equals(PROMETHEUS_NAN)) {
	        return Double.NaN;
	    }
 
	    try {
	        return Double.valueOf(value);
	    } catch (NumberFormatException e) {
	        logger.warn(String.format("Exception converting value", e.getMessage()));
	        return null;
	    }
	}

	/**
	 */
	public void init() throws StreamBaseException {
		super.init();
		outputSchema = getRuntimeOutputSchema(0);
		
		if (outputSchema.hasField(otherLabels)) {
			fieldOther=outputSchema.getField(otherLabels);
		}
		
		// get the label fields out of the output schema
		for (Schema.Field f : outputSchema.getFields()) {
			columnFields.put(f.getName(), f);
		}
	}

	/**
	*  The shutdown method is called when the StreamBase server is in the process of shutting down.
	*/
	public void shutdown() {
	}

	
	public Schema getTableschema() {
		return tableSchema;
	}
	public void setTableschema(Schema ts) {
		tableSchema=ts;
	}
}
