//
// Copyright (c) 2004-2009 StreamBase Systems, Inc. All rights reserved.
//
package com.example.ev3;

import com.example.ev3.EV3StatusAdapter.ColorEnum;
import com.j4ev3.core.*;
import com.j4ev3.desktop.*;
import com.streambase.sb.*;
import com.streambase.sb.operator.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import com.streambase.sb.adapter.InputAdapter;
import com.streambase.sb.adapter.OutputAdapter;
import com.streambase.sb.operator.Operator;


public class EV3SharedObject implements Runnable, Comparable<EV3SharedObject>, Parameterizable
{
	
	//
    // Contains the list of all sharedObjects in use in the application
    //
    public static final List<EV3SharedObject> EV3SharedObjects = new ArrayList<EV3SharedObject>();
    //
    // Contains the list of all input adapters that are waiting to be linked with an output adapter 
    //
    public static final Map<ISharableAdapter, EV3SharedObject> adaptersInLimbo = new HashMap<ISharableAdapter, EV3SharedObject>();
	
	//stored shared data that exists ONLY at runtime
	private EV3ConnectionManager manager;
	protected List<ISharableAdapter> linkedAdapters = new ArrayList<ISharableAdapter>();
	public Brick robot;
	
	public static EV3SharedObject getSharedObjectInstance(ISharableAdapter adapter) throws StreamBaseException{
		 if (adapter == null)
             throw new StreamBaseException("Tried to find shared object instance for 'null'");
		EV3SharedObject sharedObj = null;
		if (adapter instanceof EV3ConnectionManager) {
			//
            // First check if existing adapters want to link with this connection manager
            //
			Map.Entry<ISharableAdapter, EV3SharedObject> entry = matchAgainstLimboList(adapter);
			if (entry != null)
            {
                //TODO make this work for more than one adapter
                // Ok, so we found two adapters to link together.
                // Remove the adapter from "limbo" and use its shared object.
                //
                sharedObj = entry.getValue();
                adaptersInLimbo.remove(entry.getKey());
            }
            else
            {
                //
                // There were no adapters waiting to be linked.
                // So create a new instance of the shared object. 
                //
                sharedObj = new EV3SharedObject();
            }
            sharedObj.setManager((EV3ConnectionManager)adapter);
            EV3SharedObjects.add(sharedObj);
		}else {
			//
            // First look if the corresponding ConnectionManager is already available
            //
            EV3SharedObject obj = matchAgainstSharedObjectList(adapter);
            if (obj != null){
            	//
                // Found the ConnectionManager with which to link;
                // use its instance of SharedObject.
                //
                sharedObj = obj;
            }else {
            	//
                // The ConnectionManager hasn't been initialized yet.
            	//Let's check if others in the limbo map are waiting for the same one.
            	//
            	Map.Entry<ISharableAdapter, EV3SharedObject> entry = matchAgainstLimboList(adapter);
    			if (entry != null){
    				//
    				//If yes, let's join that one if it doesn't already have us
    				//
    				sharedObj = entry.getValue();
    				if (!sharedObj.linkedAdapters.contains(adapter)) sharedObj.linkedAdapters.add(adapter);
    			}else {
    				//
    				//No, let's add ourselves to the limbo map to wait for it:
    				//
    				sharedObj = new EV3SharedObject();
    				sharedObj.linkedAdapters.add(adapter);
                    adaptersInLimbo.put(adapter, sharedObj);
    			}
            }
		}
		return sharedObj;
	}
	
	
	private static EV3SharedObject matchAgainstSharedObjectList(ISharableAdapter in) throws StreamBaseException {
		List<EV3SharedObject> possibleMatches = new ArrayList<EV3SharedObject>();
        
        for (EV3SharedObject obj : EV3SharedObjects){
        	
        	EV3ConnectionManager manager = obj.getManager();
            if (manager == null)
                throw new StreamBaseException("Found a shared object without a Connection Manager!");
            if (in.getContainerName().compareTo(manager.getContainerName()) != 0)
                continue; // Can't share across containers
            
            String sTargetConnectionManager = in.getConnectionManagerName();
            
            if (sTargetConnectionManager.compareTo(manager.getName()) != 0)
                continue; // looking for a manager with a different name
            
            //
            // Are these two adapters in the same module? If so, we have an exact match and
            // we can stop looking.
            //
            String inModuleName = in.getFullyQualifiedName().substring(0, in.getFullyQualifiedName().lastIndexOf('.'));
            String outModuleName = manager.getFullyQualifiedName().substring(0, manager.getFullyQualifiedName().lastIndexOf('.'));
            if (inModuleName.compareTo(outModuleName) == 0)
                return obj; // Exact match!
            
            //
            // Ok, we have a non-specific match -- i.e. the ConnectionManager's simple name matches the string entered
            // by the user in the adapter's properties, but it's in a different module.
            // Add this entry to the list of possible matches and continue looking for an exact match.
            //
            possibleMatches.add(obj);
        }
        
        //
        // If our search returned only one Connection Manager with the correct simple name,
        // let's use that.
        //
        if (possibleMatches.size() == 1){
            EV3SharedObject obj = possibleMatches.get(0);
            return possibleMatches.get(0);
        }
		
        if (possibleMatches.size() > 1)
        {
            StringBuilder sMsg = new StringBuilder();
            sMsg.append(String.format("Adapter %s wants to link with a Connection Manager named '%s', but multiple Connection Managers with that name were found. Those managers are:",
                                      in.getFullyQualifiedName(),
                                      in.getConnectionManagerName()));
            for (EV3SharedObject obj : possibleMatches) {
            	 sMsg.append("\n  - " + obj.getManager().getFullyQualifiedName());
            	 for (ISharableAdapter listObj : obj.linkedAdapters) {
            		 sMsg.append("\n  -- " + listObj.getFullyQualifiedName());
            	 }
            }
            throw new StreamBaseException(sMsg.toString());
        }
        
        //
        // No matches were found
        //
        return null;
	}

	
	private static Entry<ISharableAdapter, EV3SharedObject> matchAgainstLimboList(ISharableAdapter out) throws StreamBaseException {
		List<Map.Entry<ISharableAdapter, EV3SharedObject>> possibleMatches = new ArrayList<Map.Entry<ISharableAdapter,EV3SharedObject>>();
        
        for (Map.Entry<ISharableAdapter, EV3SharedObject> entry : adaptersInLimbo.entrySet()){
        	ISharableAdapter in = entry.getKey();
        	
        	 if (in.getContainerName().compareTo(out.getContainerName()) != 0)
                 continue; // Can't share across containers
        	 
        	 String sTargetConnectionManager = in.getConnectionManagerName();
             
             if (sTargetConnectionManager.compareTo(out.getName()) != 0)
                 continue; // adapter is looking for Connection Manager with a different name
        	
             //
             // Are these two adapters in the same module? If so, we have an exact match and
             // we can stop looking.
             //
             String inModuleName = in.getFullyQualifiedName().substring(0, in.getFullyQualifiedName().indexOf('.'));
             String outModuleName = out.getFullyQualifiedName().substring(0, out.getFullyQualifiedName().indexOf('.'));
             if (inModuleName.compareTo(outModuleName) == 0)
                 return entry; // Exact match!
             
             //
             // Ok, we have a non-specific match -- i.e. the ConnectionManager simple name matches the string entered
             // by the user in the adapter's properties, but it's in a different module.
             // Add this entry to the list of possible matches and continue looking for an exact match.
             //
             possibleMatches.add(entry);
        }	

        //
        // If our search returned only one Output adapter with the correct simple name,
        // let's use that.
        //
        if (possibleMatches.size() == 1)
            return possibleMatches.get(0);
        
        if (possibleMatches.size() > 1)
        {
            StringBuilder sMsg = new StringBuilder();
            sMsg.append(String.format("Adapters wanting to link with ConnectionManager named '%s' were not correctly paired. Those Input adapters are:",
                                      out.getName()));
            for (Map.Entry<ISharableAdapter, EV3SharedObject> entry : possibleMatches)
                sMsg.append("\n  - " + entry.getKey().getFullyQualifiedName());
            throw new StreamBaseException(sMsg.toString());
        }
        
        //
        // No matches were found
        //
        return null;
	}
	


