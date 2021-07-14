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
import com.streambase.sb.adapter.InputAdapter;
import com.streambase.sb.operator.Parameterizable;
import com.streambase.sb.operator.TypecheckException;

/**
* The Blocker adapter is intended to take a tuple in the from event flow and not return
* from the processTuple method until signaled to do so by a linked Releaser adapter.
* 
* The link between the Blocker and the Releaser is a simple string name. Multiple pairs
* of Blocker/Releaser adapters may be used, but their linking names must be unique.
* Linked adapters must be in the same container - linking does not occur across containers.
*   
* The linking names are not typechecked, so authors are responsible to ensuring there
* is an exact match.
*/
public class Blocker extends InputAdapter implements Parameterizable {

	public static final long serialVersionUID = 1626207289559L;
	// Properties
	
	// The name use to link Blocker and Releaser 
	private String idName;
	
	private Schema tSchema;
	private String displayName = "Blocker";
	
	private SharedObjectManager sharedMgr;
	private SharedGateKeeper sharedKeeper;

	/**
	* 
	*/
	public Blocker() {
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

		if ((getIdName() ==null) || (getIdName().isEmpty())) {
			throw new TypecheckException("The Releaser linked name must be provided");
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

	@Override
    public void processTuple(int inputPort, Tuple tuple) throws StreamBaseException
    {
		// Forward the tuple
		sendOutput(0, tuple);
		
		// Wait to return until Releaser signals.
		sharedKeeper.waitForRelease();
		
    }
	
	/**
	* Shutdown adapter
	*/
	public void shutdown() {

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
