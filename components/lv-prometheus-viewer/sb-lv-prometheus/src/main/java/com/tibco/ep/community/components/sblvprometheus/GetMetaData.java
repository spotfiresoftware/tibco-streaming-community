/*
* Copyright © 2020. TIBCO Software Inc.
* This file is subject to the license terms contained
* in the license file that is distributed with this file.
*/
package com.tibco.ep.community.components.sblvprometheus;

import java.util.Set;

import org.slf4j.Logger;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.JSONPath;
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

		try {
			String jsonS=tuple.getString(dataName);
			JSONObject jsonO = (JSONObject)JSONPath.read(jsonS, "data");
			String dataS = "[" + jsonO.toJSONString() + "]";
			JSONArray jsonArray = JSON.parseArray(dataS);

			for (int i=0; i<jsonArray.size(); i++) {
				JSONObject jsonObj = (JSONObject)jsonArray.get(i);

				Set<String> keys= jsonObj.keySet();
				for (String metricName : keys) {
					JSONArray metaArray=(JSONArray)jsonObj.get(metricName);

					for (int j=0; j<metaArray.size(); j++) {
						JSONObject metaObj= (JSONObject) metaArray.get(j);

						Tuple output=outputSchema.createTuple();
						output.setField(nameName, metricName);
						output.setString(nameType, metaObj.get("type").toString());
						output.setString(nameHelp, metaObj.get("help").toString());
						output.setString(nameUnits, metaObj.get("unit").toString());

						sendOutput(0,output);
					}
				}
			}
		} catch (Exception e) {
			logger.warn(String.format("Exception parsing metadata: %s", e.getMessage()));
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