	//RUNTIME METHODS
	public void run(String address) throws StreamBaseException {
		//TODO add several tries
		int tries = 10;
		for (int i = 0; i<tries; i++) {
			robot = new Brick(new BluetoothComm(address));
			if (robot != null) {
				break;
			}
		}
		robot.getLED().setPattern(LED.LED_ORANGE);
		 if (robot == null)
             throw new StreamBaseException("Could not connect to robot at given MAC address");
	}
	
	//METHODS FOR COMMUNICATING WITH ROBOT
	/**
	 * Translates String ports to their byte values according to j4ev3 Sensor class
	 * @param name of port
	 * @return byte value of port address
	 */
	public byte getSensorPortByte(String name) {
		switch (name) {
		case "A": return Sensor.PORT_A;
		case "B": return Sensor.PORT_B;
		case "C": return Sensor.PORT_C;
		case "D": return Sensor.PORT_D;
		case "1": return Sensor.PORT_1;
		case "2": return Sensor.PORT_2;
		case "3": return Sensor.PORT_3;
		case "4": return Sensor.PORT_4;
		}
		return 0x00;//TODO throw an error
	}
	
	public byte getMotorPortByte(String name) {
		switch (name) {
		case "A": return Motor.PORT_A;
		case "B": return Motor.PORT_B;
		case "C": return Motor.PORT_C;
		case "D": return Motor.PORT_D;
		default: return Motor.PORT_ALL;
		}
	}
	

	//GETTERS & SETTERS
	
	public EV3ConnectionManager getManager() {
		return manager;
	}

	private void setManager(EV3ConnectionManager manager) {
		this.manager = manager;
	}



	@Override
	public int compareTo(EV3SharedObject o) {
		// Auto-generated method stub
		return 0;
	}


	@Override
	public void run() {
		// Auto-generated method stub
		
	}



}
