package com.tibco.ep.community.components.ev3;

import java.net.URL;

import com.j4ev3.core.Sensor;
import com.streambase.sb.DataType;
import com.streambase.sb.Schema;
import com.streambase.sb.StreamBaseException;
import com.streambase.sb.Tuple;
import com.streambase.sb.operator.Operator;
import com.streambase.sb.operator.Parameterizable;
import com.streambase.sb.operator.TypecheckException;

/**
 * EV3 "output" adapter object. This adapter allows for sending commands to the
 * motors and LEDs connected to the MINDSTORMS EV3 brick.
 * 
 * Requires a {@link EV3ConnectionManager} in the instance to work.
 * 
 * Copyright © 2021. TIBCO Software Inc. This file is subject to the license
 * terms contained in the license file that is distributed with this file.
 */
public class EV3CommandAdapter extends Operator implements Parameterizable, ISharableAdapter {

	public static final long serialVersionUID = 1623944657934L;
	// Local variables
	private int inputPorts = 3;
	private int outputPorts = 1;

	// Expected motor command schema
	private static Schema.Field FIELD_COMMAND = Schema.createField(DataType.STRING, "Command");
	private boolean StreamPortA;
	private boolean StreamPortB;
	private boolean StreamPortC;
	private boolean StreamPortD;
	private boolean StreamPort1;
	private boolean StreamPort2;
	private boolean StreamPort3;
	private boolean StreamPort4;
	private static Schema.Field FIELD_COMMAND_TARGET = Schema.createField(DataType.STRING, "TargetPort");
	private static Schema.Field FIELD_COMMAND_RATE = Schema.createField(DataType.INT, "Rate");

	public static final String COMMAND_STOP = "STOP";
	public static final String COMMAND_BRAKE = "BRAKE";
	public static final String COMMAND_POWER = "POWER";
	public static final String COMMAND_SPEED = "SPEED";

	// Expected blocking motor command schema
	private static Schema.Field FIELD_COMMAND_POSE = Schema.createField(DataType.INT, "Position");
	private static Schema.Field FIELD_COMMAND_TIME = Schema.createField(DataType.INT, "Time");

	private static Schema.Field FIELD_SUCCESS = Schema.createField(DataType.BOOL, "Success");
	private static Schema.Field FIELD_SUCCESS_POSE = Schema.createField(DataType.INT, "Position(Actual)");
	private Schema BLOCKING_STATUS = new Schema("", FIELD_SUCCESS, FIELD_SUCCESS_POSE, FIELD_COMMAND_TARGET,
			FIELD_COMMAND_POSE, FIELD_COMMAND_TIME, FIELD_COMMAND_RATE);

	// Expected LED command schema
	private static Schema.Field FIELD_LED_ON = Schema.createField(DataType.BOOL, "LED");
	private static Schema.Field FIELD_LED_COLOR = Schema.createField(DataType.STRING, "Color");
	private static Schema.Field FIELD_LED_BLINK = Schema.createField(DataType.BOOL, "Blink");

	public static final String LED_RED = "RED";
	public static final String LED_ORANGE = "ORANGE";
	public static final String LED_GREEN = "GREEN";

	private EV3SharedObject connectTo;
	public String ConnectionManagerName;
	private int error = 5; // degrees off from the target position deemed "acceptable"

	public EV3CommandAdapter() {
		super();
		setPortHints(inputPorts, outputPorts);
		setDisplayName("MINDSTORMS Command Adapter for EV3");
		setShortDisplayName("EV3 Command");
		setDisplayDescription(
				"The EV3 Command adapter allows you to send motor and LED commands to the EV3 Brick from a StreamBase module.");
	}

	/**
	 * Delegate the icon resolution to {@link KuduIcons}
	 */
	public URL getIconResource(IconKind iconType) {
		return EV3AdapterIcons.getIconResource(iconType);
	}

