package com.example.ev3;

import java.util.HashMap;

import com.j4ev3.core.*;
import com.streambase.sb.*;
import com.streambase.sb.operator.*;
import com.streambase.sb.util.Util;

/**
 * EV3 "input" adapter object. This adapter allows for monitoring the values of
 * the motors and sensors connected to the MINDSTORMS EV3 brick.
 * 
 * Requires a {@link EV3ConnectionManager} in the instance to work.
 * 
 * @author <a href="mailto:oblaufus@tibco.com">Owen Blaufuss</a>
 */
public class EV3StatusAdapter extends Operator implements Parameterizable, ISharableAdapter, Runnable {

	public static final long serialVersionUID = 1623698966801L;
	// Properties
	private OutputTypeEnum OutputType;

	// Enum definition for property OutputType
	public static enum OutputTypeEnum {
		RAW("Raw"), SI("SI"), PERCENT("Percent");

		private final String rep;

		private OutputTypeEnum(String s) {
			rep = s;
		}

		public String toString() {
			return rep;
		}
	}

	private boolean StreamPortA;
	private boolean StreamPortB;
	private boolean StreamPortC;
	private boolean StreamPortD;
	private boolean StreamPort1;
	private boolean StreamPort2;
	private boolean StreamPort3;
	private boolean StreamPort4;

	private SensorTypeEnum Port1Device;
	private SensorTypeEnum Port2Device;
	private SensorTypeEnum Port3Device;
	private SensorTypeEnum Port4Device;

	private boolean PortAMotor;
	private boolean PortBMotor;
	private boolean PortCMotor;
	private boolean PortDMotor;

	private EV3SharedObject connectTo;
	public String ConnectionManagerName;

	private String displayName = "MINDSTORMS EV3 Input Adapter";
	// Local variables
	private int inputPorts = 2;
	private int outputPorts = 8;
	private int nextOutputPort = 0;
	private Schema[] outputSchemas; // caches the Schemas given during init() for use at processTuple()
	private RobotPort[] botPortsInfo; // caches the schemas and port names being used
	private HashMap<String, Integer> outputPortNames;

	// Input schema definition
	private static Schema.Field FIELD_TARGET_PORT = Schema.createField(DataType.STRING, "TargetPort");
	private static Schema.Field FIELD_STREAM = Schema.createField(DataType.BOOL, "Stream");
	// private static Schema.Field FIELD_SENSOR_MODE =
	// Schema.createField(DataType.INT, "SensorMode");

	// Output schema fieldnames
	private static String FIELD_DEGREES = "Degrees";
	private static String FIELD_ROTATION = "Rotation";
	private static String FIELD_POWER = "Power";

	private static String FIELD_LEFT = "LeftButton";
	private static String FIELD_CENTER = "CenterButton";
	private static String FIELD_RIGHT = "RightButton";
	private static String FIELD_UP = "UpButton";
	private static String FIELD_DOWN = "DownButton";

	private static String FIELD_LISTEN = "Listen";
	private static String FIELD_TOUCH = "Touch";
	private static String FIELD_BUMPED = "Bumped";
	private static String FIELD_COLOR = "Color";
	private static String FIELD_REFLECT = "LightReflected";
	private static String FIELD_AMBIENT = "LightAmbient";
	private static String FIELD_DIST_CM = "Distance(cm)";
	private static String FIELD_DIST_IN = "Distance(in)";
	private static String FIELD_ANGLE = "Angle";
	private static String FIELD_RATE = "Rate";
	private static String FIELD_PROXIMITY = "Proximity";

	public EV3StatusAdapter() {
		super();
		setPortHints(inputPorts, outputPorts);

		setDisplayName("MINDSTORMS Status Adapter for EV3");
		setShortDisplayName("EV3 Status");
		setDisplayDescription(
				"The EV3 Status Adapter allows you request sensor readings from the EV3 Brick with a StreamBase module.");

		setConnectionManagerName("");
		setStreamPortA(false);
		setStreamPortB(false);
		setStreamPortC(false);
		setStreamPortD(false);

		setPort1Device(SensorTypeEnum.NONE);
		setPort2Device(SensorTypeEnum.NONE);
		setPort3Device(SensorTypeEnum.NONE);
		setPort4Device(SensorTypeEnum.NONE);

		setPortAMotor(false);
		setPortBMotor(false);
		setPortCMotor(false);
		setPortDMotor(false);
	}

