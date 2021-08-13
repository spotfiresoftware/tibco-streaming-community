/*
* Copyright © 2021. TIBCO Software Inc.
* This file is subject to the license terms contained
* in the license file that is distributed with this file.
*/
package com.tibco.ep.community.components.ev3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.j4ev3.core.Brick;
import com.j4ev3.core.LED;
import com.j4ev3.core.Motor;
import com.j4ev3.core.Sensor;
import com.j4ev3.desktop.BluetoothComm;
import com.streambase.sb.StreamBaseException;
import com.streambase.sb.operator.Parameterizable;

/**
 * Shared object linking EV3 adapters to the {@link EV3ConnectionManager}. This
 * object is initialized at runtime. This object also stores the {@link Brick}
 * object and handles all direct communication requiring the j4ev3 library.
 * 
 */
public class EV3SharedObject implements Runnable, Comparable<EV3SharedObject>, Parameterizable {

	//
	// Contains the list of all sharedObjects in use in the application
	//
	public static final List<EV3SharedObject> EV3SharedObjects = new ArrayList<>();
	//
	// Contains the list of all input adapters that are waiting to be linked with an
	// output adapter
	//
	public static final Map<ISharableAdapter, EV3SharedObject> adaptersInLimbo = new HashMap<>();

	// stored shared data that exists ONLY at runtime
	private EV3ConnectionManager manager;
	protected List<ISharableAdapter> linkedAdapters = new ArrayList<>();
	public Brick robot;