	public void typecheck() throws TypecheckException {
		// typecheck: require a specific number of input ports
		requireInputPortCount(inputPorts);

		if (ConnectionManagerName.length() < 1) {
			throw new PropertyTypecheckException("ConnectionManagerName",
					String.format("The 'Linked Connection Manager Name' must not be left blank."));
		}

		// check motor commands
		if (getInputSchema(0) == null || !getInputSchema(0).hasField(FIELD_COMMAND.getName())
				|| getInputSchema(0).getField(getInputSchema(0).getFieldIndex(FIELD_COMMAND.getName()))
						.getElementType() != FIELD_COMMAND.getElementType()
				|| !getInputSchema(0).hasField(FIELD_COMMAND_TARGET.getName())
				|| getInputSchema(0).getField(getInputSchema(0).getFieldIndex(FIELD_COMMAND_TARGET.getName()))
						.getElementType() != FIELD_COMMAND_TARGET.getElementType()) {
			throw new TypecheckException(String.format(
					"The non-blocking motor control port schema must at least have fields named %s of type String and %s of type Integer",
					FIELD_COMMAND.getName(), FIELD_COMMAND_TARGET.getName()));
		}

		// check blocking motor commands
		if (getInputSchema(1) == null || !getInputSchema(1).hasField(FIELD_COMMAND_TARGET.getName())
				|| getInputSchema(1).getField(getInputSchema(1).getFieldIndex(FIELD_COMMAND_TARGET.getName()))
						.getElementType() != FIELD_COMMAND_TARGET.getElementType()) {
			throw new TypecheckException(String.format(
					"The blocking motor control port schema must at least have a field named %s of type String",
					FIELD_COMMAND_TARGET.getName()));
		}
		if (getInputSchema(1) == null || !getInputSchema(1).hasField(FIELD_COMMAND_POSE.getName())
				|| !getInputSchema(1).hasField(FIELD_COMMAND_TIME.getName())
				|| !getInputSchema(1).hasField(FIELD_COMMAND_RATE.getName())) {
			throw new TypecheckException(String.format(
					"The blocking motor control port schema must at have 3 fields %s, %s, and %s of type Integer",
					FIELD_COMMAND_POSE.getName(), FIELD_COMMAND_TIME.getName(), FIELD_COMMAND_RATE.getName()));
		}

		// set blocking motor status output schema
		setOutputSchema(0, BLOCKING_STATUS);

		// check LED
		if (getInputSchema(2) == null || !getInputSchema(2).hasField(FIELD_LED_ON.getName())
				|| getInputSchema(2).getField(getInputSchema(2).getFieldIndex(FIELD_LED_ON.getName()))
						.getElementType() != FIELD_LED_ON.getElementType()) {
			throw new TypecheckException(
					String.format("The LED control port schema must at least have a field named %s of type Boolean",
							FIELD_LED_ON.getName()));
		}

	}

	/**
	 * This method will be called by the StreamBase server for each Tuple given to
	 * the Operator to process.
	 * 
	 * @param inputPort the input port that the tuple is from (ports are zero based)
	 * @param tuple     the tuple from the given input port
	 * @throws StreamBaseException Terminates the application.
	 */
	public void processTuple(int inputPort, Tuple tuple) throws StreamBaseException {
		// ensure that it's actually connected to a robot
		if (connectTo.getManager() == null || connectTo.robot == null) {
			throw new StreamBaseException(String.format(
					"Command Adapter not connected to EV3 brick. Check that a Connection Manager named %s exists.",
					ConnectionManagerName));
		}

		// processing motor commands port
		if (inputPort == 0) {
			String command = tuple.getString(FIELD_COMMAND.getName()).toUpperCase();
			if (isValidCommand(command)) {
				// apply to single port or apply to all?
				String target = tuple.getString(FIELD_COMMAND_TARGET.getName()).toUpperCase();
				byte targetPort = connectTo.getMotorPortByte(target);
				switch (command) {
				case COMMAND_STOP:
					connectTo.robot.getMotor().stopMotor(targetPort, false);
					break;
				case COMMAND_BRAKE:
					connectTo.robot.getMotor().stopMotor(targetPort, true);
					break;
				case COMMAND_POWER:
					if (hasValidCommandValue(tuple)) {
						int val = tuple.getInt(FIELD_COMMAND_RATE.getName());
						connectTo.robot.getMotor().turnAtPower(targetPort, val);
					} else {
						sendErrorOutput(String.format("Cannot set POWER without a field named %s of type Integer",
								FIELD_COMMAND_RATE.getName()));
					}
					break;
				case COMMAND_SPEED:
					if (hasValidCommandValue(tuple)) {
						int val = tuple.getInt(FIELD_COMMAND_RATE.getName());
						connectTo.robot.getMotor().turnAtSpeed(targetPort, val);
					} else {
						sendErrorOutput(String.format("Cannot set SPEED without a field named %s of type Integer",
								FIELD_COMMAND_RATE.getName()));
					}
					break;
				default:
					break;
				}
			} else {
				getLogger().warn(
						String.format("Command %s is not a valid command (try STOP, BRAKE, POWER, or SPEED)", command));
			}
			return;
		}

		// processing blocking motor commands port
		if (inputPort == 1) {
			// apply to single port or apply to all?
			String target = tuple.getString(FIELD_COMMAND_TARGET.getName()).toUpperCase();

			// 2 of the 3 values need to be provided for this to work.
			if (tuple.getSchema().hasField(FIELD_COMMAND_POSE.getName())
					&& !tuple.isNull(FIELD_COMMAND_POSE.getName())) {
				if (tuple.getSchema().hasField(FIELD_COMMAND_TIME.getName())
						&& !tuple.isNull(FIELD_COMMAND_TIME.getName())) {
					int pose = tuple.getInt(FIELD_COMMAND_POSE.getName());
					int time = tuple.getInt(FIELD_COMMAND_TIME.getName());
					// try running the motor & report on the result
					reportBlockingStatus(blockingPoseTime(pose, time, target), tuple);
				} else if (tuple.getSchema().hasField(FIELD_COMMAND_RATE.getName())
						&& !tuple.isNull(FIELD_COMMAND_RATE.getName())) {
					int pose = tuple.getInt(FIELD_COMMAND_POSE.getName());
					int rate = tuple.getInt(FIELD_COMMAND_RATE.getName());
					// try running the motor & report on the result
					reportBlockingStatus(blockingPoseRate(pose, rate, target), tuple);
				} else {
					getLogger().warn("Motor command port needs at least 2 of 3 values: Position, Time, and Rate");
					reportBlockingStatus(false, tuple);
				}
			} else { // if the first value isn't present, the next two should be
				if (tuple.getSchema().hasField(FIELD_COMMAND_TIME.getName())
						&& !tuple.isNull(FIELD_COMMAND_TIME.getName())
						&& tuple.getSchema().hasField(FIELD_COMMAND_RATE.getName())
						&& !tuple.isNull(FIELD_COMMAND_RATE.getName())) {
					int rate = tuple.getInt(FIELD_COMMAND_RATE.getName());
					int time = tuple.getInt(FIELD_COMMAND_TIME.getName());
					// try running the motor & report on the result
					reportBlockingStatus(blockingRateTime(rate, time, target), tuple);
				} else {
					getLogger().warn("Motor command port needs at least 2 of 3 values: Position, Time, and Rate");
					reportBlockingStatus(false, tuple);
				}
			}
		}

		// processing LED commands port
		if (inputPort == 2) {
			Boolean on = tuple.getBoolean(FIELD_LED_ON.getName());
			Boolean blink = false; // by default, does not blink
			if (tuple.getSchema().hasField(FIELD_LED_BLINK.getName()) && !tuple.isNull(FIELD_LED_BLINK.getName())) {
				blink = tuple.getBoolean(FIELD_LED_BLINK.getName());
			}
			String color = ""; // by default, no valid color is treated as green
			if (tuple.getSchema().hasField(FIELD_LED_COLOR.getName()) && !tuple.isNull(FIELD_LED_COLOR.getName())) {
				color = tuple.getString(FIELD_LED_COLOR.getName()).toUpperCase();
			}

			// set LED pattern
			connectTo.robot.getLED().setPattern(connectTo.getLEDPattern(on, blink, color));
		}
	}

