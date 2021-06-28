package com.example.ev3;

import com.streambase.sb.Schema;

public class RobotSensorPort extends RobotPort {
	
	private SensorTypeEnum sensor;

	public RobotSensorPort(String name, boolean streaming, byte address, Schema scheme, SensorTypeEnum sensor) {
		super(name, streaming, address, scheme);
		this.sensor = sensor;
		// TODO Auto-generated constructor stub
	}

	public SensorTypeEnum getSensor() {
		return sensor;
	}

	
}
