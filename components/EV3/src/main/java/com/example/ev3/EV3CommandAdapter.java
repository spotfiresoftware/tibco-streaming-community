package com.example.ev3;

import com.streambase.sb.*;
import com.streambase.sb.adapter.python.PythonInstance;
import com.streambase.sb.operator.*;

/**
 * EV3 "output" adapter object. This adapter allows for sending commands to the motors and LEDs connected to the
 * MINDSTORMS EV3 brick.
 * 
 * Requires a {@link EV3ConnectionManager} in the instance to work.
 * 
 * @author <a href="mailto:oblaufus@tibco.com">Owen Blaufuss</a>
 */
public class EV3CommandAdapter extends Operator implements Parameterizable, ISharableAdapter {

    public static final long serialVersionUID = 1623944657934L;
    // Local variables
    private int inputPorts = 2;
    private int outputPorts = 0;

    // Expected motor command schema
    private static Schema.Field FIELD_COMMAND = Schema.createField(DataType.STRING, "Command");
    private static Schema.Field FIELD_COMMAND_TARGET = Schema.createField(DataType.STRING, "TargetPort");
    private static Schema.Field FIELD_COMMAND_VALUE = Schema.createField(DataType.INT, "Rate");

    public static final String COMMAND_STOP = "STOP";
    public static final String COMMAND_BRAKE = "BRAKE";
    public static final String COMMAND_POWER = "POWER";
    public static final String COMMAND_SPEED = "SPEED";

    // Expected LED command schema
    private static Schema.Field FIELD_LED_ON = Schema.createField(DataType.BOOL, "LED");
    private static Schema.Field FIELD_LED_COLOR = Schema.createField(DataType.STRING, "Color");
    private static Schema.Field FIELD_LED_BLINK = Schema.createField(DataType.BOOL, "Blink");

    public static final String LED_RED = "RED";
    public static final String LED_ORANGE = "ORANGE";
    public static final String LED_GREEN = "GREEN";

    private EV3SharedObject connectTo;
    public String ConnectionManagerName;

    public EV3CommandAdapter() {
        super();
        setPortHints(inputPorts, outputPorts);
        setDisplayName("MINDSTORMS Command Adapter for EV3");
        setShortDisplayName("EV3 Command");
        setDisplayDescription("The EV3 Command adapter allows you to send motor and LED commands to the EV3 Brick from a StreamBase module.");
    }

    public void typecheck() throws TypecheckException {
        // typecheck: require a specific number of input ports
        requireInputPortCount(inputPorts);

        if (ConnectionManagerName.length() < 1) {
            throw new TypecheckException(String.format("The 'Linked Connection Manager Name' must not be left blank."));
        }

        // check motor commands
        if (getInputSchema(0) == null || !getInputSchema(0).hasField(FIELD_COMMAND.getName())
                || getInputSchema(0).getField(getInputSchema(0).getFieldIndex(FIELD_COMMAND.getName())).getElementType() != FIELD_COMMAND.getElementType()
                || !getInputSchema(0).hasField(FIELD_COMMAND_TARGET.getName())
                || getInputSchema(0).getField(getInputSchema(0).getFieldIndex(FIELD_COMMAND_TARGET.getName()))
                                    .getElementType() != FIELD_COMMAND_TARGET.getElementType()) {
            throw new TypecheckException(String.format("The motor control port schema must at least have fields named %s of type String and %s of type Integer",
                                                       FIELD_COMMAND.getName(), FIELD_COMMAND_TARGET.getName()));
        }

        // check LED
        if (getInputSchema(1) == null || !getInputSchema(1).hasField(FIELD_LED_ON.getName())
                || getInputSchema(1).getField(getInputSchema(1).getFieldIndex(FIELD_LED_ON.getName())).getElementType() != FIELD_LED_ON.getElementType()) {
            throw new TypecheckException(String.format("The LED control port schema must at least have a field named %s of type Boolean",
                                                       FIELD_LED_ON.getName()));
        }

    }

    /**
     * This method will be called by the StreamBase server for each Tuple given to the Operator to process.
     * 
     * @param inputPort the input port that the tuple is from (ports are zero based)
     * @param tuple the tuple from the given input port
     * @throws StreamBaseException Terminates the application.
     */
    public void processTuple(int inputPort, Tuple tuple) throws StreamBaseException {
        // ensure that it's actually connected to a robot
        if (connectTo.getManager() == null || connectTo.robot == null) {
            throw new StreamBaseException(String.format("Command Adapter not connected to EV3 robot. Check that a Connection Manager named %s exists.",
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
                        int val = tuple.getInt(FIELD_COMMAND_VALUE.getName());
                        connectTo.robot.getMotor().turnAtPower(targetPort, val);
                    } else {
                        getLogger().warn(String.format("Cannot set POWER without a field named %s of type Integer", FIELD_COMMAND_VALUE.getName()));
                    }
                    break;
                case COMMAND_SPEED:
                    if (hasValidCommandValue(tuple)) {
                        int val = tuple.getInt(FIELD_COMMAND_VALUE.getName());
                        connectTo.robot.getMotor().turnAtSpeed(targetPort, val);
                    } else {
                        getLogger().warn(String.format("Cannot set SPEED without a field named %s of type Integer", FIELD_COMMAND_VALUE.getName()));
                    }
                    break;
                default:
                    break;
                }
            } else {
                getLogger().warn(String.format("Command %s is not a valid command (try STOP, BRAKE, POWER, or SPEED)", command));
            }
            return;
        }

        // processing LED commands port
        if (inputPort == 1) {
            Boolean on = tuple.getBoolean(FIELD_LED_ON.getName());
            Boolean blink = false; // by default, does not blink
            if (tuple.getSchema().hasField(FIELD_LED_BLINK.getName())) {
                blink = tuple.getBoolean(FIELD_LED_BLINK.getName());
            }
            String color = ""; // by default, no valid color is treated as green
            if (tuple.getSchema().hasField(FIELD_LED_COLOR.getName())) {
                color = tuple.getString(FIELD_LED_COLOR.getName()).toUpperCase();
            }

            // set LED pattern
            connectTo.robot.getLED().setPattern(connectTo.getLEDPattern(on, blink, color));
        }
    }

    public boolean isValidCommand(String s) {
        return (s.equals(COMMAND_STOP) || s.equals(COMMAND_BRAKE) || s.equals(COMMAND_POWER) || s.equals(COMMAND_SPEED));
    }

    public boolean isValidMotorPort(String s) {
        return (s.equals("A") || s.equals("B") || s.equals("C") || s.equals("D"));
    }

    public boolean hasValidCommandValue(Tuple t) {
        return t.getSchema().hasField(FIELD_COMMAND_VALUE.getName()); // values are allowed to be negative
    }

    /**
     * If typecheck succeeds, the init method is called before the StreamBase application is started.
     */
    public void init() throws StreamBaseException {
        super.init();
        // connect to shared object;
        connectTo = EV3SharedObject.getSharedObjectInstance(this);
    }

    /**
     * The shutdown method is called when the StreamBase server is in the process of shutting down.
     */
    public void shutdown() {
    	//safely shut off all motors
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

}
