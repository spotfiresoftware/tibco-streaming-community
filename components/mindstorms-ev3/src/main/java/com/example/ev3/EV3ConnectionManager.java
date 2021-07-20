package com.example.ev3;

import com.streambase.sb.*;
import com.streambase.sb.operator.*;

/**
 * EV3 connection adapter object. This adapter establishes a shared Bluetooth
 * connection to a MINDSTORMS EV3 brick.
 * 
 * No input or output ports; an EV3 Command Adapter or EV3 Status Adapter must
 * be paired with the Connection Manager in order to interact with the robot.
 * 
 * @author <a href="mailto:oblaufus@tibco.com">Owen Blaufuss</a>
 */
public class EV3ConnectionManager extends Operator implements Parameterizable, ISharableAdapter {

	public static final long serialVersionUID = 1623849395795L;
	private String displayName = "EV3 Connection Manager";

	// Properties
	private String MACaddress;
	private int ConnectionTries;

	private EV3SharedObject connectTo;

	// Local variables
	private int inputPorts = 0;
	private int outputPorts = 0;
	private Schema[] outputSchemas; // caches the Schemas given during init() for use at processTuple()

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

	public void typecheck() throws TypecheckException {
		// typecheck: require a specific number of input ports
		requireInputPortCount(inputPorts);

		if (!isValid(MACaddress)) {
			throw new PropertyTypecheckException("MACaddress", String.format("The adapter requires a 12-character Bluetooth MAC address."));
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

		// for best performance, consider caching input or output Schema.Field objects
		// for
		// use later in processTuple()
		outputSchemas = new Schema[outputPorts];

		for (int i = 0; i < outputPorts; ++i) {
			outputSchemas[i] = getRuntimeOutputSchema(i);
		}

		connectTo.run(MACaddress, ConnectionTries);
		getLogger().info(String.format("Connection to EV3 at Bluetooth MAC address %s successful.", MACaddress));
	}

	/**
	 * The shutdown method is called when the StreamBase server is in the process of
	 * shutting down.
	 */
	public void shutdown() {
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