	public void typecheck() throws TypecheckException {
		// typecheck: require a specific number of input ports
		requireInputPortCount(inputPorts);

		if (ConnectionManagerName.length() < 1) {
			throw new TypecheckException(String.format("The 'Linked Connection Manager Name' must not be left blank."));
		}
		if (getInputSchema(0) == null || !getInputSchema(0).hasField(FIELD_TARGET_PORT.getName())) {
			throw new TypecheckException(
					String.format("The control port schema must at least have a field named %s of type String",
							FIELD_TARGET_PORT.getName()));
		}

		int portNumber = 0;
		String[] portNames = { "A", "B", "C", "D", "1", "2", "3", "4" };

		for (int i = 0; i < portNames.length; i++) { // for each named port:
			if (isPort(portNames[i])) {// if it is set to be used;
				setOutputSchema(portNumber, getSchemaForSensorType(getPortDevice(portNames[i])));// set its schema
				portNumber++;// increment
			}
		}

		// button port
		setOutputSchema(portNumber, createButtonOutputSchema());

		outputPorts = portNumber + 1;// since the ports start at index zero the number will be one more

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
					"Command Adapter not connected to EV3 robot. Check that a Connection Manager named %s exists.",
					ConnectionManagerName));
		}

		// TODO only the first input port is processed
		if (inputPort == 0) {
			String target = tuple.getString(FIELD_TARGET_PORT.getName()).toUpperCase();
			if (outputPortNames.containsKey(target)) {
				int outputPort = outputPortNames.get(target);
				Tuple out = buildSensorTuple(target);
				sendOutput(outputPort, out);
				// change the streaming value if needed
				if (tuple.getSchema().hasField(FIELD_STREAM.getName())) {
					botPortsInfo[outputPort].setStreaming(tuple.getBoolean(FIELD_STREAM.getName()));
					;
				}
			} else {
				getLogger().warn(String.format("No output port available for target %s", target));
			}
		}
		// process button values
		if (inputPort == 1) {
			Tuple out = buildButtonTuple();
			sendOutput(outputPorts - 1, out);
		}
	}

	public boolean isPort(String s) {
		switch (s) {
		case "A":
			return isPortAMotor();
		case "B":
			return isPortBMotor();
		case "C":
			return isPortCMotor();
		case "D":
			return isPortDMotor();
		case "1":
			return getPort1Device() != SensorTypeEnum.NONE;
		case "2":
			return getPort2Device() != SensorTypeEnum.NONE;
		case "3":
			return getPort3Device() != SensorTypeEnum.NONE;
		case "4":
			return getPort4Device() != SensorTypeEnum.NONE;
		default:
			return false;
		}
	}

	public SensorTypeEnum getPortDevice(String s) {
		switch (s) {
		case "1":
			return getPort1Device();
		case "2":
			return getPort2Device();
		case "3":
			return getPort3Device();
		case "4":
			return getPort4Device();
		default:
			return SensorTypeEnum.NONE;
		}
	}

	public boolean getPortStreaming(String s) {
		switch (s) {
		case "A":
			return getStreamPortA();
		case "B":
			return getStreamPortB();
		case "C":
			return getStreamPortC();
		case "D":
			return getStreamPortD();
		case "1":
			return getStreamPort1();
		case "2":
			return getStreamPort2();
		case "3":
			return getStreamPort3();
		case "4":
			return getStreamPort4();
		default:
			return false;
		}
	}

	public Tuple buildSensorTuple(String targetPort) {

		int outputPort = outputPortNames.get(targetPort);
		int sensorType;

		// this information is helpfully cached
		byte outputPortByte = botPortsInfo[outputPort].getAddress();
		Tuple out = botPortsInfo[outputPort].getSchema().createTuple();

		if (!(botPortsInfo[outputPort] instanceof RobotSensorPort)) {
			try {
				sensorType = Sensor.TYPE_LARGE_MOTOR;
				// TODO check that this doesn't cause problems to call both motors "large"
				out = setFieldByOutputType(out, FIELD_DEGREES, outputPortByte, sensorType, Sensor.LARGE_MOTOR_DEGREE);
				out = setFieldByOutputType(out, FIELD_ROTATION, outputPortByte, sensorType,
						Sensor.LARGE_MOTOR_ROTATION);
				out = setFieldByOutputType(out, FIELD_POWER, outputPortByte, sensorType, Sensor.LARGE_MOTOR_POWER);
			} catch (Exception e) {
				getLogger().error("Error", e);
			}
		} else {
			SensorTypeEnum sensor = ((RobotSensorPort) botPortsInfo[outputPort]).getSensor();
			try {
				switch (sensor) {
				case TOUCH:
					sensorType = Sensor.TYPE_TOUCH;
					out = setFieldByOutputType(out, FIELD_TOUCH, outputPortByte, sensorType, Sensor.TOUCH_TOUCH);
					// TODO this one needs to be set as a boolean regardless
					// TODO actually what ARE these two values, and how do they differ?
					out = setFieldByOutputType(out, FIELD_BUMPED, outputPortByte, sensorType, Sensor.TOUCH_BUMPS);
					// boolean isBumped = (connectTo.robot.getSensor().getValueRaw(outputPortByte,
					// sensorType, Sensor.TOUCH_BUMPS) > 0.5);
					// out.setField(FIELD_BUMPED, isBumped);
					break;
				case COLOR:
					sensorType = Sensor.TYPE_COLOR;
					
					int color = ((int)connectTo.robot.getSensor().getValueSI(outputPortByte, sensorType, Sensor.COLOR_COLOR));
					String colorName = ColorEnum.values()[color].toString();
					out.setField(FIELD_COLOR, colorName);
					out = setFieldByOutputType(out, FIELD_REFLECT, outputPortByte, sensorType, Sensor.COLOR_REFLECTED);
					out = setFieldByOutputType(out, FIELD_AMBIENT, outputPortByte, sensorType, Sensor.COLOR_AMBIENT);
					break;
				case ULTRA:
					sensorType = Sensor.TYPE_ULTRASONIC;
					out = setFieldByOutputType(out, FIELD_DIST_CM, outputPortByte, sensorType, Sensor.ULTRASONIC_CM);
					out = setFieldByOutputType(out, FIELD_DIST_IN, outputPortByte, sensorType, Sensor.ULTRASONIC_INCH);

					boolean listen = (connectTo.robot.getSensor().getValueRaw(outputPortByte, sensorType,
							Sensor.ULTRASONIC_LISTEN) > 0.5);
					out.setField(FIELD_LISTEN, listen);
					break;
				case GYRO:
					sensorType = Sensor.TYPE_GYRO;
					out = setFieldByOutputType(out, FIELD_ANGLE, outputPortByte, sensorType, Sensor.GYRO_ANGLE);
					out = setFieldByOutputType(out, FIELD_RATE, outputPortByte, sensorType, Sensor.GYRO_RATE);
				case IR:
					sensorType = Sensor.TYPE_IR;
					out = setFieldByOutputType(out, FIELD_PROXIMITY, outputPortByte, sensorType, Sensor.IR_PROXIMITY);
				default:
					break;
				}
			} catch (Exception e) {
				getLogger().error("Error", e);
			}
		}

		return out;
	}

	public Tuple buildButtonTuple() {
		Tuple out = createButtonOutputSchema().createTuple();
		try {
			out.setField(FIELD_LEFT, connectTo.robot.getButton().buttonPressed(Button.LEFT_BUTTON));
			out.setField(FIELD_RIGHT, connectTo.robot.getButton().buttonPressed(Button.RIGHT_BUTTON));
			out.setField(FIELD_CENTER, connectTo.robot.getButton().buttonPressed(Button.ENTER_BUTTON));
			out.setField(FIELD_UP, connectTo.robot.getButton().buttonPressed(Button.UP_BUTTON));
			out.setField(FIELD_DOWN, connectTo.robot.getButton().buttonPressed(Button.DOWN_BUTTON));
		} catch (Exception e) {
			getLogger().error("Error", e);
		}
		return out;
	}

	/**
	 * Handles the setting of sensor field according to the output type as set in
	 * "properties"
	 * 
	 * @param tuple
	 * @param field
	 * @param port
	 * @param sensorType
	 * @param sensorMode
	 * @return Tuple with the appropriate field set
	 * @throws TupleException
	 */
	public Tuple setFieldByOutputType(Tuple tuple, String field, byte port, int sensorType, int sensorMode)
			throws TupleException {
		Sensor sensorRead = connectTo.robot.getSensor();
		switch (OutputType) {
		case RAW:
			tuple.setField(field, sensorRead.getValueRaw(port, sensorType, sensorMode));
			break;
		case SI:
			tuple.setField(field, sensorRead.getValueSI(port, sensorType, sensorMode));
			break;
		case PERCENT:
			tuple.setField(field, sensorRead.getValuePercent(port, sensorType, sensorMode));
			break;
		}

		return tuple;
	}

	public void init() throws StreamBaseException {
		super.init();
		// Register the object so it will be run as a thread managed by StreamBase.
		registerRunnable(this, true);

		// connect to shared object;
		connectTo = EV3SharedObject.getSharedObjectInstance(this);

		// for best performance, consider caching input or output Schema.Field objects
		// for
		// use later in processTuple()
		outputSchemas = new Schema[outputPorts];
		for (int i = 0; i < outputPorts; ++i) {
			outputSchemas[i] = getRuntimeOutputSchema(i);
		}
		botPortsInfo = new RobotPort[outputPorts - 1];// all but the button port

		// map each port name to the port number it connects to TODO
		outputPortNames = new HashMap<String, Integer>();
		int portNumber = 0;

		// for the four motors:
		String[] motorPortNames = { "A", "B", "C", "D" };
		for (int i = 0; i < motorPortNames.length; i++) {
			if (isPort(motorPortNames[i])) {
				botPortsInfo[portNumber] = initBotPort(motorPortNames[i]);
				outputPortNames.put(motorPortNames[i], portNumber);
				portNumber++;
			}
		}

		// for the four sensors:
		String[] sensorPortNames = { "1", "2", "3", "4" };
		for (int i = 0; i < sensorPortNames.length; i++) {
			if (isPort(sensorPortNames[i])) {
				botPortsInfo[portNumber] = initBotSensorPort(sensorPortNames[i]);
				outputPortNames.put(sensorPortNames[i], portNumber);
				portNumber++;
			}
		}

	}

	public RobotPort initBotPort(String name) {
		return new RobotPort(name, getPortStreaming(name), connectTo.getSensorPortByte(name),
				createMotorOutputSchema());
	}

	public RobotSensorPort initBotSensorPort(String name) {
		SensorTypeEnum type = getPortDevice(name);
		return new RobotSensorPort(name, getPortStreaming(name), connectTo.getSensorPortByte(name),
				getSchemaForSensorType(type), type);
	}

	public void run() {
		while (shouldRun()) {
			try {
				for (int i = 0; i < botPortsInfo.length; i++) {
					if (botPortsInfo[i].isStreaming()) {
						getLogger().info("Requesting tuple from port " + botPortsInfo[i].getName());
						String target = botPortsInfo[i].getName();
						int outputPort = outputPortNames.get(target);
						Tuple out = buildSensorTuple(target);
						sendOutput(outputPort, out);
					}
				}
			} catch (Exception e) {
				getLogger().error("Error", e);
			}
		}
		shutdown();
	}

	/**
	 * The shutdown method is called when the StreamBase server is in the process of
	 * shutting down.
	 */
	public void shutdown() {

	}

	/*
	 * Define types of schema:
	 * 
	 * Input: Control
	 * 
	 * Output: -ButtonOutput -MotorOutput -TouchOutput -ColorOutput -UltraOutput
	 * -GyroOutput -IROutput
	 */

	/**
	 * @return Schema
	 */
	public Schema createButtonOutputSchema() {
		Schema buttonSchema = new Schema("buttonSchema", new Schema.Field(FIELD_LEFT, CompleteDataType.forBoolean()),
				new Schema.Field(FIELD_CENTER, CompleteDataType.forBoolean()),
				new Schema.Field(FIELD_RIGHT, CompleteDataType.forBoolean()),
				new Schema.Field(FIELD_UP, CompleteDataType.forBoolean()),
				new Schema.Field(FIELD_DOWN, CompleteDataType.forBoolean()));
		return buttonSchema;
	}

	/**
	 * @return Schema
	 */
	public Schema createMotorOutputSchema() {
		CompleteDataType returnType = OutputType == OutputTypeEnum.PERCENT ? CompleteDataType.forInt()
				: CompleteDataType.forDouble();
		// if a percentage is requested, it will be an integer; otherwise a double
		Schema motorSchema = new Schema("motorSchema", new Schema.Field(FIELD_DEGREES, returnType),
				new Schema.Field(FIELD_ROTATION, returnType), new Schema.Field(FIELD_POWER, returnType));
		return motorSchema;
	}

	/**
	 * @param type
	 * @return Schema
	 */
	public Schema getSchemaForSensorType(SensorTypeEnum type) {
		switch (type) {
		case TOUCH:
			return createTouchOutputSchema();
		case COLOR:
			return createColorOutputSchema();
		case ULTRA:
			return createUltraOutputSchema();
		case GYRO:
			return createGyroOutputSchema();
		case IR:
			return createIROutputSchema();
		default:
			return createMotorOutputSchema();
		}
	}

	/**
	 * @return Schema
	 */
	public Schema createTouchOutputSchema() {
		CompleteDataType returnType = OutputType == OutputTypeEnum.PERCENT ? CompleteDataType.forInt()
				: CompleteDataType.forDouble();
		// if a percentage is requested, it will be an integer; otherwise a double
		Schema touchSchema = new Schema(SensorTypeEnum.TOUCH.toString(), new Schema.Field(FIELD_TOUCH, returnType),
				new Schema.Field(FIELD_BUMPED, returnType));
		return touchSchema;
	}

	/**
	 * @return Schema
	 */
	public Schema createColorOutputSchema() {
		CompleteDataType returnType = OutputType == OutputTypeEnum.PERCENT ? CompleteDataType.forInt()
				: CompleteDataType.forDouble();
		// if a percentage is requested, it will be an integer; otherwise a double
		Schema colorSchema = new Schema(SensorTypeEnum.COLOR.toString(),
				new Schema.Field(FIELD_COLOR, CompleteDataType.forString()),
				new Schema.Field(FIELD_REFLECT, returnType), new Schema.Field(FIELD_AMBIENT, returnType));
		return colorSchema;
	}

	public static enum ColorEnum {
		NONE("NONE"), BLACK("BLACK"), BLUE("BLUE"), GREEN("GREEN"), YELLOW("YELLOW"), RED("RED"), WHITE("WHITE"),
		BROWN("BROWN");

		private final String rep;

		private ColorEnum(String s) {
			rep = s;
		}

		public String toString() {
			return rep;
		}
	}

	/**
	 * @return Schema
	 */
	public Schema createUltraOutputSchema() {
		CompleteDataType returnType = OutputType == OutputTypeEnum.PERCENT ? CompleteDataType.forInt()
				: CompleteDataType.forDouble();
		// if a percentage is requested, it will be an integer; otherwise a double
		Schema ultraSchema = new Schema(SensorTypeEnum.ULTRA.toString(), new Schema.Field(FIELD_DIST_CM, returnType),
				new Schema.Field(FIELD_DIST_IN, returnType),
				new Schema.Field(FIELD_LISTEN, CompleteDataType.forBoolean()));
		return ultraSchema;
	}

	/**
	 * @return Schema
	 */
	public Schema createGyroOutputSchema() {
		CompleteDataType returnType = OutputType == OutputTypeEnum.PERCENT ? CompleteDataType.forInt()
				: CompleteDataType.forDouble();
		// if a percentage is requested, it will be an integer; otherwise a double
		Schema gyroSchema = new Schema(SensorTypeEnum.GYRO.toString(), new Schema.Field(FIELD_ANGLE, returnType),
				new Schema.Field(FIELD_RATE, returnType));
		return gyroSchema;
	}

	/**
	 * @return Schema
	 */
	public Schema createIROutputSchema() {
		CompleteDataType returnType = OutputType == OutputTypeEnum.PERCENT ? CompleteDataType.forInt()
				: CompleteDataType.forDouble();
		// if a percentage is requested, it will be an integer; otherwise a double
		Schema IRSchema = new Schema(SensorTypeEnum.IR.toString(), new Schema.Field(FIELD_PROXIMITY, returnType));
		return IRSchema;
	}

	/***************************************************************************************
	 * The getter and setter methods provided by the Parameterizable object. *
	 * StreamBase Studio uses them to determine the name and type of each property *
	 * and obviously, to set and get the property values. *
	 ***************************************************************************************/

	public void setOutputType(OutputTypeEnum OutputType) {
		this.OutputType = OutputType;
	}

	public OutputTypeEnum getOutputType() {
		return this.OutputType;
	}

	public void setStreamPortA(boolean StreamPortA) {
		this.StreamPortA = StreamPortA;
	}

	public boolean getStreamPortA() {
		return this.StreamPortA;
	}

	public void setStreamPortB(boolean StreamPortB) {
		this.StreamPortB = StreamPortB;
	}

	public boolean getStreamPortB() {
		return this.StreamPortB;
	}

	public void setStreamPortC(boolean StreamPortC) {
		this.StreamPortC = StreamPortC;
	}

	public boolean getStreamPortC() {
		return this.StreamPortC;
	}

	public void setStreamPortD(boolean StreamPortD) {
		this.StreamPortD = StreamPortD;
	}

	public boolean getStreamPortD() {
		return this.StreamPortD;
	}

	public void setStreamPort1(boolean StreamPort1) {
		this.StreamPort1 = StreamPort1;
	}

	public boolean getStreamPort1() {
		return this.StreamPort1;
	}

	public void setStreamPort2(boolean StreamPort2) {
		this.StreamPort2 = StreamPort2;
	}

	public boolean getStreamPort2() {
		return this.StreamPort2;
	}

	public void setStreamPort3(boolean StreamPort3) {
		this.StreamPort3 = StreamPort3;
	}

	public boolean getStreamPort3() {
		return this.StreamPort3;
	}

	public void setStreamPort4(boolean StreamPort4) {
		this.StreamPort4 = StreamPort4;
	}

	public boolean getStreamPort4() {
		return this.StreamPort4;
	}

	public int getNextOutputPort() {
		return nextOutputPort;
	}

	public void setNextOutputPort(int nextOutputPort) {
		this.nextOutputPort = nextOutputPort;
	}

	public SensorTypeEnum getPort1Device() {
		return Port1Device;
	}

	public void setPort1Device(SensorTypeEnum port1Device) {
		Port1Device = port1Device;
	}

	public SensorTypeEnum getPort2Device() {
		return Port2Device;
	}

	public void setPort2Device(SensorTypeEnum port2Device) {
		Port2Device = port2Device;
	}

	public SensorTypeEnum getPort3Device() {
		return Port3Device;
	}

	public void setPort3Device(SensorTypeEnum port3Device) {
		Port3Device = port3Device;
	}

	public SensorTypeEnum getPort4Device() {
		return Port4Device;
	}

	public void setPort4Device(SensorTypeEnum port4Device) {
		Port4Device = port4Device;
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

	/**
	 * For detailed information about shouldEnable methods, see interface
	 * Parameterizable java doc
	 * 
	 * @see Parameterizable
	 */

	public boolean shouldEnableStreamPortA() {
		return this.isPortAMotor();
	}

	public boolean shouldEnableStreamPortB() {
		return this.isPortBMotor();
	}

	public boolean shouldEnableStreamPortC() {
		return this.isPortCMotor();
	}

	public boolean shouldEnableStreamPortD() {
		return this.isPortDMotor();
	}

	public boolean shouldEnableStreamPort1() {
		return this.getPort1Device() != SensorTypeEnum.NONE;
	}

	public boolean shouldEnableStreamPort2() {
		return this.getPort2Device() != SensorTypeEnum.NONE;
	}

	public boolean shouldEnableStreamPort3() {
		return this.getPort3Device() != SensorTypeEnum.NONE;
	}

	public boolean shouldEnableStreamPort4() {
		return this.getPort4Device() != SensorTypeEnum.NONE;
	}

	@Override
	public String getConnectionManagerName() {
		return ConnectionManagerName;
	}

	public void setConnectionManagerName(String s) {
		ConnectionManagerName = s;
	}

}
