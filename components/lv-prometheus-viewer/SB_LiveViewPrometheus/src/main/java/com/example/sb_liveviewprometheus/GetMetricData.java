/*
* Copyright © 2020. TIBCO Software Inc.
* This file is subject to the license terms contained
* in the license file that is distributed with this file.
*/
package com.example.sb_liveviewprometheus;

import org.slf4j.Logger;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.streambase.sb.CompleteDataType;
import com.streambase.sb.Schema;
import com.streambase.sb.StreamBaseException;
import com.streambase.sb.Tuple;
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
	private final String nameFilter="Filter";
	private final String nameValue="Value";
	
	private Schema.Field fieldName=new Schema.Field(nameName, CompleteDataType.forString());
	private Schema.Field fieldFilter=new Schema.Field(nameFilter, CompleteDataType.forString());
	private Schema.Field fieldValue=new Schema.Field(nameValue, CompleteDataType.forDouble());
	private Schema outputSchema = new Schema(null, fieldName, fieldFilter, fieldValue);
	
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
			String mValue=null;
			boolean first=true;
			for (String k: metricJO.keySet()) {
					if ("__name__".equals(k)) {
						mName=(String)metricJO.get("__name__");
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
	
			Tuple output=outputSchema.createTuple();
			output.setString(nameName, mName);
			output.setString(nameFilter, mFilter.toString());
			output.setDouble(nameValue, Double.valueOf(value));
			sendOutput(0,output);
		}
	}
	
	/**
	 */
	public void init() throws StreamBaseException {
		super.init();
		outputSchema = getRuntimeOutputSchema(0);
	}

	/**
	*  The shutdown method is called when the StreamBase server is in the process of shutting down.
	*/
	public void shutdown() {
	}

}
