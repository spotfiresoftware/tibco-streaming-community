//
// Copyright (c) 2004-2009 StreamBase Systems, Inc. All rights reserved.
//
package com.example.ev3;

import com.streambase.sb.*;
import com.streambase.sb.operator.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.streambase.sb.StreamBaseException;
import com.streambase.sb.adapter.InputAdapter;
import com.streambase.sb.adapter.OutputAdapter;
import com.streambase.sb.operator.Operator;

public class EV3SharedObject implements Runnable, Comparable<EV3SharedObject>
{
	private static EV3SharedObject instance = new EV3SharedObject();
	
	//stored shared data
	private EV3ConnectionManager manager;
	
	//properties
	private boolean PortAMotor;
	private boolean PortBMotor;
	private boolean PortCMotor;
	private boolean PortDMotor;

	private EV3SharedObject(EV3ConnectionManager m){
		//TODO
	}
	
	private EV3SharedObject() {
		manager = null;//TODO does this make sense?
	}
	
	public EV3SharedObject getEV3SharedObject() {
		//TODO make one from a connection manager
		return instance;
	}
	
    @Override
    public int compareTo(EV3SharedObject o)
    {
        return this.hashCode() - o.hashCode();
    }

	@Override
	public void run() {
		// TODO Auto-generated method stub
		
	}
	
	public boolean isPortAMotor() {
		return PortAMotor;
	}

	public void setPortAMotor(boolean portAMotor) {
		PortAMotor = portAMotor;
	}

	public boolean isPortBMotor() {
		return PortBMotor;
	}

	public void setPortBMotor(boolean portBMotor) {
		PortBMotor = portBMotor;
	}

	public boolean isPortCMotor() {
		return PortCMotor;
	}

	public void setPortCMotor(boolean portCMotor) {
		PortCMotor = portCMotor;
	}

	public boolean isPortDMotor() {
		return PortDMotor;
	}

	public void setPortDMotor(boolean portDMotor) {
		PortDMotor = portDMotor;
	}
}
