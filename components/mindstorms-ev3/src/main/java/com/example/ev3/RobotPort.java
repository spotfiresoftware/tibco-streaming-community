package com.example.ev3;

import com.streambase.sb.Schema;

public class RobotPort {

	private String name;
	private boolean streaming;
	private SensorTypeEnum sensor;
	private String mode;
	private byte address;
	private Schema schema;
	
	public RobotPort(String name, String mode, boolean streaming, byte address, Schema schema, SensorTypeEnum sensor) {
		this.name = name;
		this.streaming = streaming;
		this.address = address;
		this.schema = schema;
		this.mode = mode;
		this.sensor = sensor;
	}
	
	public RobotPort(String name, String mode, boolean streaming, byte address, Schema schema) {
		this(name, mode, streaming, address, schema, SensorTypeEnum.MOTOR);
	}
	
	public RobotPort(String name, boolean streaming, byte address, Schema schema, SensorTypeEnum sensor) {
		this(name, "", streaming, address, schema, sensor);
	}
	
	public RobotPort(String name, boolean streaming, byte address, Schema schema) {
		this(name, streaming, address, schema, SensorTypeEnum.MOTOR);
	}

	public boolean isStreaming() {
		return streaming;
	}

	public void setStreaming(boolean streaming) {
		this.streaming = streaming;
	}

	public String getMode() {
		return mode;
	}

	public void setMode(String mode) {
		this.mode = mode;
	}

	public String getName() {
		return name;
	}

	public byte getAddress() {
		return address;
	}

	public Schema getSchema() {
		return schema;
	}
	
	public SensorTypeEnum getSensor() {
		return sensor;
	}

}
