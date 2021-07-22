package com.example.ev3;

public enum SensorTypeEnum {
	TOUCH("Touch"), COLOR("Color"), ULTRA("Ultrasonic"), GYRO("Gyroscope"), IR("Infrared"), MOTOR("Motor"), NONE("None");

	private final String rep;

	private SensorTypeEnum(String s) {
		rep = s;
	}

	public String toString() {
		return rep;
	}
}