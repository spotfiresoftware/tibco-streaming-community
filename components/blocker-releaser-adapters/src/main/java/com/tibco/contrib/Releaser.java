/*
 * Copyright (c) 2015-2021 TIBCO Software Inc.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of TIBCO Software Inc.
 *
 */
package com.tibco.contrib;

import com.streambase.sb.Schema;
import com.streambase.sb.StreamBaseException;
import com.streambase.sb.Tuple;
import com.streambase.sb.adapter.OutputAdapter;
import com.streambase.sb.operator.Parameterizable;
import com.streambase.sb.operator.TypecheckException;

/**
* The Releaser adapter is linked with a Blocker adapter. See documentation there for more info.
*/
public class Releaser extends OutputAdapter implements Parameterizable {

	public static final long serialVersionUID = 1626210456103L;
	// Properties
	
	// The name use to link Blocker and Releaser 
	private String idName;
	
	private Schema tSchema;
	private String displayName = "Releaser";

	private SharedObjectManager sharedMgr;
	private SharedGateKeeper sharedKeeper;

	/**
	* 
	*/
	public Releaser() {
		super();
		setPortHints(1, 1);
		setDisplayName(displayName);
		setShortDisplayName(this.getClass().getSimpleName());
		setIdName("");
	}

	/**
	* Typecheck this adapter. 
	*/
	public void typecheck() throws TypecheckException {
		
		if ((getIdName() == null) || (getIdName().isEmpty())) {
			throw new TypecheckException("The Blocker linked name must be provided");
		}
		
		// output schema is just the input schema
		tSchema = getInputSchema(0);
		setOutputSchema(0, tSchema);
	}

	/**
	* Initialize the adapter.
	*/
	public void init() throws StreamBaseException {
		super.init();
		
		sharedMgr = getRuntimeEnvironment().getSharedObjectManager();
		sharedKeeper=(SharedGateKeeper)sharedMgr.getSharedObject(getIdName());
		
		if (sharedKeeper==null) {
			sharedKeeper=new SharedGateKeeper();
			sharedMgr.registerSharedObject(getIdName(), sharedKeeper);
		}
	}

	/**
	* Shutdown adapter.
	*/
	public void shutdown() {

	}

	/**
	 * This method will be called by the StreamBase server for each
	 * Tuple given to the adapter to process.
	 */
	public void processTuple(int inputPort, Tuple tuple) throws StreamBaseException {
		// Forward the tuple
		sendOutput(0, tuple);
		
		// Release Blocker
		sharedKeeper.release();
	}

	/***************************************************************************************
	 * The getter and setter methods provided by the Parameterizable object.               *
	 * StreamBase Studio uses them to determine the name and type of each property         *
	 * and obviously, to set and get the property values.                                  *
	 ***************************************************************************************/

	public void setIdName(String idName) {
		this.idName = idName;
	}

	public String getIdName() {
		return this.idName;
	}
}