	// time assumed to be in miliseconds
	// rate assumed to be in degrees/second; needs to be converted to motor speed
	// rate * time = delta distance
	// deg/sec * ms/1000 = deg

	/**
	 * @param rate in degrees/second
	 * @param port motor is attached to
	 * @return speed of motor as a percentage of its maximum speed
	 */
	public int toMotorSpeed(int rate, String port) {
		byte sensorPort = connectTo.getSensorPortByte(port);
		String type = connectTo.robot.getSensor().getDeviceName(sensorPort, 2);
		int speedCap;
		if (type == "M") {
			// Medium motor maximum speed 250 rpm
			speedCap = 250;
		} else {
			// Large motor maximum speed 170 rpm
			speedCap = 170;
		}
		// (convert to degrees/second)
		speedCap = speedCap * 6;
		float percent = (float) rate / speedCap;
		getLogger().info(String.format("%d/%d = %f", rate, speedCap, percent));
		return (int) Math.ceil(100 * percent);
	}

	public boolean blockingPoseTime(int p, int t, String port) {
		byte motorPort = connectTo.getMotorPortByte(port);
		byte sensorPort = connectTo.getSensorPortByte(port);
		// get starting pose.
		int pStart = (int) connectTo.robot.getSensor().getValueSI(sensorPort, Sensor.TYPE_LARGE_MOTOR,
				Sensor.LARGE_MOTOR_DEGREE);
		// distance needed to travel, approximately
		int pDiff = p - pStart;

		getLogger().info(String.format("Blocking: traveling distance %d, in %d seconds", pDiff, t));

		// determine speed needed to reach desired pose in time
		int rDegSec = pDiff * 1000 / t; // rate is in seconds presumably and not ms

		int r = toMotorSpeed(rDegSec, port);
		connectTo.robot.getMotor().timeAtSpeed(motorPort, r, 0, t, 0, true);
		// blocking while the motor runs
		try {
			Thread.sleep(Math.abs(t));
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		// check how close we are now & report success if within the range of acceptable
		// error
		int pFinish = (int) connectTo.robot.getSensor().getValueSI(sensorPort, Sensor.TYPE_LARGE_MOTOR,
				Sensor.LARGE_MOTOR_DEGREE);
		if (r > 100 || r < -100) {
			getLogger().warn("Rate required for blocking motor command exceeds possible motor speeds");
			return false;
		}
		return Math.abs(p - pFinish) <= error;
	}

	public boolean blockingPoseRate(int p, int r, String port) {
		byte motorPort = connectTo.getMotorPortByte(port);
		byte sensorPort = connectTo.getSensorPortByte(port);

		// get starting pose. (For now, always use raw value.)
		int pStart = (int) connectTo.robot.getSensor().getValueSI(sensorPort, Sensor.TYPE_LARGE_MOTOR,
				Sensor.LARGE_MOTOR_DEGREE);
		// distance needed to travel, approximately
		int pDiff = p - pStart;

		// determine time needed to reach desired pose at this speed
		int t = Math.abs(pDiff * 1000 / r);

		int runRate = toMotorSpeed(r, port);

		getLogger().info(String.format("Running at rate: %d", runRate));
		getLogger().info(String.format("Blocking: traveling distance %d, in %d seconds", pDiff, t));

		connectTo.robot.getMotor().timeAtSpeed(motorPort, runRate, 0, t, 0, true);
		// blocking while the motor runs
		try {
			Thread.sleep(t);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		// check how close we are now & report success if within the range of acceptable
		// error
		int pFinish = (int) connectTo.robot.getSensor().getValueSI(sensorPort, Sensor.TYPE_LARGE_MOTOR,
				Sensor.LARGE_MOTOR_DEGREE);
		return Math.abs(pFinish - p) <= error;
	}

	public boolean blockingRateTime(int r, int t, String port) {
		byte motorPort = connectTo.getMotorPortByte(port);
		connectTo.robot.getMotor().timeAtSpeed(motorPort, r, 0, t, 0, true);
		// blocking while the motor runs
		try {
			Thread.sleep(t);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		return true;
	}

	public void reportBlockingStatus(boolean b, Tuple t) throws StreamBaseException {
		Tuple out = BLOCKING_STATUS.createTuple();
		out.setBoolean(FIELD_SUCCESS.getName(), b);
		out.setString(FIELD_COMMAND_TARGET.getName(), t.getString(FIELD_COMMAND_TARGET.getName()));
		byte sensorPort = connectTo.getSensorPortByte(t.getString(FIELD_COMMAND_TARGET.getName()));
		out.setInt(FIELD_SUCCESS_POSE.getName(), (int) connectTo.robot.getSensor().getValueSI(sensorPort,
				Sensor.TYPE_LARGE_MOTOR, Sensor.LARGE_MOTOR_DEGREE));
		if (!t.isNull(FIELD_COMMAND_POSE.getName())) {
			out.setInt(FIELD_COMMAND_POSE.getName(), t.getInt(FIELD_COMMAND_POSE.getName()));
		}
		if (!t.isNull(FIELD_COMMAND_TIME.getName())) {
			out.setInt(FIELD_COMMAND_TIME.getName(), t.getInt(FIELD_COMMAND_TIME.getName()));
		}
		if (!t.isNull(FIELD_COMMAND_RATE.getName())) {
			out.setInt(FIELD_COMMAND_RATE.getName(), t.getInt(FIELD_COMMAND_RATE.getName()));
		}
		sendOutput(0, out);
	}

	public boolean isValidCommand(String s) {
		return (s.equals(COMMAND_STOP) || s.equals(COMMAND_BRAKE) || s.equals(COMMAND_POWER)
				|| s.equals(COMMAND_SPEED));
	}

	public boolean isValidMotorPort(String s) {
		return (s.equals("A") || s.equals("B") || s.equals("C") || s.equals("D"));
	}

	public boolean hasValidCommandValue(Tuple t) {
		return t.getSchema().hasField(FIELD_COMMAND_RATE.getName()); // values are allowed to be negative
	}

	/**
	 * If typecheck succeeds, the init method is called before the StreamBase
	 * application is started.
	 */
	public void init() throws StreamBaseException {
		super.init();
		// connect to shared object;
		connectTo = EV3SharedObject.getSharedObjectInstance(this);
	}

	/**
	 * The shutdown method is called when the StreamBase server is in the process of
	 * shutting down.
	 */
	public void shutdown() {
		// safely shut off all motors
		byte targetPort = connectTo.getMotorPortByte("all");
		connectTo.robot.getMotor().stopMotor(targetPort, false);
	}

	@Override
	public String getConnectionManagerName() {
		return ConnectionManagerName;
	}

	public void setConnectionManagerName(String s) {
		ConnectionManagerName = s;
	}

	public int isError() {
		return error;
	}

	public void setError(int error) {
		this.error = error;
	}

}
