/*
* Copyright © 2021. Cloud Software Group, Inc.
* This file is subject to the license terms contained
* in the license file that is distributed with this file.
*/
package com.tibco.ep.community.components.ev3;

import com.streambase.sb.Schema;

/**
 * Object representing a physical port on the EV3 brick. Stores information
 * about its address and sensor type for the EV3StatusAdapter.
 * 
 */

public class RobotPort {

	private String name;
	private boolean streaming;
	private SensorTypeEnum sensor;
	private String mode;
	private byte address;
	private Schema schema;

	public RobotPort(String name, boolean streaming, byte address, Schema schema, SensorTypeEnum sensor, String mode) {
		this.name = name;
		this.streaming = streaming;
		this.address = address;
		this.schema = schema;
		this.mode = mode;
		this.sensor = sensor;
	}

	public RobotPort(String name, boolean streaming, byte address, Schema schema, String mode) {
		this(name, streaming, address, schema, SensorTypeEnum.MOTOR, mode);
	}

	public RobotPort(String name, boolean streaming, byte address, Schema schema, SensorTypeEnum sensor) {
		this(name, streaming, address, schema, sensor, "");
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
