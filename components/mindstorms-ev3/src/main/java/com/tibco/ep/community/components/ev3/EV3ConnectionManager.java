/*
* Copyright © 2021. Cloud Software Group, Inc.
* This file is subject to the license terms contained
* in the license file that is distributed with this file.
*/
package com.tibco.ep.community.components.ev3;

import java.net.URL;

import com.streambase.sb.DataType;
import com.streambase.sb.Schema;
import com.streambase.sb.StreamBaseException;
import com.streambase.sb.Tuple;
import com.streambase.sb.operator.Operator;
import com.streambase.sb.operator.Parameterizable;
import com.streambase.sb.operator.TypecheckException;

/**
 * EV3 connection adapter object. This adapter establishes a shared Bluetooth
 * connection to a MINDSTORMS EV3 brick.
 * 
 * No input or output ports; an EV3 Command Adapter or EV3 Status Adapter must
 * be paired with the Connection Manager in order to interact with the robot.
 */
public class EV3ConnectionManager extends Operator implements Parameterizable, ISharableAdapter, Runnable {

	public static final long serialVersionUID = 1623849395795L;
	private String displayName = "EV3 Connection Manager";

	// Properties
	private String MACaddress;
	private int ConnectionTries;

	private EV3SharedObject connectTo;

	// Local variables
	private int inputPorts = 0;
	private int outputPorts = 1;
	private static Schema.Field FIELD_SUCCESS = Schema.createField(DataType.BOOL, "Success");
	private Schema STATUS = new Schema("", FIELD_SUCCESS);

	public EV3ConnectionManager() {
		super();
		setPortHints(inputPorts, outputPorts);
		setDisplayName(displayName);
		setShortDisplayName(this.getClass().getSimpleName());

		setDisplayName("Connection Manager for Lego(R) Mindstorms(R) EV3");
		setShortDisplayName("EV3 Connection");
		setDisplayDescription(
				"The EV3 Connection Manager establishes a shareable Bluetooth connection with the EV3 Brick.");

		setMACaddress("");
		setConnectionTries(1);
	}

	/**
	 * Delegate the icon resolution to {@link KuduIcons}
	 */
	public URL getIconResource(IconKind iconType) {
		return EV3AdapterIcons.getIconResource(iconType);
	}

	public void typecheck() throws TypecheckException {
		setOutputSchema(0, STATUS);

		if (!isValid(MACaddress)) {
			throw new PropertyTypecheckException("MACaddress",
					String.format("The adapter requires a 12-character Bluetooth MAC address."));
		}

	}

	private boolean isValid(String MAC) {
		int length = MAC.length();
		if (length != 12) {
			return false; // must be 12 characters
		}
		for (int i = 0; i < length; i++) {
			char ch = MAC.charAt(i);
			if ((ch < '0' || ch > '9') && (ch < 'A' || ch > 'F') && (ch < 'a' || ch > 'f')) { // must be a hex number
				return false;
			}
		}
		return true;
	}

	public void processTuple(int inputPort, Tuple tuple) throws StreamBaseException {
		// This operator does not have an input port and does not process tuples.
	}

	public void init() throws StreamBaseException {
		super.init();
		// connect to shared object;
		connectTo = EV3SharedObject.getSharedObjectInstance(this);
		connectTo.run(MACaddress, ConnectionTries);

		getLogger().info(String.format("Connection to EV3 at Bluetooth MAC address %s successful.", MACaddress));
		// Register the object so it will be run as a thread managed by StreamBase.
		registerRunnable(this, true);
	}

	@Override
	public void run() {
		// confirm initialization
		Tuple status = STATUS.createTuple();
		try {
			status.setBoolean(FIELD_SUCCESS.getName(), true);
			sendOutput(0, status);
		} catch (Exception e) {
			getLogger().error("Error", e);
		}

	}

	// Getters & setters
	public String getMACaddress() {
		return MACaddress;
	}

	public void setMACaddress(String settingForSharedObject) {
		this.MACaddress = settingForSharedObject;
	}

	public int getConnectionTries() {
		return ConnectionTries;
	}

	public void setConnectionTries(int connectionTries) {
		ConnectionTries = connectionTries;
	}

	@Override
	public String getConnectionManagerName() {
		return this.getName();
	}

}