	public static EV3SharedObject getSharedObjectInstance(ISharableAdapter adapter) throws StreamBaseException {
		if (adapter == null) {
			throw new StreamBaseException("Tried to find shared object instance for 'null'");
		}
		EV3SharedObject sharedObj = null;
		if (adapter instanceof EV3ConnectionManager) {
			//
			// First check if existing adapters want to link with this connection manager
			//
			Map.Entry<ISharableAdapter, EV3SharedObject> entry = matchAgainstLimboList(adapter);
			if (entry != null) {
				// Ok, so we found two adapters to link together.
				// Remove the adapter from "limbo" and use its shared object.
				//
				sharedObj = entry.getValue();
				adaptersInLimbo.remove(entry.getKey());
			} else {
				//
				// There were no adapters waiting to be linked.
				// So create a new instance of the shared object.
				//
				sharedObj = new EV3SharedObject();
			}
			sharedObj.setManager((EV3ConnectionManager) adapter);
			EV3SharedObjects.add(sharedObj);
		} else {
			//
			// First look if the corresponding ConnectionManager is already available
			//
			EV3SharedObject obj = matchAgainstSharedObjectList(adapter);
			if (obj != null) {
				//
				// Found the ConnectionManager with which to link;
				// use its instance of SharedObject.
				//
				sharedObj = obj;
			} else {
				//
				// The ConnectionManager hasn't been initialized yet.
				// Let's check if others in the limbo map are waiting for the same one.
				//
				Map.Entry<ISharableAdapter, EV3SharedObject> entry = matchAgainstLimboList(adapter);
				if (entry != null) {
					//
					// If yes, let's join that one if it doesn't already have us
					//
					sharedObj = entry.getValue();
					if (!sharedObj.linkedAdapters.contains(adapter))
						sharedObj.linkedAdapters.add(adapter);
				} else {
					//
					// No, let's add ourselves to the limbo map to wait for it:
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

		for (EV3SharedObject obj : EV3SharedObjects) {

			EV3ConnectionManager manager = obj.getManager();
			if (manager == null) {
				throw new StreamBaseException("Found a shared object without a Connection Manager!");
			}
			if (in.getContainerName().compareTo(manager.getContainerName()) != 0) {
				continue; // Can't share across containers
			}

			String sTargetConnectionManager = in.getConnectionManagerName();

			if (sTargetConnectionManager.compareTo(manager.getName()) != 0) {
				continue; // looking for a manager with a different name
			}

			//
			// Are these two adapters in the same module? If so, we have an exact match and
			// we can stop looking.
			//
			String inModuleName = in.getFullyQualifiedName().substring(0, in.getFullyQualifiedName().lastIndexOf('.'));
			String outModuleName = manager.getFullyQualifiedName().substring(0,
					manager.getFullyQualifiedName().lastIndexOf('.'));
			if (inModuleName.compareTo(outModuleName) == 0)
				return obj; // Exact match!

			//
			// Ok, we have a non-specific match -- i.e. the ConnectionManager's simple name
			// matches the string entered
			// by the user in the adapter's properties, but it's in a different module.
			// Add this entry to the list of possible matches and continue looking for an
			// exact match.
			//
			possibleMatches.add(obj);
		}

		//
		// If our search returned only one Connection Manager with the correct simple
		// name,
		// let's use that.
		//
		if (possibleMatches.size() == 1) {
			EV3SharedObject obj = possibleMatches.get(0);
			return possibleMatches.get(0);
		}

		if (possibleMatches.size() > 1) {
			StringBuilder sMsg = new StringBuilder();
			sMsg.append(String.format(
					"Adapter %s wants to link with a Connection Manager named '%s', but multiple Connection Managers with that name were found. Those managers are:",
					in.getFullyQualifiedName(), in.getConnectionManagerName()));
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

	private static Entry<ISharableAdapter, EV3SharedObject> matchAgainstLimboList(ISharableAdapter out)
			throws StreamBaseException {
		List<Map.Entry<ISharableAdapter, EV3SharedObject>> possibleMatches = new ArrayList<Map.Entry<ISharableAdapter, EV3SharedObject>>();

		for (Map.Entry<ISharableAdapter, EV3SharedObject> entry : adaptersInLimbo.entrySet()) {
			ISharableAdapter in = entry.getKey();

			if (in.getContainerName().compareTo(out.getContainerName()) != 0) {
				continue; // Can't share across containers
			}

			String sTargetConnectionManager = in.getConnectionManagerName();

			if (sTargetConnectionManager.compareTo(out.getName()) != 0) {
				continue; // adapter is looking for Connection Manager with a different name
			}

			//
			// Are these two adapters in the same module? If so, we have an exact match and
			// we can stop looking.
			//
			String inModuleName = in.getFullyQualifiedName().substring(0, in.getFullyQualifiedName().indexOf('.'));
			String outModuleName = out.getFullyQualifiedName().substring(0, out.getFullyQualifiedName().indexOf('.'));
			if (inModuleName.compareTo(outModuleName) == 0) {
				return entry; // Exact match!
			}

			//
			// Ok, we have a non-specific match -- i.e. the ConnectionManager simple name
			// matches the string entered
			// by the user in the adapter's properties, but it's in a different module.
			// Add this entry to the list of possible matches and continue looking for an
			// exact match.
			//
			possibleMatches.add(entry);
		}

		//
		// If our search returned only one Output adapter with the correct simple name,
		// let's use that.
		//
		if (possibleMatches.size() == 1) {
			return possibleMatches.get(0);
		}

		if (possibleMatches.size() > 1) {
			StringBuilder sMsg = new StringBuilder();
			sMsg.append(String.format(
					"Adapters wanting to link with ConnectionManager named '%s' were not correctly paired. Those Input adapters are:",
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

	// RUNTIME METHODS
	public void run(String address, int tries) throws StreamBaseException {
		for (int i = 0; i < tries; i++) {
			robot = new Brick(new BluetoothComm(address));
			if (robot != null) {
				break;
			} else {
				try {
					Thread.sleep(250);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
		robot.getLED().setPattern(LED.LED_ORANGE);
		if (robot == null) {
			throw new StreamBaseException(String.format("Could not connect to EV3 at MAC address %s", address));
		}
	}

	// METHODS FOR COMMUNICATING WITH ROBOT
	// These are necessary to let the adapters interface with the j4ev3 code
	// without needing direct access to its classes.

	/**
	 * Translates String ports to their byte values according to j4ev3 Sensor class
	 * 
	 * @param name of port
	 * @return byte value of port address
	 */
	public byte getSensorPortByte(String name) {
		switch (name) {
		case "A":
			return Sensor.PORT_A;
		case "B":
			return Sensor.PORT_B;
		case "C":
			return Sensor.PORT_C;
		case "D":
			return Sensor.PORT_D;
		case "1":
			return Sensor.PORT_1;
		case "2":
			return Sensor.PORT_2;
		case "3":
			return Sensor.PORT_3;
		case "4":
			return Sensor.PORT_4;
		}
		return 0x00;// TODO throw an error
	}

	public byte getMotorPortByte(String name) {
		switch (name) {
		case "A":
			return Motor.PORT_A;
		case "B":
			return Motor.PORT_B;
		case "C":
			return Motor.PORT_C;
		case "D":
			return Motor.PORT_D;
		default:
			return Motor.PORT_ALL;
		}
	}

	public int getSensorInt(SensorTypeEnum e) {
		switch (e) {
		case TOUCH:
			return Sensor.TYPE_TOUCH;
		case COLOR:
			return Sensor.TYPE_COLOR;
		case ULTRA:
			return Sensor.TYPE_ULTRASONIC;
		case GYRO:
			return Sensor.TYPE_GYRO;
		case IR:
			return Sensor.TYPE_IR;
		case MOTOR:
			return Sensor.TYPE_LARGE_MOTOR;
		default:
			return Sensor.TYPE_DONT_CHANGE;
		}
	}

	public int getSensorModeInt(String mode) {
		switch (mode) {
		// touch modes
		case EV3StatusAdapter.FIELD_TOUCH:
			return Sensor.TOUCH_TOUCH;
		case EV3StatusAdapter.FIELD_BUMPED:
			return Sensor.TOUCH_BUMPS;
		// color modes
		case EV3StatusAdapter.FIELD_COLOR:
			return Sensor.COLOR_COLOR;
		case EV3StatusAdapter.FIELD_AMBIENT:
			return Sensor.COLOR_AMBIENT;
		case EV3StatusAdapter.FIELD_REFLECT:
			return Sensor.COLOR_REFLECTED;
		// ultrasonic modes
		case EV3StatusAdapter.FIELD_DIST_CM:
			return Sensor.ULTRASONIC_CM;
		case EV3StatusAdapter.FIELD_DIST_IN:
			return Sensor.ULTRASONIC_INCH;
		case EV3StatusAdapter.FIELD_LISTEN:
			return Sensor.ULTRASONIC_LISTEN;
		// gyroscope modes
		case EV3StatusAdapter.FIELD_ANGLE:
			return Sensor.GYRO_ANGLE;
		case EV3StatusAdapter.FIELD_RATE:
			return Sensor.GYRO_RATE;
		// IR modes
		case EV3StatusAdapter.FIELD_PROXIMITY:
			return Sensor.IR_PROXIMITY;
		case EV3StatusAdapter.FIELD_REMOTE:
			return Sensor.IR_REMOTE;
		// motor modes
		case EV3StatusAdapter.FIELD_DEGREES:
			return Sensor.LARGE_MOTOR_DEGREE;
		case EV3StatusAdapter.FIELD_POWER:
			return Sensor.LARGE_MOTOR_POWER;
		case EV3StatusAdapter.FIELD_ROTATION:
			return Sensor.LARGE_MOTOR_ROTATION;
		default:
			return Sensor.MODE_DONT_CHANGE;
		}
	}

	public byte getLEDPattern(Boolean on, Boolean blink, String color) {
		if (!on) {
			return LED.LED_OFF;
		}
		if (!blink) {
			return getLEDPattern(on, color);
		} else {
			switch (color) {
			case EV3CommandAdapter.LED_RED:
				return LED.LED_GREEN_FLASH;
			case EV3CommandAdapter.LED_ORANGE:
				return LED.LED_ORANGE_FLASH;
			default: // default case treated as green
				return LED.LED_GREEN_FLASH;
			}
		}
	}

	public byte getLEDPattern(Boolean on, String color) {
		if (!on) {
			return LED.LED_OFF;
		}
		switch (color) {
		case EV3CommandAdapter.LED_RED:
			return LED.LED_RED;
		case EV3CommandAdapter.LED_ORANGE:
			return LED.LED_ORANGE;
		default: // default case treated as green
			return LED.LED_GREEN;
		}
	}

	// GETTERS & SETTERS

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
