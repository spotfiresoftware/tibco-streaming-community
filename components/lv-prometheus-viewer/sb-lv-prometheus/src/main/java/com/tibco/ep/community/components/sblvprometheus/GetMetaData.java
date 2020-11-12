/*
* Copyright © 2020. TIBCO Software Inc.
* This file is subject to the license terms contained
* in the license file that is distributed with this file.
*/
package com.tibco.ep.community.components.sblvprometheus;

import java.util.HashMap;
import java.util.List;

import org.slf4j.Logger;

import com.streambase.com.jayway.jsonpath.Configuration;
import com.streambase.com.jayway.jsonpath.JsonPath;
import com.streambase.com.jayway.jsonpath.Option;
import com.streambase.sb.CompleteDataType;
import com.streambase.sb.Schema;
import com.streambase.sb.StreamBaseException;
import com.streambase.sb.Tuple;
import com.streambase.sb.adapter.common.AdapterUtil;
import com.streambase.sb.operator.Operator;
import com.streambase.sb.operator.Parameterizable;
import com.streambase.sb.operator.TypecheckException;


/**
 * Operator to parse Prometheus api/v1/metadata URL data
 */
public class GetMetaData extends Operator implements Parameterizable {

	public static final long serialVersionUID = 1601380368906L;
	private String displayName = "Parse Prometheus MetaData";
	// Local variables
	private int inputPorts = 1;
	private int outputPorts = 1;

	private Logger logger=AdapterUtil.getLogger(this, LogLevel.INFO);
	
	private final String dataName="data";
	
	private final String nameName="Name";
	private final String nameType="type";
	private final String nameHelp="help";
	private final String nameUnits="unit";
	
	private Schema.Field fieldName=new Schema.Field(nameName, CompleteDataType.forString());
	private Schema.Field fieldType=new Schema.Field(nameType, CompleteDataType.forString());
	private Schema.Field fieldHelp=new Schema.Field(nameHelp, CompleteDataType.forString());
	private Schema.Field fieldUnits=new Schema.Field(nameUnits, CompleteDataType.forString());
	
	private Schema outputSchema = new Schema(null, fieldName, fieldType, fieldHelp, fieldUnits);
	
	/**

	 */
	public GetMetaData() {
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
		List<HashMap<String, String>> jsonHash=jsonPathHash(jsonS, "data");

		try {
			for (HashMap<String, String> metricsHash : jsonHash) {

				String [] nameArray=metricsHash.keySet().toArray(new String[0]);
				for (int j=0; j<nameArray.length; j++) {
					 String	metricName=nameArray[j];

					Object MetricData=(Object)metricsHash.get(metricName);
					com.streambase.net.minidev.json.JSONArray fa = (com.streambase.net.minidev.json.JSONArray)MetricData;

					for (int i=0 ; i<fa.size() ; i++) {
						try {
							Object metaData=fa.get(i);
							HashMap<String, String> parts=(HashMap<String, String>)metaData;
							
							Tuple output=outputSchema.createTuple();
							
							output.setField(nameName, metricName);
							output.setString(nameType, parts.get("type"));
							output.setString(nameHelp, parts.get("help"));
							output.setString(nameUnits, parts.get("unit"));
							
							sendOutput(0,output);
						} catch (Exception ex) {
							logger.warn(ex.getMessage());
						}
					}
				}
			}
		
		} catch (Exception e) {
			logger.warn(e.getMessage());
		}
	}
	
	private List<HashMap<String, String>> jsonPathHash(String json, String jsonPath) {  // TODO: TEMPORARILY CHANGED FROM LIST<STRING> TO STRING FOR DEBUGGING     
	    Configuration conf = Configuration.defaultConfiguration().addOptions(Option.ALWAYS_RETURN_LIST);
	    JsonPath jsonObj = JsonPath.compile(jsonPath);
	    List<HashMap<String, String>> ret = jsonObj.read(json, conf);
	    return ret;
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
