//
// Copyright (c) 2004-2009 StreamBase Systems, Inc. All rights reserved.
//
package com.example.ev3;

import com.j4ev3.core.*;
import com.j4ev3.desktop.*;
import com.streambase.sb.*;
import com.streambase.sb.operator.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.streambase.sb.StreamBaseException;
import com.streambase.sb.adapter.InputAdapter;
import com.streambase.sb.adapter.OutputAdapter;
import com.streambase.sb.operator.Operator;


public class EV3SharedObject implements Runnable, Comparable<EV3SharedObject>, Parameterizable
{
	private static EV3SharedObject instance = new EV3SharedObject();
	
	//stored shared data
	protected EV3ConnectionManager manager;
	public List<Operator> operators = new ArrayList<Operator>();
	public Brick robot;
	
	
	private EV3SharedObject() {
		manager = null;
	}
	
	public static EV3SharedObject getEV3SharedObject() {
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
	
	public void run(String address) {
		robot = new Brick(new BluetoothComm(address));
	}
	
	//getters & setters (checked)
	public void setManager(EV3ConnectionManager m) {
		this.manager = m;
	}
	
	public EV3ConnectionManager getManager() {
		return this.manager;
	}

}
